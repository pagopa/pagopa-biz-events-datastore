package it.gov.pagopa.bizeventsdatastore;

import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.annotation.CosmosDBInput;
import com.microsoft.azure.functions.annotation.CosmosDBOutput;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.TimerTrigger;

import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewGeneral;

public class BizEventViewDataStoreUpdateTimerTrigger {

	private final boolean enableUpdate = Boolean.parseBoolean(System.getenv().getOrDefault("UPDATE_VIEW_TIMER_TRIGGER_ENABLE", "false"));

	/**
	 * This function will be invoked periodically according to the specified schedule.
	 */
	@FunctionName("BizEventViewDataStoreUpdateTimerTriggerProcessor")
	public void processBizEventScheduledTrigger(
			@TimerTrigger(
					name = "timerInfo", 
					schedule = "%UPDATE_VIEW_TRIGGER_SCHEDULE%"
					) 
			String timerInfo,
			@CosmosDBInput(
					name = "BizEventUserViewInput",
					databaseName = "db",
					containerName = "biz-events-view-general",
					connection = "COSMOS_CONN_STRING",
					sqlQuery = "%UPDATE_VIEW_TRIGGER_SQL_QUERY%"
					)
			BizEventsViewGeneral[] items,
			@CosmosDBOutput(
					name = "BizEventGeneralView",
					databaseName = "db",
					containerName = "biz-events-view-general",
					createIfNotExists = false,
					connection = "COSMOS_CONN_STRING")
			OutputBinding<List<BizEventsViewGeneral>> bizEventGeneralView,
			final ExecutionContext context) {

		if (enableUpdate) {
			Logger logger = context.getLogger();
			String message = String.format("BizEventViewDataStoreUpdateTimerTrigger function pp-0-start - called at %s with %s biz-events extracted to process.", 
					LocalDateTime.now(), items.length);
			logger.info(message);
			
			
		
			String textBlock = """
					BizEventViewDataStoreUpdateTimerTrigger function pp-0-stop - DATA UPDATED at %s:
					- number of data events ingested on the Biz-event views [user - %d, general - %d, cart - %d]
					- number of biz events processed and updated [biz-events - %d] on a total of %d items
					""";
			logger.info(message);
		}
	}
}
