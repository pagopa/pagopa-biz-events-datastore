package it.gov.pagopa.bizeventsdatastore.service.impl;

import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.azure.functions.ExecutionContext;
import it.gov.pagopa.bizeventsdatastore.entity.BizEvent;
import it.gov.pagopa.bizeventsdatastore.service.BizEventDeadLetterService;
import it.gov.pagopa.bizeventsdatastore.util.BlobStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;

/**
 * {@inheritDoc}
 */
public class BizEventDeadLetterServiceImpl implements BizEventDeadLetterService {

    private final Logger logger = LoggerFactory.getLogger(BizEventDeadLetterServiceImpl.class);

    /**
     * {@inheritDoc}
     */
    public void handleLastRetry(ExecutionContext context, String id, LocalDateTime now, String type, List<BizEvent> bizEvtMsg,
                                String deadLetterContainerName, TelemetryClient telemetryClient) {
        boolean deadLetterResult = this.uploadToDeadLetter(id, now, context.getInvocationId(), type, bizEvtMsg,deadLetterContainerName);
        String deadLetterLog = deadLetterResult ?
                "List<BizEvent> " + type + " message was correctly saved in the dead letter." :
                "There was an error when saving List<BizEvent> " + type + " message in the dead letter.";
        logger.error("[LAST RETRY] [{}] function with invocationId [{}] performing the last retry for events ingestion. {}",
                context.getFunctionName(),context.getInvocationId(), deadLetterLog);
        telemetryClient.trackEvent(String.format("[LAST RETRY] invocationId [%s]", context.getInvocationId()));
    }

    /**
     * {@inheritDoc}
     */
    public boolean uploadToDeadLetter(String id, LocalDateTime now, String invocationId, String type, List<BizEvent> bizEvtMsg,
                                      String deadLetterContainerName) {
        try {
            return BlobStorage.getInstance().uploadToDeadLetter(id, now, invocationId, type, bizEvtMsg,deadLetterContainerName);
        } catch (Exception e) {
            return false;
        }
    }
}
