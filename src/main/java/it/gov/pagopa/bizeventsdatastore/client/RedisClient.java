package it.gov.pagopa.bizeventsdatastore.client;

import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.JedisPooled;

public class RedisClient {

    private static final RedisClient INSTANCE = new RedisClient();
    private final JedisPooled jedisPooled;

    private RedisClient() {
        String redisHost = System.getenv("REDIS_HOST");
        int redisPort = System.getenv("REDIS_PORT") != null ? Integer.parseInt(System.getenv("REDIS_PORT")) : 6380;
        String redisPwd = System.getenv("REDIS_PWD");

        HostAndPort address = new HostAndPort(redisHost, redisPort);

        JedisClientConfig config = DefaultJedisClientConfig.builder()
                .ssl(true)
                .user("default")
                .password(redisPwd)
                .build();

        this.jedisPooled = new JedisPooled(address, config);
    }

    public static RedisClient getInstance() {
        return INSTANCE;
    }

    public JedisPooled getConnection() {
        return jedisPooled;
    }
}
