package it.gov.pagopa.bizeventsdatastore.client.impl;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.util.CosmosPagedIterable;
import it.gov.pagopa.bizeventsdatastore.entity.BizEvent;
import it.gov.pagopa.bizeventsdatastore.exception.BizEventNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.org.webcompere.systemstubs.SystemStubs.withEnvironmentVariables;

@ExtendWith(MockitoExtension.class)
class BizEventCosmosClientImplTest {

    @Mock
    private CosmosClient cosmosClientMock;

    @InjectMocks
    private BizEventCosmosClientImpl sut;

    @Test
    void testSingletonConnectionError() throws Exception {
        String mockKey = "mockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeyMK==";
        withEnvironmentVariables(
                "COSMOS_DB_PRIMARY_KEY", mockKey,
                "COSMOS_DB_URI", ""
        ).execute(() -> assertThrows(IllegalArgumentException.class, BizEventCosmosClientImpl::getInstance)
        );
    }

    @Test
    void getBizEventDocumentOK() {
        String bizEventId = "a valid event id";
        BizEvent bizEvent = new BizEvent();
        bizEvent.setId(bizEventId);

        CosmosDatabase mockDatabase = mock(CosmosDatabase.class);
        CosmosContainer mockContainer = mock(CosmosContainer.class);
        CosmosPagedIterable mockIterable = mock(CosmosPagedIterable.class);
        Iterator<BizEvent> mockIterator = mock(Iterator.class);

        when(cosmosClientMock.getDatabase(any())).thenReturn(mockDatabase);
        when(mockDatabase.getContainer(any())).thenReturn(mockContainer);
        when(mockContainer.queryItems(anyString(), any(), eq(BizEvent.class))).thenReturn(mockIterable);
        when(mockIterable.iterator()).thenReturn(mockIterator);
        when(mockIterator.hasNext()).thenReturn(true);
        when(mockIterator.next()).thenReturn(bizEvent);

        BizEvent result = assertDoesNotThrow(() -> sut.getBizEventDocument(bizEventId));

        assertEquals(bizEventId, result.getId());
    }

    @Test
    void getBizEventDocumentKO() {

        CosmosDatabase mockDatabase = mock(CosmosDatabase.class);
        CosmosContainer mockContainer = mock(CosmosContainer.class);
        CosmosPagedIterable mockIterable = mock(CosmosPagedIterable.class);
        Iterator<BizEvent> mockIterator = mock(Iterator.class);

        when(cosmosClientMock.getDatabase(any())).thenReturn(mockDatabase);
        when(mockDatabase.getContainer(any())).thenReturn(mockContainer);
        when(mockContainer.queryItems(anyString(), any(), eq(BizEvent.class))).thenReturn(mockIterable);
        when(mockIterable.iterator()).thenReturn(mockIterator);
        when(mockIterator.hasNext()).thenReturn(false);

        assertThrows(BizEventNotFoundException.class, () -> sut.getBizEventDocument("an invalid event id"));
    }
}