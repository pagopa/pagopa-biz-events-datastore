package it.gov.pagopa.bizeventsdatastore.service.impl;

import com.azure.core.http.rest.Response;
import com.azure.storage.queue.models.SendMessageResult;
import com.microsoft.azure.functions.HttpStatus;
import it.gov.pagopa.bizeventsdatastore.client.MassiveBizViewRegenQueueClient;
import it.gov.pagopa.bizeventsdatastore.client.impl.MassiveBizViewRegenQueueClientImpl;
import it.gov.pagopa.bizeventsdatastore.service.MassiveBizViewRegenQueueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * {@inheritDoc}
 */
public class MassiveBizViewRegenQueueServiceImpl implements MassiveBizViewRegenQueueService {

    private static final int THREAD_POOL_SIZE = Integer.parseInt(
            System.getenv().getOrDefault("MASSIVE_VIEW_REGEN_THREAD_POOL_SIZE", "20"));

    private final Logger logger = LoggerFactory.getLogger(MassiveBizViewRegenQueueServiceImpl.class);

    private final MassiveBizViewRegenQueueClient massiveBizViewRegenQueueClient;

    MassiveBizViewRegenQueueServiceImpl(MassiveBizViewRegenQueueClient massiveBizViewRegenQueueClient) {
        this.massiveBizViewRegenQueueClient = massiveBizViewRegenQueueClient;
    }

    public MassiveBizViewRegenQueueServiceImpl() {
        this.massiveBizViewRegenQueueClient = MassiveBizViewRegenQueueClientImpl.getInstance();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean sendBizEventIdToQueue(String bizEventId) {
        try {
            Response<SendMessageResult> response = this.massiveBizViewRegenQueueClient.sendMessageToQueue(bizEventId);
            if (response.getStatusCode() == HttpStatus.CREATED.value()) {
                return true;
            }
            logger.error("Sending BizEvent id {} to queue failed. Response status {}", bizEventId, response.getStatusCode());
        } catch (Exception e) {
            logger.error("Unexpected error while sending BizEvent id {} to queue", bizEventId, e);
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> sendBizEventIdsToQueueInBatch(List<String> bizEventIds) {
        List<String> failedIds = Collections.synchronizedList(new ArrayList<>());
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        try {
            List<CompletableFuture<Void>> futures = bizEventIds.stream()
                    .map(id -> CompletableFuture.runAsync(() -> {
                        if (!sendBizEventIdToQueue(id)) {
                            failedIds.add(id);
                        }
                    }, executor))
                    .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } finally {
            executor.shutdown();
        }
        return failedIds;
    }
}
