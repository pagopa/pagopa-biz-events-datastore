package it.gov.pagopa.bizeventsdatastore.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;

import java.io.IOException;

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
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisPooled;

@ExtendWith(MockitoExtension.class)
@TestInstance(Lifecycle.PER_CLASS)
class RedisClientTest {

	@Spy
	RedisClient redisClient;

	RedisServer server;

	@BeforeAll
	void setup() throws IOException, NoSuchFieldException, SecurityException, IllegalArgumentException,
			IllegalAccessException, InterruptedException {
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

		doReturn(new JedisPooled(new HostAndPort(server.getHost(), server.getBindPort()))).when(redisClient)
				.redisConnectionFactory();

		JedisPooled jedis = redisClient.redisConnectionFactory();
		jedis.set("foo", "bar");

		assertEquals("bar", jedis.get("foo"));

		jedis.close();

	}

	@AfterAll
	void teardown() {
		server.stop();
	}

}
