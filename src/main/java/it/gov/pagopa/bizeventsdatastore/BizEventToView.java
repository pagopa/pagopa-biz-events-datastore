package it.gov.pagopa.bizeventsdatastore;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.CosmosDBOutput;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import it.gov.pagopa.bizeventsdatastore.client.BizEventCosmosClient;
import it.gov.pagopa.bizeventsdatastore.client.impl.BizEventCosmosClientImpl;
import it.gov.pagopa.bizeventsdatastore.entity.BizEvent;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewCart;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewGeneral;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewUser;
import it.gov.pagopa.bizeventsdatastore.exception.BizEventToViewConstraintViolationException;
import it.gov.pagopa.bizeventsdatastore.exception.BizEventNotFoundException;
import it.gov.pagopa.bizeventsdatastore.model.BizEventToViewResult;
import it.gov.pagopa.bizeventsdatastore.model.ProblemJson;
import it.gov.pagopa.bizeventsdatastore.service.BizEventToViewService;
import it.gov.pagopa.bizeventsdatastore.service.impl.BizEventToViewServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Azure Functions with Azure Http trigger.
 */
public class BizEventToView {

    private final Logger logger = LoggerFactory.getLogger(BizEventToView.class);

    private final BizEventCosmosClient bizEventCosmosClient;
    private final BizEventToViewService bizEventToViewService;

    public BizEventToView() {
        this.bizEventCosmosClient = BizEventCosmosClientImpl.getInstance();
        this.bizEventToViewService = new BizEventToViewServiceImpl();
    }

    BizEventToView(BizEventCosmosClient bizEventCosmosClient, BizEventToViewService bizEventToViewService) {
        this.bizEventCosmosClient = bizEventCosmosClient;
        this.bizEventToViewService = bizEventToViewService;
    }

    /**
     * This function will be invoked when a Http Trigger occurs
     */
    @FunctionName("BizEventToView")
    public HttpResponseMessage run(
            @HttpTrigger(name = "BizEventToViewFunctionTrigger",
                    methods = {HttpMethod.POST},
                    route = "biz-events/{biz-event-id}/create-view",
                    authLevel = AuthorizationLevel.ANONYMOUS)
            HttpRequestMessage<Optional<String>> request,
            @BindingName("biz-event-id") String bizEventId,
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
        logger.info("[{}] - Called at {} with id {}.",
                context.getFunctionName(), LocalDateTime.now(), bizEventId);

        if (bizEventId == null || bizEventId.isBlank()) {
            return request
                    .createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body(ProblemJson.builder()
                            .title(HttpStatus.BAD_REQUEST.name())
                            .detail("Please provide a valid biz-event id")
                            .status(HttpStatus.BAD_REQUEST.value())
                            .build())
                    .build();
        }

        BizEvent bizEvent;
        try {
            bizEvent = this.bizEventCosmosClient.getBizEventDocument(bizEventId);
        } catch (BizEventNotFoundException e) {
            String msg = String.format("Unable to retrieve the biz-event with id %s", bizEventId);
            logger.error(msg, e);
            return request
                    .createResponseBuilder(HttpStatus.NOT_FOUND)
                    .body(ProblemJson.builder()
                            .title(HttpStatus.NOT_FOUND.name())
                            .detail(msg)
                            .status(HttpStatus.NOT_FOUND.value())
                            .build())
                    .build();
        } catch (Exception e) {
            String msg = String.format("Unable to retrieve the biz-event with id %s: unexpected error", bizEventId);
            logger.error(msg, e);
            return request
                    .createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ProblemJson.builder()
                            .title(HttpStatus.INTERNAL_SERVER_ERROR.name())
                            .detail(msg)
                            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                            .build())
                    .build();
        }

        BizEventToViewResult bizEventToViewResult;
        try {
            bizEventToViewResult = this.bizEventToViewService.mapBizEventToView(bizEvent);
        } catch (BizEventToViewConstraintViolationException e) {
            String msg = String.format("Unable to map the biz-event with id %s to the views: %s", bizEventId, e.getErrorMessages());
            logger.error(msg, e);
            return request
                    .createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ProblemJson.builder()
                            .title(HttpStatus.INTERNAL_SERVER_ERROR.name())
                            .detail(msg)
                            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                            .build())
                    .build();
        } catch (Exception e) {
            String msg = String.format("Unable to map the biz-event with id %s to the views: unexpected error", bizEventId);
            logger.error(msg, e);
            return request
                    .createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ProblemJson.builder()
                            .title(HttpStatus.INTERNAL_SERVER_ERROR.name())
                            .detail(msg)
                            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                            .build())
                    .build();
        }

        if (bizEventToViewResult == null) {
            String msg = String.format("Unable to create the biz-event view for biz-event with id %s: bot debtor and user section are invalid",
                    bizEventId);
            logger.error(msg);
            return request
                    .createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ProblemJson.builder()
                            .title(HttpStatus.INTERNAL_SERVER_ERROR.name())
                            .detail(msg)
                            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                            .build())
                    .build();
        }

        bizEventUserView.setValue(bizEventToViewResult.getUserViewList());
        bizEventGeneralView.setValue(bizEventToViewResult.getGeneralView());
        bizEventCartView.setValue(bizEventToViewResult.getCartView());

        String responseMessage = String.format("View for Biz event with id %s successfully created", bizEventId);
        logger.info("[{}] {}", context.getFunctionName(), responseMessage);
        return request.createResponseBuilder(HttpStatus.OK)
                .body(responseMessage)
                .build();
    }
}
