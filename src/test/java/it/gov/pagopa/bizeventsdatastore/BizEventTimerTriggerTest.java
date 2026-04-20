package it.gov.pagopa.bizeventsdatastore;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;

import it.gov.pagopa.bizeventsdatastore.entity.BizEvent;
import it.gov.pagopa.bizeventsdatastore.util.TestUtil;

@ExtendWith(MockitoExtension.class)
class BizEventTimerTriggerTest {

	@Spy
	BizEventTimerTrigger function;

	@Mock
	ExecutionContext context; 

	@Mock
	OutputBinding<List<BizEvent>> outputBindingMock;

	@Test
	void runOk() throws IOException {
		List<BizEvent> bizEvtMsgList = new ArrayList<>();
		BizEvent bizEventMsg = TestUtil.readModelFromFile("payment-manager/bizEvent.json", BizEvent.class);
		bizEvtMsgList.add (bizEventMsg);

		function.processBizEventScheduledTrigger("timer info", bizEvtMsgList.toArray(new BizEvent[bizEvtMsgList.size()]), outputBindingMock, context);
		
		// test assertion -> this line means the call was successful
		assertTrue(true);
	}
}
