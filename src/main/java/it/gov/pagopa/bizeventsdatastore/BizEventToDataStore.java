package it.gov.pagopa.bizeventsdatastore;

import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.annotation.Cardinality;
import com.microsoft.azure.functions.annotation.CosmosDBOutput;
import com.microsoft.azure.functions.annotation.EventHubTrigger;
import com.microsoft.azure.functions.annotation.FunctionName;

import it.gov.pagopa.bizeventsdatastore.entity.BizEvent;

/**
 * Azure Functions with Azure Queue trigger.
 */
public class BizEventToDataStore {
    /**
     * This function will be invoked when an Event Hub trigger occurs
     */
    @FunctionName("EventHubBizEventProcessor")
    public void processBizEvent (
            @EventHubTrigger(
                    name = "BizEvent",
                    eventHubName = "", // blank because the value is included in the connection string
                    connection = "EVENTHUB_CONN_STRING",
                    cardinality = Cardinality.MANY)
    		List<BizEvent> bizEvtMsg,
            @CosmosDBOutput(
    	            name = "BizEventDatastore",
    	            databaseName = "db",
    	            collectionName = "biz-events",
    	            createIfNotExists = false,
                    connectionStringSetting = "COSMOS_CONN_STRING")
    	            OutputBinding<List<BizEvent>> document,
            final ExecutionContext context) {

        Logger logger = context.getLogger();

//        String message = String.format("BizEventToDataStore function called at: %s", LocalDateTime.now());
//        logger.info(message);

        // persist the item
        try {
            if (document != null) {
                document.setValue(bizEvtMsg);
            }
        } catch (Exception e) {
            logger.severe("Exception on cosmos biz-events msg ingestion at "+ LocalDateTime.now()+ " : " + e.getMessage());
        }

    }
}
