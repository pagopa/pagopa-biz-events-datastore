package it.gov.pagopa.bizeventsdatastore.service.impl;

import it.gov.pagopa.bizeventsdatastore.client.BizEventCosmosClient;
import it.gov.pagopa.bizeventsdatastore.entity.BizEvent;
import it.gov.pagopa.bizeventsdatastore.entity.enumeration.StatusType;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewCart;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewGeneral;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewUser;
import it.gov.pagopa.bizeventsdatastore.exception.BizEventNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

@ExtendWith(MockitoExtension.class)
class BizEventCosmosServiceImplTest {

    public static final String EVENT_ID = "1";
    @Mock
    private BizEventCosmosClient bizEventCosmosClientMock;

    @InjectMocks
    private BizEventCosmosServiceImpl sut;

    @ParameterizedTest
    @EnumSource(value = StatusType.class, names = {"DONE", "INGESTED"})
    void getBizEventSuccess(StatusType status) throws BizEventNotFoundException {
        when(bizEventCosmosClientMock.getBizEventDocument(EVENT_ID))
                .thenReturn(BizEvent.builder().eventStatus(status).build());

        BizEvent result = assertDoesNotThrow(() -> sut.getBizEvent(EVENT_ID));

        assertNotNull(result);
    }

    @Test
    void getBizEventErrorNotFound() throws BizEventNotFoundException {
        when(bizEventCosmosClientMock.getBizEventDocument(EVENT_ID))
                .thenThrow(BizEventNotFoundException.class);

        assertThrows(BizEventNotFoundException.class, () -> sut.getBizEvent(EVENT_ID));
    }

    @ParameterizedTest
    @EnumSource(value = StatusType.class, names = {"DONE", "INGESTED"}, mode = EnumSource.Mode.EXCLUDE)
    void getBizEventErrorWrongStatus(StatusType status) throws BizEventNotFoundException {
        when(bizEventCosmosClientMock.getBizEventDocument(EVENT_ID))
                .thenReturn(BizEvent.builder().eventStatus(status).build());

        assertThrows(BizEventNotFoundException.class, () -> sut.getBizEvent(EVENT_ID));
    }

    @Test
    void saveBizEventViewUsersPreservingVisibilitySuccess() {
        BizEventsViewUser payerView = BizEventsViewUser.builder().id(EVENT_ID + "-p").build();
        BizEventsViewUser debtorView = BizEventsViewUser.builder().id(EVENT_ID + "-d").build();

        assertDoesNotThrow(() -> sut.saveBizEventViewUsersPreservingVisibility(List.of(payerView, debtorView)));

        verify(bizEventCosmosClientMock).saveBizEventViewUserPreservingVisibility(payerView);
        verify(bizEventCosmosClientMock).saveBizEventViewUserPreservingVisibility(debtorView);
    }

    @Test
    void recoveryMethodsDelegateToClient() {
        BizEvent bizEvent = BizEvent.builder().id(EVENT_ID).build();
        BizEventsViewGeneral viewGeneral = new BizEventsViewGeneral();
        BizEventsViewCart viewCart = new BizEventsViewCart();
        when(bizEventCosmosClientMock.createBizEventIfAbsent(bizEvent)).thenReturn(true);
        when(bizEventCosmosClientMock.findOtherBizEventIdsOfPayment("pa", "iuv", EVENT_ID)).thenReturn(List.of("2"));

        assertEquals(true, sut.createBizEventIfAbsent(bizEvent));
        assertEquals(List.of("2"), sut.findOtherBizEventIdsOfPayment("pa", "iuv", EVENT_ID));
        sut.upsertBizEventViewGeneral(viewGeneral);
        sut.upsertBizEventViewCart(viewCart);

        verify(bizEventCosmosClientMock).upsertBizEventViewGeneral(viewGeneral);
        verify(bizEventCosmosClientMock).upsertBizEventViewCart(viewCart);
    }
}
