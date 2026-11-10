package it.gov.pagopa.bizeventsdatastore;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.annotation.EventHubOutput;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.TimerTrigger;
import it.gov.pagopa.bizeventsdatastore.entity.BizEvent;
import it.gov.pagopa.bizeventsdatastore.service.BizEventDeadLetterRecoveryService;
import it.gov.pagopa.bizeventsdatastore.service.impl.BizEventDeadLetterRecoveryServiceImpl;

import java.util.List;

/**
 * Azure Functions with Timer trigger that recovers the biz-events saved in the dead letter.
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
            @EventHubOutput(
                    name = "PdndBizEventHub",
                    eventHubName = "", // blank because the value is included in the connection string
                    connection = "PDND_EVENTHUB_CONN_STRING")
            OutputBinding<List<BizEvent>> bizPdndEvtMsg,
            final ExecutionContext context) {

        List<BizEvent> createdBizEvents = this.recoveryService.recoverBizEvents(context.getInvocationId());

        if (!createdBizEvents.isEmpty()) {
            bizPdndEvtMsg.setValue(createdBizEvents);
        }
    }
}
