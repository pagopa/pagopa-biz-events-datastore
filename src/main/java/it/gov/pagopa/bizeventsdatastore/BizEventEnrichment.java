package it.gov.pagopa.bizeventsdatastore;

import com.google.common.base.Strings;
import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.annotation.*;
import it.gov.pagopa.bizeventsdatastore.entity.BizEvent;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewCart;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewGeneral;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewUser;
import it.gov.pagopa.bizeventsdatastore.exception.AppException;
import it.gov.pagopa.bizeventsdatastore.model.BizEventToViewResult;
import it.gov.pagopa.bizeventsdatastore.service.BizEventDeadLetterService;
import it.gov.pagopa.bizeventsdatastore.service.BizEventToViewService;
import it.gov.pagopa.bizeventsdatastore.service.RedisCacheService;
import it.gov.pagopa.bizeventsdatastore.service.impl.BizEventDeadLetterServiceImpl;
import it.gov.pagopa.bizeventsdatastore.service.impl.BizEventToViewServiceImpl;
import it.gov.pagopa.bizeventsdatastore.service.impl.RedisCacheServiceImpl;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.logging.Logger;

public class BizEventEnrichment {
	
	private final boolean enableTransactionListView = Boolean.parseBoolean(System.getenv().getOrDefault("ENABLE_TRANSACTION_LIST_VIEW", "false"));

	private static final String REDIS_ID_VIEW_PREFIX = "bizView_";

	private static final int EBR_MAX_RETRY_COUNT = 10;

	private final TelemetryClient telemetryClient = new TelemetryClient();

	private static final String CONTAINER_BIZ_EVENTS_VIEWS_DEAD_LETTER_NAME = "biz-events-views-dead-letter";

	private final BizEventToViewService bizEventToViewService;

	private final RedisCacheService redisCacheService;

	private final BizEventDeadLetterService bizEventDeadLetterService;

	public BizEventEnrichment() {

		this.bizEventToViewService = new BizEventToViewServiceImpl();
		this.redisCacheService = new RedisCacheServiceImpl();
		this.bizEventDeadLetterService = new BizEventDeadLetterServiceImpl();
	}

	BizEventEnrichment(BizEventToViewService bizEventToViewService,
					   RedisCacheService redisCacheService, BizEventDeadLetterService bizEventDeadLetterService) {

		this.bizEventToViewService = bizEventToViewService;
		this.redisCacheService = redisCacheService;
		this.bizEventDeadLetterService = bizEventDeadLetterService;
	}

	@FunctionName("BizEventEnrichmentProcessor")
	@ExponentialBackoffRetry(maxRetryCount = EBR_MAX_RETRY_COUNT, maximumInterval = "00:30:00", minimumInterval = "00:00:30")
	public void processBizEventEnrichment(
			@EventHubTrigger(
					name = "BizEvent",
					eventHubName = "", // blank because the value is included in the connection string
					connection = "VIEWS_EVENTHUB_CONN_STRING",
					cardinality = Cardinality.MANY)
			List<BizEvent> items,
			@BindingName(value = "PropertiesArray") Map<String, Object>[] properties,
			@EventHubOutput(
					name = "PdndBizEventHub",
					eventHubName = "", // blank because the value is included in the connection string
					connection = "PDND_EVENTHUB_CONN_STRING")
			OutputBinding<List<BizEvent>> bizPdndEvtMsg,
			@CosmosDBOutput(
					name = "BizEventUserView",
					databaseName = "db",
					containerName = "biz-events-view-user",
					createIfNotExists = false,
					connection = "COSMOS_CONN_STRING")
			OutputBinding<List<BizEventsViewUser>> bizEventUserView,
			@CosmosDBOutput(
					name = "BizEventGeneralView",
					databaseName = "db",
					containerName = "biz-events-view-general",
					createIfNotExists = false,
					connection = "COSMOS_CONN_STRING")
			OutputBinding<List<BizEventsViewGeneral>> bizEventGeneralView,
			@CosmosDBOutput(
					name = "BizEventCartView",
					databaseName = "db",
					containerName = "biz-events-view-cart",
					createIfNotExists = false,
					connection = "COSMOS_CONN_STRING")
			OutputBinding<List<BizEventsViewCart>> bizEventCartView,
			final ExecutionContext context
	) throws AppException {

		List<BizEvent> itemsDone = new ArrayList<>();
		List<BizEventsViewUser> userViewToInsert = new ArrayList<>();
		List<BizEventsViewGeneral> generalViewToInsert = new ArrayList<>();
		List<BizEventsViewCart> cartViewToInsert = new ArrayList<>();
		Logger logger = context.getLogger();

		String msg = String.format("BizEventEnrichment stat %s function - num events triggered %d", context.getInvocationId(),  items.size());
		logger.info(msg);

		int retryIndex = context.getRetryContext() == null ? 0 : context.getRetryContext().getRetrycount();
		String id = String.valueOf(UUID.randomUUID());
		LocalDateTime executionDateTime = LocalDateTime.now();
		
		// This retry check is needed because setValue in OutputBinding doesn’t throw exceptions, 
		// requiring an additional check to send the message to the dead letter queue after multiple failed attempts (e.g., 429 Too Many Requests).
		handleLastRetry(items, context, retryIndex, id, "last-retry-input", executionDateTime);

		StringJoiner eventDetails = new StringJoiner(", ", "{", "}");
		List<BizEvent> bizEvtMsgWithProperties = new ArrayList<>();

		try {
			if (items.size() == properties.length) {
				for (int i=0; i<items.size(); i++) {

					BizEvent be = items.get(i);
					eventDetails.add("id: " + be.getId());
					eventDetails.add("idPA: " + Optional.ofNullable(be.getCreditor()).map(o -> o.getIdPA()).orElse("N/A"));
					eventDetails.add("modelType: " + Optional.ofNullable(be.getDebtorPosition()).map(o -> o.getModelType()).orElse("N/A"));
					eventDetails.add("noticeNumber: " + Optional.ofNullable(be.getDebtorPosition()).map(o -> o.getNoticeNumber()).orElse("N/A"));
					eventDetails.add("iuv: " + Optional.ofNullable(be.getDebtorPosition()).map(o -> o.getIuv()).orElse("N/A"));

					String message = String.format("BizEventEnrichment function with invocationId %s called at %s for event with id %s and status %s and numEnrichmentRetry %s , enriching biz-event %s",
							context.getInvocationId(), LocalDateTime.now(), be.getId(), be.getEventStatus(), retryIndex, eventDetails);
					logger.fine(message);

					// Cache lookup: The cache is queried to find out if the event has already been queued --> if yes it is skipped
					String cachedValue = redisCacheService.findByBizEventId(be.getId(),REDIS_ID_VIEW_PREFIX, logger);

					if (Strings.isNullOrEmpty(cachedValue)) {

						// set the IUR also on Debtor Position
						be.getDebtorPosition().setIur(be.getPaymentInfo().getIUR());
						// set the event creation date
						be.setTimestamp(ZonedDateTime.now().toInstant().toEpochMilli());
						// set the event associated properties
						be.setProperties(properties[i]);

						generateBizEventViews(userViewToInsert, generalViewToInsert, cartViewToInsert, logger, be);

						// Cache write: The result of the insertion in the cache is logged to verify the correct functioning
						String result = redisCacheService.saveBizEventId(be.getId(),REDIS_ID_VIEW_PREFIX, logger);

						String cachedMsg = String.format("BizEventEnrichment function with invocationId [%s] cached biz-event view with id [%s] and result: [%s]",
								context.getInvocationId(), be.getId(), result);
						logger.fine(cachedMsg);

						//the event is propagated only if it is not a duplicate
						itemsDone.add(be);
					}
					else {
						// just to track duplicate events
						String duplicateMsg = String.format("BizEventEnrichment function with invocationId [%s] has already processed and cached biz-event view with id [%s]: it is discarded",
								context.getInvocationId(), be.getId());
						logger.fine(duplicateMsg);
					}
				}
			}
			else {
				handleSizeMismatch(items, properties, context, logger, id, executionDateTime);
			}
		} catch (Exception e) {
			String exceptionMsg = String.format("BizEventEnrichmentProcessor function with invocationId [%s] - %s on cosmos biz-events msg ingestion at %s" +
					" [%s]: %s. Retry index: %s", context.getInvocationId(), e.getClass(), LocalDateTime.now(), eventDetails, e.getMessage(), retryIndex);
			logger.severe(exceptionMsg);
			// retry check and dead-letter upload
			handleLastRetry(bizEvtMsgWithProperties, context, retryIndex, id, "exception-output", executionDateTime);
			// it is important to trigger the retry mechanism (rethrow any errors that you want to result in a retry)
			throw e;
		}

		// call the Event Hub
		msg = String.format("BizEventEnrichment stat %s function - number of events in DONE sent to the event hub %d", context.getInvocationId(), itemsDone.size());
		logger.fine(msg);
		bizPdndEvtMsg.setValue(itemsDone);

		// Insert in Biz-event views
		msg = String.format("BizEventEnrichment stat %s function - number of events to update on the Biz-event views: user - %d, general - %d, cart - %d ",
				context.getInvocationId(), userViewToInsert.size(), generalViewToInsert.size(), cartViewToInsert.size());
		logger.info(msg);
		if (!userViewToInsert.isEmpty()) {
			bizEventUserView.setValue(userViewToInsert);
		}
		if (!generalViewToInsert.isEmpty()) {
			bizEventGeneralView.setValue(generalViewToInsert);
		}
		if (!cartViewToInsert.isEmpty()) {
			bizEventCartView.setValue(cartViewToInsert);
		}
	}

	private void handleLastRetry(List<BizEvent> items, final ExecutionContext context, int retryIndex, String id, String type,
			LocalDateTime executionDateTime) {
		if (retryIndex >= EBR_MAX_RETRY_COUNT) {
			bizEventDeadLetterService.handleLastRetry(context, id, executionDateTime, type, items,
					CONTAINER_BIZ_EVENTS_VIEWS_DEAD_LETTER_NAME, telemetryClient);
		}
	}

	private void handleSizeMismatch(List<BizEvent> items, Map<String, Object>[] properties,
			final ExecutionContext context, Logger logger, String id, LocalDateTime executionDateTime) {
		bizEventDeadLetterService.uploadToDeadLetter(id, executionDateTime, context.getInvocationId(), "different-size-error", items,CONTAINER_BIZ_EVENTS_VIEWS_DEAD_LETTER_NAME);
		String event = String.format("BizEventEnrichmentProcessor function with invocationId [%s] - Error during processing - "
						+ "The size of the events to be processed and their associated properties does not match [bizEvtMsg.size=%s; properties.length=%s]",
				context.getInvocationId(), items.size(), properties.length);
		logger.severe(event);
		telemetryClient.trackEvent(event);
	}

	private void generateBizEventViews(List<BizEventsViewUser> userViewToInsert,
			List<BizEventsViewGeneral> generalViewToInsert, List<BizEventsViewCart> cartViewToInsert, Logger logger,
			BizEvent be) throws AppException {
		if (enableTransactionListView) {
			BizEventToViewResult bizEventToViewResult = this.bizEventToViewService.mapBizEventToView(logger, be);
			if (bizEventToViewResult != null) {
				userViewToInsert.addAll(bizEventToViewResult.getUserViewList());
				generalViewToInsert.add(bizEventToViewResult.getGeneralView());
				cartViewToInsert.add(bizEventToViewResult.getCartView());

			}
		}
	}

}
