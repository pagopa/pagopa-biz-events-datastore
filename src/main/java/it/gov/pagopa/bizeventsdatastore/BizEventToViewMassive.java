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
import it.gov.pagopa.bizeventsdatastore.model.ProblemJson;
import it.gov.pagopa.bizeventsdatastore.service.MassiveBizViewRegenQueueService;
import it.gov.pagopa.bizeventsdatastore.service.impl.MassiveBizViewRegenQueueServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Azure Functions with Azure Http trigger.
 */
public class BizEventToViewMassive {

    private final Logger logger = LoggerFactory.getLogger(BizEventToViewMassive.class);

    private final MassiveBizViewRegenQueueService queueService;

    BizEventToViewMassive(MassiveBizViewRegenQueueService queueService) {
        this.queueService = queueService;
    }

    public BizEventToViewMassive() {
        this.queueService = new MassiveBizViewRegenQueueServiceImpl();
    }

    /**
     * This function will be invoked when a Http Trigger occurs
     */
    @FunctionName("BizEventToViewMassive")
    public HttpResponseMessage run(
            @HttpTrigger(name = "BizEventToViewMassiveFunctionTrigger",
                    methods = {HttpMethod.POST},
                    route = "biz-events/massive-create-view",
                    authLevel = AuthorizationLevel.ANONYMOUS)
            HttpRequestMessage<Optional<byte[]>> request,
            final ExecutionContext context
    ) {

        Optional<byte[]> body = request.getBody();
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

        int processed = 0;
        int skipped = 0;
        List<String> failedIds = new ArrayList<>();

        try (CSVReader reader = new CSVReaderBuilder(new InputStreamReader(new ByteArrayInputStream(body.get()))).build()) {
            String[] line;
            while ((line = reader.readNext()) != null) {
                processed++;
                if (line.length > 1 || line[0] == null || line[0].trim().isBlank()) {
                    logger.warn("Invalid CSV format at line {}", processed);
                    skipped++;
                    continue;
                }

                String bizEventId = line[0].trim();
                boolean queueResult = this.queueService.sendBizEventIdToQueue(bizEventId);
                if (!queueResult) {
                    failedIds.add(bizEventId);
                }
            }
        } catch (IOException | CsvValidationException e) {
            String errMsg = formatResponseDetail("Error processing CSV file", processed, skipped, failedIds);
            logger.error(errMsg, e);
            return request
                    .createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ProblemJson.builder()
                            .title(HttpStatus.INTERNAL_SERVER_ERROR.name())
                            .detail(errMsg)
                            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                            .build())
                    .build();
        }

        int failed = failedIds.size();
        int success = processed - skipped - failed;
        logger.info("CSV ingestion completed. Processed: {}, Success: {}, Skipped: {}, Failed: {}",
                processed, success, skipped, failed);

        if (success == processed) {
            return request.createResponseBuilder(HttpStatus.OK)
                    .body(formatResponseDetail("Elaboration completed successfully", processed, skipped, failedIds))
                    .build();
        }

        if (success == 0) {
            return request
                    .createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ProblemJson.builder()
                            .title(HttpStatus.INTERNAL_SERVER_ERROR.name())
                            .detail(formatResponseDetail("All processed id result in an error", processed, skipped, failedIds))
                            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                            .build())
                    .build();
        }

        return request
                .createResponseBuilder(HttpStatus.MULTI_STATUS)
                .body(ProblemJson.builder()
                        .title(HttpStatus.MULTI_STATUS.name())
                        .detail(formatResponseDetail("The recovery has a partial result", processed, skipped, failedIds))
                        .status(HttpStatus.MULTI_STATUS.value())
                        .build())
                .build();
    }

    private String formatResponseDetail(
            String message,
            int processed,
            int skipped,
            List<String> failedIds
    ) {
        return String.format("%s. Processed: %s, Skipped: %s, Failed: %s", message, processed, skipped, failedIds);
    }
}