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
                    name = "event",
                    eventHubName = "pagopa-d-evh-ns01",
                    connection = "Endpoint=sb://pagopa-d-evh-ns01.servicebus.windows.net/;SharedAccessKeyName=pagopa-biz-evt-rx;SharedAccessKey=hiV2B9lwUh66113dBE9lOhSoDa7eN6oJRTSRJp6mrc0=;EntityPath=nodo-dei-pagamenti-biz-evt",
                    cardinality = Cardinality.ONE) 
    		BizEvent bizEvtMsg,
    		@CosmosDBOutput(
    	            name = "BizEventDatastore",
    	            databaseName = "db",
    	            collectionName = "BizEvents",
    	            connectionStringSetting = "AccountEndpoint=https://localhost:8081/;AccountKey=C2y6yDjf5/R+ob0N8A7Cgv30VRDJIWEHLM+4QDU5DE2nQ9nDuVTqobD4b8mGGyPMbIZnqyMsEcaGQy67XIw/Jw==")
    	            OutputBinding<BizEvent> document,
            final ExecutionContext context) {

        Logger logger = context.getLogger();

        String message = String.format("BizEventToDataStore function called at: %s", LocalDateTime.now());
        logger.log(Level.INFO, () -> message);
        
        //document.setValue(bizEvtMsg);
    }
}
