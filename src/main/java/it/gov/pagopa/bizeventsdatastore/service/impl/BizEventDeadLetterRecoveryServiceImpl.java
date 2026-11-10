package it.gov.pagopa.bizeventsdatastore.service.impl;

import com.google.common.base.Strings;
import com.microsoft.applicationinsights.TelemetryClient;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@inheritDoc}
 */
public class BizEventDeadLetterRecoveryServiceImpl implements BizEventDeadLetterRecoveryService {

    static final String CONTAINER_BIZ_EVENTS_DEAD_LETTER_NAME = "biz-events-dead-letter";
    static final String CONTAINER_BIZ_EVENTS_VIEWS_DEAD_LETTER_NAME = "biz-events-views-dead-letter";
    static final String METADATA_RECOVERY_ATTEMPTS = "recoveryAttempts";
    static final String METADATA_RECOVERY_STATUS = "recoveryStatus";
    static final String METADATA_RECOVERY_REASON = "recoveryReason";
    static final String RECOVERY_STATUS_FAILED = "failed";

    private static final int DEFAULT_MAX_ATTEMPTS = 3;
    private static final int DEFAULT_MAX_BLOBS_PER_RUN = 50;

    private final Logger logger = LoggerFactory.getLogger(BizEventDeadLetterRecoveryServiceImpl.class);

    private final BlobStorage blobStorage;
    private final BizEventCosmosService bizEventCosmosService;
    private final BizEventToViewService bizEventToViewService;
    private final TelemetryClient telemetryClient;
    private final OffsetDateTime createdFrom;
    private final int maxAttempts;
    private final int maxBlobsPerRun;

    public BizEventDeadLetterRecoveryServiceImpl() {
        this(BlobStorage.getInstance(), new BizEventCosmosServiceImpl(), new BizEventToViewServiceImpl(), new TelemetryClient(),
                parseStartDate(System.getenv("DL_RECOVERY_START_DATE")),
                Integer.parseInt(System.getenv().getOrDefault("DL_RECOVERY_MAX_ATTEMPTS", String.valueOf(DEFAULT_MAX_ATTEMPTS))),
                Integer.parseInt(System.getenv().getOrDefault("DL_RECOVERY_MAX_BLOBS_PER_RUN", String.valueOf(DEFAULT_MAX_BLOBS_PER_RUN))));
    }

    BizEventDeadLetterRecoveryServiceImpl(BlobStorage blobStorage, BizEventCosmosService bizEventCosmosService,
                                          BizEventToViewService bizEventToViewService, TelemetryClient telemetryClient,
                                          OffsetDateTime createdFrom, int maxAttempts, int maxBlobsPerRun) {
        this.blobStorage = blobStorage;
        this.bizEventCosmosService = bizEventCosmosService;
        this.bizEventToViewService = bizEventToViewService;
        this.telemetryClient = telemetryClient;
        this.createdFrom = createdFrom;
        this.maxAttempts = maxAttempts;
        this.maxBlobsPerRun = maxBlobsPerRun;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<BizEvent> recoverBizEvents(String invocationId) {
        List<BizEvent> createdBizEvents = new ArrayList<>();
        recover(CONTAINER_BIZ_EVENTS_DEAD_LETTER_NAME, invocationId, bizEvent -> recoverBizEvent(bizEvent, createdBizEvents));
        return createdBizEvents;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void recoverBizEventViews(String invocationId) {
        recover(CONTAINER_BIZ_EVENTS_VIEWS_DEAD_LETTER_NAME, invocationId, this::recoverBizEventView);
    }

    private void recover(String containerName, String invocationId, EventRecovery eventRecovery) {
        if (this.createdFrom == null) {
            logger.warn("Dead letter recovery with invocationId [{}] skipped: DL_RECOVERY_START_DATE is not set", invocationId);
            return;
        }
        List<DeadLetterBlob> blobs = this.blobStorage.listDeadLetterBlobs(containerName, this.createdFrom,
                blob -> !RECOVERY_STATUS_FAILED.equals(blob.getMetadata().get(METADATA_RECOVERY_STATUS)), this.maxBlobsPerRun);

        logger.info("Dead letter recovery with invocationId [{}] found {} blobs in container [{}]", invocationId, blobs.size(), containerName);
        blobs.forEach(blob -> recoverBlob(containerName, blob, invocationId, eventRecovery));
    }

    private void recoverBlob(String containerName, DeadLetterBlob blob, String invocationId, EventRecovery eventRecovery) {
        String permanentFailure = null;
        String temporaryFailure = null;

        try {
            for (BizEvent bizEvent : this.blobStorage.readDeadLetterBlob(containerName, blob.getName())) {
                try {
                    String failure = eventRecovery.recover(bizEvent);
                    permanentFailure = failure != null ? failure : permanentFailure;
                } catch (Exception e) {
                    logger.error("Dead letter recovery with invocationId [{}] - error recovering biz-event [{}] of blob [{}]",
                            invocationId, bizEvent.getId(), blob.getName(), e);
                    temporaryFailure = "event-recovery-error";
                }
            }

            if (permanentFailure != null) {
                markAsFailed(containerName, blob, permanentFailure);
            } else if (temporaryFailure != null) {
                registerFailedAttempt(containerName, blob, temporaryFailure);
            } else {
                this.blobStorage.deleteDeadLetterBlob(containerName, blob.getName());
                logger.info("Dead letter recovery with invocationId [{}] - blob [{}] recovered", invocationId, blob.getName());
            }
        } catch (Exception e) {
            logger.error("Dead letter recovery with invocationId [{}] - error managing blob [{}]", invocationId, blob.getName(), e);
            registerFailedAttemptSafely(containerName, blob, invocationId);
        }
    }

    private String recoverBizEvent(BizEvent bizEvent, List<BizEvent> createdBizEvents) {
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

        enrich(bizEvent);
        if (this.bizEventCosmosService.createBizEventIfAbsent(bizEvent)) {
            createdBizEvents.add(bizEvent);
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

    private static OffsetDateTime parseStartDate(String startDate) {
        return Strings.isNullOrEmpty(startDate) ? null : LocalDate.parse(startDate).atStartOfDay().atOffset(ZoneOffset.UTC);
    }

    /**
     * Recovers a single event
     */
    @FunctionalInterface
    private interface EventRecovery {

        /**
         * @param bizEvent the event to recover
         * @return null if the event is recovered, otherwise the reason why it can never be recovered automatically
         * @throws Exception if the recovery can be attempted again
         */
        String recover(BizEvent bizEvent) throws Exception;
    }
}
