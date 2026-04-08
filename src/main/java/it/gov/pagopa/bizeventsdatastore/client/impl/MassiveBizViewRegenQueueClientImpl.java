package it.gov.pagopa.bizeventsdatastore.client.impl;

import com.azure.core.http.rest.Response;
import com.azure.storage.queue.QueueClient;
import com.azure.storage.queue.QueueClientBuilder;
import com.azure.storage.queue.models.SendMessageResult;
import it.gov.pagopa.bizeventsdatastore.client.MassiveBizViewRegenQueueClient;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

/**
 * {@inheritDoc}
 */
public class MassiveBizViewRegenQueueClientImpl implements MassiveBizViewRegenQueueClient {

    private static MassiveBizViewRegenQueueClientImpl instance;

    private final int queueDelay = Integer.parseInt(System.getenv().getOrDefault("MASSIVE_VIEW_RENG_QUEUE_DELAY", "1"));

    private final QueueClient queueClient;

    private MassiveBizViewRegenQueueClientImpl() {
        String receiptQueueConnString = System.getenv("MASSIVE_VIEW_RENG_QUEUE_CONN_STRING");
        String receiptQueueTopic = System.getenv("MASSIVE_VIEW_RENG_QUEUE_TOPIC");

        this.queueClient = new QueueClientBuilder()
                .connectionString(receiptQueueConnString)
                .queueName(receiptQueueTopic)
                .buildClient();
    }

    public MassiveBizViewRegenQueueClientImpl(QueueClient queueClient) {
        this.queueClient = queueClient;
    }

    public static MassiveBizViewRegenQueueClientImpl getInstance() {
        if (instance == null) {
            instance = new MassiveBizViewRegenQueueClientImpl();
        }
        return instance;
    }


    /**
     * {@inheritDoc}
     */
    public Response<SendMessageResult> sendMessageToQueue(String messageText) {
        return this.queueClient.sendMessageWithResponse(
                messageText,
                Duration.of(queueDelay, ChronoUnit.SECONDS),
                null,
                null,
                null
        );

    }
}
