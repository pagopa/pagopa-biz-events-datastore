package it.gov.pagopa.bizeventsdatastore;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.google.common.base.Strings;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.Cardinality;
import com.microsoft.azure.functions.annotation.CosmosDBOutput;
import com.microsoft.azure.functions.annotation.EventHubTrigger;
import com.microsoft.azure.functions.annotation.FunctionName;

import it.gov.pagopa.bizeventsdatastore.client.RedisClient;
import it.gov.pagopa.bizeventsdatastore.entity.BizEvent;
import it.gov.pagopa.bizeventsdatastore.exception.AppException;
import lombok.NonNull;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.params.SetParams;

/**
 * Azure Functions with Azure Queue trigger.
 */
public class BizEventToDataStore {
    /**
     * This function will be invoked when an Event Hub trigger occurs
     */
	
	public static final JedisPooled jedis = RedisClient.getInstance().redisConnectionFactory();
	
	private final int expireTimeInMS = 
			System.getenv("REDIS_EXPIRE_TIME_MS") != null ? Integer.parseInt(System.getenv("REDIS_EXPIRE_TIME_MS")) : 3600000;
	
	private static final String REDIS_ID_PREFIX = "biz_";
	
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
    	            containerName = "biz-events",
    	            createIfNotExists = false,
    	            connection = "COSMOS_CONN_STRING")
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
					
					// READ FROM THE CACHE: The cache is queried to find out if the event has already been queued --> if yes it is skipped
					String value = this.findByBizEventId(bizEvtMsg.get(i).getId());
					
					if (Strings.isNullOrEmpty(value)) {
						BizEvent bz = bizEvtMsg.get(i);
						// set the IUR also on Debtor Position
						bz.getDebtorPosition().setIur(bz.getPaymentInfo().getIUR());
						// set the event creation date
						bz.setTimestamp(ZonedDateTime.now().toInstant().toEpochMilli());
						// set the event associated properties
						bz.setProperties(properties[i]);

						// WRITE IN THE CACHE: The result of the insertion in the cache is logged to verify the correct functioning
						String result = this.saveBizEventId(bizEvtMsg.get(i).getId());
						msg = String.format("BizEvent message with id %s was cached with result: %s",
								bizEvtMsg.get(i).getId(), result);
						logger.info(msg);

						bizEvtMsgWithProperties.add(bz);
					}
					else {
						// just to track duplicate events  
						msg = String.format("The BizEvent message with id %s has already been processed previously, it is discarded",
								bizEvtMsg.get(i).getId());
						logger.info(msg);
					}
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
    
    public String findByBizEventId(String id) {
    	return jedis.get(REDIS_ID_PREFIX+id);
    }
    
    public String saveBizEventId(String id) {
		return jedis.set(REDIS_ID_PREFIX+id, id, new SetParams().px(expireTimeInMS));
    }
    
    
}
