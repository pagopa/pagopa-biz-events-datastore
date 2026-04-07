package it.gov.pagopa.bizeventsdatastore.client;


import com.azure.cosmos.models.CosmosItemResponse;
import it.gov.pagopa.bizeventsdatastore.entity.BizEvent;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewCart;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewGeneral;
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
     * Update BizEventsViewUser on CosmosDB database
     *
     * @param viewUser BizEventsViewUser to update
     * @return viewUser documents
     */
    CosmosItemResponse<BizEventsViewUser> upsertBizEventViewUser(BizEventsViewUser viewUser);

    /**
     * Update BizEventsViewGeneral on CosmosDB database
     *
     * @param viewGeneral BizEventsViewGeneral to update
     * @return viewGeneral documents
     */
    CosmosItemResponse<BizEventsViewGeneral> upsertBizEventViewGeneral(BizEventsViewGeneral viewGeneral);

    /**
     * Update BizEventsViewCart on CosmosDB database
     *
     * @param viewCart BizEventsViewCart to update
     * @return viewCart documents
     */
    CosmosItemResponse<BizEventsViewCart> upsertBizEventViewCart(BizEventsViewCart viewCart);

}
