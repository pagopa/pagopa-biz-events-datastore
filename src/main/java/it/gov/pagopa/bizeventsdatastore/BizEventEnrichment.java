package it.gov.pagopa.bizeventsdatastore;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.UUID;

import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.Cardinality;
import com.microsoft.azure.functions.annotation.CosmosDBOutput;
import com.microsoft.azure.functions.annotation.EventHubTrigger;
import com.microsoft.azure.functions.annotation.ExponentialBackoffRetry;
import com.microsoft.azure.functions.annotation.FunctionName;

import it.gov.pagopa.bizeventsdatastore.entity.BizEvent;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewCart;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewGeneral;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewUser;
import it.gov.pagopa.bizeventsdatastore.exception.BizEventToViewConstraintViolationException;
import it.gov.pagopa.bizeventsdatastore.model.BizEventToViewResult;
import it.gov.pagopa.bizeventsdatastore.service.BizEventDeadLetterService;
import it.gov.pagopa.bizeventsdatastore.service.BizEventToViewService;
import it.gov.pagopa.bizeventsdatastore.service.impl.BizEventDeadLetterServiceImpl;
import it.gov.pagopa.bizeventsdatastore.service.impl.BizEventToViewServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BizEventEnrichment {

	private final Logger logger = LoggerFactory.getLogger(BizEventEnrichment.class);

	private final boolean enableTransactionListView = Boolean.parseBoolean(System.getenv().getOrDefault("ENABLE_TRANSACTION_LIST_VIEW", "false"));

	private static final int EBR_MAX_RETRY_COUNT = 10;
	private static final String CONTAINER_BIZ_EVENTS_VIEWS_DEAD_LETTER_NAME = "biz-events-views-dead-letter";

	private final TelemetryClient telemetryClient = new TelemetryClient();

	private final BizEventToViewService bizEventToViewService;

	private final BizEventDeadLetterService bizEventDeadLetterService;

	public BizEventEnrichment() {
		this.bizEventToViewService = new BizEventToViewServiceImpl();
		this.bizEventDeadLetterService = new BizEventDeadLetterServiceImpl();
	}

	BizEventEnrichment(BizEventToViewService bizEventToViewService,
			BizEventDeadLetterService bizEventDeadLetterService) {
		this.bizEventToViewService = bizEventToViewService;
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
			) throws BizEventToViewConstraintViolationException {

		List<BizEventsViewUser> userViewToInsert = new ArrayList<>();
		List<BizEventsViewGeneral> generalViewToInsert = new ArrayList<>();
		List<BizEventsViewCart> cartViewToInsert = new ArrayList<>();

		logger.info("BizEventEnrichment stat {} function - num events triggered {}", context.getInvocationId(),  items.size());

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

					logger.debug("BizEventEnrichment function with invocationId {} called at {} for event with id {}" +
									" and status {} and numEnrichmentRetry {} , enriching biz-event {}",
							context.getInvocationId(), LocalDateTime.now(), be.getId(), be.getEventStatus(), retryIndex, eventDetails);

					// set the IUR also on Debtor Position
					be.getDebtorPosition().setIur(be.getPaymentInfo().getIUR());
					// set the event creation date
					be.setTimestamp(ZonedDateTime.now().toInstant().toEpochMilli());
					// set the event associated properties
					be.setProperties(properties[i]);

					generateBizEventViews(userViewToInsert, generalViewToInsert, cartViewToInsert, be);
				}
			}
			else {
				handleSizeMismatch(items, properties, context, id, executionDateTime);
			}
		} catch (Exception e) {
			logger.error("BizEventEnrichmentProcessor function with invocationId [{}] - {} on cosmos biz-events msg ingestion at {}" +
					" [{}]: {}. Retry index: {}", context.getInvocationId(), e.getClass(), LocalDateTime.now(), eventDetails, e.getMessage(), retryIndex);
			// retry check and dead-letter upload
			handleLastRetry(bizEvtMsgWithProperties, context, retryIndex, id, "exception-output", executionDateTime);
			// it is important to trigger the retry mechanism (rethrow any errors that you want to result in a retry)
			throw e;
		}

		// Insert in Biz-event views
		logger.info("BizEventEnrichment stat {} function - number of events to update on the Biz-event views: user - {}, general - {}, cart - {}",
				context.getInvocationId(), userViewToInsert.size(), generalViewToInsert.size(), cartViewToInsert.size());
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

	private void handleSizeMismatch(
			List<BizEvent> items,
			Map<String, Object>[] properties,
			final ExecutionContext context,
			String id,
			LocalDateTime executionDateTime
	) {
		bizEventDeadLetterService.uploadToDeadLetter(
				id, executionDateTime,
				context.getInvocationId(),
				"different-size-error",
				items,
				CONTAINER_BIZ_EVENTS_VIEWS_DEAD_LETTER_NAME
		);
		String event = String.format("BizEventEnrichmentProcessor function with invocationId [%s] - Error during processing - "
				+ "The size of the events to be processed and their associated properties does not match [bizEvtMsg.size=%s; properties.length=%s]",
				context.getInvocationId(), items.size(), properties.length);
		logger.error(event);
		telemetryClient.trackEvent(event);
	}

	private void generateBizEventViews(
			List<BizEventsViewUser> userViewToInsert,
			List<BizEventsViewGeneral> generalViewToInsert,
			List<BizEventsViewCart> cartViewToInsert,
			BizEvent be
	) throws BizEventToViewConstraintViolationException {
		if (enableTransactionListView) {
			BizEventToViewResult bizEventToViewResult = this.bizEventToViewService.mapBizEventToView(be);
			if (bizEventToViewResult != null) {
				userViewToInsert.addAll(bizEventToViewResult.getUserViewList());
				generalViewToInsert.add(bizEventToViewResult.getGeneralView());
				cartViewToInsert.add(bizEventToViewResult.getCartView());
			}
		}
	}

}
