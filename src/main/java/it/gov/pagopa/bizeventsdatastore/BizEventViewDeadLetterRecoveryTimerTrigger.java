package it.gov.pagopa.bizeventsdatastore;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.TimerTrigger;
import it.gov.pagopa.bizeventsdatastore.service.BizEventDeadLetterRecoveryService;
import it.gov.pagopa.bizeventsdatastore.service.impl.BizEventDeadLetterRecoveryServiceImpl;

/**
 * Azure Functions with Timer trigger that recovers the biz-event views saved in the dead letter.
 */
public class BizEventViewDeadLetterRecoveryTimerTrigger {

    private final BizEventDeadLetterRecoveryService recoveryService;

    public BizEventViewDeadLetterRecoveryTimerTrigger() {
        this.recoveryService = new BizEventDeadLetterRecoveryServiceImpl();
    }

    BizEventViewDeadLetterRecoveryTimerTrigger(BizEventDeadLetterRecoveryService recoveryService) {
        this.recoveryService = recoveryService;
    }

    /**
     * This function will be invoked periodically according to the specified schedule.
     */
    @FunctionName("BizEventViewDeadLetterRecoveryTimerTrigger")
    public void run(
            @TimerTrigger(
                    name = "timerInfo",
                    schedule = "%BIZ_EVENT_VIEW_DL_RECOVERY_SCHEDULE%")
            String timerInfo,
            final ExecutionContext context) {

        this.recoveryService.recoverBizEventViews(context.getInvocationId());
    }
}
