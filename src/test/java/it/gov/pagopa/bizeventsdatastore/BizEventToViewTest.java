package it.gov.pagopa.bizeventsdatastore;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.OutputBinding;
import it.gov.pagopa.bizeventsdatastore.client.BizEventCosmosClient;
import it.gov.pagopa.bizeventsdatastore.entity.BizEvent;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewCart;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewGeneral;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewUser;
import it.gov.pagopa.bizeventsdatastore.exception.BizEventToViewConstraintViolationException;
import it.gov.pagopa.bizeventsdatastore.exception.BizEventNotFoundException;
import it.gov.pagopa.bizeventsdatastore.model.BizEventToViewResult;
import it.gov.pagopa.bizeventsdatastore.service.BizEventToViewService;
import it.gov.pagopa.bizeventsdatastore.util.TestUtil;
import it.gov.pagopa.bizeventsdatastore.utils.HttpResponseMessageMock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BizEventToViewTest {

    private static final String BIZ_EVENT_ID = "valid_biz_event_id";
    private static final String VIEW_USER_ID = "viewUserId";
    private static final String VIEW_GENERAL_ID = "viewGeneralId";
    private static final String VIEW_CART_ID = "viewCartId";

    @Mock
    private ExecutionContext executionContextMock;

    @Mock
    private Logger loggerMock;

    @Mock
    private BizEventCosmosClient bizEventCosmosClientMock;

    @Mock
    private BizEventToViewService bizEventToViewService;

    @Mock
    private HttpRequestMessage<Optional<String>> request;

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
    private BizEventToView sut;

    @Test
    void bizEventToViewOK() throws IOException, BizEventNotFoundException, BizEventToViewConstraintViolationException {
        BizEvent bizEvent = TestUtil.readModelFromFile("payment-manager/bizEvent.json", BizEvent.class);

        doReturn(loggerMock).when(executionContextMock).getLogger();
        doReturn(bizEvent).when(bizEventCosmosClientMock).getBizEventDocument(BIZ_EVENT_ID);
        doReturn(buildBizEventToViewResult()).when(bizEventToViewService).mapBizEventToView(loggerMock, bizEvent);
        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(request).createResponseBuilder(any(HttpStatus.class));

        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(
                request,
                BIZ_EVENT_ID,
                viewUserOutputBinding,
                viewGeneralOutputBinding,
                viewCartOutputBinding,
                executionContextMock
        ));

        assertEquals(HttpStatus.OK, response.getStatus());

        verify(viewUserOutputBinding).setValue(viewUserCaptor.capture());
        List<BizEventsViewUser> viewUsersResult = viewUserCaptor.getValue();
        assertFalse(viewUsersResult.isEmpty());
        assertEquals(1, viewUsersResult.size());
        assertEquals(VIEW_USER_ID, viewUsersResult.get(0).getId());

        verify(viewGeneralOutputBinding).setValue(viewGeneralCaptor.capture());
        BizEventsViewGeneral viewGeneralResult = viewGeneralCaptor.getValue();
        assertNotNull(viewUsersResult);
        assertEquals(VIEW_GENERAL_ID, viewGeneralResult.getId());

        verify(viewCartOutputBinding).setValue(viewCartCaptor.capture());
        BizEventsViewCart viewCartResult = viewCartCaptor.getValue();
        assertNotNull(viewUsersResult);
        assertEquals(VIEW_CART_ID, viewCartResult.getId());
    }

    @Test
    void bizEventToViewKO_InvalidParam() {
        doReturn(loggerMock).when(executionContextMock).getLogger();
        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(request).createResponseBuilder(any(HttpStatus.class));

        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(
                request,
                null,
                viewUserOutputBinding,
                viewGeneralOutputBinding,
                viewCartOutputBinding,
                executionContextMock
        ));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatus());

        verify(viewUserOutputBinding, never()).setValue(any());
        verify(viewGeneralOutputBinding, never()).setValue(any());
        verify(viewCartOutputBinding, never()).setValue(any());
    }

    @Test
    void bizEventToViewKO_BizEventNotFound() throws BizEventNotFoundException {
        doReturn(loggerMock).when(executionContextMock).getLogger();
        doThrow(BizEventNotFoundException.class).when(bizEventCosmosClientMock).getBizEventDocument(BIZ_EVENT_ID);
        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(request).createResponseBuilder(any(HttpStatus.class));

        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(
                request,
                BIZ_EVENT_ID,
                viewUserOutputBinding,
                viewGeneralOutputBinding,
                viewCartOutputBinding,
                executionContextMock
        ));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatus());

        verify(viewUserOutputBinding, never()).setValue(any());
        verify(viewGeneralOutputBinding, never()).setValue(any());
        verify(viewCartOutputBinding, never()).setValue(any());
    }

    @Test
    void bizEventToViewKO_CosmosException() throws BizEventNotFoundException {
        doReturn(loggerMock).when(executionContextMock).getLogger();
        doThrow(RuntimeException.class).when(bizEventCosmosClientMock).getBizEventDocument(BIZ_EVENT_ID);
        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(request).createResponseBuilder(any(HttpStatus.class));

        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(
                request,
                BIZ_EVENT_ID,
                viewUserOutputBinding,
                viewGeneralOutputBinding,
                viewCartOutputBinding,
                executionContextMock
        ));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());

        verify(viewUserOutputBinding, never()).setValue(any());
        verify(viewGeneralOutputBinding, never()).setValue(any());
        verify(viewCartOutputBinding, never()).setValue(any());
    }

    @Test
    void bizEventToViewKO_MapToViewError() throws BizEventNotFoundException, BizEventToViewConstraintViolationException, IOException {
        BizEvent bizEvent = TestUtil.readModelFromFile("payment-manager/bizEvent.json", BizEvent.class);

        doReturn(loggerMock).when(executionContextMock).getLogger();
        doReturn(bizEvent).when(bizEventCosmosClientMock).getBizEventDocument(BIZ_EVENT_ID);
        doThrow(BizEventToViewConstraintViolationException.class).when(bizEventToViewService).mapBizEventToView(loggerMock, bizEvent);
        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(request).createResponseBuilder(any(HttpStatus.class));

        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(
                request,
                BIZ_EVENT_ID,
                viewUserOutputBinding,
                viewGeneralOutputBinding,
                viewCartOutputBinding,
                executionContextMock
        ));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());

        verify(viewUserOutputBinding, never()).setValue(any());
        verify(viewGeneralOutputBinding, never()).setValue(any());
        verify(viewCartOutputBinding, never()).setValue(any());
    }

    @Test
    void bizEventToViewKO_MapToViewGenericException() throws BizEventNotFoundException, BizEventToViewConstraintViolationException, IOException {
        BizEvent bizEvent = TestUtil.readModelFromFile("payment-manager/bizEvent.json", BizEvent.class);

        doReturn(loggerMock).when(executionContextMock).getLogger();
        doReturn(bizEvent).when(bizEventCosmosClientMock).getBizEventDocument(BIZ_EVENT_ID);
        doThrow(RuntimeException.class).when(bizEventToViewService).mapBizEventToView(loggerMock, bizEvent);
        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(request).createResponseBuilder(any(HttpStatus.class));

        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(
                request,
                BIZ_EVENT_ID,
                viewUserOutputBinding,
                viewGeneralOutputBinding,
                viewCartOutputBinding,
                executionContextMock
        ));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());

        verify(viewUserOutputBinding, never()).setValue(any());
        verify(viewGeneralOutputBinding, never()).setValue(any());
        verify(viewCartOutputBinding, never()).setValue(any());
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