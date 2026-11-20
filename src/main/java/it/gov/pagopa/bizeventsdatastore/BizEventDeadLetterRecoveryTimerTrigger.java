package it.gov.pagopa.bizeventsdatastore;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.TimerTrigger;
import it.gov.pagopa.bizeventsdatastore.service.BizEventDeadLetterRecoveryService;
import it.gov.pagopa.bizeventsdatastore.service.impl.BizEventDeadLetterRecoveryServiceImpl;

/**
 * Azure Functions with Timer trigger that recovers the biz-events saved in the dead letter.
 * The recovered biz-events are sent to PDND by the recovery service, that deletes a blob only after the publication.
 */
public class BizEventDeadLetterRecoveryTimerTrigger {

    private final BizEventDeadLetterRecoveryService recoveryService;

    public BizEventDeadLetterRecoveryTimerTrigger() {
        this.recoveryService = new BizEventDeadLetterRecoveryServiceImpl();
    }

    BizEventDeadLetterRecoveryTimerTrigger(BizEventDeadLetterRecoveryService recoveryService) {
        this.recoveryService = recoveryService;
    }

    /**
     * This function will be invoked periodically according to the specified schedule.
     */
    @FunctionName("BizEventDeadLetterRecoveryTimerTrigger")
    public void run(
            @TimerTrigger(
                    name = "timerInfo",
                    schedule = "%BIZ_EVENT_DL_RECOVERY_SCHEDULE%")
            String timerInfo,
            final ExecutionContext context) {

        this.recoveryService.recoverBizEvents(context.getInvocationId());
    }
}
