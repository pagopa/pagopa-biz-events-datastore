package it.gov.pagopa.bizeventsdatastore.client;


import it.gov.pagopa.bizeventsdatastore.entity.BizEvent;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewUser;
import it.gov.pagopa.bizeventsdatastore.exception.BizEventNotFoundException;

/**
 * Client for the CosmosDB database
 */
public interface BizEventCosmosClient {

    /**
     * Retrieve biz-even document from CosmosDB database
     *
     * @param eventId Biz-event id
     * @return biz-event document
     * @throws BizEventNotFoundException in case no biz-event has been found with the given idEvent
     */
    BizEvent getBizEventDocument(String eventId) throws BizEventNotFoundException;

    /**
     * Save the biz-event user view preserving the visibility chosen by the user.
     * The view is created if it does not exist, otherwise all its fields are updated except for the hidden flag,
     * that is set only when the given view is hidden (the final value is: existing hidden OR given hidden)
     *
     * @param viewUser the biz-event user view to save
     */
    void saveBizEventViewUserPreservingVisibility(BizEventsViewUser viewUser);
}
