package it.gov.pagopa.bizeventsdatastore.service.impl;

import java.util.logging.Logger;

import it.gov.pagopa.bizeventsdatastore.client.RedisClient;
import it.gov.pagopa.bizeventsdatastore.service.RedisCacheService;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.SetParams;

/**
 * {@inheritDoc}
 */
public class RedisCacheServiceImpl implements RedisCacheService {

    public static final JedisPool pool = RedisClient.getInstance().redisConnectionFactory(); 

    private static final int EXPIRE_TIME_IN_MS =
            System.getenv("REDIS_EXPIRE_TIME_MS") != null ? Integer.parseInt(System.getenv("REDIS_EXPIRE_TIME_MS")) : 3600000;

    /**
     * {@inheritDoc}
     */
    @Override
    public String findByBizEventId(String id, String cachePrefix, Logger logger) {
        try (Jedis jedis = pool.getResource()){
            return jedis.get(cachePrefix+id);
        } catch (Exception e) {
            String msg = String.format("Error getting existing connection to Redis. A new one is created to GET the BizEvent message with id %s. [error message = %s]",
                    cachePrefix+id, e.getMessage());
            logger.warning(msg);
            // It tries to acquire the connection again. If it fails, a null value is returned so that the data is not discarded
            try (Jedis j = RedisClient.getInstance().redisConnectionFactory().getResource()) {
                return j.get(cachePrefix+id);
            } catch (Exception ex) {
                return null;
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public String saveBizEventId(String id, String cachePrefix, Logger logger) {
        try (Jedis jedis = pool.getResource()){
            return jedis.set(cachePrefix+id, id, new SetParams().px(EXPIRE_TIME_IN_MS));
        } catch (Exception e) {
            String msg = String.format("Error getting existing connection to Redis. A new one is created to SET the BizEvent message with id %s. [error message = %s]",
                    cachePrefix+id, e.getMessage());
            logger.warning(msg);
            // It tries to acquire the connection again. If it fails, a null value is returned so that the data is not discarded
            try (Jedis j = RedisClient.getInstance().redisConnectionFactory().getResource()){
                return j.set(cachePrefix+id, id, new SetParams().px(EXPIRE_TIME_IN_MS));
            } catch (Exception ex) {
                return null;
            }
        }
    }

}
