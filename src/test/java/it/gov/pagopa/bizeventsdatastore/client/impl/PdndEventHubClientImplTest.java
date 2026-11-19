package it.gov.pagopa.bizeventsdatastore.client.impl;

import com.azure.messaging.eventhubs.EventData;
import com.azure.messaging.eventhubs.EventDataBatch;
import com.azure.messaging.eventhubs.EventHubClientBuilder;
import com.azure.messaging.eventhubs.EventHubProducerClient;
import com.google.gson.Gson;
import it.gov.pagopa.bizeventsdatastore.entity.BizEvent;
import it.gov.pagopa.bizeventsdatastore.entity.Transfer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PdndEventHubClientImplTest {

    @Mock
    private EventHubProducerClient producer;
    @Mock
    private EventDataBatch batch;

    private final AtomicInteger producerBuilds = new AtomicInteger();

    private PdndEventHubClientImpl sut;

    @BeforeEach
    void setUp() {
        sut = new PdndEventHubClientImpl(() -> {
            producerBuilds.incrementAndGet();
            return producer;
        });
    }

    @Test
    void realProducerCanBeBuiltWithTheResolvedDependencies() {
        String connectionString = "Endpoint=sb://test.servicebus.windows.net/;SharedAccessKeyName=key;SharedAccessKey=a2V5;EntityPath=hub";

        assertDoesNotThrow(() -> new EventHubClientBuilder().connectionString(connectionString).buildProducerClient().close());
    }

    @Test
    void oneEventForEachBizEventWithTheSameJsonOfTheOutputBinding() {
        BizEvent bizEvent = BizEvent.builder().id("1")
                .transferList(List.of(Transfer.builder().IBAN("IT00TEST").build()))
                .build();
        when(producer.createBatch()).thenReturn(batch);
        when(batch.tryAdd(any())).thenReturn(true);
        when(batch.getCount()).thenReturn(1);

        sut.send(List.of(bizEvent));

        ArgumentCaptor<EventData> captor = ArgumentCaptor.forClass(EventData.class);
        verify(batch).tryAdd(captor.capture());
        String body = captor.getValue().getBodyAsString();
        assertEquals(new Gson().toJson(bizEvent), body);
        assertTrue(body.contains("\"IBAN\":\"IT00TEST\""));
        verify(producer).send(batch);
    }

    @Test
    void fullBatchIsSentAndANewOneIsCreated() {
        EventDataBatch second = mock(EventDataBatch.class);
        when(producer.createBatch()).thenReturn(batch, second);
        when(batch.tryAdd(any())).thenReturn(true, false);
        when(second.tryAdd(any())).thenReturn(true);
        when(second.getCount()).thenReturn(1);

        sut.send(List.of(BizEvent.builder().id("1").build(), BizEvent.builder().id("2").build()));

        verify(producer).send(batch);
        verify(producer).send(second);
    }

    @Test
    void tooLargeBizEventIsAnError() {
        EventDataBatch empty = mock(EventDataBatch.class);
        when(producer.createBatch()).thenReturn(batch, empty);
        when(batch.tryAdd(any())).thenReturn(false);
        when(empty.tryAdd(any())).thenReturn(false);

        List<BizEvent> bizEvents = List.of(BizEvent.builder().id("1").build());
        assertThrows(IllegalArgumentException.class, () -> sut.send(bizEvents));
    }

    @Test
    void nothingIsSentForAnEmptyList() {
        when(producer.createBatch()).thenReturn(batch);

        sut.send(List.of());

        verify(producer, never()).send(any(EventDataBatch.class));
    }

    @Test
    void producerIsBuiltOnlyOnceAndOnlyWhenNeeded() {
        assertEquals(0, producerBuilds.get());
        when(producer.createBatch()).thenReturn(batch);

        sut.send(List.of());
        sut.send(List.of());

        assertEquals(1, producerBuilds.get());
        verify(producer, times(2)).createBatch();
    }
}
