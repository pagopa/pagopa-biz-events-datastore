package it.gov.pagopa.bizeventsdatastore;

import com.microsoft.azure.functions.ExecutionContext;
import it.gov.pagopa.bizeventsdatastore.service.BizEventDeadLetterRecoveryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BizEventDeadLetterRecoveryTimerTriggerTest {

    @Mock
    private BizEventDeadLetterRecoveryService recoveryService;
    @Mock
    private ExecutionContext context;

    @Test
    void bizEventsAreRecovered() {
        when(context.getInvocationId()).thenReturn("invocation");

        new BizEventDeadLetterRecoveryTimerTrigger(recoveryService).run("timer", context);

        verify(recoveryService).recoverBizEvents("invocation");
    }

    @Test
    void viewsAreRecovered() {
        when(context.getInvocationId()).thenReturn("invocation");

        new BizEventViewDeadLetterRecoveryTimerTrigger(recoveryService).run("timer", context);

        verify(recoveryService).recoverBizEventViews("invocation");
    }
}
