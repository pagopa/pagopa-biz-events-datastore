package it.gov.pagopa.bizeventsdatastore.service.impl;

import com.azure.cosmos.CosmosException;
import com.microsoft.applicationinsights.TelemetryClient;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static it.gov.pagopa.bizeventsdatastore.service.impl.BizEventDeadLetterRecoveryServiceImpl.CONTAINER_BIZ_EVENTS_DEAD_LETTER_NAME;
import static it.gov.pagopa.bizeventsdatastore.service.impl.BizEventDeadLetterRecoveryServiceImpl.CONTAINER_BIZ_EVENTS_VIEWS_DEAD_LETTER_NAME;
import static it.gov.pagopa.bizeventsdatastore.service.impl.BizEventDeadLetterRecoveryServiceImpl.METADATA_RECOVERY_ATTEMPTS;
import static it.gov.pagopa.bizeventsdatastore.service.impl.BizEventDeadLetterRecoveryServiceImpl.METADATA_RECOVERY_REASON;
import static it.gov.pagopa.bizeventsdatastore.service.impl.BizEventDeadLetterRecoveryServiceImpl.METADATA_RECOVERY_STATUS;
import static it.gov.pagopa.bizeventsdatastore.service.impl.BizEventDeadLetterRecoveryServiceImpl.RECOVERY_STATUS_FAILED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
    void recoverBizEventCreatedAndEnriched() {
        BizEvent bizEvent = buildBizEvent();
        mockBlob(CONTAINER_BIZ_EVENTS_DEAD_LETTER_NAME, Map.of("invocationId", "1"), bizEvent);
        when(cosmosService.findOtherBizEventIdsOfPayment("pa", "iuv", "1")).thenReturn(List.of());
        when(cosmosService.createBizEventIfAbsent(bizEvent)).thenReturn(true);

        List<BizEvent> result = sut.recoverBizEvents(INVOCATION);

        assertEquals(List.of(bizEvent), result);
        assertEquals("iur", bizEvent.getDebtorPosition().getIur());
        assertNotNull(bizEvent.getTimestamp());
        verify(blobStorage).deleteDeadLetterBlob(CONTAINER_BIZ_EVENTS_DEAD_LETTER_NAME, BLOB);
    }

    @Test
    void recoverBizEventAlreadyPresentIsNotSentAgain() {
        BizEvent bizEvent = buildBizEvent();
        mockBlob(CONTAINER_BIZ_EVENTS_DEAD_LETTER_NAME, Map.of(), bizEvent);
        when(cosmosService.findOtherBizEventIdsOfPayment("pa", "iuv", "1")).thenReturn(List.of());
        when(cosmosService.createBizEventIfAbsent(bizEvent)).thenReturn(false);

        assertTrue(sut.recoverBizEvents(INVOCATION).isEmpty());

        verify(blobStorage).deleteDeadLetterBlob(CONTAINER_BIZ_EVENTS_DEAD_LETTER_NAME, BLOB);
    }

    @Test
    void recoverBizEventSamePaymentOfOtherBizEventNeedsManualCheck() {
        BizEvent bizEvent = buildBizEvent();
        mockBlob(CONTAINER_BIZ_EVENTS_DEAD_LETTER_NAME, Map.of("invocationId", "1"), bizEvent);
        when(cosmosService.findOtherBizEventIdsOfPayment("pa", "iuv", "1")).thenReturn(List.of("coso-id"));

        assertTrue(sut.recoverBizEvents(INVOCATION).isEmpty());

        verify(cosmosService, never()).createBizEventIfAbsent(any());
        verify(blobStorage, never()).deleteDeadLetterBlob(anyString(), anyString());
        Map<String, String> metadata = captureMetadata(CONTAINER_BIZ_EVENTS_DEAD_LETTER_NAME);
        assertEquals(RECOVERY_STATUS_FAILED, metadata.get(METADATA_RECOVERY_STATUS));
        assertEquals("same-payment-of-other-biz-event", metadata.get(METADATA_RECOVERY_REASON));
        assertEquals("1", metadata.get("invocationId"));
        verify(telemetryClient).trackEvent(anyString());
    }

    @Test
    void recoverBizEventWithoutPaymentKeyNeedsManualCheck() {
        mockBlob(CONTAINER_BIZ_EVENTS_DEAD_LETTER_NAME, Map.of(), BizEvent.builder().id("1").build());

        assertTrue(sut.recoverBizEvents(INVOCATION).isEmpty());

        verifyNoInteractions(cosmosService);
        assertEquals("missing-payment-key", captureMetadata(CONTAINER_BIZ_EVENTS_DEAD_LETTER_NAME).get(METADATA_RECOVERY_REASON));
    }

    @Test
    void recoverBizEventErrorKeepsTheBlobAndCountsTheAttempt() {
        BizEvent bizEvent = buildBizEvent();
        mockBlob(CONTAINER_BIZ_EVENTS_DEAD_LETTER_NAME, Map.of(), bizEvent);
        when(cosmosService.findOtherBizEventIdsOfPayment("pa", "iuv", "1")).thenReturn(List.of());
        when(cosmosService.createBizEventIfAbsent(bizEvent)).thenThrow(mock(CosmosException.class));

        sut.recoverBizEvents(INVOCATION);

        verify(blobStorage, never()).deleteDeadLetterBlob(anyString(), anyString());
        Map<String, String> metadata = captureMetadata(CONTAINER_BIZ_EVENTS_DEAD_LETTER_NAME);
        assertEquals("1", metadata.get(METADATA_RECOVERY_ATTEMPTS));
        assertNull(metadata.get(METADATA_RECOVERY_STATUS));
        verifyNoInteractions(telemetryClient);
    }

    @Test
    void recoverBizEventLastAttemptMarksTheBlobAsFailed() {
        BizEvent bizEvent = buildBizEvent();
        mockBlob(CONTAINER_BIZ_EVENTS_DEAD_LETTER_NAME, Map.of(METADATA_RECOVERY_ATTEMPTS, "2"), bizEvent);
        when(cosmosService.findOtherBizEventIdsOfPayment("pa", "iuv", "1")).thenReturn(List.of());
        when(cosmosService.createBizEventIfAbsent(bizEvent)).thenThrow(mock(CosmosException.class));

        sut.recoverBizEvents(INVOCATION);

        assertEquals(RECOVERY_STATUS_FAILED, captureMetadata(CONTAINER_BIZ_EVENTS_DEAD_LETTER_NAME).get(METADATA_RECOVERY_STATUS));
        verify(telemetryClient).trackEvent(anyString());
    }

    @Test
    void unreadableBlobCountsTheAttempt() {
        DeadLetterBlob blob = new DeadLetterBlob(BLOB, Map.of(METADATA_RECOVERY_ATTEMPTS, "not-a-number"));
        when(blobStorage.listDeadLetterBlobs(eq(CONTAINER_BIZ_EVENTS_DEAD_LETTER_NAME), eq(FROM), any(), eq(50))).thenReturn(List.of(blob));
        when(blobStorage.readDeadLetterBlob(CONTAINER_BIZ_EVENTS_DEAD_LETTER_NAME, BLOB)).thenThrow(new IllegalStateException("error"));

        sut.recoverBizEvents(INVOCATION);

        assertEquals("1", captureMetadata(CONTAINER_BIZ_EVENTS_DEAD_LETTER_NAME).get(METADATA_RECOVERY_ATTEMPTS));
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
        sut = new BizEventDeadLetterRecoveryServiceImpl(blobStorage, cosmosService, viewService, telemetryClient, null, 3, 50);

        assertTrue(sut.recoverBizEvents(INVOCATION).isEmpty());
        sut.recoverBizEventViews(INVOCATION);

        verifyNoInteractions(blobStorage);
    }

    @Test
    void recoverViewsFromTheBizEventOnTheDatastore() throws Exception {
        BizEvent deadLetterEvent = BizEvent.builder().id("1").build();
        BizEvent datastoreEvent = buildBizEvent();
        BizEventToViewResult views = buildViews();
        mockBlob(CONTAINER_BIZ_EVENTS_VIEWS_DEAD_LETTER_NAME, Map.of(), deadLetterEvent);
        when(cosmosService.getBizEvent("1")).thenReturn(datastoreEvent);
        when(viewService.mapBizEventToView(datastoreEvent)).thenReturn(views);

        sut.recoverBizEventViews(INVOCATION);

        verify(cosmosService).saveBizEventViewUsersPreservingVisibility(views.getUserViewList());
        verify(cosmosService).upsertBizEventViewGeneral(views.getGeneralView());
        verify(cosmosService).upsertBizEventViewCart(views.getCartView());
        verify(blobStorage).deleteDeadLetterBlob(CONTAINER_BIZ_EVENTS_VIEWS_DEAD_LETTER_NAME, BLOB);
    }

    @Test
    void recoverViewsNothingToGenerate() throws Exception {
        BizEvent datastoreEvent = buildBizEvent();
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
        assertEquals("1", captureMetadata(CONTAINER_BIZ_EVENTS_VIEWS_DEAD_LETTER_NAME).get(METADATA_RECOVERY_ATTEMPTS));
    }

    @Test
    void recoverViewsInvalidNeedsManualCheck() throws Exception {
        BizEvent datastoreEvent = buildBizEvent();
        mockBlob(CONTAINER_BIZ_EVENTS_VIEWS_DEAD_LETTER_NAME, Map.of(), BizEvent.builder().id("1").build());
        when(cosmosService.getBizEvent("1")).thenReturn(datastoreEvent);
        when(viewService.mapBizEventToView(datastoreEvent)).thenThrow(new BizEventToViewConstraintViolationException("invalid", List.of()));

        sut.recoverBizEventViews(INVOCATION);

        Map<String, String> metadata = captureMetadata(CONTAINER_BIZ_EVENTS_VIEWS_DEAD_LETTER_NAME);
        assertEquals(RECOVERY_STATUS_FAILED, metadata.get(METADATA_RECOVERY_STATUS));
        assertEquals("invalid-views", metadata.get(METADATA_RECOVERY_REASON));
    }

    private void mockBlob(String container, Map<String, String> metadata, BizEvent... events) {
        when(blobStorage.listDeadLetterBlobs(eq(container), eq(FROM), any(), eq(50))).thenReturn(List.of(new DeadLetterBlob(BLOB, metadata)));
        when(blobStorage.readDeadLetterBlob(container, BLOB)).thenReturn(List.of(events));
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> captureMetadata(String container) {
        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(blobStorage).updateDeadLetterBlobMetadata(eq(container), eq(BLOB), captor.capture());
        return captor.getValue();
    }

    private BizEvent buildBizEvent() {
        return BizEvent.builder()
                .id("1")
                .creditor(Creditor.builder().idPA("pa").build())
                .debtorPosition(DebtorPosition.builder().iuv("iuv").build())
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
