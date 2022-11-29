package it.gov.pagopa.bizeventsdatastore;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
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
import it.gov.pagopa.bizeventsdatastore.model.WrapperTransactionDetails;
import it.gov.pagopa.bizeventsdatastore.util.ObjectMapperUtils;


public class BizEventEnrichment {

	@FunctionName("BizEventEnrichmentProcessor")
	public void processBizEventEnrichment(
			@CosmosDBTrigger(
					name = "BizEventDatastore",
					databaseName = "db",
					collectionName = "biz-events",
					leaseCollectionName = "biz-events-leases",
					createLeaseCollectionIfNotExists = true,
					connectionStringSetting = "COSMOS_CONN_STRING") 
			List<BizEvent> items,
			@EventHubOutput(
					name = "PdndBizEventHub", 
					eventHubName = "", // blank because the value is included in the connection string
					connection = "PDND_EVENTHUB_CONN_STRING")
			OutputBinding<BizEvent> bizEvtMsg,
			@CosmosDBOutput(
					name = "EnrichedBizEventDatastore",
					databaseName = "db",
					collectionName = "biz-events",
					createIfNotExists = false,
					connectionStringSetting = "COSMOS_CONN_STRING")
			OutputBinding<BizEvent> document,
			final ExecutionContext context
			) {

		for (BizEvent be: items) {
			if (be.getEventStatus().equals(StatusType.NA) || be.getEventStatus().equals(StatusType.RETRY)) {
				
				Logger logger = context.getLogger();
		        String message = String.format("BizEventEnrichment function called at %s for event with id %s and status %s", LocalDateTime.now(), be.getId(), be.getEventStatus());
		        logger.info(message);
				
				be.setEventStatus(StatusType.DONE);
				
				// check if the event is to enrich -> field 'idPaymentManager' valued but section 'transactionDetails' not present
				if (null != be.getIdPaymentManager() && null == be.getTransactionDetails()) {
					this.enrichBizEvent(be, logger);
				}

				// call the Event Hub
				bizEvtMsg.setValue(be);
				
				// call the Cosmos DB and update the event
				document.setValue(be);
			}	
		}
	}

	// the return of the status has the purpose of testing the correct execution of the method
	public StatusType enrichBizEvent(BizEvent be, Logger logger) {
		// call the Payment Manager
		PaymentManagerClient pmClient = PaymentManagerClient.getInstance();
		try {
			WrapperTransactionDetails wrapperTD = pmClient.getPMEventDetails(be.getIdPaymentManager());
			be.setTransactionDetails(ObjectMapperUtils.map(wrapperTD.getTransactionDetails(), it.gov.pagopa.bizeventsdatastore.entity.TransactionDetails.class));
		} catch (PM5XXException | IOException | IllegalArgumentException e) {
			logger.warning("non-blocking exception occurred: " + e.getMessage());
			be.setEventStatus(StatusType.RETRY);
		} catch (PM4XXException e) {
			logger.severe("blocking exception occurred: " + e.getMessage());
			be.setEventStatus(StatusType.FAILED);
		} catch (Exception e) {
			logger.severe("blocking unexpected exception occurred: " + e.getMessage());
			be.setEventStatus(StatusType.FAILED);
		}
		
		return be.getEventStatus();
	}
}
