package it.gov.pagopa.bizeventsdatastore.service.impl;

import com.google.common.base.Strings;
import com.microsoft.applicationinsights.TelemetryClient;
import it.gov.pagopa.bizeventsdatastore.client.PdndEventHubClient;
import it.gov.pagopa.bizeventsdatastore.client.impl.PdndEventHubClientImpl;
import it.gov.pagopa.bizeventsdatastore.entity.BizEvent;
import it.gov.pagopa.bizeventsdatastore.exception.BizEventNotFoundException;
import it.gov.pagopa.bizeventsdatastore.exception.BizEventToViewConstraintViolationException;
import it.gov.pagopa.bizeventsdatastore.model.BizEventToViewResult;
import it.gov.pagopa.bizeventsdatastore.model.DeadLetterBlob;
import it.gov.pagopa.bizeventsdatastore.service.BizEventCosmosService;
import it.gov.pagopa.bizeventsdatastore.service.BizEventDeadLetterRecoveryService;
import it.gov.pagopa.bizeventsdatastore.service.BizEventToViewService;
import it.gov.pagopa.bizeventsdatastore.util.BlobStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@inheritDoc}
 */
public class BizEventDeadLetterRecoveryServiceImpl implements BizEventDeadLetterRecoveryService {

    static final String CONTAINER_BIZ_EVENTS_DEAD_LETTER_NAME = "biz-events-dead-letter";
    static final String CONTAINER_BIZ_EVENTS_VIEWS_DEAD_LETTER_NAME = "biz-events-views-dead-letter";
    static final String METADATA_RECOVERY_ATTEMPTS = "recoveryAttempts";
    static final String METADATA_RECOVERY_STATUS = "recoveryStatus";
    static final String METADATA_RECOVERY_REASON = "recoveryReason";
    static final String METADATA_PDND_PENDING_IDS = "pdndPendingIds";
    static final String RECOVERY_STATUS_FAILED = "failed";
    static final String EVENT_RECOVERY_ERROR = "event-recovery-error";
    static final String PDND_PUBLICATION_ERROR = "pdnd-publication-error";

    private static final int DEFAULT_MAX_ATTEMPTS = 3;
    private static final int DEFAULT_MAX_BLOBS_PER_RUN = 50;
    private static final String REASONS_SEPARATOR = ",";
    private static final String IDS_SEPARATOR = ",";

    private final Logger logger = LoggerFactory.getLogger(BizEventDeadLetterRecoveryServiceImpl.class);

    private final BlobStorage blobStorage;
    private final BizEventCosmosService bizEventCosmosService;
    private final BizEventToViewService bizEventToViewService;
    private final PdndEventHubClient pdndEventHubClient;
    private final TelemetryClient telemetryClient;
    private final OffsetDateTime createdFrom;
    private final int maxAttempts;
    private final int maxBlobsPerRun;

    public BizEventDeadLetterRecoveryServiceImpl() {
        this(BlobStorage.getInstance(), new BizEventCosmosServiceImpl(), new BizEventToViewServiceImpl(),
                PdndEventHubClientImpl.getInstance(), new TelemetryClient(),
                parseStartDate(System.getenv("DL_RECOVERY_START_DATE")),
                Integer.parseInt(System.getenv().getOrDefault("DL_RECOVERY_MAX_ATTEMPTS", String.valueOf(DEFAULT_MAX_ATTEMPTS))),
                Integer.parseInt(System.getenv().getOrDefault("DL_RECOVERY_MAX_BLOBS_PER_RUN", String.valueOf(DEFAULT_MAX_BLOBS_PER_RUN))));
    }

    BizEventDeadLetterRecoveryServiceImpl(BlobStorage blobStorage, BizEventCosmosService bizEventCosmosService,
                                          BizEventToViewService bizEventToViewService, PdndEventHubClient pdndEventHubClient,
                                          TelemetryClient telemetryClient, OffsetDateTime createdFrom, int maxAttempts, int maxBlobsPerRun) {
        this.blobStorage = blobStorage;
        this.bizEventCosmosService = bizEventCosmosService;
        this.bizEventToViewService = bizEventToViewService;
        this.pdndEventHubClient = pdndEventHubClient;
        this.telemetryClient = telemetryClient;
        this.createdFrom = createdFrom;
        this.maxAttempts = maxAttempts;
        this.maxBlobsPerRun = maxBlobsPerRun;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void recoverBizEvents(String invocationId) {
        recover(CONTAINER_BIZ_EVENTS_DEAD_LETTER_NAME, invocationId, this::startBizEventsRecovery);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void recoverBizEventViews(String invocationId) {
        recover(CONTAINER_BIZ_EVENTS_VIEWS_DEAD_LETTER_NAME, invocationId, (containerName, blob, bizEvents) -> this::recoverBizEventView);
    }

    private void recover(String containerName, String invocationId, BlobRecoveryFactory blobRecoveryFactory) {
        if (this.createdFrom == null) {
            logger.warn("Dead letter recovery with invocationId [{}] skipped: DL_RECOVERY_START_DATE is not set", invocationId);
            return;
        }
        List<DeadLetterBlob> blobs = this.blobStorage.listDeadLetterBlobs(containerName, this.createdFrom,
                blob -> !RECOVERY_STATUS_FAILED.equals(blob.getMetadata().get(METADATA_RECOVERY_STATUS)), this.maxBlobsPerRun);

        logger.info("Dead letter recovery with invocationId [{}] found {} blobs in container [{}]", invocationId, blobs.size(), containerName);
        blobs.forEach(blob -> recoverBlob(containerName, blob, invocationId, blobRecoveryFactory));
    }

    private void recoverBlob(String containerName, DeadLetterBlob blob, String invocationId, BlobRecoveryFactory blobRecoveryFactory) {
        Set<String> permanentFailures = new LinkedHashSet<>();
        Set<String> temporaryFailures = new LinkedHashSet<>();

        try {
            List<BizEvent> bizEvents = this.blobStorage.readDeadLetterBlob(containerName, blob.getName());
            BlobRecovery blobRecovery = blobRecoveryFactory.start(containerName, blob, bizEvents);

            for (BizEvent bizEvent : bizEvents) {
                try {
                    String failure = blobRecovery.recover(bizEvent);
                    if (failure != null) {
                        permanentFailures.add(failure);
                    }
                } catch (Exception e) {
                    logger.error("Dead letter recovery with invocationId [{}] - error recovering biz-event [{}] of blob [{}]",
                            invocationId, bizEvent.getId(), blob.getName(), e);
                    temporaryFailures.add(EVENT_RECOVERY_ERROR);
                }
            }
            String completionFailure = blobRecovery.complete(invocationId);
            if (completionFailure != null) {
                temporaryFailures.add(completionFailure);
            }

            // The temporary failures come first: the blob is marked as failed only when no event can be recovered at the next attempts
            String reason = joinReasons(temporaryFailures, permanentFailures);
            if (!temporaryFailures.isEmpty()) {
                registerFailedAttempt(containerName, blob, reason);
            } else if (!permanentFailures.isEmpty()) {
                markAsFailed(containerName, blob, reason);
            } else {
                this.blobStorage.deleteDeadLetterBlob(containerName, blob.getName());
                logger.info("Dead letter recovery with invocationId [{}] - blob [{}] recovered", invocationId, blob.getName());
            }
        } catch (Exception e) {
            logger.error("Dead letter recovery with invocationId [{}] - error managing blob [{}]", invocationId, blob.getName(), e);
            registerFailedAttemptSafely(containerName, blob, invocationId);
        }
    }

    /**
     * Saves on the blob the ids of the biz-events that could be created by this attempt, before creating them:
     * if the process stops after the creation and before the publication, the next attempt publishes them anyway.
     */
    private BlobRecovery startBizEventsRecovery(String containerName, DeadLetterBlob blob, List<BizEvent> bizEvents) {
        Set<String> previouslyPendingIds = parseIds(blob.getMetadata().get(METADATA_PDND_PENDING_IDS));
        BizEventsBlobRecovery blobRecovery = new BizEventsBlobRecovery(blob, previouslyPendingIds);

        if (!blob.getMetadata().containsKey(METADATA_PDND_PENDING_IDS)) {
            Set<String> ids = bizEvents.stream().map(BizEvent::getId).filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            Map<String, String> metadata = new HashMap<>(blob.getMetadata());
            metadata.put(METADATA_PDND_PENDING_IDS, String.join(IDS_SEPARATOR, ids));
            this.blobStorage.updateDeadLetterBlobMetadata(containerName, blob.getName(), metadata);
            
            blobRecovery.pendingIds.addAll(ids);
        }
        return blobRecovery;
    }

    /**
     * @return null if the biz-event can be created, otherwise the reason why it can never be recovered automatically
     */
    private String checkBizEvent(BizEvent bizEvent) {
        String idPA = bizEvent.getCreditor() != null ? bizEvent.getCreditor().getIdPA() : null;
        String iuv = bizEvent.getDebtorPosition() != null ? bizEvent.getDebtorPosition().getIuv() : null;
        if (Strings.isNullOrEmpty(idPA) || Strings.isNullOrEmpty(iuv)) {
            return "missing-payment-key";
        }

        // The same payment could have been restored with a different id: creating this event would duplicate it
        List<String> otherBizEventIds = this.bizEventCosmosService.findOtherBizEventIdsOfPayment(idPA, iuv, bizEvent.getId());
        if (!otherBizEventIds.isEmpty()) {
            logger.warn("Dead letter recovery - biz-event [{}] not created: same payment of the biz-events {}", bizEvent.getId(), otherBizEventIds);
            return "same-payment-of-other-biz-event";
        }
        return null;
    }

    private void enrich(BizEvent bizEvent) {
        if (bizEvent.getDebtorPosition() != null && bizEvent.getPaymentInfo() != null) {
            bizEvent.getDebtorPosition().setIur(bizEvent.getPaymentInfo().getIUR());
        }
        if (bizEvent.getTimestamp() == null) {
            bizEvent.setTimestamp(ZonedDateTime.now().toInstant().toEpochMilli());
        }
    }

    private String recoverBizEventView(BizEvent deadLetterBizEvent) throws BizEventNotFoundException {
        BizEvent bizEvent = this.bizEventCosmosService.getBizEvent(deadLetterBizEvent.getId());
        try {
            BizEventToViewResult views = this.bizEventToViewService.mapBizEventToView(bizEvent);
            if (views != null) {
                this.bizEventCosmosService.saveBizEventViewUsersPreservingVisibility(views.getUserViewList());
                this.bizEventCosmosService.upsertBizEventViewGeneral(views.getGeneralView());
                this.bizEventCosmosService.upsertBizEventViewCart(views.getCartView());
            }
            return null;
        } catch (BizEventToViewConstraintViolationException e) {
            logger.warn("Dead letter recovery - invalid views for biz-event [{}]", bizEvent.getId(), e);
            return "invalid-views";
        }
    }

    private void registerFailedAttemptSafely(String containerName, DeadLetterBlob blob, String invocationId) {
        try {
            registerFailedAttempt(containerName, blob, "blob-error");
        } catch (Exception e) {
            logger.error("Dead letter recovery with invocationId [{}] - unable to update blob [{}]", invocationId, blob.getName(), e);
        }
    }

    private void registerFailedAttempt(String containerName, DeadLetterBlob blob, String reason) {
        int attempts = getAttempts(blob) + 1;
        if (attempts >= this.maxAttempts) {
            markAsFailed(containerName, blob, reason);
            return;
        }
        Map<String, String> metadata = new HashMap<>(blob.getMetadata());
        metadata.put(METADATA_RECOVERY_ATTEMPTS, String.valueOf(attempts));
        metadata.put(METADATA_RECOVERY_REASON, reason);
        this.blobStorage.updateDeadLetterBlobMetadata(containerName, blob.getName(), metadata);
    }

    private void markAsFailed(String containerName, DeadLetterBlob blob, String reason) {
        Map<String, String> metadata = new HashMap<>(blob.getMetadata());
        metadata.put(METADATA_RECOVERY_STATUS, RECOVERY_STATUS_FAILED);
        metadata.put(METADATA_RECOVERY_REASON, reason);
        this.blobStorage.updateDeadLetterBlobMetadata(containerName, blob.getName(), metadata);

        logger.error("[DEAD LETTER RECOVERY FAILED] blob [{}] of container [{}] needs a manual check: {}", blob.getName(), containerName, reason);
        this.telemetryClient.trackEvent(String.format("[DEAD LETTER RECOVERY FAILED] container [%s]", containerName));
    }

    private int getAttempts(DeadLetterBlob blob) {
        try {
            return Integer.parseInt(blob.getMetadata().getOrDefault(METADATA_RECOVERY_ATTEMPTS, "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String joinReasons(Set<String> temporaryFailures, Set<String> permanentFailures) {
        List<String> reasons = new ArrayList<>(temporaryFailures);
        reasons.addAll(permanentFailures);
        return String.join(REASONS_SEPARATOR, reasons);
    }

    private static Set<String> parseIds(String ids) {
        if (Strings.isNullOrEmpty(ids)) {
            return new LinkedHashSet<>();
        }
        return Arrays.stream(ids.split(IDS_SEPARATOR)).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static OffsetDateTime parseStartDate(String startDate) {
        return Strings.isNullOrEmpty(startDate) ? null : LocalDate.parse(startDate).atStartOfDay().atOffset(ZoneOffset.UTC);
    }

    /**
     * Recovery of the biz-events of a blob: they are created on the datastore and the created ones are sent to PDND
     * before the blob is deleted.
     */
    private final class BizEventsBlobRecovery implements BlobRecovery {

        private final DeadLetterBlob blob;
        private final Set<String> previouslyPendingIds;
        private final Set<String> pendingIds;
        private final List<BizEvent> toPublish = new ArrayList<>();

        private BizEventsBlobRecovery(DeadLetterBlob blob, Set<String> previouslyPendingIds) {
            this.blob = blob;
            this.previouslyPendingIds = previouslyPendingIds;
            this.pendingIds = new LinkedHashSet<>(previouslyPendingIds);
        }

        @Override
        public String recover(BizEvent bizEvent) {
            String failure = checkBizEvent(bizEvent);
            if (failure != null) {
                this.pendingIds.remove(bizEvent.getId());
                return failure;
            }

            enrich(bizEvent);
            boolean created = bizEventCosmosService.createBizEventIfAbsent(bizEvent);
            
            if (created || this.previouslyPendingIds.contains(bizEvent.getId())) {
                this.toPublish.add(bizEvent);
            } else {
                this.pendingIds.remove(bizEvent.getId());
            }
            return null;
        }

        @Override
        public String complete(String invocationId) {
            if (this.toPublish.isEmpty()) {
                savePendingIdsInMetadata();
                return null;
            }
            List<String> ids = this.toPublish.stream().map(BizEvent::getId).toList();
            try {
                pdndEventHubClient.send(this.toPublish);
                logger.info("Dead letter recovery with invocationId [{}] - biz-events {} of blob [{}] sent to PDND",
                        invocationId, ids, this.blob.getName());
                ids.forEach(this.pendingIds::remove);
                return null;
            } catch (Exception e) {
                logger.error("Dead letter recovery with invocationId [{}] - unable to send the biz-events {} of blob [{}] to PDND",
                        invocationId, ids, this.blob.getName(), e);
                return PDND_PUBLICATION_ERROR;
            } finally {
                savePendingIdsInMetadata();
            }
        }

        private void savePendingIdsInMetadata() {
            if (this.pendingIds.isEmpty()) {
                this.blob.getMetadata().remove(METADATA_PDND_PENDING_IDS);
            } else {
                this.blob.getMetadata().put(METADATA_PDND_PENDING_IDS, String.join(IDS_SEPARATOR, this.pendingIds));
            }
        }
    }

    /**
     * Recovery of the events of a single blob
     */
    @FunctionalInterface
    private interface BlobRecovery {

        /**
         * @param bizEvent the event to recover
         * @return null if the event is recovered, otherwise the reason why it can never be recovered automatically
         * @throws Exception if the recovery can be attempted again
         */
        String recover(BizEvent bizEvent) throws Exception;

        /**
         * Completes the recovery after all the events of the blob have been processed
         *
         * @param invocationId the invocation id of the current execution context
         * @return null if completed, otherwise the reason why the recovery must be attempted again
         */
        default String complete(String invocationId) {
            return null;
        }
    }

    /**
     * Starts the recovery of a blob
     */
    @FunctionalInterface
    private interface BlobRecoveryFactory {

        BlobRecovery start(String containerName, DeadLetterBlob blob, List<BizEvent> bizEvents);
    }
}
