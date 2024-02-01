package it.gov.pagopa.bizeventsdatastore;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.annotation.CosmosDBOutput;
import com.microsoft.azure.functions.annotation.CosmosDBTrigger;
import com.microsoft.azure.functions.annotation.EventHubOutput;
import com.microsoft.azure.functions.annotation.FunctionName;
import it.gov.pagopa.bizeventsdatastore.client.PaymentManagerClient;
import it.gov.pagopa.bizeventsdatastore.entity.BizEvent;
import it.gov.pagopa.bizeventsdatastore.entity.enumeration.StatusType;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewCart;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewGeneral;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewUser;
import it.gov.pagopa.bizeventsdatastore.exception.PM4XXException;
import it.gov.pagopa.bizeventsdatastore.exception.PM5XXException;
import it.gov.pagopa.bizeventsdatastore.model.BizEventToViewResult;
import it.gov.pagopa.bizeventsdatastore.model.pm.TransactionDetails;
import it.gov.pagopa.bizeventsdatastore.service.BizEventToViewService;
import it.gov.pagopa.bizeventsdatastore.service.impl.BizEventToViewServiceImpl;
import it.gov.pagopa.bizeventsdatastore.util.ObjectMapperUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;


public class BizEventEnrichment {

    private final int maxRetryAttempts =
            System.getenv("MAX_RETRY_ON_TRIGGER_ATTEMPTS") != null ? Integer.parseInt(System.getenv("MAX_RETRY_ON_TRIGGER_ATTEMPTS")) : 3;

    private static final String BPAY_PAYMENT_TYPE = "BPAY";

    private static final String PPAL_PAYMENT_TYPE = "PPAL";

	private final BizEventToViewService bizEventToViewService;

	public BizEventEnrichment() {
		this.bizEventToViewService = new BizEventToViewServiceImpl();
	}

	BizEventEnrichment(BizEventToViewService bizEventToViewService) {
		this.bizEventToViewService = bizEventToViewService;
	}

	@FunctionName("BizEventEnrichmentProcessor")
    public void processBizEventEnrichment(
            @CosmosDBTrigger(
                    name = "BizEventDatastore",
                    databaseName = "db",
                    containerName = "biz-events",
                    leaseContainerName = "biz-events-leases",
                    createLeaseContainerIfNotExists = true,
                    maxItemsPerInvocation = 100,
                    connection = "COSMOS_CONN_STRING")
            List<BizEvent> items,
            @EventHubOutput(
                    name = "PdndBizEventHub",
                    eventHubName = "", // blank because the value is included in the connection string
                    connection = "PDND_EVENTHUB_CONN_STRING")
            OutputBinding<List<BizEvent>> bizEvtMsg,
            @CosmosDBOutput(
                    name = "EnrichedBizEventDatastore",
                    databaseName = "db",
                    containerName = "biz-events",
                    createIfNotExists = false,
                    connection = "COSMOS_CONN_STRING")
            OutputBinding<List<BizEvent>> documentdb,
            @CosmosDBOutput(
                    name = "BizEventUserView",
                    databaseName = "db",
                    containerName = "biz-events-view-user",
                    createIfNotExists = false,
                    connection = "COSMOS_CONN_STRING")
            OutputBinding<List<BizEventsViewUser>> bizEventUserView,
            @CosmosDBOutput(
                    name = "BizEventGeneralView",
                    databaseName = "db",
                    containerName = "biz-events-view-general",
                    createIfNotExists = false,
                    connection = "COSMOS_CONN_STRING")
            OutputBinding<List<BizEventsViewGeneral>> bizEventGeneralView,
            @CosmosDBOutput(
                    name = "BizEventCartView",
                    databaseName = "db",
                    containerName = "biz-events-view-cart",
                    createIfNotExists = false,
                    connection = "COSMOS_CONN_STRING")
            OutputBinding<List<BizEventsViewCart>> bizEventCartView,
            final ExecutionContext context
    ) {

        List<BizEvent> itemsDone = new ArrayList<>();
        List<BizEvent> itemsToUpdate = new ArrayList<>();
        List<BizEventsViewUser> userViewToInsert = new ArrayList<>();
        List<BizEventsViewGeneral> generalViewToInsert = new ArrayList<>();
        List<BizEventsViewCart> cartViewToInsert = new ArrayList<>();
        Logger logger = context.getLogger();

		String msg = String.format("BizEventEnrichment stat %s function - num events triggered %d", context.getInvocationId(),  items.size());
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
					try {
						BizEventToViewResult bizEventToViewResult = this.bizEventToViewService.mapBizEventToView(be);
						if (bizEventToViewResult != null) {
							userViewToInsert.addAll(bizEventToViewResult.getUserViewList());
							generalViewToInsert.add(bizEventToViewResult.getGeneralView());
							cartViewToInsert.add(bizEventToViewResult.getCartView());
						}
						itemsDone.add(be);
					} catch (Exception e) {
						String errMsg = String.format("Error on mapping biz-event with id %s to its views", be.getId());
						logger.log(Level.SEVERE, errMsg, e);
						be.setEventErrorMessage(e.getMessage());
						be.setEventStatus(StatusType.RETRY);
						be.setEventRetryEnrichmentCount(be.getEventRetryEnrichmentCount() + 1);
					}
				}
				
				/*
				 * Populates the list with events to update.
				 * If the number of attempts has reached the maxRetryAttempts, the update is stopped to avoid triggering again
				 */
				if (be.getEventRetryEnrichmentCount() <= maxRetryAttempts) {
					message = String.format("BizEventEnrichment COSMOS UPDATE at %s for event with id %s and status %s and numEnrichmentRetry %s", 
			        		LocalDateTime.now(), be.getId(), be.getEventStatus(), be.getEventRetryEnrichmentCount());
			        logger.info(message);
			        itemsToUpdate.add(be);
				} else {
					discarder++;
				}
			} else {
				discarder++;
			}

		}

		// discarder
		msg = String.format("BizEventEnrichment stat %s function - %d number of events in discarder  ", context.getInvocationId(), discarder);
		logger.info(msg);
		// call the Event Hub
		msg = String.format("BizEventEnrichment stat %s function - number of events in DONE sent to the event hub %d", context.getInvocationId(), itemsDone.size());
		logger.info(msg);
		bizEvtMsg.setValue(itemsDone);
		// call the Datastore
		msg = String.format("BizEventEnrichment stat %s function - number of events to update on the datastore %d", context.getInvocationId(), itemsToUpdate.size());
		logger.info(msg);
		documentdb.setValue(itemsToUpdate);


		// Insert in Biz-event views
		msg = String.format("BizEventEnrichment stat %s function - number of events to update on the Biz-event views: user - %d, general - %d, cart - %d ",
				context.getInvocationId(), userViewToInsert.size(), generalViewToInsert.size(), cartViewToInsert.size());
		logger.info(msg);
		if (!userViewToInsert.isEmpty()) {
			bizEventUserView.setValue(userViewToInsert);
		}
		if (!generalViewToInsert.isEmpty()) {
			bizEventGeneralView.setValue(generalViewToInsert);
		}
		if (!cartViewToInsert.isEmpty()) {
			bizEventCartView.setValue(cartViewToInsert);
		}
	}

	// the return of the BizEvent has the purpose of testing the correct execution of the method
	public BizEvent enrichBizEvent(BizEvent be, Logger logger, String invocationId) {
		// call the Payment Manager
		PaymentManagerClient pmClient = PaymentManagerClient.getInstance();
		try {
			String pMethod = be.getPaymentInfo().getPaymentMethod();
			String method = ((pMethod.equalsIgnoreCase(BPAY_PAYMENT_TYPE) || pMethod.equalsIgnoreCase(PPAL_PAYMENT_TYPE)) ? pMethod : "");
			TransactionDetails td = pmClient.getPMEventDetails(be.getIdPaymentManager(), method);
			be.setTransactionDetails(ObjectMapperUtils.map(td, it.gov.pagopa.bizeventsdatastore.entity.TransactionDetails.class));
			//Task PAGOPA-1193: adding mapping transactionId to align the PM with NDP, remove when ready
			be.getTransactionDetails().getTransaction().setTransactionId(String.valueOf(td.getTransaction().getIdTransaction()));
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
