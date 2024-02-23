package it.gov.pagopa.bizeventsdatastore.service;

import java.util.logging.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import it.gov.pagopa.bizeventsdatastore.entity.BizEvent;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewCart;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewGeneral;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewUser;
import it.gov.pagopa.bizeventsdatastore.exception.PDVTokenizerException;
import it.gov.pagopa.bizeventsdatastore.model.BizEventToViewResult;

/**
 * Service that map a {@link BizEvent} to its view:
 * <ul>
 * <li> {@link BizEventsViewUser}
 * <li> {@link BizEventsViewGeneral}
 * <li> {@link BizEventsViewCart}
 */
public interface BizEventToViewService {

    /**
     * Map the provided biz-event to its views
     *
     * @param bizEvent the event to process
     * @return a {@link BizEventToViewResult} that hold the biz-event views
     * @throws PDVTokenizerException thrown if an error occur when invoking the PDV
     * @throws JsonProcessingException thrown if an error occur when mapping the PDV response
     */
    BizEventToViewResult mapBizEventToView(Logger logger, BizEvent bizEvent) throws PDVTokenizerException, JsonProcessingException;
}
