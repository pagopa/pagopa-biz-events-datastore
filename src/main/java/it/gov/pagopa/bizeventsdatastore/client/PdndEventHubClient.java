package it.gov.pagopa.bizeventsdatastore.client;

import it.gov.pagopa.bizeventsdatastore.entity.BizEvent;

import java.util.List;

/**
 * Client for the PDND Event Hub
 */
public interface PdndEventHubClient {

    /**
     * Sends the biz-events to the PDND Event Hub, one Event Hub event for each biz-event, waiting for the outcome.
     *
     * @param bizEvents the biz-events to send
     * @throws RuntimeException if the biz-events could not be sent
     */
    void send(List<BizEvent> bizEvents);
}
