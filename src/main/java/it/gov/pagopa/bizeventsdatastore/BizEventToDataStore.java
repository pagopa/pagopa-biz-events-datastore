package it.gov.pagopa.bizeventsdatastore;

import com.google.common.base.Strings;
import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.annotation.*;
import it.gov.pagopa.bizeventsdatastore.entity.BizEvent;
import it.gov.pagopa.bizeventsdatastore.service.BizEventDeadLetterService;
import it.gov.pagopa.bizeventsdatastore.service.RedisCacheService;
import it.gov.pagopa.bizeventsdatastore.service.impl.BizEventDeadLetterServiceImpl;
import it.gov.pagopa.bizeventsdatastore.service.impl.RedisCacheServiceImpl;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.*;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Azure Functions with Azure Queue trigger.
 */
public class BizEventToDataStore {

	private final Logger logger = LoggerFactory.getLogger(BizEventToDataStore.class);

	private static final String REDIS_ID_PREFIX = "biz_";
	private static final int EBR_MAX_RETRY_COUNT = 10;
	private static final String CONTAINER_BIZ_EVENTS_DEAD_LETTER_NAME = "biz-events-dead-letter";

	private final TelemetryClient telemetryClient = new TelemetryClient();

	private final RedisCacheService redisCacheService;

	private final BizEventDeadLetterService bizEventDeadLetterService;

	public BizEventToDataStore() {
		this.redisCacheService = new RedisCacheServiceImpl();
		this.bizEventDeadLetterService = new BizEventDeadLetterServiceImpl();
	}

	public BizEventToDataStore(RedisCacheService redisCacheService, BizEventDeadLetterService bizEventDeadLetterService) {
		this.redisCacheService = redisCacheService;
		this.bizEventDeadLetterService = bizEventDeadLetterService;
	}

	/**
	 * This function will be invoked when an Event Hub trigger occurs
	 */
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
			@EventHubOutput(
					name = "PdndBizEventHub",
					eventHubName = "", // blank because the value is included in the connection string
					connection = "PDND_EVENTHUB_CONN_STRING")
			OutputBinding<List<BizEvent>> bizPdndEvtMsg,
			final ExecutionContext context) {

		logger.info("BizEventToDataStore function with invocationId [{}] called at [{}] with events list size [{}] and properties size [{}]",
				context.getInvocationId(), LocalDateTime.now(), bizEvtMsg.size(), properties.length);

		List<BizEvent> itemsDone = new ArrayList<>();

		int retryIndex = context.getRetryContext() == null ? 0 : context.getRetryContext().getRetrycount();
		String id = String.valueOf(UUID.randomUUID());
		LocalDateTime executionDateTime = LocalDateTime.now();

		// This retry check is needed because setValue in OutputBinding doesn’t throw exceptions,
		// requiring an additional check to send the message to the dead letter queue after multiple failed attempts (e.g., 429 Too Many Requests).
		if (retryIndex >= EBR_MAX_RETRY_COUNT) {
			bizEventDeadLetterService.handleLastRetry(context, id, executionDateTime, "last-retry-input", bizEvtMsg, CONTAINER_BIZ_EVENTS_DEAD_LETTER_NAME, telemetryClient);
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

					logger.debug("BizEventToDataStore function with invocationId [{}] working the biz-event [{}]",
							context.getInvocationId(), eventDetails);

					// READ FROM THE CACHE: The cache is queried to find out if the event has already been queued --> if yes it is skipped
					String value = redisCacheService.findByBizEventId(bizEvtMsg.get(i).getId(), REDIS_ID_PREFIX);

					if (Strings.isNullOrEmpty(value)) {
						BizEvent bz = bizEvtMsg.get(i);
						// set the IUR also on Debtor Position
						bz.getDebtorPosition().setIur(bz.getPaymentInfo().getIUR());
						// set the event creation date
						bz.setTimestamp(ZonedDateTime.now().toInstant().toEpochMilli());
						// set the event associated properties
						bz.setProperties(properties[i]);

						// WRITE IN THE CACHE: The result of the insertion in the cache is logged to verify the correct functioning
						String result = redisCacheService.saveBizEventId(bizEvtMsg.get(i).getId(), REDIS_ID_PREFIX);

						logger.debug("BizEventToDataStore function with invocationId [{}] cached biz-event message with id [{}] and result: [{}]",
								context.getInvocationId(), bizEvtMsg.get(i).getId(), result);

						bizEvtMsgWithProperties.add(bz);
						itemsDone.add(bz);
					}
					else {
						// just to track duplicate events
						logger.debug("BizEventToDataStore function with invocationId [{}] has already processed and cached biz-event message with id [{}]: it is discarded",
								context.getInvocationId(), bizEvtMsg.get(i).getId());
					}
				}
				// persist the item
				documentdb.setValue(bizEvtMsgWithProperties);

				// call PDND the Event Hub
				logger.debug("BizEventToDataStore stat {} function - number of events in DONE sent to the event hub {}",
						context.getInvocationId(), itemsDone.size());
				bizPdndEvtMsg.setValue(itemsDone);
			} else {
				bizEventDeadLetterService.uploadToDeadLetter(id, executionDateTime, context.getInvocationId(), "different-size-error", bizEvtMsg, CONTAINER_BIZ_EVENTS_DEAD_LETTER_NAME);
				String event = String.format("BizEventToDataStore function with invocationId [%s] - Error during processing - "
								+ "The size of the events to be processed and their associated properties does not match [bizEvtMsg.size=%s; properties.length=%s]",
						context.getInvocationId(), bizEvtMsg.size(), properties.length);
				logger.error(event);
				telemetryClient.trackEvent(event);
			}
		} catch (Exception e) {
			logger.error("BizEventToDataStore function with invocationId [{}] - {} on cosmos biz-events msg ingestion at {}" +
					" [{}]: {}. Retry index: {}", context.getInvocationId(), e.getClass(), LocalDateTime.now(), eventDetails, e.getMessage(), retryIndex);
			// retry check and dead-letter upload
			if (retryIndex >= EBR_MAX_RETRY_COUNT) {
				bizEventDeadLetterService.handleLastRetry(context, id, executionDateTime, "exception-output", bizEvtMsgWithProperties, CONTAINER_BIZ_EVENTS_DEAD_LETTER_NAME, telemetryClient);
			}
			// it is important to trigger the retry mechanism (rethrow any errors that you want to result in a retry)
			throw e;
		}
	}

}
