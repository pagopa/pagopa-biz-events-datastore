package it.gov.pagopa.bizeventsdatastore;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.common.base.Strings;
import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.Cardinality;
import com.microsoft.azure.functions.annotation.CosmosDBOutput;
import com.microsoft.azure.functions.annotation.EventHubTrigger;
import com.microsoft.azure.functions.annotation.ExponentialBackoffRetry;
import com.microsoft.azure.functions.annotation.FunctionName;

import it.gov.pagopa.bizeventsdatastore.client.RedisClient;
import it.gov.pagopa.bizeventsdatastore.entity.BizEvent;
import it.gov.pagopa.bizeventsdatastore.util.BlobStorage;
import lombok.NonNull;
import redis.clients.jedis.Connection;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.params.SetParams;


/**
 * Azure Functions with Azure Queue trigger.
 */
public class BizEventToDataStore {
	/**
	 * This function will be invoked when an Event Hub trigger occurs
	 */

	public static final JedisPooled jedis = RedisClient.getInstance().redisConnectionFactory();

	private static final int EXPIRE_TIME_IN_MS =
			System.getenv("REDIS_EXPIRE_TIME_MS") != null ? Integer.parseInt(System.getenv("REDIS_EXPIRE_TIME_MS")) : 3600000;

	private static final String REDIS_ID_PREFIX = "biz_";

	private static final int EBR_MAX_RETRY_COUNT = 10;

	private final TelemetryClient telemetryClient = new TelemetryClient();

	@FunctionName("EventHubBizEventProcessor")
	@ExponentialBackoffRetry(maxRetryCount = EBR_MAX_RETRY_COUNT, maximumInterval = "00:30:00", minimumInterval = "00:00:30")
	public void processBizEvent (
			@EventHubTrigger(
					name = "BizEvent",
					eventHubName = "", // blank because the value is included in the connection string
					connection = "EVENTHUB_CONN_STRING",
					cardinality = Cardinality.MANY)
			List<BizEvent> bizEvtMsg,
			@BindingName(value = "PropertiesArray") Map<String, Object>[] properties,
			@CosmosDBOutput(
					name = "BizEventDatastore",
					databaseName = "db",
					containerName = "biz-events",
					createIfNotExists = false,
					connection = "COSMOS_CONN_STRING")
			@NonNull OutputBinding<List<BizEvent>> documentdb,
			final ExecutionContext context) {

		Logger logger = context.getLogger();
		logger.log(Level.INFO, () -> String.format("BizEventToDataStore function with invocationId [%s] called at [%s] with events list size [%s] and properties size [%s]",
				context.getInvocationId(), LocalDateTime.now(), bizEvtMsg.size(), properties.length));

		int retryIndex = context.getRetryContext() == null ? 0 : context.getRetryContext().getRetrycount();
		String id = String.valueOf(UUID.randomUUID());
		LocalDateTime executionDateTime = LocalDateTime.now();

		if (retryIndex >= EBR_MAX_RETRY_COUNT) {
			this.handleLastRetry(context, id, executionDateTime, "last-retry-input", bizEvtMsg);
		}

		StringJoiner eventDetails = new StringJoiner(", ", "{", "}");
		List<BizEvent> bizEvtMsgWithProperties = new ArrayList<>();

		try {
			if (bizEvtMsg.size() == properties.length) {
				for (int i=0; i<bizEvtMsg.size(); i++) {

					eventDetails.add("id: " + bizEvtMsg.get(i).getId());
					eventDetails.add("idPA: " + Optional.ofNullable(bizEvtMsg.get(i).getCreditor()).map(o -> o.getIdPA()).orElse("N/A"));
					eventDetails.add("modelType: " + Optional.ofNullable(bizEvtMsg.get(i).getDebtorPosition()).map(o -> o.getModelType()).orElse("N/A"));
					eventDetails.add("noticeNumber: " + Optional.ofNullable(bizEvtMsg.get(i).getDebtorPosition()).map(o -> o.getNoticeNumber()).orElse("N/A"));
					eventDetails.add("iuv: " + Optional.ofNullable(bizEvtMsg.get(i).getDebtorPosition()).map(o -> o.getIuv()).orElse("N/A"));

					logger.log(Level.FINE, () -> String.format("BizEventToDataStore function with invocationId [%s] working the biz-event [%s]",
							context.getInvocationId(), eventDetails));

					// READ FROM THE CACHE: The cache is queried to find out if the event has already been queued --> if yes it is skipped
					String value = this.findByBizEventId(bizEvtMsg.get(i).getId(), logger);

					if (Strings.isNullOrEmpty(value)) {
						BizEvent bz = bizEvtMsg.get(i);
						// set the IUR also on Debtor Position
						bz.getDebtorPosition().setIur(bz.getPaymentInfo().getIUR());
						// set the event creation date
						bz.setTimestamp(ZonedDateTime.now().toInstant().toEpochMilli());
						// set the event associated properties
						bz.setProperties(properties[i]);

						// WRITE IN THE CACHE: The result of the insertion in the cache is logged to verify the correct functioning
						String result = this.saveBizEventId(bizEvtMsg.get(i).getId(), logger);

						String msg = String.format("BizEventToDataStore function with invocationId [%s] cached biz-event message with id [%s] and result: [%s]",
								context.getInvocationId(), bizEvtMsg.get(i).getId(), result);
						logger.fine(msg);

						bizEvtMsgWithProperties.add(bz);
					}
					else {
						// just to track duplicate events
						String msg = String.format("BizEventToDataStore function with invocationId [%s] has already processed and cached biz-event message with id [%s]: it is discarded",
								context.getInvocationId(), bizEvtMsg.get(i).getId());
						logger.fine(msg);
					}
				}
				// persist the item
				documentdb.setValue(bizEvtMsgWithProperties);
			} else {
				this.uploadToDeadLetter(id, executionDateTime, context.getInvocationId(), "different-size-error", bizEvtMsg);
				String event = String.format("BizEventToDataStore function with invocationId [%s] - Error during processing - "
						+ "The size of the events to be processed and their associated properties does not match [bizEvtMsg.size=%s; properties.length=%s]",
						context.getInvocationId(), bizEvtMsg.size(), properties.length);
				logger.severe(event);
				telemetryClient.trackEvent(event);
			}
		} catch (Exception e) {
			String exceptionMsg = String.format("BizEventToDataStore function with invocationId [%s] - %s on cosmos biz-events msg ingestion at %s" +
					" [%s]: %s. Retry index: %s", context.getInvocationId(), e.getClass(), LocalDateTime.now(), eventDetails, e.getMessage(), retryIndex);
			logger.severe(exceptionMsg);
			// retry check and dead-letter upload
			if (retryIndex >= EBR_MAX_RETRY_COUNT) {
				this.handleLastRetry(context, id, executionDateTime, "exception-output", bizEvtMsgWithProperties);
			}
			// it is important to trigger the retry mechanism (rethrow any errors that you want to result in a retry)
			throw e;
		}
	}

	public String findByBizEventId(String id, Logger logger) {
		try (Connection j = jedis.getPool().getResource()){
			return jedis.get(REDIS_ID_PREFIX+id);
		} catch (Exception e) {
			String msg = String.format("Error getting existing connection to Redis. A new one is created to GET the BizEvent message with id %s. [error message = %s]",
					REDIS_ID_PREFIX+id, e.getMessage());
			logger.warning(msg);
			// It try to acquire the connection again. If it fails, a null value is returned so that the data is not discarded
			try (JedisPooled j = RedisClient.getInstance().redisConnectionFactory()){
				return j.get(REDIS_ID_PREFIX+id);
			} catch (Exception ex) {
				return null;
			}
		}
	}

	public String saveBizEventId(String id, Logger logger) {
		try (Connection j = jedis.getPool().getResource()){
			return jedis.set(REDIS_ID_PREFIX+id, id, new SetParams().px(EXPIRE_TIME_IN_MS));
		} catch (Exception e) {
			String msg = String.format("Error getting existing connection to Redis. A new one is created to SET the BizEvent message with id %s. [error message = %s]",
					REDIS_ID_PREFIX+id, e.getMessage());
			logger.warning(msg);
			// It try to acquire the connection again. If it fails, a null value is returned so that the data is not discarded
			try (JedisPooled j = RedisClient.getInstance().redisConnectionFactory()){
				return j.set(REDIS_ID_PREFIX+id, id, new SetParams().px(EXPIRE_TIME_IN_MS));
			} catch (Exception ex) {
				return null;
			}
		}
	}

	public void handleLastRetry(ExecutionContext context, String id, LocalDateTime now, String type, List<BizEvent> bizEvtMsg) {
		boolean deadLetterResult = this.uploadToDeadLetter(id, now, context.getInvocationId(), type, bizEvtMsg);
		String deadLetterLog = deadLetterResult ?
				"List<BizEvent> " + type + " message was correctly saved in the dead letter." :
				"There was an error when saving List<BizEvent> " + type + " message in the dead letter.";
		String retryTrace = String.format("[LAST RETRY] BizEventToDataStore function with invocationId [%s] performing the last retry for events ingestion. %s",
				context.getInvocationId(), deadLetterLog);
		context.getLogger().log(Level.SEVERE, () -> retryTrace);
		telemetryClient.trackEvent(String.format("[LAST RETRY] invocationId [%s]", context.getInvocationId()));
	}

	public boolean uploadToDeadLetter(String id, LocalDateTime now, String invocationId, String type, List<BizEvent> bizEvtMsg) {
		try {
			return BlobStorage.getInstance().uploadToDeadLetter(id, now, invocationId, type, bizEvtMsg);
		} catch (Exception e) {
			return false;
		}
	}
}
