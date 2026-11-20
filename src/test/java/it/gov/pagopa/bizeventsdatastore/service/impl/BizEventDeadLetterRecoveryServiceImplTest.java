package it.gov.pagopa.bizeventsdatastore.service.impl;

import com.azure.cosmos.CosmosException;
import com.microsoft.applicationinsights.TelemetryClient;
import it.gov.pagopa.bizeventsdatastore.client.PdndEventHubClient;
import it.gov.pagopa.bizeventsdatastore.entity.BizEvent;
import it.gov.pagopa.bizeventsdatastore.entity.Creditor;
import it.gov.pagopa.bizeventsdatastore.entity.DebtorPosition;
import it.gov.pagopa.bizeventsdatastore.entity.PaymentInfo;
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

import static it.gov.pagopa.bizeventsdatastore.service.impl.BizEventDeadLetterRecoveryServiceImpl.CONTAINER_BIZ_EVENTS_DEAD_LETTER_NAME;
import static it.gov.pagopa.bizeventsdatastore.service.impl.BizEventDeadLetterRecoveryServiceImpl.CONTAINER_BIZ_EVENTS_VIEWS_DEAD_LETTER_NAME;
import static it.gov.pagopa.bizeventsdatastore.service.impl.BizEventDeadLetterRecoveryServiceImpl.METADATA_PDND_PENDING_IDS;
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
    private PdndEventHubClient pdndClient;
    @Mock
    private TelemetryClient telemetryClient;

    private BizEventDeadLetterRecoveryServiceImpl sut;

    @BeforeEach
    void setUp() {
        sut = new BizEventDeadLetterRecoveryServiceImpl(blobStorage, cosmosService, viewService, pdndClient, telemetryClient, FROM, 3, 50);
    }

    @Test
    void recoverBizEventCreatedEnrichedAndSentToPdndBeforeDeletingTheBlob() {
        BizEvent bizEvent = buildBizEvent("1", "iuv");
        mockBlob(CONTAINER_BIZ_EVENTS_DEAD_LETTER_NAME, Map.of("invocationId", "inv"), bizEvent);
        mockNoOtherPayment("1", "iuv");
        when(cosmosService.createBizEventIfAbsent(bizEvent)).thenReturn(true);

        sut.recoverBizEvents(INVOCATION);

        assertEquals("iur", bizEvent.getDebtorPosition().getIur());
        assertTrue(bizEvent.getTimestamp() != null);
        InOrder inOrder = inOrder(blobStorage, cosmosService, pdndClient);
        Map<String, String> pending = Map.of("invocationId", "inv", METADATA_PDND_PENDING_IDS, "1");
        inOrder.verify(blobStorage).updateDeadLetterBlobMetadata(CONTAINER_BIZ_EVENTS_DEAD_LETTER_NAME, BLOB, pending);
        inOrder.verify(cosmosService).createBizEventIfAbsent(bizEvent);
        inOrder.verify(pdndClient).send(List.of(bizEvent));
        inOrder.verify(blobStorage).deleteDeadLetterBlob(CONTAINER_BIZ_EVENTS_DEAD_LETTER_NAME, BLOB);
    }

    @Test
    void recoverBizEventAlreadyPresentAtTheFirstAttemptIsNotSentAgain() {
        BizEvent bizEvent = buildBizEvent("1", "iuv");
        mockBlob(CONTAINER_BIZ_EVENTS_DEAD_LETTER_NAME, Map.of(), bizEvent);
        mockNoOtherPayment("1", "iuv");
        when(cosmosService.createBizEventIfAbsent(bizEvent)).thenReturn(false);

        sut.recoverBizEvents(INVOCATION);

        verifyNoInteractions(pdndClient);
        verify(blobStorage).deleteDeadLetterBlob(CONTAINER_BIZ_EVENTS_DEAD_LETTER_NAME, BLOB);
    }

    @Test
    void recoverBizEventCreatedByAPreviousAttemptIsSentToPdnd() {
        BizEvent pending = buildBizEvent("1", "iuv1");
        BizEvent alreadyPublished = buildBizEvent("2", "iuv2");
        mockBlob(CONTAINER_BIZ_EVENTS_DEAD_LETTER_NAME, Map.of(METADATA_PDND_PENDING_IDS, "1", METADATA_RECOVERY_ATTEMPTS, "1"),
                pending, alreadyPublished);
        mockNoOtherPayment("1", "iuv1");
        mockNoOtherPayment("2", "iuv2");
        when(cosmosService.createBizEventIfAbsent(any())).thenReturn(false);

        sut.recoverBizEvents(INVOCATION);

        verify(blobStorage, never()).updateDeadLetterBlobMetadata(anyString(), anyString(), any());
        verify(pdndClient).send(List.of(pending));
        verify(blobStorage).deleteDeadLetterBlob(CONTAINER_BIZ_EVENTS_DEAD_LETTER_NAME, BLOB);
    }

    @Test
    void recoverBizEventPdndErrorKeepsTheBlobAndThePendingIds() {
        BizEvent bizEvent = buildBizEvent("1", "iuv");
        mockBlob(CONTAINER_BIZ_EVENTS_DEAD_LETTER_NAME, Map.of(), bizEvent);
        mockNoOtherPayment("1", "iuv");
        when(cosmosService.createBizEventIfAbsent(bizEvent)).thenReturn(true);
        doThrow(new IllegalStateException("event hub error")).when(pdndClient).send(any());

        sut.recoverBizEvents(INVOCATION);

        verify(blobStorage, never()).deleteDeadLetterBlob(anyString(), anyString());
        Map<String, String> metadata = captureLastMetadata(CONTAINER_BIZ_EVENTS_DEAD_LETTER_NAME);
        assertEquals("1", metadata.get(METADATA_PDND_PENDING_IDS));
        assertEquals("1", metadata.get(METADATA_RECOVERY_ATTEMPTS));
        assertEquals("PDND_PUBLICATION_ERROR", metadata.get(METADATA_RECOVERY_REASON));
        assertNull(metadata.get(METADATA_RECOVERY_STATUS));
    }

    @Test
    void recoverBizEventPublishedEventsAreNotPendingAnymoreWhenAnotherEventFails() {
        BizEvent created = buildBizEvent("1", "iuv1");
        BizEvent error = buildBizEvent("2", "iuv2");
        mockBlob(CONTAINER_BIZ_EVENTS_DEAD_LETTER_NAME, Map.of(), created, error);
        mockNoOtherPayment("1", "iuv1");
        mockNoOtherPayment("2", "iuv2");
        when(cosmosService.createBizEventIfAbsent(created)).thenReturn(true);
        when(cosmosService.createBizEventIfAbsent(error)).thenThrow(mock(CosmosException.class));

        sut.recoverBizEvents(INVOCATION);

        verify(pdndClient).send(List.of(created));
        Map<String, String> metadata = captureLastMetadata(CONTAINER_BIZ_EVENTS_DEAD_LETTER_NAME);
        assertEquals("2", metadata.get(METADATA_PDND_PENDING_IDS));
        assertEquals("COSMOS_ERROR", metadata.get(METADATA_RECOVERY_REASON));
    }

    @Test
    void recoverBizEventTemporaryFailureComesBeforeThePermanentOne() {
        BizEvent coso = buildBizEvent("1", "iuv1");
        BizEvent error = buildBizEvent("2", "iuv2");
        mockBlob(CONTAINER_BIZ_EVENTS_DEAD_LETTER_NAME, Map.of(), coso, error);
        when(cosmosService.findOtherBizEventIdsOfPayment("pa", "iuv1", "1")).thenReturn(List.of("coso-id"));
        mockNoOtherPayment("2", "iuv2");
        when(cosmosService.createBizEventIfAbsent(error)).thenThrow(mock(CosmosException.class));

        sut.recoverBizEvents(INVOCATION);

        Map<String, String> metadata = captureLastMetadata(CONTAINER_BIZ_EVENTS_DEAD_LETTER_NAME);
        assertNull(metadata.get(METADATA_RECOVERY_STATUS));
        assertEquals("1", metadata.get(METADATA_RECOVERY_ATTEMPTS));
        assertEquals("COSMOS_ERROR,SAME_PAYMENT_OF_OTHER_BIZ_EVENT", metadata.get(METADATA_RECOVERY_REASON));
        verifyNoInteractions(telemetryClient);
    }

    @Test
    void recoverBizEventLastAttemptKeepsBothReasons() {
        BizEvent coso = buildBizEvent("1", "iuv1");
        BizEvent error = buildBizEvent("2", "iuv2");
        mockBlob(CONTAINER_BIZ_EVENTS_DEAD_LETTER_NAME, Map.of(METADATA_RECOVERY_ATTEMPTS, "2", METADATA_PDND_PENDING_IDS, "2"), coso, error);
        when(cosmosService.findOtherBizEventIdsOfPayment("pa", "iuv1", "1")).thenReturn(List.of("coso-id"));
        mockNoOtherPayment("2", "iuv2");
        when(cosmosService.createBizEventIfAbsent(error)).thenThrow(mock(CosmosException.class));

        sut.recoverBizEvents(INVOCATION);

        Map<String, String> metadata = captureLastMetadata(CONTAINER_BIZ_EVENTS_DEAD_LETTER_NAME);
        assertEquals(RECOVERY_STATUS_FAILED, metadata.get(METADATA_RECOVERY_STATUS));
        assertEquals("COSMOS_ERROR,SAME_PAYMENT_OF_OTHER_BIZ_EVENT", metadata.get(METADATA_RECOVERY_REASON));
        verify(telemetryClient).trackEvent(anyString());
    }

    @Test
    void recoverBizEventSamePaymentOfOtherBizEventNeedsManualCheck() {
        BizEvent bizEvent = buildBizEvent("1", "iuv");
        mockBlob(CONTAINER_BIZ_EVENTS_DEAD_LETTER_NAME, Map.of("invocationId", "inv"), bizEvent);
        when(cosmosService.findOtherBizEventIdsOfPayment("pa", "iuv", "1")).thenReturn(List.of("coso-id"));

        sut.recoverBizEvents(INVOCATION);

        verify(cosmosService, never()).createBizEventIfAbsent(any());
        verifyNoInteractions(pdndClient);
        verify(blobStorage, never()).deleteDeadLetterBlob(anyString(), anyString());
        Map<String, String> metadata = captureLastMetadata(CONTAINER_BIZ_EVENTS_DEAD_LETTER_NAME);
        assertEquals(RECOVERY_STATUS_FAILED, metadata.get(METADATA_RECOVERY_STATUS));
        assertEquals("SAME_PAYMENT_OF_OTHER_BIZ_EVENT", metadata.get(METADATA_RECOVERY_REASON));
        assertEquals("inv", metadata.get("invocationId"));
        assertFalse(metadata.containsKey(METADATA_PDND_PENDING_IDS));
        verify(telemetryClient).trackEvent(anyString());
    }

    @Test
    void recoverBizEventWithoutPaymentKeyNeedsManualCheck() {
        mockBlob(CONTAINER_BIZ_EVENTS_DEAD_LETTER_NAME, Map.of(), BizEvent.builder().id("1").build());

        sut.recoverBizEvents(INVOCATION);

        verifyNoInteractions(cosmosService, pdndClient);
        assertEquals("MISSING_PAYMENT_KEY", captureLastMetadata(CONTAINER_BIZ_EVENTS_DEAD_LETTER_NAME).get(METADATA_RECOVERY_REASON));
    }

    @Test
    void recoverBizEventErrorKeepsTheBlobAndCountsTheAttempt() {
        BizEvent bizEvent = buildBizEvent("1", "iuv");
        mockBlob(CONTAINER_BIZ_EVENTS_DEAD_LETTER_NAME, Map.of(), bizEvent);
        mockNoOtherPayment("1", "iuv");
        when(cosmosService.createBizEventIfAbsent(bizEvent)).thenThrow(mock(CosmosException.class));

        sut.recoverBizEvents(INVOCATION);

        verify(blobStorage, never()).deleteDeadLetterBlob(anyString(), anyString());
        Map<String, String> metadata = captureLastMetadata(CONTAINER_BIZ_EVENTS_DEAD_LETTER_NAME);
        assertEquals("1", metadata.get(METADATA_RECOVERY_ATTEMPTS));
        assertEquals("1", metadata.get(METADATA_PDND_PENDING_IDS));
        assertNull(metadata.get(METADATA_RECOVERY_STATUS));
        verifyNoInteractions(telemetryClient, pdndClient);
    }

    @Test
    void recoverBizEventLastAttemptMarksTheBlobAsFailed() {
        BizEvent bizEvent = buildBizEvent("1", "iuv");
        mockBlob(CONTAINER_BIZ_EVENTS_DEAD_LETTER_NAME, Map.of(METADATA_RECOVERY_ATTEMPTS, "2", METADATA_PDND_PENDING_IDS, "1"), bizEvent);
        mockNoOtherPayment("1", "iuv");
        when(cosmosService.createBizEventIfAbsent(bizEvent)).thenThrow(mock(CosmosException.class));

        sut.recoverBizEvents(INVOCATION);

        assertEquals(RECOVERY_STATUS_FAILED, captureLastMetadata(CONTAINER_BIZ_EVENTS_DEAD_LETTER_NAME).get(METADATA_RECOVERY_STATUS));
        verify(telemetryClient).trackEvent(anyString());
    }

    @Test
    void recoverBizEventUnexpectedErrorCountsTheAttempt() {
        BizEvent bizEvent = buildBizEvent("1", "iuv");
        mockBlob(CONTAINER_BIZ_EVENTS_DEAD_LETTER_NAME, Map.of(), bizEvent);
        mockNoOtherPayment("1", "iuv");
        when(cosmosService.createBizEventIfAbsent(bizEvent)).thenThrow(new IllegalStateException("unexpected"));

        sut.recoverBizEvents(INVOCATION);

        Map<String, String> metadata = captureLastMetadata(CONTAINER_BIZ_EVENTS_DEAD_LETTER_NAME);
        assertEquals("1", metadata.get(METADATA_RECOVERY_ATTEMPTS));
        assertEquals("UNEXPECTED_ERROR", metadata.get(METADATA_RECOVERY_REASON));
    }

    @Test
    void pendingIdsWriteErrorDoesNotSaveThePendingIdsWithTheAttempt() {
        BizEvent bizEvent = buildBizEvent("1", "iuv");
        mockBlob(CONTAINER_BIZ_EVENTS_DEAD_LETTER_NAME, Map.of(), bizEvent);
        doThrow(new IllegalStateException("storage error")).doNothing()
                .when(blobStorage).updateDeadLetterBlobMetadata(anyString(), anyString(), any());

        sut.recoverBizEvents(INVOCATION);

        verifyNoInteractions(cosmosService, pdndClient);
        Map<String, String> metadata = captureLastMetadata(CONTAINER_BIZ_EVENTS_DEAD_LETTER_NAME);
        assertEquals("BLOB_UPDATE_ERROR", metadata.get(METADATA_RECOVERY_REASON));
        assertFalse(metadata.containsKey(METADATA_PDND_PENDING_IDS));
    }

    @Test
    void blobDeleteErrorCountsTheAttempt() {
        BizEvent bizEvent = buildBizEvent("1", "iuv");
        mockBlob(CONTAINER_BIZ_EVENTS_DEAD_LETTER_NAME, Map.of(), bizEvent);
        mockNoOtherPayment("1", "iuv");
        when(cosmosService.createBizEventIfAbsent(bizEvent)).thenReturn(true);
        doThrow(new IllegalStateException("storage error")).when(blobStorage).deleteDeadLetterBlob(CONTAINER_BIZ_EVENTS_DEAD_LETTER_NAME, BLOB);

        sut.recoverBizEvents(INVOCATION);

        verify(pdndClient).send(List.of(bizEvent));
        Map<String, String> metadata = captureLastMetadata(CONTAINER_BIZ_EVENTS_DEAD_LETTER_NAME);
        assertEquals("1", metadata.get(METADATA_RECOVERY_ATTEMPTS));
        assertEquals("BLOB_DELETE_ERROR", metadata.get(METADATA_RECOVERY_REASON));
        assertFalse(metadata.containsKey(METADATA_PDND_PENDING_IDS));
    }

    @Test
    void unreadableBlobCountsTheAttempt() {
        DeadLetterBlob blob = new DeadLetterBlob(BLOB, Map.of(METADATA_RECOVERY_ATTEMPTS, "not-a-number"));
        when(blobStorage.listDeadLetterBlobs(eq(CONTAINER_BIZ_EVENTS_DEAD_LETTER_NAME), eq(FROM), any(), eq(50))).thenReturn(List.of(blob));
        when(blobStorage.readDeadLetterBlob(CONTAINER_BIZ_EVENTS_DEAD_LETTER_NAME, BLOB)).thenThrow(new IllegalStateException("error"));

        sut.recoverBizEvents(INVOCATION);

        Map<String, String> metadata = captureLastMetadata(CONTAINER_BIZ_EVENTS_DEAD_LETTER_NAME);
        assertEquals("1", metadata.get(METADATA_RECOVERY_ATTEMPTS));
        assertEquals("BLOB_READ_ERROR", metadata.get(METADATA_RECOVERY_REASON));
    }

    @Test
    @SuppressWarnings("unchecked")
    void failedBlobsAreNotListed() {
        ArgumentCaptor<Predicate<DeadLetterBlob>> captor = ArgumentCaptor.forClass(Predicate.class);
        when(blobStorage.listDeadLetterBlobs(anyString(), any(), captor.capture(), anyInt())).thenReturn(List.of());

        sut.recoverBizEvents(INVOCATION);

        assertTrue(captor.getValue().test(new DeadLetterBlob(BLOB, Map.of())));
        assertFalse(captor.getValue().test(new DeadLetterBlob(BLOB, Map.of(METADATA_RECOVERY_STATUS, RECOVERY_STATUS_FAILED))));
    }

    @Test
    void noRecoveryWithoutStartDate() {
        sut = new BizEventDeadLetterRecoveryServiceImpl(blobStorage, cosmosService, viewService, pdndClient, telemetryClient, null, 3, 50);

        sut.recoverBizEvents(INVOCATION);
        sut.recoverBizEventViews(INVOCATION);

        verifyNoInteractions(blobStorage, pdndClient);
    }

    @Test
    void recoverViewsFromTheBizEventOnTheDatastore() throws Exception {
        BizEvent datastoreEvent = buildBizEvent("1", "iuv");
        BizEventToViewResult views = buildViews();
        mockBlob(CONTAINER_BIZ_EVENTS_VIEWS_DEAD_LETTER_NAME, Map.of(), BizEvent.builder().id("1").build());
        when(cosmosService.getBizEvent("1")).thenReturn(datastoreEvent);
        when(viewService.mapBizEventToView(datastoreEvent)).thenReturn(views);

        sut.recoverBizEventViews(INVOCATION);

        // the user views are saved last, after the views with the receipt details
        InOrder inOrder = inOrder(cosmosService, blobStorage);
        inOrder.verify(cosmosService).upsertBizEventViewGeneral(views.getGeneralView());
        inOrder.verify(cosmosService).upsertBizEventViewCart(views.getCartView());
        inOrder.verify(cosmosService).saveBizEventViewUsersPreservingVisibility(views.getUserViewList());
        inOrder.verify(blobStorage).deleteDeadLetterBlob(CONTAINER_BIZ_EVENTS_VIEWS_DEAD_LETTER_NAME, BLOB);
        
        verifyNoInteractions(pdndClient);
        verify(blobStorage, never()).updateDeadLetterBlobMetadata(anyString(), anyString(), any());
    }

    @Test
    void recoverViewsNothingToGenerate() throws Exception {
        BizEvent datastoreEvent = buildBizEvent("1", "iuv");
        mockBlob(CONTAINER_BIZ_EVENTS_VIEWS_DEAD_LETTER_NAME, Map.of(), BizEvent.builder().id("1").build());
        when(cosmosService.getBizEvent("1")).thenReturn(datastoreEvent);
        when(viewService.mapBizEventToView(datastoreEvent)).thenReturn(null);

        sut.recoverBizEventViews(INVOCATION);

        verify(cosmosService, never()).saveBizEventViewUsersPreservingVisibility(any());
        verify(blobStorage).deleteDeadLetterBlob(CONTAINER_BIZ_EVENTS_VIEWS_DEAD_LETTER_NAME, BLOB);
    }

    @Test
    void recoverViewsBizEventNotFoundDoesNotCreateOrphanViews() throws Exception {
        mockBlob(CONTAINER_BIZ_EVENTS_VIEWS_DEAD_LETTER_NAME, Map.of(), BizEvent.builder().id("1").build());
        when(cosmosService.getBizEvent("1")).thenThrow(new BizEventNotFoundException("not found"));

        sut.recoverBizEventViews(INVOCATION);

        verifyNoInteractions(viewService);
        verify(blobStorage, never()).deleteDeadLetterBlob(anyString(), anyString());
        Map<String, String> metadata = captureLastMetadata(CONTAINER_BIZ_EVENTS_VIEWS_DEAD_LETTER_NAME);
        assertEquals("1", metadata.get(METADATA_RECOVERY_ATTEMPTS));
        assertEquals("BIZ_EVENT_NOT_FOUND", metadata.get(METADATA_RECOVERY_REASON));
    }

    @Test
    void recoverViewsInvalidNeedsManualCheck() throws Exception {
        BizEvent datastoreEvent = buildBizEvent("1", "iuv");
        mockBlob(CONTAINER_BIZ_EVENTS_VIEWS_DEAD_LETTER_NAME, Map.of(), BizEvent.builder().id("1").build());
        when(cosmosService.getBizEvent("1")).thenReturn(datastoreEvent);
        when(viewService.mapBizEventToView(datastoreEvent)).thenThrow(new BizEventToViewConstraintViolationException("invalid", List.of()));

        sut.recoverBizEventViews(INVOCATION);

        Map<String, String> metadata = captureLastMetadata(CONTAINER_BIZ_EVENTS_VIEWS_DEAD_LETTER_NAME);
        assertEquals(RECOVERY_STATUS_FAILED, metadata.get(METADATA_RECOVERY_STATUS));
        assertEquals("INVALID_VIEWS", metadata.get(METADATA_RECOVERY_REASON));
    }

    private void mockBlob(String container, Map<String, String> metadata, BizEvent... events) {
        when(blobStorage.listDeadLetterBlobs(eq(container), eq(FROM), any(), eq(50)))
                .thenReturn(List.of(new DeadLetterBlob(BLOB, new HashMap<>(metadata))));
        when(blobStorage.readDeadLetterBlob(container, BLOB)).thenReturn(List.of(events));
    }

    private void mockNoOtherPayment(String id, String iuv) {
        when(cosmosService.findOtherBizEventIdsOfPayment("pa", iuv, id)).thenReturn(List.of());
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> captureLastMetadata(String container) {
        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(blobStorage, atLeastOnce()).updateDeadLetterBlobMetadata(eq(container), eq(BLOB), captor.capture());
        return captor.getValue();
    }

    private BizEvent buildBizEvent(String id, String iuv) {
        return BizEvent.builder()
                .id(id)
                .creditor(Creditor.builder().idPA("pa").build())
                .debtorPosition(DebtorPosition.builder().iuv(iuv).build())
                .paymentInfo(PaymentInfo.builder().IUR("iur").build())
                .build();
    }

    private BizEventToViewResult buildViews() {
        return BizEventToViewResult.builder()
                .userViewList(List.of(BizEventsViewUser.builder().id("1-p").build()))
                .generalView(new BizEventsViewGeneral())
                .cartView(new BizEventsViewCart())
                .build();
    }
}
