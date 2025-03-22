package it.gov.pagopa.bizeventsdatastore.service;

import it.gov.pagopa.bizeventsdatastore.entity.BizEvent;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewCart;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewGeneral;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewUser;
import it.gov.pagopa.bizeventsdatastore.exception.AppException;
import it.gov.pagopa.bizeventsdatastore.model.BizEventToViewResult;

import java.util.logging.Logger;

/**
 * Service that saves and retrieves a {@link BizEvent} to a Redis cache
 */
public interface RedisCacheService {

    /**
     * Cache lookup for a BizEvent view by its id and prefix
     *
     * @param id the id to look for in cache
     * @param cachePrefix the prefix appended to the id before the lookup
     * @param logger the logger to log messages
     * @return a {@link String} that holds the biz-event views id
     */
     String findByBizEventId(String id, String cachePrefix, Logger logger) ;


    /**
     * Saves a BizEvent view id to the cache with a specified prefix
     *
     * @param id the id to save in cache
     * @param cachePrefix the prefix appended to the id before saving
     * @param logger the logger to log messages
     * @return a {@link String} that holds the result of the save operation
     */
    String saveBizEventId(String id, String cachePrefix, Logger logger);
}
