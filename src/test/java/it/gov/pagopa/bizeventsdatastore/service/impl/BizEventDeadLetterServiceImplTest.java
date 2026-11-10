package it.gov.pagopa.bizeventsdatastore.service.impl;

import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.azure.functions.ExecutionContext;
import it.gov.pagopa.bizeventsdatastore.entity.BizEvent;
import it.gov.pagopa.bizeventsdatastore.util.BlobStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BizEventDeadLetterServiceImplTest {

    private static final String DEAD_LETTER_FAILED = "[DEAD LETTER FAILED]";

    @Mock
    private ExecutionContext context;
    @Mock
    private TelemetryClient telemetryClient;
    @Mock
    private BlobStorage blobStorage;

    private final BizEventDeadLetterServiceImpl sut = new BizEventDeadLetterServiceImpl();
    private final List<BizEvent> events = List.of(BizEvent.builder().id("1").build());

    @Test
    void handleLastRetryUploadOk() {
        try (MockedStatic<BlobStorage> mocked = Mockito.mockStatic(BlobStorage.class)) {
            mocked.when(BlobStorage::getInstance).thenReturn(blobStorage);
            when(blobStorage.uploadToDeadLetter(any(), any(), any(), any(), any(), any())).thenReturn(true);

            sut.handleLastRetry(context, "id", LocalDateTime.now(), "type", events, "container", telemetryClient);

            verify(telemetryClient, never()).trackEvent(contains(DEAD_LETTER_FAILED));
        }
    }

    @Test
    void handleLastRetryUploadKoIsTracked() {
        try (MockedStatic<BlobStorage> mocked = Mockito.mockStatic(BlobStorage.class)) {
            mocked.when(BlobStorage::getInstance).thenReturn(blobStorage);
            when(blobStorage.uploadToDeadLetter(any(), any(), any(), any(), any(), any())).thenReturn(false);

            sut.handleLastRetry(context, "id", LocalDateTime.now(), "type", events, "container", telemetryClient);

            verify(telemetryClient).trackEvent(contains(DEAD_LETTER_FAILED));
        }
    }

    @Test
    void uploadToDeadLetterExceptionReturnsFalse() {
        try (MockedStatic<BlobStorage> mocked = Mockito.mockStatic(BlobStorage.class)) {
            mocked.when(BlobStorage::getInstance).thenThrow(new IllegalStateException("error"));

            assertFalse(sut.uploadToDeadLetter("id", LocalDateTime.now(), "invocation", "type", events, "container"));
        }
    }
}
