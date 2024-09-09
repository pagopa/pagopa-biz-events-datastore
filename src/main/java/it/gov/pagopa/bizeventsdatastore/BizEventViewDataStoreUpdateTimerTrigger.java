package it.gov.pagopa.bizeventsdatastore;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Stream;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.annotation.CosmosDBInput;
import com.microsoft.azure.functions.annotation.CosmosDBOutput;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.TimerTrigger;

import it.gov.pagopa.bizeventsdatastore.entity.enumeration.ServiceIdentifierType;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewGeneral;

public class BizEventViewDataStoreUpdateTimerTrigger {

	private final boolean enableUpdate = Boolean.parseBoolean(System.getenv().getOrDefault("UPDATE_VIEW_TIMER_TRIGGER_ENABLE", "false"));
	private final String originValue = System.getenv().getOrDefault("UPDATE_VIEW_ORIGIN_VALUE", ServiceIdentifierType.NDP003PROD.name());

	/**
	 * This function will be invoked periodically according to the specified schedule.
	 */
	@FunctionName("BizEventViewDataStoreUpdateTimerTriggerProcessor")
	public void processBizEventViewScheduledTrigger(
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
			String message = String.format("BizEventViewDataStoreUpdateTimerTrigger function pp-0-start - called at %s with %s biz-events-view-general extracted to process.", 
					LocalDateTime.now(), items.length);
			logger.info(message);

			List<BizEventsViewGeneral> itemsToUpdate = Collections.synchronizedList(new ArrayList<>());

			Stream.of(items).parallel().unordered().forEach(i -> 
			this.bizEventsViewUpdate(itemsToUpdate, i)
					);

			if (!itemsToUpdate.isEmpty()) {
				bizEventGeneralView.setValue(itemsToUpdate);
			}


			String textBlock = """
					BizEventViewDataStoreUpdateTimerTrigger function pp-0-stop - DATA UPDATED at %s:
					- number %d of biz-events-view-general processed and updated on a total of %d items
					""";
			message = String.format(textBlock,
					LocalDateTime.now(), itemsToUpdate.size(), items.length);
			logger.info(message);
		}
	}

	public void bizEventsViewUpdate(List<BizEventsViewGeneral> itemsToUpdate, BizEventsViewGeneral bizEventsViewGeneral) {
		if (bizEventsViewGeneral != null) {
			bizEventsViewGeneral.setOrigin(Enum.valueOf(ServiceIdentifierType.class, originValue));
			itemsToUpdate.add(bizEventsViewGeneral);
		}
	}
}
