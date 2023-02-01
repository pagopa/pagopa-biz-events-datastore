package it.gov.pagopa.bizeventsdatastore;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;

import it.gov.pagopa.bizeventsdatastore.entity.BizEvent;
import it.gov.pagopa.bizeventsdatastore.model.TransactionDetails;
import it.gov.pagopa.bizeventsdatastore.util.TestUtil;

@ExtendWith(MockitoExtension.class)
class BizEventTimerTriggerTest {

	@Spy
	BizEventTimerTrigger function;

	@Mock
	ExecutionContext context; 

	@Test
	void runOk() throws IOException {

		// precondition
		Logger logger = Logger.getLogger("BizEventTimerTriggerTest-test-logger");
		when(context.getLogger()).thenReturn(logger);

		List<BizEvent> bizEvtMsgList = new ArrayList<>();
		BizEvent bizEventMsg = TestUtil.readModelFromFile("payment-manager/bizEvent.json", BizEvent.class);
		bizEvtMsgList.add (bizEventMsg);
		@SuppressWarnings("unchecked")
		OutputBinding<List<BizEvent>> BizEventToCosmos = (OutputBinding<List<BizEvent>>)mock(OutputBinding.class);

		function.processBizEventScheduledTrigger("timer info", bizEvtMsgList.toArray(new BizEvent[bizEvtMsgList.size()]), BizEventToCosmos, context);
		
		// test assertion -> this line means the call was successful
		assertTrue(true);
	}

}
