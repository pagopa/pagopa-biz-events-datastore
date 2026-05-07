package it.gov.pagopa.bizeventsdatastore.client;

import com.azure.core.http.rest.Response;
import com.azure.storage.queue.models.SendMessageResult;

/**
 * Client for the Queue
 */
public interface MassiveBizViewRegenQueueClient {

    /**
     * Send string message to the queue
     *
     * @param messageText Biz-event id
     * @return response from the queue
     */
    Response<SendMessageResult> sendMessageToQueue(String messageText);
}

