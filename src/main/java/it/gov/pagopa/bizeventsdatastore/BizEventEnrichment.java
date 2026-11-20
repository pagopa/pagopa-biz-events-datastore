package it.gov.pagopa.bizeventsdatastore;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;

import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.CosmosDBTrigger;
import com.microsoft.azure.functions.annotation.ExponentialBackoffRetry;
import com.microsoft.azure.functions.annotation.FunctionName;

import it.gov.pagopa.bizeventsdatastore.entity.BizEvent;
import it.gov.pagopa.bizeventsdatastore.entity.enumeration.StatusType;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewCart;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewGeneral;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewUser;
import it.gov.pagopa.bizeventsdatastore.exception.BizEventToViewConstraintViolationException;
import it.gov.pagopa.bizeventsdatastore.model.BizEventToViewResult;
import it.gov.pagopa.bizeventsdatastore.service.BizEventCosmosService;
import it.gov.pagopa.bizeventsdatastore.service.BizEventDeadLetterService;
import it.gov.pagopa.bizeventsdatastore.service.BizEventToViewService;
import it.gov.pagopa.bizeventsdatastore.service.impl.BizEventCosmosServiceImpl;
import it.gov.pagopa.bizeventsdatastore.service.impl.BizEventDeadLetterServiceImpl;
import it.gov.pagopa.bizeventsdatastore.service.impl.BizEventToViewServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BizEventEnrichment {

	private final Logger logger = LoggerFactory.getLogger(BizEventEnrichment.class);

	private final boolean enableTransactionListView = Boolean.parseBoolean(System.getenv().getOrDefault("ENABLE_TRANSACTION_LIST_VIEW", "false"));

	private static final int EBR_MAX_RETRY_COUNT = 10;
	private static final int MAX_ITEMS_PER_INVOCATION = 64;
	private static final String CONTAINER_BIZ_EVENTS_VIEWS_DEAD_LETTER_NAME = "biz-events-views-dead-letter";
	private static final Set<StatusType> VIEW_ELIGIBLE_STATUSES = EnumSet.of(StatusType.DONE, StatusType.INGESTED);
	private static final String VALIDATION_ERROR_TYPE = "validation-error";

	private final TelemetryClient telemetryClient = new TelemetryClient();

	private final BizEventToViewService bizEventToViewService;

	private final BizEventDeadLetterService bizEventDeadLetterService;

	private final BizEventCosmosService bizEventCosmosService;

	public BizEventEnrichment() {
		this.bizEventToViewService = new BizEventToViewServiceImpl();
		this.bizEventDeadLetterService = new BizEventDeadLetterServiceImpl();
		this.bizEventCosmosService = new BizEventCosmosServiceImpl();
	}

	BizEventEnrichment(BizEventToViewService bizEventToViewService,
			BizEventDeadLetterService bizEventDeadLetterService,
			BizEventCosmosService bizEventCosmosService) {
		this.bizEventToViewService = bizEventToViewService;
		this.bizEventDeadLetterService = bizEventDeadLetterService;
		this.bizEventCosmosService = bizEventCosmosService;
	}

	@FunctionName("BizEventEnrichmentProcessor")
	@ExponentialBackoffRetry(maxRetryCount = EBR_MAX_RETRY_COUNT, maximumInterval = "00:30:00", minimumInterval = "00:00:30")
	public void processBizEventEnrichment(
			@CosmosDBTrigger(
					name = "BizEventDatastore",
					databaseName = "db",
					containerName = "biz-events",
					leaseDatabaseName = "db",
					leaseContainerName = "biz-events-views-leases",
					createLeaseContainerIfNotExists = false,
					startFromBeginning = false,
					maxItemsPerInvocation = MAX_ITEMS_PER_INVOCATION,
					connection = "COSMOS_CONN_STRING")
			List<BizEvent> items,
			final ExecutionContext context
			) throws BizEventToViewConstraintViolationException {

		List<BizEventsViewUser> userViewToInsert = new ArrayList<>();
		List<BizEventsViewGeneral> generalViewToInsert = new ArrayList<>();
		List<BizEventsViewCart> cartViewToInsert = new ArrayList<>();

		// Debug level log because function start/completion is already available from Azure Functions runtime logs
		// through the Function.BizEventEnrichmentProcessor category (Executing/Executed).
		logger.debug("BizEventEnrichment stat {} function - num events triggered {}", context.getInvocationId(),  items.size());

		int retryIndex = context.getRetryContext() == null ? 0 : context.getRetryContext().getRetrycount();
		String id = String.valueOf(UUID.randomUUID());
		LocalDateTime executionDateTime = LocalDateTime.now();

		// On the last retry the events are saved in the dead letter before processing them: if this attempt fails too
		// (e.g., 429 Too Many Requests), the retry policy is exhausted and the change feed moves on, so they would be lost.
		handleLastRetry(items, context, retryIndex, id, "last-retry-input", executionDateTime);

		StringJoiner eventDetails = new StringJoiner(", ", "{", "}");

		try {
			for (BizEvent be : items) {
				eventDetails.add("id: " + be.getId());
				eventDetails.add("idPA: " + Optional.ofNullable(be.getCreditor()).map(o -> o.getIdPA()).orElse("N/A"));
				eventDetails.add("modelType: " + Optional.ofNullable(be.getDebtorPosition()).map(o -> o.getModelType()).orElse("N/A"));
				eventDetails.add("noticeNumber: " + Optional.ofNullable(be.getDebtorPosition()).map(o -> o.getNoticeNumber()).orElse("N/A"));
				eventDetails.add("iuv: " + Optional.ofNullable(be.getDebtorPosition()).map(o -> o.getIuv()).orElse("N/A"));

				logger.debug("BizEventEnrichment function with invocationId {} called at {} for event with id {}" +
								" and status {} and numEnrichmentRetry {} , enriching biz-event {}",
						context.getInvocationId(), LocalDateTime.now(), be.getId(), be.getEventStatus(), retryIndex, eventDetails);

				if (!VIEW_ELIGIBLE_STATUSES.contains(be.getEventStatus())) {
					logger.debug("BizEventEnrichment function with invocationId {} - skipping biz-event with id {} and status {}:" +
									" views are generated only for status {}",
							context.getInvocationId(), be.getId(), be.getEventStatus(), VIEW_ELIGIBLE_STATUSES);
					continue;
				}

				generateBizEventViewsOrSendToDeadLetter(userViewToInsert, generalViewToInsert, cartViewToInsert, be, context, executionDateTime);
			}

			saveViews(userViewToInsert, generalViewToInsert, cartViewToInsert, context);
		} catch (Exception e) {
			logger.error("BizEventEnrichmentProcessor function with invocationId [{}] - {} on cosmos biz-events msg ingestion at {}" +
					" [{}]: {}. Retry index: {}", context.getInvocationId(), e.getClass(), LocalDateTime.now(), eventDetails, e.getMessage(), retryIndex);
			// it is important to trigger the retry mechanism (rethrow any errors that you want to result in a retry)
			throw e;
		}
	}

	/**
	 * The user views are saved last: they make the receipt visible to the user, so they must not exist
	 * if the general or cart views, needed to show the receipt details, have not been saved.
	 * Any error is rethrown to trigger the retry policy.
	 */
	private void saveViews(List<BizEventsViewUser> userViewToInsert, List<BizEventsViewGeneral> generalViewToInsert,
			List<BizEventsViewCart> cartViewToInsert, final ExecutionContext context) {
		logger.info("BizEventEnrichment stat {} function - number of events to update on the Biz-event views: user - {}, general - {}, cart - {}",
				context.getInvocationId(), userViewToInsert.size(), generalViewToInsert.size(), cartViewToInsert.size());
		generalViewToInsert.forEach(bizEventCosmosService::upsertBizEventViewGeneral);
		cartViewToInsert.forEach(bizEventCosmosService::upsertBizEventViewCart);
		if (!userViewToInsert.isEmpty()) {
			bizEventCosmosService.saveBizEventViewUsersPreservingVisibility(userViewToInsert);
		}
	}

	private void handleLastRetry(List<BizEvent> items, final ExecutionContext context, int retryIndex, String id, String type,
			LocalDateTime executionDateTime) {
		if (retryIndex >= EBR_MAX_RETRY_COUNT) {
			bizEventDeadLetterService.handleLastRetry(context, id, executionDateTime, type, items,
					CONTAINER_BIZ_EVENTS_VIEWS_DEAD_LETTER_NAME, telemetryClient);
		}
	}

	private void generateBizEventViewsOrSendToDeadLetter(
			List<BizEventsViewUser> userViewToInsert,
			List<BizEventsViewGeneral> generalViewToInsert,
			List<BizEventsViewCart> cartViewToInsert,
			BizEvent be,
			final ExecutionContext context,
			LocalDateTime executionDateTime
	) throws BizEventToViewConstraintViolationException {
		try {
			generateBizEventViews(userViewToInsert, generalViewToInsert, cartViewToInsert, be);
		} catch (BizEventToViewConstraintViolationException e) {
			// A validation error would be the same at every retry: it must not block the other events of the batch.
			logger.error("BizEventEnrichment function with invocationId [{}] - invalid views for biz-event [{}]: it is sent to the dead letter",
					context.getInvocationId(), be.getId(), e);
			boolean uploaded = bizEventDeadLetterService.uploadToDeadLetter(String.valueOf(UUID.randomUUID()), executionDateTime,
					context.getInvocationId(), VALIDATION_ERROR_TYPE, List.of(be), CONTAINER_BIZ_EVENTS_VIEWS_DEAD_LETTER_NAME);
			if (!uploaded) {
				throw e;
			}
		}
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
