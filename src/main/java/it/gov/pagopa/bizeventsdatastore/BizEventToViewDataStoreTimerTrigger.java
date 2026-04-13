package it.gov.pagopa.bizeventsdatastore;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.annotation.CosmosDBInput;
import com.microsoft.azure.functions.annotation.CosmosDBOutput;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.TimerTrigger;

import it.gov.pagopa.bizeventsdatastore.entity.BizEvent;
import it.gov.pagopa.bizeventsdatastore.entity.enumeration.StatusType;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewCart;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewGeneral;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewUser;
import it.gov.pagopa.bizeventsdatastore.model.BizEventToViewResult;
import it.gov.pagopa.bizeventsdatastore.service.BizEventToViewService;
import it.gov.pagopa.bizeventsdatastore.service.impl.BizEventToViewServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BizEventToViewDataStoreTimerTrigger {

	private final Logger logger = LoggerFactory.getLogger(BizEventToViewDataStoreTimerTrigger.class);

	private final boolean enableTransactionListView = Boolean.parseBoolean(System.getenv().getOrDefault("TIMER_TRIGGER_ENABLE_TRANSACTION_LIST_VIEW", "false"));

	private final BizEventToViewService bizEventToViewService;

	public BizEventToViewDataStoreTimerTrigger() {
		this.bizEventToViewService = new BizEventToViewServiceImpl();
	}

	BizEventToViewDataStoreTimerTrigger(BizEventToViewService bizEventToViewService) {
		this.bizEventToViewService = bizEventToViewService;
	}

	/**
	 * This function will be invoked periodically according to the specified schedule.
	 */
	@FunctionName("BizEventToViewDataStoreTimerTriggerProcessor")
	public void processBizEventScheduledTrigger(
			@TimerTrigger(
					name = "timerInfo", 
					schedule = "%VIEW_TRIGGER_SCHEDULE%"
					) 
			String timerInfo,
			@CosmosDBInput(
					name = "BizEventDatastore",
					databaseName = "db",
					containerName = "biz-events",
					connection = "COSMOS_CONN_STRING",
					sqlQuery = "%VIEW_TRIGGER_SQL_QUERY%"
					)
			BizEvent[] items,
			@CosmosDBOutput(
					name = "EnrichedBizEventDatastore",
					databaseName = "db",
					containerName = "biz-events",
					createIfNotExists = false,
					connection = "COSMOS_CONN_STRING")
			OutputBinding<List<BizEvent>> documentdb,
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
			final ExecutionContext context) {

		if (enableTransactionListView) {
			logger.info("BizEventToViewDataStoreTimerTriggerProcessor function pp-0-start - called at {} with {} biz-events extracted to process.",
					LocalDateTime.now(), items.length);

			List<BizEvent> itemsToUpdate = Collections.synchronizedList(new ArrayList<>());
			List<BizEventsViewUser> userViewToInsert = Collections.synchronizedList(new ArrayList<>());
			List<BizEventsViewGeneral> generalViewToInsert = Collections.synchronizedList(new ArrayList<>());
			List<BizEventsViewCart> cartViewToInsert = Collections.synchronizedList(new ArrayList<>());
			
			Stream.of(items).parallel().unordered().forEach(bizEvent -> 
				this.bizEventsViewDataIngestion(
						itemsToUpdate,
						userViewToInsert,
						generalViewToInsert,
						cartViewToInsert,
						bizEvent)
			);

			if (!userViewToInsert.isEmpty()) {
				bizEventUserView.setValue(userViewToInsert);
			}
			if (!generalViewToInsert.isEmpty()) {
				bizEventGeneralView.setValue(generalViewToInsert);
			}
			if (!cartViewToInsert.isEmpty()) {
				bizEventCartView.setValue(cartViewToInsert);
			}
			if (!itemsToUpdate.isEmpty()) {
				documentdb.setValue(itemsToUpdate);
			}
		
			// data ingested to Biz-event views
			logger.info("""
					BizEventToViewDataStoreTimerTriggerProcessor function pp-0-stop - DATA INGESTION at {}:
					- number of data events ingested on the Biz-event views [user - {}, general - {}, cart - {}]
					- number of biz events processed and updated [biz-events - {}] on a total of {} items
					""",
					LocalDateTime.now(), userViewToInsert.size(), generalViewToInsert.size(), cartViewToInsert.size(), itemsToUpdate.size(), items.length);
		}
	}

	private void bizEventsViewDataIngestion(List<BizEvent> itemsToUpdate,
			List<BizEventsViewUser> userViewToInsert, List<BizEventsViewGeneral> generalViewToInsert,
			List<BizEventsViewCart> cartViewToInsert, BizEvent be) {
		try {
			BizEventToViewResult bizEventToViewResult = this.bizEventToViewService.mapBizEventToView(be);
			if (bizEventToViewResult != null) {
				be.setEventStatus(StatusType.INGESTED);
				userViewToInsert.addAll(bizEventToViewResult.getUserViewList());
				generalViewToInsert.add(bizEventToViewResult.getGeneralView());
				cartViewToInsert.add(bizEventToViewResult.getCartView());
				itemsToUpdate.add(be);
			}
		} catch (Exception e) {
			logger.error("BizEventToViewDataStoreTimerTriggerProcessor function error on mapping biz-event with id {} to its views", be.getId(), e);
		}
	}
}
