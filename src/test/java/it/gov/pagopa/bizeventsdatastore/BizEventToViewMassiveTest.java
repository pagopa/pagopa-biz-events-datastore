package it.gov.pagopa.bizeventsdatastore;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import it.gov.pagopa.bizeventsdatastore.model.ProblemJson;
import it.gov.pagopa.bizeventsdatastore.service.MassiveBizViewRegenQueueService;
import it.gov.pagopa.bizeventsdatastore.utils.HttpResponseMessageMock;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BizEventToViewMassiveTest {

    @Mock
    private MassiveBizViewRegenQueueService queueServiceMock;

    @Mock
    private HttpRequestMessage<String> request;

    @Mock
    private ExecutionContext executionContextMock;

    @InjectMocks
    private BizEventToViewMassive sut;

    @BeforeEach
    void setUp() {
        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(request).createResponseBuilder(any(HttpStatus.class));
    }

    @Test
    @SneakyThrows
    void runOK() {
        when(request.getBody()).thenReturn(loadCsv("massive-create-view/biz-event-ids.csv"));
        when(queueServiceMock.sendBizEventIdsToQueueInBatch(anyList())).thenReturn(Collections.emptyList());

        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(request, executionContextMock));

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatus());
        assertNotNull(response.getBody());

        verify(queueServiceMock, times(1)).sendBizEventIdsToQueueInBatch(anyList());
    }

    @Test
    @SneakyThrows
    void runPartialOK_SomeErrorOnQueue() {
        when(request.getBody()).thenReturn(loadCsv("massive-create-view/biz-event-ids.csv"));
        when(queueServiceMock.sendBizEventIdsToQueueInBatch(anyList())).thenReturn(List.of("id2", "id4"));

        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(request, executionContextMock));

        assertNotNull(response);
        assertEquals(HttpStatus.MULTI_STATUS, response.getStatus());
        ProblemJson body = (ProblemJson) response.getBody();
        assertNotNull(body);
        assertEquals(HttpStatus.MULTI_STATUS.value(), body.getStatus());
        assertEquals(HttpStatus.MULTI_STATUS.name(), body.getTitle());

        verify(queueServiceMock, times(1)).sendBizEventIdsToQueueInBatch(anyList());
    }

    @Test
    void runKO_NoBody() {
        when(request.getBody()).thenReturn(null);

        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(request, executionContextMock));

        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatus());
        ProblemJson body = (ProblemJson) response.getBody();
        assertNotNull(body);
        assertEquals(HttpStatus.BAD_REQUEST.value(), body.getStatus());
        assertEquals(HttpStatus.BAD_REQUEST.name(), body.getTitle());

        verify(queueServiceMock, never()).sendBizEventIdsToQueueInBatch(anyList());
    }

    @Test
    @SneakyThrows
    void runKO_InvalidFile() {
        when(request.getBody()).thenReturn(loadCsv("massive-create-view/malformed.csv"));

        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(request, executionContextMock));

        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());
        ProblemJson body = (ProblemJson) response.getBody();
        assertNotNull(body);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), body.getStatus());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.name(), body.getTitle());

        verify(queueServiceMock, never()).sendBizEventIdsToQueueInBatch(anyList());
    }

    @Test
    @SneakyThrows
    void runPartialOK_InvalidLines() {
        when(request.getBody()).thenReturn(loadCsv("massive-create-view/biz-event-ids-with-invalid-line.csv"));
        when(queueServiceMock.sendBizEventIdsToQueueInBatch(anyList())).thenReturn(Collections.emptyList());

        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(request, executionContextMock));

        assertNotNull(response);
        assertEquals(HttpStatus.MULTI_STATUS, response.getStatus());
        ProblemJson body = (ProblemJson) response.getBody();
        assertNotNull(body);
        assertEquals(HttpStatus.MULTI_STATUS.value(), body.getStatus());
        assertEquals(HttpStatus.MULTI_STATUS.name(), body.getTitle());

        verify(queueServiceMock, times(1)).sendBizEventIdsToQueueInBatch(anyList());
    }

    @Test
    @SneakyThrows
    void runKO_AllErrorOnQueue() {
        when(request.getBody()).thenReturn(loadCsv("massive-create-view/biz-event-ids.csv"));
        when(queueServiceMock.sendBizEventIdsToQueueInBatch(anyList()))
                .thenAnswer(invocation -> {
                    List<String> ids = invocation.getArgument(0);
                    return new java.util.ArrayList<>(ids); // all failed
                });

        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(request, executionContextMock));

        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());
        ProblemJson body = (ProblemJson) response.getBody();
        assertNotNull(body);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), body.getStatus());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.name(), body.getTitle());

        verify(queueServiceMock, times(1)).sendBizEventIdsToQueueInBatch(anyList());
    }

    private static String loadCsv(String fileName) throws IOException {
        return new String(BizEventToViewMassiveTest.class.getClassLoader().getResourceAsStream(fileName).readAllBytes());
    }
}