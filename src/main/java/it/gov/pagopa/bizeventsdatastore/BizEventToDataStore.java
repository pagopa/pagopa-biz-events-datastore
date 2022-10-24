package it.gov.pagopa.bizeventsdatastore;

import java.time.LocalDateTime;
import java.util.logging.Level;
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
                    connection = "EventHubConnectionString",
                    cardinality = Cardinality.ONE) 
    		BizEvent bizEvtMsg,
    		@CosmosDBOutput(
    	            name = "BizEventDatastore",
    	            databaseName = "%COSMOS_DB_NAME%",
    	            collectionName = "%COSMOS_DB_CONTAINER_NAME%",
    	            createIfNotExists = false,
    	            connectionStringSetting = "CosmosDBConnectionString")
    	            OutputBinding<BizEvent> document,
            final ExecutionContext context) {

        Logger logger = context.getLogger();

        String message = String.format("BizEventToDataStore function called at: %s", LocalDateTime.now());
        logger.log(Level.INFO, () -> message);
        
        // persist the item
        document.setValue(bizEvtMsg);
    }
}
