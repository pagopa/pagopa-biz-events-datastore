package it.gov.pagopa.bizeventsdatastore.service.impl;

import com.azure.cosmos.CosmosException;
import com.microsoft.applicationinsights.TelemetryClient;
import it.gov.pagopa.bizeventsdatastore.entity.BizEvent;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewCart;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewGeneral;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewUser;
import it.gov.pagopa.bizeventsdatastore.exception.BizEventNotFoundException;
import it.gov.pagopa.bizeventsdatastore.exception.BizEventToViewConstraintViolationException;
import it.gov.pagopa.bizeventsdatastore.model.BizEventToViewResult;
import it.gov.pagopa.bizeventsdatastore.model.DeadLetterBlob;
import it.gov.pagopa.bizeventsdatastore.service.BizEventCosmosService;
import it.gov.pagopa.bizeventsdatastore.service.BizEventToViewService;
import it.gov.pagopa.bizeventsdatastore.util.BlobStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static it.gov.pagopa.bizeventsdatastore.service.impl.BizEventDeadLetterRecoveryServiceImpl.CONTAINER_BIZ_EVENTS_VIEWS_DEAD_LETTER_NAME;
import static it.gov.pagopa.bizeventsdatastore.service.impl.BizEventDeadLetterRecoveryServiceImpl.METADATA_RECOVERY_ATTEMPTS;
import static it.gov.pagopa.bizeventsdatastore.service.impl.BizEventDeadLetterRecoveryServiceImpl.METADATA_RECOVERY_REASON;
import static it.gov.pagopa.bizeventsdatastore.service.impl.BizEventDeadLetterRecoveryServiceImpl.METADATA_RECOVERY_STATUS;
import static it.gov.pagopa.bizeventsdatastore.service.impl.BizEventDeadLetterRecoveryServiceImpl.RECOVERY_STATUS_FAILED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BizEventDeadLetterRecoveryServiceImplTest {

    private static final String BLOB = "blob.json";
    private static final String INVOCATION = "invocation";
    private static final OffsetDateTime FROM = OffsetDateTime.of(2026, 11, 6, 0, 0, 0, 0, ZoneOffset.UTC);

    @Mock
    private BlobStorage blobStorage;
    @Mock
    private BizEventCosmosService cosmosService;
    @Mock
    private BizEventToViewService viewService;
    @Mock
    private TelemetryClient telemetryClient;

    private BizEventDeadLetterRecoveryServiceImpl sut;

    @BeforeEach
    void setUp() {
        sut = new BizEventDeadLetterRecoveryServiceImpl(blobStorage, cosmosService, viewService, telemetryClient, FROM, 3, 50);
    }

    @Test
    void recoverViewsFromTheBizEventOnTheDatastore() throws Exception {
        BizEvent datastoreEvent = buildBizEvent("1");
        BizEventToViewResult views = buildViews();
        mockBlob(Map.of(), buildBizEvent("1"));
        when(cosmosService.getBizEvent("1")).thenReturn(datastoreEvent);
        when(viewService.mapBizEventToView(datastoreEvent)).thenReturn(views);
        sut.recoverBizEventViews(INVOCATION);
        InOrder inOrder = inOrder(cosmosService, blobStorage);
        inOrder.verify(cosmosService).upsertBizEventViewGeneral(views.getGeneralView());
        inOrder.verify(cosmosService).upsertBizEventViewCart(views.getCartView());
        inOrder.verify(cosmosService).saveBizEventViewUsersPreservingVisibility(views.getUserViewList());
        inOrder.verify(blobStorage).deleteDeadLetterBlob(CONTAINER_BIZ_EVENTS_VIEWS_DEAD_LETTER_NAME, BLOB);

        verify(blobStorage, never()).updateDeadLetterBlobMetadata(anyString(), anyString(), any());
    }

    @Test
    void recoverViewsNothingToGenerate() throws Exception {
        BizEvent datastoreEvent = buildBizEvent("1");
        mockBlob(Map.of(), buildBizEvent("1"));
        when(cosmosService.getBizEvent("1")).thenReturn(datastoreEvent);
        when(viewService.mapBizEventToView(datastoreEvent)).thenReturn(null);

        sut.recoverBizEventViews(INVOCATION);

        verify(cosmosService, never()).saveBizEventViewUsersPreservingVisibility(any());
        verify(blobStorage).deleteDeadLetterBlob(CONTAINER_BIZ_EVENTS_VIEWS_DEAD_LETTER_NAME, BLOB);
    }

    @Test
    void recoverViewsBizEventNotFoundDoesNotCreateOrphanViews() throws Exception {
        mockBlob(Map.of(), buildBizEvent("1"));
        when(cosmosService.getBizEvent("1")).thenThrow(new BizEventNotFoundException("not found"));

        sut.recoverBizEventViews(INVOCATION);

        verifyNoInteractions(viewService);
        verify(blobStorage, never()).deleteDeadLetterBlob(anyString(), anyString());
        Map<String, String> metadata = captureLastMetadata();
        assertEquals("1", metadata.get(METADATA_RECOVERY_ATTEMPTS));
        assertEquals("BIZ_EVENT_NOT_FOUND", metadata.get(METADATA_RECOVERY_REASON));
        assertNull(metadata.get(METADATA_RECOVERY_STATUS));
        verifyNoInteractions(telemetryClient);
    }

    @Test
    void recoverViewsInvalidNeedsManualCheck() throws Exception {
        BizEvent datastoreEvent = buildBizEvent("1");
        mockBlob(Map.of("invocationId", "inv"), buildBizEvent("1"));
        when(cosmosService.getBizEvent("1")).thenReturn(datastoreEvent);
        when(viewService.mapBizEventToView(datastoreEvent)).thenThrow(new BizEventToViewConstraintViolationException("invalid", List.of()));

        sut.recoverBizEventViews(INVOCATION);

        verify(blobStorage, never()).deleteDeadLetterBlob(anyString(), anyString());
        Map<String, String> metadata = captureLastMetadata();
        assertEquals(RECOVERY_STATUS_FAILED, metadata.get(METADATA_RECOVERY_STATUS));
        assertEquals("INVALID_VIEWS", metadata.get(METADATA_RECOVERY_REASON));
        assertEquals("inv", metadata.get("invocationId"));
        verify(telemetryClient).trackEvent(anyString());
    }

    @Test
    void recoverViewsCosmosErrorKeepsTheBlobAndCountsTheAttempt() throws Exception {
        BizEvent datastoreEvent = buildBizEvent("1");
        BizEventToViewResult views = buildViews();
        mockBlob(Map.of(), buildBizEvent("1"));
        when(cosmosService.getBizEvent("1")).thenReturn(datastoreEvent);
        when(viewService.mapBizEventToView(datastoreEvent)).thenReturn(views);
        doThrow(mock(CosmosException.class)).when(cosmosService).upsertBizEventViewGeneral(views.getGeneralView());

        sut.recoverBizEventViews(INVOCATION);

        verify(cosmosService, never()).saveBizEventViewUsersPreservingVisibility(any());
        verify(blobStorage, never()).deleteDeadLetterBlob(anyString(), anyString());
        Map<String, String> metadata = captureLastMetadata();
        assertEquals("1", metadata.get(METADATA_RECOVERY_ATTEMPTS));
        assertEquals("COSMOS_ERROR", metadata.get(METADATA_RECOVERY_REASON));
        assertNull(metadata.get(METADATA_RECOVERY_STATUS));
        verifyNoInteractions(telemetryClient);
    }

    @Test
    void recoverViewsLastAttemptMarksTheBlobAsFailed() throws Exception {
        mockBlob(Map.of(METADATA_RECOVERY_ATTEMPTS, "2"), buildBizEvent("1"));
        when(cosmosService.getBizEvent("1")).thenThrow(mock(CosmosException.class));

        sut.recoverBizEventViews(INVOCATION);

        Map<String, String> metadata = captureLastMetadata();
        assertEquals(RECOVERY_STATUS_FAILED, metadata.get(METADATA_RECOVERY_STATUS));
        assertEquals("COSMOS_ERROR", metadata.get(METADATA_RECOVERY_REASON));
        verify(telemetryClient).trackEvent(anyString());
    }

    @Test
    void recoverViewsTemporaryFailureComesBeforeThePermanentOne() throws Exception {
        BizEvent invalid = buildBizEvent("1");
        mockBlob(Map.of(), buildBizEvent("1"), buildBizEvent("2"));
        when(cosmosService.getBizEvent("1")).thenReturn(invalid);
        when(viewService.mapBizEventToView(invalid)).thenThrow(new BizEventToViewConstraintViolationException("invalid", List.of()));
        when(cosmosService.getBizEvent("2")).thenThrow(new BizEventNotFoundException("not found"));

        sut.recoverBizEventViews(INVOCATION);

        Map<String, String> metadata = captureLastMetadata();
        assertNull(metadata.get(METADATA_RECOVERY_STATUS));
        assertEquals("1", metadata.get(METADATA_RECOVERY_ATTEMPTS));
        assertEquals("BIZ_EVENT_NOT_FOUND,INVALID_VIEWS", metadata.get(METADATA_RECOVERY_REASON));
        verifyNoInteractions(telemetryClient);
    }

    @Test
    void recoverViewsLastAttemptKeepsBothReasons() throws Exception {
        BizEvent invalid = buildBizEvent("1");
        mockBlob(Map.of(METADATA_RECOVERY_ATTEMPTS, "2"), buildBizEvent("1"), buildBizEvent("2"));
        when(cosmosService.getBizEvent("1")).thenReturn(invalid);
        when(viewService.mapBizEventToView(invalid)).thenThrow(new BizEventToViewConstraintViolationException("invalid", List.of()));
        when(cosmosService.getBizEvent("2")).thenThrow(new BizEventNotFoundException("not found"));

        sut.recoverBizEventViews(INVOCATION);

        Map<String, String> metadata = captureLastMetadata();
        assertEquals(RECOVERY_STATUS_FAILED, metadata.get(METADATA_RECOVERY_STATUS));
        assertEquals("BIZ_EVENT_NOT_FOUND,INVALID_VIEWS", metadata.get(METADATA_RECOVERY_REASON));
        verify(telemetryClient).trackEvent(anyString());
    }

    @Test
    void recoverViewsUnexpectedErrorCountsTheAttempt() throws Exception {
        mockBlob(Map.of(), buildBizEvent("1"));
        when(cosmosService.getBizEvent("1")).thenThrow(new IllegalStateException("unexpected"));

        sut.recoverBizEventViews(INVOCATION);

        Map<String, String> metadata = captureLastMetadata();
        assertEquals("1", metadata.get(METADATA_RECOVERY_ATTEMPTS));
        assertEquals("UNEXPECTED_ERROR", metadata.get(METADATA_RECOVERY_REASON));
    }

    @Test
    void metadataUpdateErrorIsSavedAsBlobUpdateError() throws Exception {
        mockBlob(Map.of(), buildBizEvent("1"));
        when(cosmosService.getBizEvent("1")).thenThrow(mock(CosmosException.class));
        doThrow(new IllegalStateException("storage error")).doNothing()
                .when(blobStorage).updateDeadLetterBlobMetadata(anyString(), anyString(), any());

        sut.recoverBizEventViews(INVOCATION);

        Map<String, String> metadata = captureLastMetadata();
        assertEquals("1", metadata.get(METADATA_RECOVERY_ATTEMPTS));
        assertEquals("BLOB_UPDATE_ERROR", metadata.get(METADATA_RECOVERY_REASON));
    }

    @Test
    void blobDeleteErrorCountsTheAttempt() throws Exception {
        BizEvent datastoreEvent = buildBizEvent("1");
        mockBlob(Map.of(), buildBizEvent("1"));
        when(cosmosService.getBizEvent("1")).thenReturn(datastoreEvent);
        when(viewService.mapBizEventToView(datastoreEvent)).thenReturn(buildViews());
        doThrow(new IllegalStateException("storage error")).when(blobStorage)
                .deleteDeadLetterBlob(CONTAINER_BIZ_EVENTS_VIEWS_DEAD_LETTER_NAME, BLOB);

        sut.recoverBizEventViews(INVOCATION);

        Map<String, String> metadata = captureLastMetadata();
        assertEquals("1", metadata.get(METADATA_RECOVERY_ATTEMPTS));
        assertEquals("BLOB_DELETE_ERROR", metadata.get(METADATA_RECOVERY_REASON));
    }

    @Test
    void unreadableBlobCountsTheAttempt() {
        DeadLetterBlob blob = new DeadLetterBlob(BLOB, Map.of(METADATA_RECOVERY_ATTEMPTS, "not-a-number"));
        when(blobStorage.listDeadLetterBlobs(eq(CONTAINER_BIZ_EVENTS_VIEWS_DEAD_LETTER_NAME), eq(FROM), any(), eq(50))).thenReturn(List.of(blob));
        when(blobStorage.readDeadLetterBlob(CONTAINER_BIZ_EVENTS_VIEWS_DEAD_LETTER_NAME, BLOB)).thenThrow(new IllegalStateException("error"));

        sut.recoverBizEventViews(INVOCATION);

        verifyNoInteractions(cosmosService, viewService);
        Map<String, String> metadata = captureLastMetadata();
        assertEquals("1", metadata.get(METADATA_RECOVERY_ATTEMPTS));
        assertEquals("BLOB_READ_ERROR", metadata.get(METADATA_RECOVERY_REASON));
    }

    @Test
    @SuppressWarnings("unchecked")
    void failedBlobsAreNotListed() {
        ArgumentCaptor<Predicate<DeadLetterBlob>> captor = ArgumentCaptor.forClass(Predicate.class);
        when(blobStorage.listDeadLetterBlobs(anyString(), any(), captor.capture(), anyInt())).thenReturn(List.of());

        sut.recoverBizEventViews(INVOCATION);

        assertTrue(captor.getValue().test(new DeadLetterBlob(BLOB, Map.of())));
        assertFalse(captor.getValue().test(new DeadLetterBlob(BLOB, Map.of(METADATA_RECOVERY_STATUS, RECOVERY_STATUS_FAILED))));
    }

    @Test
    void noRecoveryWithoutStartDate() {
        sut = new BizEventDeadLetterRecoveryServiceImpl(blobStorage, cosmosService, viewService, telemetryClient, null, 3, 50);

        sut.recoverBizEventViews(INVOCATION);

        verifyNoInteractions(blobStorage, cosmosService, viewService);
    }

    private void mockBlob(Map<String, String> metadata, BizEvent... events) {
        when(blobStorage.listDeadLetterBlobs(eq(CONTAINER_BIZ_EVENTS_VIEWS_DEAD_LETTER_NAME), eq(FROM), any(), eq(50)))
                .thenReturn(List.of(new DeadLetterBlob(BLOB, new HashMap<>(metadata))));
        when(blobStorage.readDeadLetterBlob(CONTAINER_BIZ_EVENTS_VIEWS_DEAD_LETTER_NAME, BLOB)).thenReturn(List.of(events));
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> captureLastMetadata() {
        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(blobStorage, atLeastOnce()).updateDeadLetterBlobMetadata(eq(CONTAINER_BIZ_EVENTS_VIEWS_DEAD_LETTER_NAME), eq(BLOB), captor.capture());
        return captor.getValue();
    }

    private BizEvent buildBizEvent(String id) {
        return BizEvent.builder().id(id).build();
    }

    private BizEventToViewResult buildViews() {
        return BizEventToViewResult.builder()
                .userViewList(List.of(BizEventsViewUser.builder().id("1-p").build()))
                .generalView(new BizEventsViewGeneral())
                .cartView(new BizEventsViewCart())
                .build();
    }
}
