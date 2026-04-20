package it.gov.pagopa.bizeventsdatastore;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.annotation.CosmosDBInput;
import com.microsoft.azure.functions.annotation.CosmosDBOutput;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.TimerTrigger;

import it.gov.pagopa.bizeventsdatastore.entity.BizEvent;
import it.gov.pagopa.bizeventsdatastore.entity.enumeration.StatusType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BizEventTimerTrigger {

	private final Logger logger = LoggerFactory.getLogger(BizEventTimerTrigger.class);

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

		for (BizEvent be: items) {
			logger.debug("BizEventTimerTriggerProcessor function called at {} with {} biz-events extracted to process.  "
					+ "In progress the event with id {} and status {} and numEnrichmentRetry {} and paymentDateTime {}",
					LocalDateTime.now(), items.length, be.getId(), be.getEventStatus(), be.getEventRetryEnrichmentCount(), be.getPaymentInfo().getPaymentDateTime());

			be.setEventStatus(StatusType.RETRY);
			be.setEventRetryEnrichmentCount(0);	
			be.setEventErrorMessage(System.getenv().getOrDefault("TRIGGER_CUSTOM_ERROR_MESSAGE", "-"));
			be.setEventTriggeredBySchedule(Boolean.TRUE);
			// Populates the list for the UPDATE of biz-event status
			logger.debug("BizEventTimerTriggerProcessor function COSMOS UPDATE at {} for event with id {} and status {} and numEnrichmentRetry {} and paymentDateTime {}",
					LocalDateTime.now(), be.getId(), be.getEventStatus(), be.getEventRetryEnrichmentCount(), be.getPaymentInfo().getPaymentDateTime());
			itemsToUpdate.add(be);
		}
		
		documentdb.setValue(itemsToUpdate);

	}
}
