package it.gov.pagopa.bizeventsdatastore.service.impl;

import java.util.List;

import it.gov.pagopa.bizeventsdatastore.client.BizEventCosmosClient;
import it.gov.pagopa.bizeventsdatastore.client.impl.BizEventCosmosClientImpl;
import it.gov.pagopa.bizeventsdatastore.entity.BizEvent;
import it.gov.pagopa.bizeventsdatastore.entity.enumeration.StatusType;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewCart;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewGeneral;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewUser;
import it.gov.pagopa.bizeventsdatastore.exception.BizEventNotFoundException;
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

        if (StatusType.DONE.equals(bizEvent.getEventStatus()) || StatusType.INGESTED.equals(bizEvent.getEventStatus())) {
            return bizEvent;
        }
        throw new BizEventNotFoundException("The biz event is not in the expected status");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void saveBizEventViewUsersPreservingVisibility(List<BizEventsViewUser> viewUserList) {
        viewUserList.forEach(this.bizEventCosmosClient::saveBizEventViewUserPreservingVisibility);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean createBizEventIfAbsent(BizEvent bizEvent) {
        return this.bizEventCosmosClient.createBizEventIfAbsent(bizEvent);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> findOtherBizEventIdsOfPayment(String idPA, String iuv, String bizEventId) {
        return this.bizEventCosmosClient.findOtherBizEventIdsOfPayment(idPA, iuv, bizEventId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void upsertBizEventViewGeneral(BizEventsViewGeneral viewGeneral) {
        this.bizEventCosmosClient.upsertBizEventViewGeneral(viewGeneral);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void upsertBizEventViewCart(BizEventsViewCart viewCart) {
        this.bizEventCosmosClient.upsertBizEventViewCart(viewCart);
    }
}
