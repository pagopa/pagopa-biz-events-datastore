package it.gov.pagopa.bizeventsdatastore.client;


import it.gov.pagopa.bizeventsdatastore.entity.BizEvent;
import it.gov.pagopa.bizeventsdatastore.exception.BizEventNotFoundException;

public interface BizEventCosmosClient {

    /**
     * Retrieve biz-even document from CosmosDB database
     *
     * @param eventId Biz-event id
     * @return biz-event document
     * @throws BizEventNotFoundException in case no biz-event has been found with the given idEvent
     */
    BizEvent getBizEventDocument(String eventId) throws BizEventNotFoundException;

}
