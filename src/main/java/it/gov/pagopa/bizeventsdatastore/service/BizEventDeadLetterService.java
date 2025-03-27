package it.gov.pagopa.bizeventsdatastore.service;

import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.azure.functions.ExecutionContext;
import it.gov.pagopa.bizeventsdatastore.entity.BizEvent;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service that handles dead letter upload and retry logic
 */
public interface BizEventDeadLetterService {

    /**
         * Handles the last retry attempt for a BizEvent ingestion, uploading the event to the dead letter if necessary.
         *
         * @param context the execution context
         * @param id the id of the BizEvent
         * @param now the current date and time
         * @param type the type of the BizEvent
         * @param bizEvtMsg the list of BizEvent messages
         * @param deadLetterContainerName the name of the dead letter container
         * @param telemetryClient the telemetry client for tracking events
         */
        void handleLastRetry(ExecutionContext context, String id, LocalDateTime now, String type, List<BizEvent> bizEvtMsg,
                             String deadLetterContainerName, TelemetryClient telemetryClient);

 /**
      * Uploads a BizEvent to the dead letter container.
      *
      * @param id the id of the BizEvent
      * @param now the current date and time
      * @param invocationId the invocation id of the current execution context
      * @param type the type of the BizEvent
      * @param bizEvtMsg the list of BizEvent messages
      * @param deadLetterContainerName the name of the dead letter container
      * @return a boolean indicating whether the upload was successful
      */
     boolean uploadToDeadLetter(String id, LocalDateTime now, String invocationId, String type, List<BizEvent> bizEvtMsg,
                                String deadLetterContainerName);
}
