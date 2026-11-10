package it.gov.pagopa.bizeventsdatastore;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import it.gov.pagopa.bizeventsdatastore.entity.BizEvent;
import it.gov.pagopa.bizeventsdatastore.service.BizEventDeadLetterRecoveryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BizEventDeadLetterRecoveryTimerTriggerTest {

    @Mock
    private BizEventDeadLetterRecoveryService recoveryService;
    @Mock
    private OutputBinding<List<BizEvent>> pdndOutput;
    @Mock
    private ExecutionContext context;

    @Test
    void createdBizEventsAreSentToPdnd() {
        List<BizEvent> created = List.of(BizEvent.builder().id("1").build());
        when(context.getInvocationId()).thenReturn("invocation");
        when(recoveryService.recoverBizEvents("invocation")).thenReturn(created);

        new BizEventDeadLetterRecoveryTimerTrigger(recoveryService).run("timer", pdndOutput, context);

        verify(pdndOutput).setValue(created);
    }

    @Test
    void nothingIsSentToPdndWhenNoBizEventIsCreated() {
        when(recoveryService.recoverBizEvents(any())).thenReturn(List.of());

        new BizEventDeadLetterRecoveryTimerTrigger(recoveryService).run("timer", pdndOutput, context);

        verify(pdndOutput, never()).setValue(any());
    }

    @Test
    void viewsAreRecovered() {
        when(context.getInvocationId()).thenReturn("invocation");

        new BizEventViewDeadLetterRecoveryTimerTrigger(recoveryService).run("timer", context);

        verify(recoveryService).recoverBizEventViews("invocation");
    }
}
