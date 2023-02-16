package it.gov.pagopa.bizeventsdatastore;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.annotation.CosmosDBOutput;
import com.microsoft.azure.functions.annotation.CosmosDBTrigger;
import com.microsoft.azure.functions.annotation.EventHubOutput;
import com.microsoft.azure.functions.annotation.FunctionName;

import it.gov.pagopa.bizeventsdatastore.client.PaymentManagerClient;
import it.gov.pagopa.bizeventsdatastore.entity.BizEvent;
import it.gov.pagopa.bizeventsdatastore.entity.enumeration.StatusType;
import it.gov.pagopa.bizeventsdatastore.exception.PM4XXException;
import it.gov.pagopa.bizeventsdatastore.exception.PM5XXException;
import it.gov.pagopa.bizeventsdatastore.model.TransactionDetails;
import it.gov.pagopa.bizeventsdatastore.util.ObjectMapperUtils;


public class BizEventEnrichment {
	
	private final int maxRetryAttempts = 
			System.getenv("MAX_RETRY_ON_TRIGGER_ATTEMPTS") != null ? Integer.parseInt(System.getenv("MAX_RETRY_ON_TRIGGER_ATTEMPTS")) : 3;

	@FunctionName("BizEventEnrichmentProcessor")
	public void processBizEventEnrichment(
			@CosmosDBTrigger(
					name = "BizEventDatastore",
					databaseName = "db",
					collectionName = "biz-events",
					leaseCollectionName = "biz-events-leases",
					createLeaseCollectionIfNotExists = true,
					maxItemsPerInvocation=100,
					connectionStringSetting = "COSMOS_CONN_STRING") 
			List<BizEvent> items,
			@EventHubOutput(
					name = "PdndBizEventHub", 
					eventHubName = "", // blank because the value is included in the connection string
					connection = "PDND_EVENTHUB_CONN_STRING")
			OutputBinding<List<BizEvent>> bizEvtMsg,
			@CosmosDBOutput(
					name = "EnrichedBizEventDatastore",
					databaseName = "db",
					collectionName = "biz-events",
					createIfNotExists = false,
					connectionStringSetting = "COSMOS_CONN_STRING")
			OutputBinding<List<BizEvent>> documentdb,
			final ExecutionContext context
			) {
		
		List<BizEvent> itemsDone = new ArrayList<>();
		List<BizEvent> itemsToUpdate = new ArrayList<>();
		Logger logger = context.getLogger();

		String msg = String.format("BizEventEnrichment %s function - num events triggered %d", context.getInvocationId(),  items.size());
		logger.info(msg);
		int discarder = 0;
		for (BizEvent be: items) {
			
	        if (be.getEventStatus().equals(StatusType.NA) || 
	        		(be.getEventStatus().equals(StatusType.RETRY) && be.getEventRetryEnrichmentCount() <= maxRetryAttempts)) {
	        	
	        	String message = String.format("BizEventEnrichment function called at %s for event with id %s and status %s and numEnrichmentRetry %s", 
		        		LocalDateTime.now(), be.getId(), be.getEventStatus(), be.getEventRetryEnrichmentCount());
		        logger.info(message);
				
	        	be.setEventStatus(StatusType.DONE);
				
				// check if the event is to enrich -> field 'idPaymentManager' valued but section 'transactionDetails' not present
				if (null != be.getIdPaymentManager() && null == be.getTransactionDetails()) {
					this.enrichBizEvent(be, logger, context.getInvocationId());
				}
				
				// if status is DONE put the event on the Event Hub
				if (be.getEventStatus()==StatusType.DONE) {
					// items in DONE status good for the Event Hub
					itemsDone.add(be);
				} else {
					discarder++;
				}
				
				/** 
				 * Populates the list with events to update.
				 * If the number of attempts has reached the maxRetryAttempts, the update is stopped to avoid triggering again
				 */
				if (be.getEventRetryEnrichmentCount() <= maxRetryAttempts) {
					message = String.format("BizEventEnrichment COSMOS UPDATE at %s for event with id %s and status %s and numEnrichmentRetry %s", 
			        		LocalDateTime.now(), be.getId(), be.getEventStatus(), be.getEventRetryEnrichmentCount());
			        logger.info(message);
			        itemsToUpdate.add(be);
				}
			}	
		}

		// discarder
		msg = String.format("BizEventEnrichment %s function - %d number of events in discarder  ", context.getInvocationId(), discarder);
		logger.info(msg);
		// call the Event Hub
		msg = String.format("BizEventEnrichment %s function - number of events in DONE sent to the event hub %d", context.getInvocationId(), itemsDone.size());
		logger.info(msg);
		bizEvtMsg.setValue(itemsDone);
		// call the Datastore
		msg = String.format("BizEventEnrichment %s function - number of events to update on the datastore %d", context.getInvocationId(), itemsToUpdate.size());
		logger.info(msg);
		documentdb.setValue(itemsToUpdate);
	}

	// the return of the BizEvent has the purpose of testing the correct execution of the method
	public BizEvent enrichBizEvent(BizEvent be, Logger logger, String invocationId) {
		// call the Payment Manager
		PaymentManagerClient pmClient = PaymentManagerClient.getInstance();
		try {
			TransactionDetails td = pmClient.getPMEventDetails(be.getIdPaymentManager());
			be.setTransactionDetails(ObjectMapperUtils.map(td, it.gov.pagopa.bizeventsdatastore.entity.TransactionDetails.class));
		} catch (PM5XXException | IOException e) {
			logger.warning("BizEventEnrichment "+ invocationId +" function - non-blocking exception occurred for event with id "+be.getId()+" : " + e.getMessage());
			be.setEventStatus(StatusType.RETRY);
			// retry count increment
			be.setEventRetryEnrichmentCount(be.getEventRetryEnrichmentCount()+1);
			be.setEventErrorMessage(e.getMessage());
		} catch (PM4XXException | IllegalArgumentException e) {
			String errorMsg = "BizEventEnrichment "+ invocationId +" function - blocking exception occurred for event with id "+be.getId()+" : " + e.getMessage();
			logger.log(Level.SEVERE, errorMsg, e);
			be.setEventStatus(StatusType.FAILED);
			be.setEventErrorMessage(e.getMessage());
		} catch (Exception e) {
			String errorMsg = "BizEventEnrichment "+ invocationId +" function - blocking unexpected exception occurred for event with id "+be.getId()+" : " + e.getMessage();
			logger.log(Level.SEVERE, errorMsg, e);
			be.setEventStatus(StatusType.FAILED);
			be.setEventErrorMessage(e.getMessage());
		}
		
		return be;
	}
}
