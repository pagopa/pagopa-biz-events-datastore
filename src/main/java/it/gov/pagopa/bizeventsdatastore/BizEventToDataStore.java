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
                    eventHubName = "nodo-dei-pagamenti-biz-evt",
                    connection = "Endpoint=sb://pagopa-d-evh-ns01.servicebus.windows.net/;SharedAccessKeyName=pagopa-biz-evt-rx;SharedAccessKey=hiV2B9lwUh66113dBE9lOhSoDa7eN6oJRTSRJp6mrc0=;EntityPath=nodo-dei-pagamenti-biz-evt",
                    cardinality = Cardinality.ONE) 
    		BizEvent bizEvtMsg,
    		@CosmosDBOutput(
    	            name = "BizEventDatastore",
    	            databaseName = "db",
    	            collectionName = "BizEvents",
    	            connectionStringSetting = "AccountEndpoint=https://pagopa-d-weu-bizevents-ds-cosmos-account.documents.azure.com:443/;AccountKey=9cFCgavL9CIA5YA2ouC6fUEOrnhSkxX617MNwWFfSC1Q958rQAw36zEt6HDZV4v2oi1eJfzPNksjSA7JTa6XIA==;")
    	            OutputBinding<BizEvent> document,
            final ExecutionContext context) {

        Logger logger = context.getLogger();

        String message = String.format("BizEventToDataStore function called at: %s", LocalDateTime.now());
        logger.log(Level.INFO, () -> message);
        
        document.setValue(bizEvtMsg);
    }
}
