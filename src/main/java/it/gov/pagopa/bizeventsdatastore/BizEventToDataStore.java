package it.gov.pagopa.bizeventsdatastore;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.Cardinality;
import com.microsoft.azure.functions.annotation.CosmosDBOutput;
import com.microsoft.azure.functions.annotation.EventHubTrigger;
import com.microsoft.azure.functions.annotation.FunctionName;

import it.gov.pagopa.bizeventsdatastore.entity.BizEvent;
import it.gov.pagopa.bizeventsdatastore.exception.AppException;
import lombok.NonNull;

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
    		@BindingName(value = "PropertiesArray") Map<String, Object>[] properties,
            @CosmosDBOutput(
    	            name = "BizEventDatastore",
    	            databaseName = "db",
    	            collectionName = "biz-events",
    	            createIfNotExists = false,
                    connectionStringSetting = "COSMOS_CONN_STRING")
                    @NonNull OutputBinding<List<BizEvent>> documentdb,
            final ExecutionContext context) {

        Logger logger = context.getLogger();

        String message = String.format("BizEventToDataStore function called at %s with events list size %s and properties size %s", LocalDateTime.now(), bizEvtMsg.size(), properties.length);
        logger.info(message);
        
        // persist the item
        try {
        	if (bizEvtMsg.size() == properties.length) {

        		List<BizEvent> bizEvtMsgWithProperties = new ArrayList<>();
    	        for (int i=0; i<bizEvtMsg.size(); i++) {

					String msg = String.format("BizEventToDataStore function called at %s with event id %s rx",
							LocalDateTime.now(), bizEvtMsg.get(i).getId());
					logger.info(msg);

    	        	BizEvent bz = bizEvtMsg.get(i);
    	        	// set the event creation date
    	        	bz.setTimestamp(ZonedDateTime.now().toInstant().toEpochMilli());
    	        	// set the event associated properties
	        		bz.setProperties(properties[i]);
	        		bizEvtMsgWithProperties.add(bz);
    	        }
    	        documentdb.setValue(bizEvtMsgWithProperties);
            } else {
            	throw new AppException("Error during processing - "
            			+ "The size of the events to be processed and their associated properties does not match [bizEvtMsg.size="+bizEvtMsg.size()+"; properties.length="+properties.length+"]");
            }
        	
        } catch (NullPointerException e) {
            logger.severe("NullPointerException exception on cosmos biz-events msg ingestion at "+ LocalDateTime.now()+ " : " + e.getMessage());
        } catch (Exception e) {
            logger.severe("Generic exception on cosmos biz-events msg ingestion at "+ LocalDateTime.now()+ " : " + e.getMessage());
        }

    }
}
