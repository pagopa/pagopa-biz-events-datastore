package it.gov.pagopa.bizeventsdatastore;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;

import it.gov.pagopa.bizeventsdatastore.entity.enumeration.ServiceIdentifierType;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewGeneral;
import it.gov.pagopa.bizeventsdatastore.exception.AppException;
import it.gov.pagopa.bizeventsdatastore.util.TestUtil;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

@ExtendWith(MockitoExtension.class)
@ExtendWith(SystemStubsExtension.class)
class BizEventViewDataStoreUpdateTimerTriggerTest {

	@Spy
	BizEventViewDataStoreUpdateTimerTrigger function;

	@Mock
	ExecutionContext context; 
	
	@SystemStub
    private EnvironmentVariables environment = new EnvironmentVariables("UPDATE_VIEW_TIMER_TRIGGER_ENABLE", "true");
	
	@BeforeEach
    void setUp() {
    }

	@SuppressWarnings("unchecked")
	@Test
	void runOK() throws IOException, AppException {

		// precondition
		Logger logger = Logger.getLogger("BizEventViewDataStoreUpdateTimerTrigger-test-logger");
		when(context.getLogger()).thenReturn(logger);

		List<BizEventsViewGeneral> bizEvtViewGeneralMsgList = new ArrayList<>();
		BizEventsViewGeneral bizEventsViewGeneralMsg = TestUtil.readModelFromFile("biz-events-view/bizEventViewGeneral.json", BizEventsViewGeneral.class);
		bizEvtViewGeneralMsgList.add (bizEventsViewGeneralMsg);
		
		List<BizEventsViewGeneral> itemsToUpdate = new ArrayList<>();
		bizEventsViewGeneralMsg.setOrigin(ServiceIdentifierType.NDP003PROD);
		itemsToUpdate.add(bizEventsViewGeneralMsg);
		
		OutputBinding<List<BizEventsViewGeneral>> bizEventToGeneralView = mock(OutputBinding.class);

		function.processBizEventViewScheduledTrigger("timer info", bizEvtViewGeneralMsgList.toArray(new BizEventsViewGeneral[bizEvtViewGeneralMsgList.size()]), bizEventToGeneralView, context);
		
		// test assertion -> this line means the call was successful
		assertTrue(true);	
		verify(function, times(1)).bizEventsViewUpdate(logger, itemsToUpdate, bizEventsViewGeneralMsg);
	}
}
