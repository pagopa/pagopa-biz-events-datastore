package it.gov.pagopa.bizeventsdatastore;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
import it.gov.pagopa.bizeventsdatastore.exception.BizEventToViewConstraintViolationException;
import it.gov.pagopa.bizeventsdatastore.model.BizEventToViewResult;
import it.gov.pagopa.bizeventsdatastore.service.BizEventToViewService;
import it.gov.pagopa.bizeventsdatastore.util.TestUtil;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

@ExtendWith({MockitoExtension.class, SystemStubsExtension.class})
class BizEventToViewDataStoreTimerTriggerTest {

	@Spy
	BizEventToViewDataStoreTimerTrigger function;

	@Mock
	ExecutionContext context; 
	
	@Mock
    private BizEventToViewService bizEventToViewService;

	@Mock
	private OutputBinding<List<BizEvent>> bizEventToCosmosOutputBinding;

	@Mock
	private OutputBinding<List<BizEventsViewUser>> viewUserOutputBinding;

	@Mock
	private OutputBinding<List<BizEventsViewGeneral>> viewGeneralOutputBinding;

	@Mock
	private OutputBinding<List<BizEventsViewCart>> viewCartOutputBinding;
	
	@SystemStub
    private EnvironmentVariables environment = new EnvironmentVariables("TIMER_TRIGGER_ENABLE_TRANSACTION_LIST_VIEW", "true");
	
	@BeforeEach
    void setUp() {
        function = spy(new BizEventToViewDataStoreTimerTrigger(bizEventToViewService));
    }

	@Test
	void runOK() throws IOException, BizEventToViewConstraintViolationException {

		// precondition
		Logger logger = Logger.getLogger("BizEventToViewDataStoreTimerTrigger-test-logger");
		when(context.getLogger()).thenReturn(logger);
		
		BizEventToViewResult viewResult = buildBizEventToViewResult();
        when(bizEventToViewService.mapBizEventToView(any(Logger.class), any())).thenReturn(viewResult);

		List<BizEvent> bizEvtMsgList = new ArrayList<>();
		BizEvent bizEventMsg = TestUtil.readModelFromFile("payment-manager/bizEvent.json", BizEvent.class);
		bizEvtMsgList.add (bizEventMsg);

		function.processBizEventScheduledTrigger("timer info", bizEvtMsgList.toArray(new BizEvent[bizEvtMsgList.size()]), bizEventToCosmosOutputBinding,
				viewUserOutputBinding, viewGeneralOutputBinding, viewCartOutputBinding, context);
		
		// test assertion -> this line means the call was successful
		assertTrue(true);
	}
	
	@Test
	void runKO() throws IOException, BizEventToViewConstraintViolationException {

		// precondition
		Logger logger = Logger.getLogger("BizEventToViewDataStoreTimerTrigger-test-logger");
		when(context.getLogger()).thenReturn(logger);
		
        when(bizEventToViewService.mapBizEventToView(any(Logger.class), any())).thenThrow(BizEventToViewConstraintViolationException.class);

		List<BizEvent> bizEvtMsgList = new ArrayList<>();
		BizEvent bizEventMsg = TestUtil.readModelFromFile("payment-manager/bizEvent.json", BizEvent.class);
		bizEvtMsgList.add (bizEventMsg);

		function.processBizEventScheduledTrigger("timer info", bizEvtMsgList.toArray(new BizEvent[bizEvtMsgList.size()]), bizEventToCosmosOutputBinding,
				viewUserOutputBinding, viewGeneralOutputBinding, viewCartOutputBinding, context);
		
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
