package it.gov.pagopa.bizeventsdatastore;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvValidationException;
import it.gov.pagopa.bizeventsdatastore.entity.BizEvent;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewUser;
import it.gov.pagopa.bizeventsdatastore.exception.BizEventNotFoundException;
import it.gov.pagopa.bizeventsdatastore.exception.BizEventNotValidForViewGenerationException;
import it.gov.pagopa.bizeventsdatastore.exception.BizEventToViewConstraintViolationException;
import it.gov.pagopa.bizeventsdatastore.exception.BizEventViewSaveErrorException;
import it.gov.pagopa.bizeventsdatastore.model.BizEventToViewResult;
import it.gov.pagopa.bizeventsdatastore.model.ProblemJson;
import it.gov.pagopa.bizeventsdatastore.service.BizEventCosmosService;
import it.gov.pagopa.bizeventsdatastore.service.BizEventToViewService;
import it.gov.pagopa.bizeventsdatastore.service.impl.BizEventCosmosServiceImpl;
import it.gov.pagopa.bizeventsdatastore.service.impl.BizEventToViewServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
    public HttpResponseMessage run(
            @HttpTrigger(name = "BizEventToViewMassiveFunctionTrigger",
                    methods = {HttpMethod.POST},
                    route = "biz-events/create-views",
                    authLevel = AuthorizationLevel.ANONYMOUS)
            HttpRequestMessage<Optional<InputStream>> request,
            final ExecutionContext context
    ) {

        String contentType = request.getHeaders().get("content-type");
        if (contentType == null || !contentType.contains("multipart/form-data")) {
            return request
                    .createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body(ProblemJson.builder()
                            .title(HttpStatus.BAD_REQUEST.name())
                            .detail("Content-Type mus be multipart/form-data")
                            .status(HttpStatus.BAD_REQUEST.value())
                            .build())
                    .build();
        }

        Optional<InputStream> body = request.getBody();
        if (body.isEmpty()) {
            return request
                    .createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body(ProblemJson.builder()
                            .title(HttpStatus.BAD_REQUEST.name())
                            .detail("Please provide a valid body")
                            .status(HttpStatus.BAD_REQUEST.value())
                            .build())
                    .build();
        }

        int processCount = 0;
        int successCount = 0;
        Map<String, List<String>> failed = new HashMap<>();
        try (CSVReader reader = new CSVReaderBuilder(new InputStreamReader(body.get())).build()) {
            String[] line;
            while ((line = reader.readNext()) != null) {
                processCount++;
                if (line.length > 1) {
                    logger.error("Unexpected CSV format");
                    continue;
                }
                String bizEventId = line[0];
                try {
                    BizEvent bizEvent = this.bizEventCosmosService.getBizEvent(bizEventId);
                    BizEventToViewResult bizEventToViewResult = this.bizEventToViewService.mapBizEventToView(bizEvent);

                    if (bizEventToViewResult == null) {
                        throw new BizEventNotValidForViewGenerationException("Unable to create the biz-event view: bot debtor and user section are invalid");
                    }

                    for (BizEventsViewUser viewUser : bizEventToViewResult.getUserViewList()) {
                        this.bizEventCosmosService.saveBizEventViewUser(viewUser);
                    }
                    this.bizEventCosmosService.saveBizEventViewGeneral(bizEventToViewResult.getGeneralView());
                    this.bizEventCosmosService.saveBizEventViewCart(bizEventToViewResult.getCartView());
                    successCount++;
                } catch (BizEventNotFoundException e) {
                    failed.computeIfAbsent("Biz event not found", k -> new ArrayList<>()).add(bizEventId);
                } catch (BizEventNotValidForViewGenerationException | BizEventViewSaveErrorException e) {
                    failed.computeIfAbsent(e.getMessage(), k -> new ArrayList<>()).add(bizEventId);
                } catch (BizEventToViewConstraintViolationException e) {
                    failed.computeIfAbsent("Generated biz event view is not valid", k -> new ArrayList<>()).add(bizEventId);
                }
            }
        } catch (IOException e) {
            logger.error("IO error occur while processing file. Processed: {}, Success: {}, Failure details: {}", processCount, successCount, failed, e);
            return request
                    .createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ProblemJson.builder()
                            .title(HttpStatus.INTERNAL_SERVER_ERROR.name())
                            .detail("IO Error while processing file")
                            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                            .build())
                    .build();
        } catch (CsvValidationException e) {
            logger.error("CSV Validation error occur while processing file. Processed: {}, Success: {}, Failure details: {}", processCount, successCount, failed, e);
            return request
                    .createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ProblemJson.builder()
                            .title(HttpStatus.INTERNAL_SERVER_ERROR.name())
                            .detail("Validation error while processing CSV file")
                            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                            .build())
                    .build();
        }

        if (failed.isEmpty() && successCount == processCount) {
            return request.createResponseBuilder(HttpStatus.OK)
                    .body(formatResponseDetail("Elaboration completed successfully", processCount, successCount, failed))
                    .build();
        }

        if (successCount == 0) {
            return request
                    .createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ProblemJson.builder()
                            .title(HttpStatus.INTERNAL_SERVER_ERROR.name())
                            .detail(formatResponseDetail("All processed id result in an error", processCount, successCount, failed))
                            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                            .build())
                    .build();
        }

        return request
                .createResponseBuilder(HttpStatus.MULTI_STATUS)
                .body(ProblemJson.builder()
                        .title(HttpStatus.MULTI_STATUS.name())
                        .detail(formatResponseDetail("The recovery has a partial result", processCount, successCount, failed))
                        .status(HttpStatus.MULTI_STATUS.value())
                        .build())
                .build();
    }

    private String formatResponseDetail(
            String elaborationCompletedSuccessfully, int processCount,
            int successCount,
            Map<String, List<String>> failed
    ) {
        return String.format("%s. Processed: %s, Success: %s, Failure details: %s", elaborationCompletedSuccessfully, processCount, successCount, failed);
    }
}