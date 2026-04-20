package it.gov.pagopa.bizeventsdatastore.service.impl;

import it.gov.pagopa.bizeventsdatastore.client.RedisClient;
import it.gov.pagopa.bizeventsdatastore.service.RedisCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.SetParams;

/**
 * {@inheritDoc}
 */
public class RedisCacheServiceImpl implements RedisCacheService {

    private final Logger logger = LoggerFactory.getLogger(RedisCacheServiceImpl.class);

    public static final JedisPool pool = RedisClient.getInstance().redisConnectionFactory(); 

    private static final int EXPIRE_TIME_IN_MS =
            System.getenv("REDIS_EXPIRE_TIME_MS") != null ? Integer.parseInt(System.getenv("REDIS_EXPIRE_TIME_MS")) : 3600000;

    /**
     * {@inheritDoc}
     */
    @Override
    public String findByBizEventId(String id, String cachePrefix) {
        try (Jedis jedis = pool.getResource()){
            return jedis.get(cachePrefix+id);
        } catch (Exception e) {
            logger.warn("Error getting existing connection to Redis. A new one is created to GET the BizEvent message with id {}. [error message = {}]",
                    cachePrefix+id, e.getMessage());
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
    public String saveBizEventId(String id, String cachePrefix) {
        try (Jedis jedis = pool.getResource()){
            return jedis.set(cachePrefix+id, id, new SetParams().px(EXPIRE_TIME_IN_MS));
        } catch (Exception e) {
            logger.warn("Error getting existing connection to Redis. A new one is created to SET the BizEvent message with id {}. [error message = {}]",
                    cachePrefix+id, e.getMessage());
            // It tries to acquire the connection again. If it fails, a null value is returned so that the data is not discarded
            try (Jedis j = RedisClient.getInstance().redisConnectionFactory().getResource()){
                return j.set(cachePrefix+id, id, new SetParams().px(EXPIRE_TIME_IN_MS));
            } catch (Exception ex) {
                return null;
            }
        }
    }

}
