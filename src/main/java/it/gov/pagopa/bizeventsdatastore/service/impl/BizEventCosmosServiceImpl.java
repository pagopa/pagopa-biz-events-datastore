package it.gov.pagopa.bizeventsdatastore.service.impl;

import com.azure.cosmos.models.CosmosItemResponse;
import com.microsoft.azure.functions.HttpStatus;
import it.gov.pagopa.bizeventsdatastore.client.BizEventCosmosClient;
import it.gov.pagopa.bizeventsdatastore.client.impl.BizEventCosmosClientImpl;
import it.gov.pagopa.bizeventsdatastore.entity.BizEvent;
import it.gov.pagopa.bizeventsdatastore.entity.enumeration.StatusType;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewCart;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewGeneral;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewUser;
import it.gov.pagopa.bizeventsdatastore.exception.BizEventNotFoundException;
import it.gov.pagopa.bizeventsdatastore.exception.BizEventViewSaveErrorException;
import it.gov.pagopa.bizeventsdatastore.service.BizEventCosmosService;

/**
 * {@inheritDoc}
 */
public class BizEventCosmosServiceImpl implements BizEventCosmosService {

    private final BizEventCosmosClient bizEventCosmosClient;

    public BizEventCosmosServiceImpl() {
        this.bizEventCosmosClient = BizEventCosmosClientImpl.getInstance();
    }

    BizEventCosmosServiceImpl(BizEventCosmosClient bizEventCosmosClient) {
        this.bizEventCosmosClient = bizEventCosmosClient;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BizEvent getBizEvent(String eventId) throws BizEventNotFoundException {
        BizEvent bizEvent = this.bizEventCosmosClient.getBizEventDocument(eventId);

        if (!StatusType.DONE.equals(bizEvent.getEventStatus()) && !StatusType.INGESTED.equals(bizEvent.getEventStatus())) {
            throw new BizEventNotFoundException("The biz event is not in the expected status");
        }
        return bizEvent;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void saveBizEventViewUser(BizEventsViewUser viewUser) throws BizEventViewSaveErrorException {
        CosmosItemResponse<BizEventsViewUser> response;
        try {
            response = this.bizEventCosmosClient.upsertBizEventViewUser(viewUser);
        } catch (Exception e) {
            throw new BizEventViewSaveErrorException("Error saving biz event view user", e);
        }
        if (response.getStatusCode() != HttpStatus.OK.value()) {
            throw new BizEventViewSaveErrorException("Error saving biz event view user: " + response.getStatusCode());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void saveBizEventViewGeneral(BizEventsViewGeneral viewGeneral) throws BizEventViewSaveErrorException {
        CosmosItemResponse<BizEventsViewGeneral> response;
        try {
            response = this.bizEventCosmosClient.upsertBizEventViewGeneral(viewGeneral);
        } catch (Exception e) {
            throw new BizEventViewSaveErrorException("Error saving biz event view general", e);
        }
        if (response.getStatusCode() != HttpStatus.OK.value()) {
            throw new BizEventViewSaveErrorException("Error saving biz event view general: " + response.getStatusCode());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void saveBizEventViewCart(BizEventsViewCart viewCart) throws BizEventViewSaveErrorException {
        CosmosItemResponse<BizEventsViewCart> response;
        try {
            response = this.bizEventCosmosClient.upsertBizEventViewCart(viewCart);
        } catch (Exception e) {
            throw new BizEventViewSaveErrorException("Error saving biz event view cart", e);
        }
        if (response.getStatusCode() != HttpStatus.OK.value()) {
            throw new BizEventViewSaveErrorException("Error saving biz event view cart: " + response.getStatusCode());
        }
    }
}
