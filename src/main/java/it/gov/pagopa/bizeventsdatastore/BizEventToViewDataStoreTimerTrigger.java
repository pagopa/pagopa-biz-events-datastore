package it.gov.pagopa.bizeventsdatastore;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
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

public class BizEventToViewDataStoreTimerTrigger {

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
			Logger logger = context.getLogger();
			String message = String.format("BizEventToViewDataStoreTimerTriggerProcessor function start - called at %s with %s biz-events extracted to process.", 
					LocalDateTime.now(), items.length);
			logger.info(message);
			
			List<BizEvent> itemsToUpdate = Collections.synchronizedList(new ArrayList<>());
			List<BizEventsViewUser> userViewToInsert = Collections.synchronizedList(new ArrayList<>());
			List<BizEventsViewGeneral> generalViewToInsert = Collections.synchronizedList(new ArrayList<>());
			List<BizEventsViewCart> cartViewToInsert = Collections.synchronizedList(new ArrayList<>());
			
			Stream.of(items).parallel().forEach(bizEvent -> 
				this.bizEventsViewDataIngestion(logger, itemsToUpdate, userViewToInsert, generalViewToInsert,
						cartViewToInsert, bizEvent)
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
		
			String textBlock = """
					BizEventToViewDataStoreTimerTriggerProcessor function stop - DATA INGESTION at %s:
					- number of data events ingested on the Biz-event views [user - %d, general - %d, cart - %d]
					- number of biz events processed and updated [biz-events - %d] on a total of %d items
					""";
			// data ingested to Biz-event views
			message = String.format(textBlock,
					LocalDateTime.now(), userViewToInsert.size(), generalViewToInsert.size(), cartViewToInsert.size(), itemsToUpdate.size(), items.length);
			logger.info(message);
		}
	}

	private void bizEventsViewDataIngestion(Logger logger, List<BizEvent> itemsToUpdate,
			List<BizEventsViewUser> userViewToInsert, List<BizEventsViewGeneral> generalViewToInsert,
			List<BizEventsViewCart> cartViewToInsert, BizEvent be) {
		try {
			BizEventToViewResult bizEventToViewResult = this.bizEventToViewService.mapBizEventToView(logger, be);
			if (bizEventToViewResult != null) {
				be.setEventStatus(StatusType.INGESTED);
				userViewToInsert.addAll(bizEventToViewResult.getUserViewList());
				generalViewToInsert.add(bizEventToViewResult.getGeneralView());
				cartViewToInsert.add(bizEventToViewResult.getCartView());
				itemsToUpdate.add(be);
			}
		} catch (Exception e) {
			String errMsg = String.format("BizEventToViewDataStoreTimerTriggerProcessor function error on mapping biz-event with id %s to its views", be.getId());
			logger.log(Level.SEVERE, errMsg, e);
		}
	}
}
