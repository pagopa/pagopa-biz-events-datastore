package it.gov.pagopa.bizeventsdatastore.client;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RedisClient {

	private static final RedisClient INSTANCE = new RedisClient(); // Eager initialization for thread-safety	
	private final String redisHost = System.getenv("REDIS_HOST");
	private final int    redisPort = System.getenv("REDIS_PORT") != null ? Integer.parseInt(System.getenv("REDIS_PORT")) : 6380;
	private final String redisPwd  = System.getenv("REDIS_PWD");
	private JedisPool jedisPool;

	public static RedisClient getInstance() {
		return INSTANCE;
	}

	public synchronized JedisPool redisConnectionFactory() {

		if (jedisPool == null || jedisPool.isClosed()) {
			JedisPoolConfig poolConfig = new JedisPoolConfig();
			poolConfig.setMaxTotal(10);
			poolConfig.setMaxIdle(8);
			poolConfig.setMinIdle(1); // Keeps at least 1 connections open
			poolConfig.setTestOnBorrow(true); // Check if the connection is valid before using it
			poolConfig.setTestWhileIdle(true); // Check for inactive connections

			JedisClientConfig clientConfig = DefaultJedisClientConfig.builder()
					.ssl(true)
					.user("default")    
					.password(redisPwd)
					.build();

			// Use JedisPool with JedisClientConfig
			jedisPool = new JedisPool(poolConfig, new HostAndPort(redisHost, redisPort), clientConfig);
		}
		return jedisPool;
	}
	
	// utility method to force the jedisPool closure if necessary
	public synchronized void close() {
        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close();
        }
    }
}