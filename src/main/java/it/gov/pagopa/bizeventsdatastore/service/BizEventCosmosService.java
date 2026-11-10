package it.gov.pagopa.bizeventsdatastore.service;

import java.util.List;

import it.gov.pagopa.bizeventsdatastore.entity.BizEvent;
import it.gov.pagopa.bizeventsdatastore.entity.enumeration.StatusType;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewCart;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewGeneral;
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
