package it.gov.pagopa.bizeventsdatastore.client;


import it.gov.pagopa.bizeventsdatastore.entity.BizEvent;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewCart;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewGeneral;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewUser;
import it.gov.pagopa.bizeventsdatastore.exception.BizEventNotFoundException;

import java.util.List;

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

    /**
     * Create the biz-event document if no document with the same id exists
     *
     * @param bizEvent the biz-event to create
     * @return true if the document has been created, false if it already exists
     */
    boolean createBizEventIfAbsent(BizEvent bizEvent);

    /**
     * Retrieve the ids of the biz-events with the given creditor and IUV, excluding the given biz-event id
     *
     * @param idPA       the creditor identifier
     * @param iuv        the IUV of the debtor position
     * @param bizEventId the biz-event id to exclude
     * @return the ids of the other biz-events of the same payment
     */
    List<String> findOtherBizEventIdsOfPayment(String idPA, String iuv, String bizEventId);

    /**
     * Create or replace the biz-event general view
     *
     * @param viewGeneral the biz-event general view
     */
    void upsertBizEventViewGeneral(BizEventsViewGeneral viewGeneral);

    /**
     * Create or replace the biz-event cart view
     *
     * @param viewCart the biz-event cart view
     */
    void upsertBizEventViewCart(BizEventsViewCart viewCart);
}
