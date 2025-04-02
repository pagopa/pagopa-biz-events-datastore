package it.gov.pagopa.bizeventsdatastore.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;

import java.io.IOException;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import ai.grakn.redismock.RedisServer;
import it.gov.pagopa.bizeventsdatastore.service.impl.RedisCacheServiceImpl;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

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
	void findByBizEventId() {
		
		Logger logger = Logger.getLogger("RedisClient-test-logger");
		
		// Connection error -> null return
		RedisCacheServiceImpl redisCacheServiceImpl = new RedisCacheServiceImpl();
		String result = redisCacheServiceImpl.findByBizEventId("id", "prefix", logger);
		
		assertEquals(null, result);
		
	}
	
	@Test
	void saveBizEventId() {
		
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
