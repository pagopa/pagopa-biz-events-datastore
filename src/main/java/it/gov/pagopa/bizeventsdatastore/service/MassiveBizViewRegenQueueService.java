package it.gov.pagopa.bizeventsdatastore.service;

import java.util.List;

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

    /**
     * Send a batch of Biz Event ids to the queue in parallel
     *
     * @param bizEventIds list of Biz-event ids
     * @return list of ids that failed to be enqueued
     */
    List<String> sendBizEventIdsToQueueInBatch(List<String> bizEventIds);
}
