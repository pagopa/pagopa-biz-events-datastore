package it.gov.pagopa.bizeventsdatastore.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import ai.grakn.redismock.RedisServer;
import it.gov.pagopa.bizeventsdatastore.service.impl.RedisCacheServiceImpl;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.SetParams;

@ExtendWith(MockitoExtension.class)
@TestInstance(Lifecycle.PER_CLASS)
class RedisClientTest {

	@Spy
	RedisClient redisClient;

	RedisServer server;

	@BeforeAll
	void setup() throws IOException  {
		server = RedisServer.newRedisServer();
		server.start();
	}

	@Test
	void getInstance() {
		var result = RedisClient.getInstance();
		Assertions.assertNotNull(result);
	}

	@Test
	void redisClient() {

		doReturn(new JedisPool(server.getHost(), server.getBindPort())).when(redisClient).redisConnectionFactory();

		JedisPool pool = redisClient.redisConnectionFactory();
		
		try (Jedis jedis = pool.getResource()) {
	        jedis.set("foo", "bar");
	        assertEquals("bar", jedis.get("foo"));
	    }
	}
	
	@Test
	void findByBizEventIdOK() {
        
        try (MockedStatic<RedisClient> mockedRedisClient = Mockito.mockStatic(RedisClient.class)) {
            JedisPool jedisPool = mock(JedisPool.class);
            Jedis jedis = mock(Jedis.class);
            Logger logger = mock(Logger.class);
            
            when(jedisPool.getResource()).thenReturn(jedis);
            when(jedis.get("biz_test123")).thenReturn("test123");

            RedisClient mockRedisClient = mock(RedisClient.class);
            when(mockRedisClient.redisConnectionFactory()).thenReturn(jedisPool);
            mockedRedisClient.when(RedisClient::getInstance).thenReturn(mockRedisClient);

            RedisCacheServiceImpl redisCacheService = new RedisCacheServiceImpl();
            String result = redisCacheService.findByBizEventId("test123", "biz_", logger);

            assertEquals("test123", result);
            verify(jedis).get("biz_test123");
        }
        
	}
	
	@Test
	void findByBizEventIdKO() {
		
		Logger logger = Logger.getLogger("RedisClient-test-logger");
		
		// Connection error -> null return
		RedisCacheServiceImpl redisCacheServiceImpl = new RedisCacheServiceImpl();
		String result = redisCacheServiceImpl.findByBizEventId("id", "prefix", logger);
		
		assertEquals(null, result);
		
	}
	
	@Test
	void saveBizEventIdOK() {
        
		try (MockedStatic<RedisClient> mockedRedisClient = Mockito.mockStatic(RedisClient.class)) {
            JedisPool jedisPool = mock(JedisPool.class);
            Jedis jedis = mock(Jedis.class);
            Logger logger = mock(Logger.class);

            when(jedisPool.getResource()).thenReturn(jedis);
            when(jedis.set(eq("biz_test123"), eq("test123"), any(SetParams.class))).thenReturn("OK");

            RedisClient redisClient = mock(RedisClient.class);
            when(redisClient.redisConnectionFactory()).thenReturn(jedisPool);
            mockedRedisClient.when(RedisClient::getInstance).thenReturn(redisClient);

            RedisCacheServiceImpl redisCacheService = new RedisCacheServiceImpl();
            String result = redisCacheService.saveBizEventId("test123", "biz_", logger);

            assertEquals("OK", result);
            verify(jedis).set(eq("biz_test123"), eq("test123"), any(SetParams.class));
        }        
	}
	
	@Test
	void saveBizEventIdKO() {
		
		Logger logger = Logger.getLogger("RedisClient-test-logger");
		
		// Connection error -> null return
		RedisCacheServiceImpl redisCacheServiceImpl = new RedisCacheServiceImpl();
		String result = redisCacheServiceImpl.saveBizEventId("id", "prefix", logger);
		
		assertEquals(null, result);
		
	}

	@AfterAll
	void teardown() {
		server.stop();
	}

}
