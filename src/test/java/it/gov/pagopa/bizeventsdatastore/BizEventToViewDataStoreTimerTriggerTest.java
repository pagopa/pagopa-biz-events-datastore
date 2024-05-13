package it.gov.pagopa.bizeventsdatastore;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
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

import it.gov.pagopa.bizeventsdatastore.entity.BizEvent;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewCart;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewGeneral;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewUser;
import it.gov.pagopa.bizeventsdatastore.exception.AppException;
import it.gov.pagopa.bizeventsdatastore.model.BizEventToViewResult;
import it.gov.pagopa.bizeventsdatastore.service.BizEventToViewService;
import it.gov.pagopa.bizeventsdatastore.util.TestUtil;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

@ExtendWith(MockitoExtension.class)
@ExtendWith(SystemStubsExtension.class)
class BizEventToViewDataStoreTimerTriggerTest {

	@Spy
	BizEventToViewDataStoreTimerTrigger function;

	@Mock
	ExecutionContext context; 
	
	@Mock
    private BizEventToViewService bizEventToViewService;
	
	@SystemStub
    private EnvironmentVariables environment = new EnvironmentVariables("TIMER_TRIGGER_ENABLE_TRANSACTION_LIST_VIEW", "true");
	
	@BeforeEach
    void setUp() {
        function = spy(new BizEventToViewDataStoreTimerTrigger(bizEventToViewService));
    }

	@Test
	void runOK() throws IOException, AppException {

		// precondition
		Logger logger = Logger.getLogger("BizEventToViewDataStoreTimerTrigger-test-logger");
		when(context.getLogger()).thenReturn(logger);
		
		BizEventToViewResult viewResult = buildBizEventToViewResult();
        when(bizEventToViewService.mapBizEventToView(any(Logger.class), any())).thenReturn(viewResult);

		List<BizEvent> bizEvtMsgList = new ArrayList<>();
		BizEvent bizEventMsg = TestUtil.readModelFromFile("payment-manager/bizEvent.json", BizEvent.class);
		bizEvtMsgList.add (bizEventMsg);
		@SuppressWarnings("unchecked")
		OutputBinding<List<BizEvent>> BizEventToCosmos = (OutputBinding<List<BizEvent>>)mock(OutputBinding.class);
		@SuppressWarnings("unchecked")
		OutputBinding<List<BizEventsViewUser>> BizEventToUserView = (OutputBinding<List<BizEventsViewUser>>)mock(OutputBinding.class);
		@SuppressWarnings("unchecked")
		OutputBinding<List<BizEventsViewGeneral>> BizEventToGeneralView = (OutputBinding<List<BizEventsViewGeneral>>)mock(OutputBinding.class);
		@SuppressWarnings("unchecked")
		OutputBinding<List<BizEventsViewCart>> BizEventToCartView = (OutputBinding<List<BizEventsViewCart>>)mock(OutputBinding.class);

		function.processBizEventScheduledTrigger("timer info", bizEvtMsgList.toArray(new BizEvent[bizEvtMsgList.size()]), BizEventToCosmos, 
				BizEventToUserView, BizEventToGeneralView, BizEventToCartView, context);
		
		// test assertion -> this line means the call was successful
		assertTrue(true);
	}
	
	@Test
	void runKO() throws IOException, AppException {

		// precondition
		Logger logger = Logger.getLogger("BizEventToViewDataStoreTimerTrigger-test-logger");
		when(context.getLogger()).thenReturn(logger);
		
        when(bizEventToViewService.mapBizEventToView(any(Logger.class), any())).thenThrow(AppException.class);

		List<BizEvent> bizEvtMsgList = new ArrayList<>();
		BizEvent bizEventMsg = TestUtil.readModelFromFile("payment-manager/bizEvent.json", BizEvent.class);
		bizEvtMsgList.add (bizEventMsg);
		@SuppressWarnings("unchecked")
		OutputBinding<List<BizEvent>> BizEventToCosmos = (OutputBinding<List<BizEvent>>)mock(OutputBinding.class);
		@SuppressWarnings("unchecked")
		OutputBinding<List<BizEventsViewUser>> BizEventToUserView = (OutputBinding<List<BizEventsViewUser>>)mock(OutputBinding.class);
		@SuppressWarnings("unchecked")
		OutputBinding<List<BizEventsViewGeneral>> BizEventToGeneralView = (OutputBinding<List<BizEventsViewGeneral>>)mock(OutputBinding.class);
		@SuppressWarnings("unchecked")
		OutputBinding<List<BizEventsViewCart>> BizEventToCartView = (OutputBinding<List<BizEventsViewCart>>)mock(OutputBinding.class);

		function.processBizEventScheduledTrigger("timer info", bizEvtMsgList.toArray(new BizEvent[bizEvtMsgList.size()]), BizEventToCosmos, 
				BizEventToUserView, BizEventToGeneralView, BizEventToCartView, context);
		
		// test assertion -> this line means the call was successful
		assertTrue(true);
	}
	
	private BizEventToViewResult buildBizEventToViewResult() {
        return BizEventToViewResult.builder()
                .userViewList(Collections.singletonList(
                        BizEventsViewUser.builder()
                                .transactionId("asdf")
                                .build()))
                .generalView(new BizEventsViewGeneral())
                .cartView(new BizEventsViewCart())
                .build();
    }

}
