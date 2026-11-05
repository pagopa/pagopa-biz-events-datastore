package it.gov.pagopa.bizeventsdatastore.service;

import java.util.List;

import it.gov.pagopa.bizeventsdatastore.entity.BizEvent;
import it.gov.pagopa.bizeventsdatastore.entity.enumeration.StatusType;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewUser;
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

    /**
     * Save the biz-event user views preserving the visibility chosen by the user: a view already hidden
     * is never made visible again
     *
     * @param viewUserList the biz-event user views to save
     */
    void saveBizEventViewUsersPreservingVisibility(List<BizEventsViewUser> viewUserList);
}
