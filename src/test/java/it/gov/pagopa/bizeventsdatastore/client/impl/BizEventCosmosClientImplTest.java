package it.gov.pagopa.bizeventsdatastore.client.impl;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosItemResponse;
import it.gov.pagopa.bizeventsdatastore.entity.BizEvent;
import it.gov.pagopa.bizeventsdatastore.exception.BizEventNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static uk.org.webcompere.systemstubs.SystemStubs.withEnvironmentVariables;

@ExtendWith(MockitoExtension.class)
class BizEventCosmosClientImplTest {

    @Mock
    private CosmosClient cosmosClientMock;

    @Mock
    private CosmosDatabase mockDatabase;
    @Mock
    private CosmosContainer mockContainer;
    @Mock
    private CosmosItemResponse<BizEvent> mockResponse;

    @InjectMocks
    private BizEventCosmosClientImpl sut;

    @Test
    void testSingletonConnectionError() throws Exception {
        String mockKey = "mockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeyMK==";
        withEnvironmentVariables(
                "COSMOS_DB_PRIMARY_KEY", mockKey,
                "COSMOS_DB_URI", "")
                .execute(() -> assertThrows(IllegalArgumentException.class, BizEventCosmosClientImpl::getInstance));
    }

    @Test
    void getBizEventDocumentSuccess() {
        when(cosmosClientMock.getDatabase(any())).thenReturn(mockDatabase);
        when(mockDatabase.getContainer(any())).thenReturn(mockContainer);
        when(mockContainer.readItem(anyString(), any(), eq(BizEvent.class))).thenReturn(mockResponse);
        when(mockResponse.getItem()).thenReturn(new BizEvent());

        assertDoesNotThrow(() -> sut.getBizEventDocument("1"));
    }

    @Test
    void getBizEventDocumentError() {
        when(cosmosClientMock.getDatabase(any())).thenReturn(mockDatabase);
        when(mockDatabase.getContainer(any())).thenReturn(mockContainer);
        when(mockContainer.readItem(anyString(), any(), eq(BizEvent.class))).thenThrow(CosmosException.class);

        assertThrows(BizEventNotFoundException.class, () -> sut.getBizEventDocument("1"));
    }
}