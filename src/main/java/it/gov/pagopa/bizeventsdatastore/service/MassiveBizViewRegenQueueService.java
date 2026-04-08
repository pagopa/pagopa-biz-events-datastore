package it.gov.pagopa.bizeventsdatastore.service;

/**
 * Service class that wrap the logic of Queue client
 */
public interface MassiveBizViewRegenQueueService {

    /**
     * Send Biz Event id to the queue
     *
     * @param bizEventId Biz-event id
     * @return true if the message is successfully enqueued, false otherwise
     */
    boolean sendBizEventIdToQueue(String bizEventId);
}
