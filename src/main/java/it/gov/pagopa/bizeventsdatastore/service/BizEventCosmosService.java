package it.gov.pagopa.bizeventsdatastore.service;

import it.gov.pagopa.bizeventsdatastore.entity.BizEvent;
import it.gov.pagopa.bizeventsdatastore.entity.enumeration.StatusType;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewCart;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewGeneral;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewUser;
import it.gov.pagopa.bizeventsdatastore.exception.BizEventNotFoundException;
import it.gov.pagopa.bizeventsdatastore.exception.BizEventViewSaveErrorException;

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
     * Saves a viewUser on  CosmosDB database
     * @param viewUser to be saved
     * @throws BizEventViewSaveErrorException if an error occur while saving the view
     */
    void saveBizEventViewUser(BizEventsViewUser viewUser) throws BizEventViewSaveErrorException;

    /**
     * Saves a viewGeneral on  CosmosDB database
     * @param viewGeneral to be saved
     * @throws BizEventViewSaveErrorException if an error occur while saving the view
     */
    void saveBizEventViewGeneral(BizEventsViewGeneral viewGeneral) throws BizEventViewSaveErrorException;

    /**
     * Saves a viewCart on  CosmosDB database
     * @param viewCart to be saved
     * @throws BizEventViewSaveErrorException if an error occur while saving the view
     */
    void saveBizEventViewCart(BizEventsViewCart viewCart) throws BizEventViewSaveErrorException;
}
