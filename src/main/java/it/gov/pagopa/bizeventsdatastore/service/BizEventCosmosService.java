package it.gov.pagopa.bizeventsdatastore.service;

import it.gov.pagopa.bizeventsdatastore.entity.BizEvent;
import it.gov.pagopa.bizeventsdatastore.entity.enumeration.StatusType;
import it.gov.pagopa.bizeventsdatastore.exception.BizEventNotFoundException;

/**
 * Service class that wrap the logic of Biz Event client
 */
public interface BizEventCosmosService {

    /**
     * Retrieve biz-even document from CosmosDB database
     *
     * @param eventId Biz-event id
     * @return biz-event document
     * @throws BizEventNotFoundException in case no biz-event has been found with the given idEvent, or it was found, but
     *                                   it is not in the expected status {@link StatusType#DONE} or {@link StatusType#INGESTED}
     */
    BizEvent getBizEvent(String eventId) throws BizEventNotFoundException;
}
