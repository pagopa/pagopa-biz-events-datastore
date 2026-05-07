package it.gov.pagopa.bizeventsdatastore;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import it.gov.pagopa.bizeventsdatastore.entity.BizEvent;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewCart;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewGeneral;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewUser;
import it.gov.pagopa.bizeventsdatastore.exception.BizEventNotFoundException;
import it.gov.pagopa.bizeventsdatastore.exception.BizEventToViewConstraintViolationException;
import it.gov.pagopa.bizeventsdatastore.model.BizEventToViewResult;
import it.gov.pagopa.bizeventsdatastore.service.BizEventCosmosService;
import it.gov.pagopa.bizeventsdatastore.service.BizEventToViewService;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BizEventToViewQueueTriggerTest {

    private static final String BIZ_EVENT_ID = "valid_biz_event_id";
    private static final String VIEW_USER_ID = "viewUserId";
    private static final String VIEW_GENERAL_ID = "viewGeneralId";
    private static final String VIEW_CART_ID = "viewCartId";

    @Mock
    private ExecutionContext executionContextMock;

    @Mock
    private BizEventCosmosService bizEventCosmosServiceMock;

    @Mock
    private BizEventToViewService bizEventToViewService;

    @Spy
    private OutputBinding<List<BizEventsViewUser>> viewUserOutputBinding;

    @Spy
    private OutputBinding<BizEventsViewGeneral> viewGeneralOutputBinding;

    @Spy
    private OutputBinding<BizEventsViewCart> viewCartOutputBinding;

    @Captor
    private ArgumentCaptor<List<BizEventsViewUser>> viewUserCaptor;

    @Captor
    private ArgumentCaptor<BizEventsViewGeneral> viewGeneralCaptor;

    @Captor
    private ArgumentCaptor<BizEventsViewCart> viewCartCaptor;

    @InjectMocks
    private BizEventToViewQueueTrigger sut;

    @Test
    @SneakyThrows
    void runOK() {
        BizEvent bizEvent = new BizEvent();
        when(bizEventCosmosServiceMock.getBizEvent(BIZ_EVENT_ID)).thenReturn(bizEvent);
        when(bizEventToViewService.mapBizEventToView(bizEvent)).thenReturn(buildBizEventToViewResult());

        assertDoesNotThrow(() -> sut.run(
                BIZ_EVENT_ID,
                viewUserOutputBinding,
                viewGeneralOutputBinding,
                viewCartOutputBinding,
                executionContextMock)
        );

        verify(viewUserOutputBinding).setValue(viewUserCaptor.capture());
        List<BizEventsViewUser> viewUsers = viewUserCaptor.getValue();
        assertNotNull(viewUsers);
        assertEquals(1, viewUsers.size());
        verify(viewGeneralOutputBinding).setValue(viewGeneralCaptor.capture());
        assertNotNull(viewGeneralCaptor.getValue());
        verify(viewCartOutputBinding).setValue(viewCartCaptor.capture());
        assertNotNull(viewCartCaptor.getValue());
    }

    @Test
    @SneakyThrows
    void runKO_BizEventNotFound() {
        when(bizEventCosmosServiceMock.getBizEvent(BIZ_EVENT_ID)).thenThrow(BizEventNotFoundException.class);

        assertDoesNotThrow(() -> sut.run(
                BIZ_EVENT_ID,
                viewUserOutputBinding,
                viewGeneralOutputBinding,
                viewCartOutputBinding,
                executionContextMock)
        );

        verify(bizEventToViewService, never()).mapBizEventToView(any());
        verify(viewUserOutputBinding, never()).setValue(viewUserCaptor.capture());
        verify(viewGeneralOutputBinding, never()).setValue(viewGeneralCaptor.capture());
        verify(viewCartOutputBinding, never()).setValue(viewCartCaptor.capture());
    }

    @Test
    @SneakyThrows
    void runKO_BizEventToViewConstraintViolationException() {
        BizEvent bizEvent = new BizEvent();
        when(bizEventCosmosServiceMock.getBizEvent(BIZ_EVENT_ID)).thenReturn(bizEvent);
        when(bizEventToViewService.mapBizEventToView(bizEvent)).thenThrow(BizEventToViewConstraintViolationException.class);

        assertDoesNotThrow(() -> sut.run(
                BIZ_EVENT_ID,
                viewUserOutputBinding,
                viewGeneralOutputBinding,
                viewCartOutputBinding,
                executionContextMock)
        );

        verify(viewUserOutputBinding, never()).setValue(viewUserCaptor.capture());
        verify(viewGeneralOutputBinding, never()).setValue(viewGeneralCaptor.capture());
        verify(viewCartOutputBinding, never()).setValue(viewCartCaptor.capture());
    }

    @Test
    @SneakyThrows
    void runKO_BizEventNotValidForViewGenerationException() {
        BizEvent bizEvent = new BizEvent();
        when(bizEventCosmosServiceMock.getBizEvent(BIZ_EVENT_ID)).thenReturn(bizEvent);
        when(bizEventToViewService.mapBizEventToView(bizEvent)).thenReturn(null);

        assertDoesNotThrow(() -> sut.run(
                BIZ_EVENT_ID,
                viewUserOutputBinding,
                viewGeneralOutputBinding,
                viewCartOutputBinding,
                executionContextMock)
        );

        verify(viewUserOutputBinding, never()).setValue(viewUserCaptor.capture());
        verify(viewGeneralOutputBinding, never()).setValue(viewGeneralCaptor.capture());
        verify(viewCartOutputBinding, never()).setValue(viewCartCaptor.capture());
    }

    private BizEventToViewResult buildBizEventToViewResult() {
        return BizEventToViewResult.builder()
                .userViewList(Collections.singletonList(
                        BizEventsViewUser.builder()
                                .id(VIEW_USER_ID)
                                .build()))
                .generalView(BizEventsViewGeneral.builder().id(VIEW_GENERAL_ID).build())
                .cartView(BizEventsViewCart.builder().id(VIEW_CART_ID).build())
                .build();
    }
}