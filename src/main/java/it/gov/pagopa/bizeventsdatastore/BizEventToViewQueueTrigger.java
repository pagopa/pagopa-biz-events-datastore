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

import java.util.ArrayList;
import java.util.List;

/**
 * Azure Functions with Azure Queue trigger.
 */
public class BizEventToViewQueueTrigger {

    private final Logger logger = LoggerFactory.getLogger(BizEventToViewQueueTrigger.class);

    private final BizEventCosmosService bizEventCosmosService;
    private final BizEventToViewService bizEventToViewService;

    public BizEventToViewQueueTrigger() {
        this.bizEventCosmosService = new BizEventCosmosServiceImpl();
        this.bizEventToViewService = new BizEventToViewServiceImpl();
    }

    BizEventToViewQueueTrigger(BizEventCosmosService bizEventCosmosService, BizEventToViewService bizEventToViewService) {
        this.bizEventCosmosService = bizEventCosmosService;
        this.bizEventToViewService = bizEventToViewService;
    }

    /**
     * This function will be invoked when a Queue Trigger occurs
     */
    @FunctionName("BizEventToViewQueueTrigger")
    public void run(
            @QueueTrigger(
                    name = "QueueMassiveBizToViewRegen",
                    queueName = "%MASSIVE_VIEW_REGEN_QUEUE_TOPIC%",
                    connection = "AzureWebJobsStorage")
            List<String> bizEventIds,
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
            OutputBinding<List<BizEventsViewGeneral>> bizEventGeneralView,
            @CosmosDBOutput(
                    name = "BizEventCartView",
                    databaseName = "db",
                    containerName = "biz-events-view-cart",
                    connection = "COSMOS_CONN_STRING")
            OutputBinding<List<BizEventsViewCart>> bizEventCartView,
            final ExecutionContext context
    ) {
        logger.debug("Received a batch of {} biz event ids", bizEventIds.size());

        List<BizEventsViewUser> userViews = new ArrayList<>();
        List<BizEventsViewGeneral> generalViews = new ArrayList<>();
        List<BizEventsViewCart> cartViews = new ArrayList<>();

        for (String bizEventId : bizEventIds) {
            logger.debug("Processing biz event id {}", bizEventId);
            try {
                BizEvent bizEvent = this.bizEventCosmosService.getBizEvent(bizEventId);
                BizEventToViewResult bizEventToViewResult = this.bizEventToViewService.mapBizEventToView(bizEvent);

                if (bizEventToViewResult == null) {
                    throw new BizEventNotValidForViewGenerationException("Biz event not valid for view generation");
                }

                userViews.addAll(bizEventToViewResult.getUserViewList());
                generalViews.add(bizEventToViewResult.getGeneralView());
                cartViews.add(bizEventToViewResult.getCartView());
            } catch (BizEventNotFoundException e) {
                logger.error("Biz event with id {} not found", bizEventId, e);
            } catch (BizEventNotValidForViewGenerationException e) {
                logger.error("Unable to create the biz-event view for biz event with id {}: bot debtor and user section are invalid", bizEventId, e);
            } catch (BizEventToViewConstraintViolationException e) {
                logger.error("Generated biz event view for biz event with id {} is not valid", bizEventId, e);
            }
        }

        if (!userViews.isEmpty()) {
            bizEventUserView.setValue(userViews);
        }
        if (!generalViews.isEmpty()) {
            bizEventGeneralView.setValue(generalViews);
        }
        if (!cartViews.isEmpty()) {
            bizEventCartView.setValue(cartViews);
        }
    }
}