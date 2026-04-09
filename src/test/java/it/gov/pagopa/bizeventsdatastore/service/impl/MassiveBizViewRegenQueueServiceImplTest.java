package it.gov.pagopa.bizeventsdatastore.service.impl;

import com.azure.core.http.rest.Response;
import com.azure.storage.queue.models.QueueStorageException;
import com.azure.storage.queue.models.SendMessageResult;
import it.gov.pagopa.bizeventsdatastore.client.MassiveBizViewRegenQueueClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MassiveBizViewRegenQueueServiceImplTest {

    private static final String BIZ_EVENT_ID = "valid_biz_event_id";

    @Mock
    private MassiveBizViewRegenQueueClient queueClientMock;

    @Mock
    private Response<SendMessageResult> responseMock;

    @InjectMocks
    private MassiveBizViewRegenQueueServiceImpl sut;

    @Test
    void sendBizEventIdToQueueOK() {
        when(queueClientMock.sendMessageToQueue(BIZ_EVENT_ID)).thenReturn(responseMock);
        when(responseMock.getStatusCode()).thenReturn(201);

        boolean result = assertDoesNotThrow(() -> sut.sendBizEventIdToQueue(BIZ_EVENT_ID));

        assertTrue(result);
    }

    @Test
    void sendBizEventIdToQueueKO_ResponseKO() {
        when(queueClientMock.sendMessageToQueue(BIZ_EVENT_ID)).thenReturn(responseMock);
        when(responseMock.getStatusCode()).thenReturn(500);

        boolean result = assertDoesNotThrow(() -> sut.sendBizEventIdToQueue(BIZ_EVENT_ID));

        assertFalse(result);
    }

    @Test
    void sendBizEventIdToQueueKO_QueueStorageException() {
        when(queueClientMock.sendMessageToQueue(BIZ_EVENT_ID)).thenThrow(QueueStorageException.class);

        boolean result = assertDoesNotThrow(() -> sut.sendBizEventIdToQueue(BIZ_EVENT_ID));

        assertFalse(result);
    }
}