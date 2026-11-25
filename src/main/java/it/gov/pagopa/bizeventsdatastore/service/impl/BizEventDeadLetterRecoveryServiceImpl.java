package it.gov.pagopa.bizeventsdatastore.service.impl;

import com.azure.cosmos.CosmosException;
import com.google.common.base.Strings;
import com.microsoft.applicationinsights.TelemetryClient;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@inheritDoc}
 */
public class BizEventDeadLetterRecoveryServiceImpl implements BizEventDeadLetterRecoveryService {

    static final String CONTAINER_BIZ_EVENTS_VIEWS_DEAD_LETTER_NAME = "biz-events-views-dead-letter";
    static final String METADATA_RECOVERY_ATTEMPTS = "recoveryAttempts";
    static final String METADATA_RECOVERY_STATUS = "recoveryStatus";
    static final String METADATA_RECOVERY_REASON = "recoveryReason";
    static final String RECOVERY_STATUS_FAILED = "FAILED";

    private static final String REASONS_SEPARATOR = ",";

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
                Integer.parseInt(System.getenv().getOrDefault("DL_RECOVERY_MAX_ATTEMPTS", "3")),
                Integer.parseInt(System.getenv().getOrDefault("DL_RECOVERY_MAX_BLOBS_PER_RUN", "50")));
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
    public void recoverBizEventViews(String invocationId) {
        if (this.createdFrom == null) {
            logger.warn("Dead letter recovery with invocationId [{}] skipped: DL_RECOVERY_START_DATE is not set", invocationId);
            return;
        }
        List<DeadLetterBlob> blobs = this.blobStorage.listDeadLetterBlobs(CONTAINER_BIZ_EVENTS_VIEWS_DEAD_LETTER_NAME, this.createdFrom,
                blob -> !RECOVERY_STATUS_FAILED.equals(blob.getMetadata().get(METADATA_RECOVERY_STATUS)), this.maxBlobsPerRun);

        logger.info("Dead letter recovery with invocationId [{}] found {} blobs in container [{}]",
                invocationId, blobs.size(), CONTAINER_BIZ_EVENTS_VIEWS_DEAD_LETTER_NAME);
        blobs.forEach(blob -> recoverBlob(CONTAINER_BIZ_EVENTS_VIEWS_DEAD_LETTER_NAME, blob, invocationId));
    }

    private void recoverBlob(String containerName, DeadLetterBlob blob, String invocationId) {
        Set<DeadLetterRecoveryError> permanentFailures = new LinkedHashSet<>();
        Set<DeadLetterRecoveryError> temporaryFailures = new LinkedHashSet<>();
        // the blob operation in progress, saved as reason if it fails
        DeadLetterRecoveryError blobFailure = DeadLetterRecoveryError.BLOB_READ_ERROR;

        try {
            List<BizEvent> bizEvents = this.blobStorage.readDeadLetterBlob(containerName, blob.getName());
            blobFailure = DeadLetterRecoveryError.BLOB_UPDATE_ERROR;

            for (BizEvent bizEvent : bizEvents) {
                tryRecoverBizEventView(bizEvent, blob, invocationId, permanentFailures, temporaryFailures);
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
     * Recovers the views of a biz-event of the blob: if it fails, the reason is added to the permanent or temporary failures,
     * so that the other biz-events of the blob are recovered anyway.
     */
    private void tryRecoverBizEventView(BizEvent bizEvent, DeadLetterBlob blob, String invocationId,
                                        Set<DeadLetterRecoveryError> permanentFailures, Set<DeadLetterRecoveryError> temporaryFailures) {
        try {
            DeadLetterRecoveryError failure = recoverBizEventView(bizEvent);
            if (failure != null) {
                permanentFailures.add(failure);
            }
        } catch (Exception e) {
            logger.error("Dead letter recovery with invocationId [{}] - error recovering biz-event [{}] of blob [{}]",
                    invocationId, bizEvent.getId(), blob.getName(), e);
            temporaryFailures.add(toRecoveryError(e));
        }
    }

    /**
     * @return null if the views are recovered, otherwise the reason why they can never be recovered automatically
     * @throws BizEventNotFoundException if the biz-event is not on the datastore yet: the recovery can be attempted again
     */
    private DeadLetterRecoveryError recoverBizEventView(BizEvent deadLetterBizEvent) throws BizEventNotFoundException {
        BizEvent bizEvent = this.bizEventCosmosService.getBizEvent(deadLetterBizEvent.getId());
        try {
            BizEventToViewResult views = this.bizEventToViewService.mapBizEventToView(bizEvent);
            if (views != null) {
                this.bizEventCosmosService.upsertBizEventViewGeneral(views.getGeneralView());
                this.bizEventCosmosService.upsertBizEventViewCart(views.getCartView());
                this.bizEventCosmosService.saveBizEventViewUsersPreservingVisibility(views.getUserViewList());
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

    private static OffsetDateTime parseStartDate(String startDate) {
        return Strings.isNullOrEmpty(startDate) ? null : LocalDate.parse(startDate).atStartOfDay().atOffset(ZoneOffset.UTC);
    }
}
