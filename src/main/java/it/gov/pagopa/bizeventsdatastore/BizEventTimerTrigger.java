package it.gov.pagopa.bizeventsdatastore;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.annotation.CosmosDBInput;
import com.microsoft.azure.functions.annotation.CosmosDBOutput;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.TimerTrigger;

import it.gov.pagopa.bizeventsdatastore.entity.BizEvent;
import it.gov.pagopa.bizeventsdatastore.entity.enumeration.StatusType;

public class BizEventTimerTrigger {

	/**
	 * This function will be invoked periodically according to the specified schedule.
	 */
	@FunctionName("BizEventTimerTriggerProcessor")
	public void processBizEventScheduledTrigger(
			@TimerTrigger(
					name = "timerInfo", 
					schedule = "%TRIGGER_SCHEDULE%"
					) 
			String timerInfo,
			@CosmosDBInput(
					name = "EnrichedBizEventDatastoreInput",
					databaseName = "db",
					containerName = "biz-events",
					connection = "COSMOS_CONN_STRING",
					sqlQuery = "%TRIGGER_SQL_QUERY%"
					)
			BizEvent[] items,
			@CosmosDBOutput(
					name = "EnrichedBizEventDatastoreOutput",
					databaseName = "db",
					containerName = "biz-events",
					createIfNotExists = false,
					connection = "COSMOS_CONN_STRING")
			OutputBinding<List<BizEvent>> documentdb,
			final ExecutionContext context) {
		
		List<BizEvent> itemsToUpdate = new ArrayList<>();
		Logger logger = context.getLogger();

		for (BizEvent be: items) {
			String message = String.format("BizEventTimerTriggerProcessor function called at %s with %s biz-events extracted to process.  "
					+ "In progress the event with id %s and status %s and numEnrichmentRetry %s and paymentDateTime %s", 
					LocalDateTime.now(), items.length, be.getId(), be.getEventStatus(), be.getEventRetryEnrichmentCount(), be.getPaymentInfo().getPaymentDateTime());
			logger.info(message);

			be.setEventStatus(StatusType.RETRY);
			be.setEventRetryEnrichmentCount(0);	
			be.setEventErrorMessage(System.getenv().getOrDefault("TRIGGER_CUSTOM_ERROR_MESSAGE", "-"));
			be.setEventTriggeredBySchedule(Boolean.TRUE);
			// Populates the list for the UPDATE of biz-event status
			message = String.format("BizEventTimerTriggerProcessor function COSMOS UPDATE at %s for event with id %s and status %s and numEnrichmentRetry %s and paymentDateTime %s", 
					LocalDateTime.now(), be.getId(), be.getEventStatus(), be.getEventRetryEnrichmentCount(), be.getPaymentInfo().getPaymentDateTime());
			logger.info(message);
			itemsToUpdate.add(be);
		}
		
		documentdb.setValue(itemsToUpdate);

	}
}
