package it.gov.pagopa.bizeventsdatastore.service.impl;

import com.azure.cosmos.CosmosException;
import com.google.common.base.Strings;
import com.microsoft.applicationinsights.TelemetryClient;
import it.gov.pagopa.bizeventsdatastore.client.PdndEventHubClient;
import it.gov.pagopa.bizeventsdatastore.client.impl.PdndEventHubClientImpl;
import it.gov.pagopa.bizeventsdatastore.entity.BizEvent;
import it.gov.pagopa.bizeventsdatastore.exception.BizEventNotFoundException;
import it.gov.pagopa.bizeventsdatastore.exception.BizEventToViewConstraintViolationException;
import it.gov.pagopa.bizeventsdatastore.exception.DeadLetterRecoveryError;
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
    static final String RECOVERY_STATUS_FAILED = "FAILED";

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
                Integer.parseInt(System.getenv().getOrDefault("DL_RECOVERY_MAX_ATTEMPTS", "3")),
                Integer.parseInt(System.getenv().getOrDefault("DL_RECOVERY_MAX_BLOBS_PER_RUN", "50")));
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
        Set<DeadLetterRecoveryError> permanentFailures = new LinkedHashSet<>();
        Set<DeadLetterRecoveryError> temporaryFailures = new LinkedHashSet<>();
        // the blob operation in progress, saved as reason if it fails
        DeadLetterRecoveryError blobFailure = DeadLetterRecoveryError.BLOB_READ_ERROR;

        try {
            List<BizEvent> bizEvents = this.blobStorage.readDeadLetterBlob(containerName, blob.getName());
            blobFailure = DeadLetterRecoveryError.BLOB_UPDATE_ERROR;
            BlobRecovery blobRecovery = blobRecoveryFactory.start(containerName, blob, bizEvents);

            for (BizEvent bizEvent : bizEvents) {
                try {
                    DeadLetterRecoveryError failure = blobRecovery.recover(bizEvent);
                    if (failure != null) {
                        permanentFailures.add(failure);
                    }
                } catch (Exception e) {
                    logger.error("Dead letter recovery with invocationId [{}] - error recovering biz-event [{}] of blob [{}]",
                            invocationId, bizEvent.getId(), blob.getName(), e);
                    temporaryFailures.add(toRecoveryError(e));
                }
            }
            DeadLetterRecoveryError completionFailure = blobRecovery.complete(invocationId);
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
                blobFailure = DeadLetterRecoveryError.BLOB_DELETE_ERROR;
                this.blobStorage.deleteDeadLetterBlob(containerName, blob.getName());
                logger.info("Dead letter recovery with invocationId [{}] - blob [{}] recovered", invocationId, blob.getName());
            }
        } catch (Exception e) {
            logger.error("Dead letter recovery with invocationId [{}] - {} on blob [{}]: {}",
                    invocationId, blobFailure, blob.getName(), blobFailure.getDetails(), e);
            registerFailedAttemptSafely(containerName, blob, invocationId, blobFailure);
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
    private DeadLetterRecoveryError checkBizEvent(BizEvent bizEvent) {
        String idPA = bizEvent.getCreditor() != null ? bizEvent.getCreditor().getIdPA() : null;
        String iuv = bizEvent.getDebtorPosition() != null ? bizEvent.getDebtorPosition().getIuv() : null;
        if (Strings.isNullOrEmpty(idPA) || Strings.isNullOrEmpty(iuv)) {
            return DeadLetterRecoveryError.MISSING_PAYMENT_KEY;
        }

        // The same payment could have been restored with a different id: creating this event would duplicate it
        List<String> otherBizEventIds = this.bizEventCosmosService.findOtherBizEventIdsOfPayment(idPA, iuv, bizEvent.getId());
        if (!otherBizEventIds.isEmpty()) {
            logger.warn("Dead letter recovery - biz-event [{}] not created: same payment of the biz-events {}", bizEvent.getId(), otherBizEventIds);
            return DeadLetterRecoveryError.SAME_PAYMENT_OF_OTHER_BIZ_EVENT;
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

    private DeadLetterRecoveryError recoverBizEventView(BizEvent deadLetterBizEvent) throws BizEventNotFoundException {
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
            return DeadLetterRecoveryError.INVALID_VIEWS;
        }
    }

    /**
     * @return the reason saved on the blob for an error that does not prevent the next attempts
     */
    private static DeadLetterRecoveryError toRecoveryError(Exception e) {
        if (e instanceof BizEventNotFoundException) {
            return DeadLetterRecoveryError.BIZ_EVENT_NOT_FOUND;
        }
        if (e instanceof CosmosException) {
            return DeadLetterRecoveryError.COSMOS_ERROR;
        }
        return DeadLetterRecoveryError.UNEXPECTED_ERROR;
    }

    private void registerFailedAttemptSafely(String containerName, DeadLetterBlob blob, String invocationId,
                                             DeadLetterRecoveryError blobFailure) {
        try {
            registerFailedAttempt(containerName, blob, blobFailure.name());
        } catch (Exception e) {
            logger.error("Dead letter recovery with invocationId [{}] - unable to save the failed attempt with reason {} on blob [{}]",
                    invocationId, blobFailure, blob.getName(), e);
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

    private static String joinReasons(Set<DeadLetterRecoveryError> temporaryFailures, Set<DeadLetterRecoveryError> permanentFailures) {
        List<DeadLetterRecoveryError> reasons = new ArrayList<>(temporaryFailures);
        reasons.addAll(permanentFailures);
        return reasons.stream().map(Enum::name).collect(Collectors.joining(REASONS_SEPARATOR));
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
        public DeadLetterRecoveryError recover(BizEvent bizEvent) {
            DeadLetterRecoveryError failure = checkBizEvent(bizEvent);
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
        public DeadLetterRecoveryError complete(String invocationId) {
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
                return DeadLetterRecoveryError.PDND_PUBLICATION_ERROR;
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
        DeadLetterRecoveryError recover(BizEvent bizEvent) throws Exception;

        /**
         * Completes the recovery after all the events of the blob have been processed
         *
         * @param invocationId the invocation id of the current execution context
         * @return null if completed, otherwise the reason why the recovery must be attempted again
         */
        default DeadLetterRecoveryError complete(String invocationId) {
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
