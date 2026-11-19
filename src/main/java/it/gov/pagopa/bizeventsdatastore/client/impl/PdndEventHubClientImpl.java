package it.gov.pagopa.bizeventsdatastore.client.impl;

import com.azure.messaging.eventhubs.EventData;
import com.azure.messaging.eventhubs.EventDataBatch;
import com.azure.messaging.eventhubs.EventHubClientBuilder;
import com.azure.messaging.eventhubs.EventHubProducerClient;
import com.google.gson.Gson;
import it.gov.pagopa.bizeventsdatastore.client.PdndEventHubClient;
import it.gov.pagopa.bizeventsdatastore.entity.BizEvent;

import java.util.List;
import java.util.function.Supplier;

/**
 * {@inheritDoc}
 */
public class PdndEventHubClientImpl implements PdndEventHubClient {

    private static final Gson GSON = new Gson();

    private final Supplier<EventHubProducerClient> producerSupplier;

    private EventHubProducerClient producer;

    private PdndEventHubClientImpl() {
        this(() -> new EventHubClientBuilder()
                .connectionString(System.getenv("PDND_EVENTHUB_CONN_STRING"))
                .buildProducerClient());
    }

    PdndEventHubClientImpl(Supplier<EventHubProducerClient> producerSupplier) {
        this.producerSupplier = producerSupplier;
    }

    public static PdndEventHubClientImpl getInstance() {
        return SingletonHolder.INSTANCE;
    }

    /**
     * Thread-safe lazy singleton holder
     */
    private static class SingletonHolder {
        static final PdndEventHubClientImpl INSTANCE = new PdndEventHubClientImpl();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void send(List<BizEvent> bizEvents) {
        EventHubProducerClient producerClient = getProducer();
        EventDataBatch batch = producerClient.createBatch();
        for (BizEvent bizEvent : bizEvents) {
            EventData eventData = new EventData(GSON.toJson(bizEvent));
            if (!batch.tryAdd(eventData)) {
                producerClient.send(batch);
                batch = producerClient.createBatch();
                if (!batch.tryAdd(eventData)) {
                    throw new IllegalArgumentException(String.format("The biz-event [%s] is too large for an Event Hub batch", bizEvent.getId()));
                }
            }
        }
        if (batch.getCount() > 0) {
            producerClient.send(batch);
        }
    }

    private synchronized EventHubProducerClient getProducer() {
        if (this.producer == null) {
            this.producer = this.producerSupplier.get();
        }
        return this.producer;
    }
}
