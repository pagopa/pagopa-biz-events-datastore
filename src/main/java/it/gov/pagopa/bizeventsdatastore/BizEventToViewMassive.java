package it.gov.pagopa.bizeventsdatastore;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.annotation.CosmosDBOutput;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.QueueTrigger;
import it.gov.pagopa.bizeventsdatastore.entity.BizEvent;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewCart;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewGeneral;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewUser;
import it.gov.pagopa.bizeventsdatastore.exception.BizEventNotFoundException;
import it.gov.pagopa.bizeventsdatastore.exception.BizEventNotValidForViewGenerationException;
import it.gov.pagopa.bizeventsdatastore.exception.BizEventToViewConstraintViolationException;
import it.gov.pagopa.bizeventsdatastore.model.BizEventToViewResult;
import it.gov.pagopa.bizeventsdatastore.service.BizEventCosmosService;
import it.gov.pagopa.bizeventsdatastore.service.BizEventToViewService;
import it.gov.pagopa.bizeventsdatastore.service.impl.BizEventCosmosServiceImpl;
import it.gov.pagopa.bizeventsdatastore.service.impl.BizEventToViewServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Azure Functions with Azure Http trigger.
 */
public class BizEventToViewMassive {

    private final Logger logger = LoggerFactory.getLogger(BizEventToViewMassive.class);

    private final BizEventCosmosService bizEventCosmosService;
    private final BizEventToViewService bizEventToViewService;

    public BizEventToViewMassive() {
        this.bizEventCosmosService = new BizEventCosmosServiceImpl();
        this.bizEventToViewService = new BizEventToViewServiceImpl();
    }

    BizEventToViewMassive(BizEventCosmosService bizEventCosmosService, BizEventToViewService bizEventToViewService) {
        this.bizEventCosmosService = bizEventCosmosService;
        this.bizEventToViewService = bizEventToViewService;
    }

    /**
     * This function will be invoked when a Http Trigger occurs
     */
    @FunctionName("BizEventToViewMassive")
    public void run(
            @QueueTrigger(
                    name = "QueueReceiptWaitingForGen",
                    queueName = "%RECEIPT_QUEUE_TOPIC%",
                    connection = "RECEIPTS_STORAGE_CONN_STRING")
            String bizEventId,
            @CosmosDBOutput(
                    name = "BizEventUserView",
                    databaseName = "db",
                    containerName = "biz-events-view-user",
                    connection = "COSMOS_CONN_STRING")
            OutputBinding<List<BizEventsViewUser>> bizEventUserView,
            @CosmosDBOutput(
                    name = "BizEventGeneralView",
                    databaseName = "db",
                    containerName = "biz-events-view-general",
                    connection = "COSMOS_CONN_STRING")
            OutputBinding<BizEventsViewGeneral> bizEventGeneralView,
            @CosmosDBOutput(
                    name = "BizEventCartView",
                    databaseName = "db",
                    containerName = "biz-events-view-cart",
                    connection = "COSMOS_CONN_STRING")
            OutputBinding<BizEventsViewCart> bizEventCartView,
            final ExecutionContext context
    ) {
        logger.debug("Received message for biz event id {}", bizEventId);
        try {
            BizEvent bizEvent = this.bizEventCosmosService.getBizEvent(bizEventId);
            BizEventToViewResult bizEventToViewResult = this.bizEventToViewService.mapBizEventToView(bizEvent);

            if (bizEventToViewResult == null) {
                throw new BizEventNotValidForViewGenerationException("Biz event not valid for view generation");
            }

            bizEventUserView.setValue(bizEventToViewResult.getUserViewList());
            bizEventGeneralView.setValue(bizEventToViewResult.getGeneralView());
            bizEventCartView.setValue(bizEventToViewResult.getCartView());
        } catch (BizEventNotFoundException e) {
            logger.error("Biz event with id {} not found", bizEventId, e);
        } catch (BizEventNotValidForViewGenerationException e) {
            logger.error("Unable to create the biz-event view for biz event with id {}: bot debtor and user section are invalid", bizEventId, e);
        } catch (BizEventToViewConstraintViolationException e) {
            logger.error("Generated biz event view for biz event with id {} is not valid", bizEventId, e);
        }
    }
}