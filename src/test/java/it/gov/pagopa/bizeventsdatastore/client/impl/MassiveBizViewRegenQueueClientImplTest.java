package it.gov.pagopa.bizeventsdatastore.client.impl;

import com.azure.core.http.rest.Response;
import com.azure.storage.queue.QueueClient;
import com.azure.storage.queue.models.QueueStorageException;
import com.azure.storage.queue.models.SendMessageResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static uk.org.webcompere.systemstubs.SystemStubs.withEnvironmentVariables;

@ExtendWith(MockitoExtension.class)
class MassiveBizViewRegenQueueClientImplTest {

    @Mock
    private QueueClient queueClientMock;
    @Mock
    private Response<SendMessageResult> responseMock;

    @InjectMocks
    private MassiveBizViewRegenQueueClientImpl sut;

    @Test
    void testSingletonConnectionError() throws Exception {
        String mockKey = "mockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeyMK==";
        withEnvironmentVariables(
                "AzureWebJobsStorage", mockKey,
                "MASSIVE_VIEW_REGEN_QUEUE_TOPIC", "")
                .execute(() -> assertThrows(IllegalArgumentException.class, MassiveBizViewRegenQueueClientImpl::getInstance));
    }

    @Test
    void sendMessageToQueueTest() {
        when(queueClientMock.sendMessageWithResponse(anyString(), any(), isNull(), isNull(), isNull()))
                .thenReturn(responseMock);

        assertDoesNotThrow(() -> sut.sendMessageToQueue("message"));
    }

    @Test
    void sendMessageToQueueErrorTest() {
        when(queueClientMock.sendMessageWithResponse(anyString(), any(), isNull(), isNull(), isNull()))
                .thenThrow(QueueStorageException.class);

        assertThrows(QueueStorageException.class, () -> sut.sendMessageToQueue("message"));
    }
}