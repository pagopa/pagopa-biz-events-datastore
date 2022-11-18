package it.gov.pagopa.bizeventsdatastore;

import java.util.List;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.annotation.CosmosDBOutput;
import com.microsoft.azure.functions.annotation.CosmosDBTrigger;
import com.microsoft.azure.functions.annotation.EventHubOutput;
import com.microsoft.azure.functions.annotation.FunctionName;

import it.gov.pagopa.bizeventsdatastore.client.PaymentManagerClient;
import it.gov.pagopa.bizeventsdatastore.entity.BizEvent;
import it.gov.pagopa.bizeventsdatastore.entity.enumeration.StatusType;
import it.gov.pagopa.bizeventsdatastore.model.PaymentEvent;


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
		context.getLogger().info(items.size() + " item(s) is/are inserted.");

		PaymentManagerClient pmClient = PaymentManagerClient.getInstance();

		for (BizEvent be: items) {
			if (be.getEventStatus().equals(StatusType.RETRY)) {
				context.getLogger().info("The ID of the item is: " + be.getId());
				be.setEventStatus(StatusType.DONE);
				
				// TODO check if the item is to enrich

				// call the Payment Manager
				try {
					List<PaymentEvent> peList = pmClient.getPMEventDetails(be.getIdPaymentManager());
					context.getLogger().info(peList.size() + " event(s) is/are recovered.");
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					be.setEventStatus(StatusType.FAILED);
				}

				// call the Event Hub
				bizEvtMsg.setValue(be);
				
				// call the Cosmos DB and update the event
				document.setValue(be);
			}	
		}
	}

}
