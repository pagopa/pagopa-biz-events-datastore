package it.gov.pagopa.bizeventsdatastore.client;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.JedisPooled;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RedisClient {

  private static RedisClient instance = null;
  private final String redisHost = System.getenv("REDIS_HOST");
  private final int    redisPort = System.getenv("REDIS_PORT") != null ? Integer.parseInt(System.getenv("REDIS_PORT")) : 6380;
  private final String redisPwd  = System.getenv("REDIS_PWD");
  
  public static RedisClient getInstance() {
      if (instance == null) {
          instance = new RedisClient();
      }
      return instance;
  }

  public JedisPooled redisConnectionFactory() {
	  
	  HostAndPort address = new HostAndPort(redisHost, redisPort);
	  
	  JedisClientConfig config = DefaultJedisClientConfig.builder()
              .ssl(true)
              .user("default")
              .password(redisPwd)
              .build();
	  
    return new JedisPooled(address, config);
  }

}
