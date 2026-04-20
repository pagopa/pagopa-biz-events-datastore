package it.gov.pagopa.bizeventsdatastore.service;

import it.gov.pagopa.bizeventsdatastore.entity.BizEvent;

/**
 * Service that saves and retrieves a {@link BizEvent} to a Redis cache
 */
public interface RedisCacheService {

    /**
     * Cache lookup for a BizEvent view by its id and prefix
     *
     * @param id          the id to look for in cache
     * @param cachePrefix the prefix appended to the id before the lookup
     * @return a {@link String} that holds the biz-event views id
     */
    String findByBizEventId(String id, String cachePrefix);


    /**
     * Saves a BizEvent view id to the cache with a specified prefix
     *
     * @param id          the id to save in cache
     * @param cachePrefix the prefix appended to the id before saving
     * @return a {@link String} that holds the result of the save operation
     */
    String saveBizEventId(String id, String cachePrefix);
}
