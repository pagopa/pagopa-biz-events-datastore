package it.gov.pagopa.bizeventsdatastore.client.impl;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.implementation.ImplementationBridgeHelpers;
import com.azure.cosmos.implementation.InternalObjectNode;
import com.azure.cosmos.implementation.NotFoundException;
import com.azure.cosmos.implementation.Utils;
import com.azure.cosmos.implementation.patch.PatchOperationCore;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosPatchOperations;
import com.azure.cosmos.models.PartitionKey;
import com.fasterxml.jackson.databind.JsonNode;
import it.gov.pagopa.bizeventsdatastore.entity.BizEvent;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewUser;
import it.gov.pagopa.bizeventsdatastore.exception.BizEventNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    void getBizEventDocumentSuccess() {
        when(cosmosClientMock.getDatabase(any())).thenReturn(mockDatabase);
        when(mockDatabase.getContainer(any())).thenReturn(mockContainer);
        when(mockContainer.readItem(anyString(), any(), eq(BizEvent.class))).thenReturn(mockResponse);
        when(mockResponse.getItem()).thenReturn(new BizEvent());

        assertDoesNotThrow(() -> sut.getBizEventDocument("1"));
    }

    @Test
    void getBizEventDocumentNotFoundError() {
        when(cosmosClientMock.getDatabase(any())).thenReturn(mockDatabase);
        when(mockDatabase.getContainer(any())).thenReturn(mockContainer);
        when(mockContainer.readItem(anyString(), any(), eq(BizEvent.class))).thenThrow(new NotFoundException());

        assertThrows(BizEventNotFoundException.class, () -> sut.getBizEventDocument("1"));
    }

    @Test
    void getBizEventDocumentError() {
        when(cosmosClientMock.getDatabase(any())).thenReturn(mockDatabase);
        when(mockDatabase.getContainer(any())).thenReturn(mockContainer);
        when(mockContainer.readItem(anyString(), any(), eq(BizEvent.class))).thenThrow(CosmosException.class);

        assertThrows(CosmosException.class, () -> sut.getBizEventDocument("1"));
    }

    @Test
    void saveBizEventViewUserCreatedWhenNotExists() {
        when(cosmosClientMock.getDatabase(any())).thenReturn(mockDatabase);
        when(mockDatabase.getContainer("biz-events-view-user")).thenReturn(mockContainer);
        BizEventsViewUser viewUser = buildViewUser(false);

        assertDoesNotThrow(() -> sut.saveBizEventViewUserPreservingVisibility(viewUser));

        verify(mockContainer).createItem(eq(viewUser), eq(new PartitionKey("AAAAAA00A00A000A")), any(CosmosItemRequestOptions.class));
        verify(mockContainer, never()).patchItem(anyString(), any(), any(), any());
    }

    @Test
    void saveBizEventViewUserAlreadyExistsNotHiddenDoesNotChangeVisibility() {
        when(cosmosClientMock.getDatabase(any())).thenReturn(mockDatabase);
        when(mockDatabase.getContainer("biz-events-view-user")).thenReturn(mockContainer);
        BizEventsViewUser viewUser = buildViewUser(false);
        CosmosException conflict = buildCosmosException(409);
        when(mockContainer.createItem(eq(viewUser), any(PartitionKey.class), any(CosmosItemRequestOptions.class))).thenThrow(conflict);

        assertDoesNotThrow(() -> sut.saveBizEventViewUserPreservingVisibility(viewUser));

        Map<String, Object> patchedFields = capturePatchedFields(viewUser);
        assertEquals(Set.of("/transactionId", "/transactionDate", "/isPayer", "/isDebtor"), patchedFields.keySet());
        assertEquals(viewUser.getTransactionId(), patchedFields.get("/transactionId"));
        assertEquals(viewUser.getTransactionDate(), patchedFields.get("/transactionDate"));
        assertEquals(true, patchedFields.get("/isPayer"));
        assertEquals(false, patchedFields.get("/isDebtor"));
    }

    @Test
    void saveBizEventViewUserAlreadyExistsHiddenSetsHidden() {
        when(cosmosClientMock.getDatabase(any())).thenReturn(mockDatabase);
        when(mockDatabase.getContainer("biz-events-view-user")).thenReturn(mockContainer);
        BizEventsViewUser viewUser = buildViewUser(true);
        CosmosException conflict = buildCosmosException(409);
        when(mockContainer.createItem(eq(viewUser), any(PartitionKey.class), any(CosmosItemRequestOptions.class))).thenThrow(conflict);

        assertDoesNotThrow(() -> sut.saveBizEventViewUserPreservingVisibility(viewUser));

        Map<String, Object> patchedFields = capturePatchedFields(viewUser);
        assertEquals(true, patchedFields.get("/hidden"));
    }

    @Test
    void saveBizEventViewUserError() {
        when(cosmosClientMock.getDatabase(any())).thenReturn(mockDatabase);
        when(mockDatabase.getContainer("biz-events-view-user")).thenReturn(mockContainer);
        BizEventsViewUser viewUser = buildViewUser(false);
        CosmosException error = buildCosmosException(429);
        when(mockContainer.createItem(eq(viewUser), any(PartitionKey.class), any(CosmosItemRequestOptions.class))).thenThrow(error);

        assertThrows(CosmosException.class, () -> sut.saveBizEventViewUserPreservingVisibility(viewUser));

        verify(mockContainer, never()).patchItem(anyString(), any(), any(), any());
    }

    @Test
    void bizEventViewUserSerializedByCosmosSdkWithSameFieldNamesOfOutputBinding() throws IOException {
        ByteBuffer content = InternalObjectNode.serializeJsonToByteBuffer(buildViewUser(true), Utils.getSimpleObjectMapper(), null);
        byte[] bytes = new byte[content.remaining()];
        content.get(bytes);
        JsonNode json = Utils.getSimpleObjectMapper().readTree(bytes);

        Set<String> fieldNames = new HashSet<>();
        json.fieldNames().forEachRemaining(fieldNames::add);
        assertEquals(Set.of("id", "taxCode", "transactionId", "transactionDate", "hidden", "isPayer", "isDebtor"), fieldNames);
        assertFalse(json.has("payer"));
        assertFalse(json.has("debtor"));
    }

    private Map<String, Object> capturePatchedFields(BizEventsViewUser viewUser) {
        ArgumentCaptor<CosmosPatchOperations> captor = ArgumentCaptor.forClass(CosmosPatchOperations.class);
        verify(mockContainer).patchItem(eq(viewUser.getId()), eq(new PartitionKey(viewUser.getTaxCode())), captor.capture(), eq(BizEventsViewUser.class));

        Map<String, Object> patchedFields = new HashMap<>();
        ImplementationBridgeHelpers.CosmosPatchOperationsHelper.getCosmosPatchOperationsAccessor()
                .getPatchOperations(captor.getValue())
                .forEach(operation -> {
                    PatchOperationCore<?> core = (PatchOperationCore<?>) operation;
                    patchedFields.put(core.getPath(), core.getResource());
                });
        return patchedFields;
    }

    private CosmosException buildCosmosException(int statusCode) {
        CosmosException exception = mock(CosmosException.class);
        when(exception.getStatusCode()).thenReturn(statusCode);
        return exception;
    }

    private BizEventsViewUser buildViewUser(boolean hidden) {
        return BizEventsViewUser.builder()
                .id("biz-event-id-p")
                .taxCode("AAAAAA00A00A000A")
                .transactionId("biz-event-id")
                .transactionDate("2026-11-03T10:00:00")
                .hidden(hidden)
                .isPayer(true)
                .isDebtor(false)
                .build();
    }
}