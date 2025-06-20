package it.gov.pagopa.bizeventsdatastore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.RetryContext;

import it.gov.pagopa.bizeventsdatastore.entity.BizEvent;
import it.gov.pagopa.bizeventsdatastore.entity.enumeration.StatusType;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewCart;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewGeneral;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewUser;
import it.gov.pagopa.bizeventsdatastore.exception.BizEventToViewConstraintViolationException;
import it.gov.pagopa.bizeventsdatastore.model.BizEventToViewResult;
import it.gov.pagopa.bizeventsdatastore.service.BizEventDeadLetterService;
import it.gov.pagopa.bizeventsdatastore.service.BizEventToViewService;
import it.gov.pagopa.bizeventsdatastore.service.impl.BizEventDeadLetterServiceImpl;
import it.gov.pagopa.bizeventsdatastore.util.TestUtil;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

@ExtendWith({MockitoExtension.class, SystemStubsExtension.class})
class BizEventEnrichmentTest {

	@Spy
	@InjectMocks
	BizEventEnrichment function;

	@Mock
	ExecutionContext context;

	@Mock
	RetryContext retryContext;

	@Mock
	BizEventDeadLetterService bizEventDeadLetterService;

	@Mock
	TelemetryClient telemetryClient;

	@Mock
	private BizEventToViewService bizEventToViewService;
	
	

	@SystemStub
	private EnvironmentVariables environment = new EnvironmentVariables("ENABLE_TRANSACTION_LIST_VIEW", "true");


	@Test
	void runOk() throws BizEventToViewConstraintViolationException, IOException  {
		// test precondition
		Logger logger = Logger.getLogger("BizEventEnrichment-test-logger");
		when(context.getLogger()).thenReturn(logger);
		when(context.getRetryContext()).thenReturn(retryContext);
		when(retryContext.getRetrycount()).thenReturn(5);
		BizEventToViewResult viewResult = buildBizEventToViewResult();
		when(bizEventToViewService.mapBizEventToView(any(Logger.class), any())).thenReturn(viewResult);

		List<BizEvent> bizEvtMsgList = new ArrayList<>();
		BizEvent bizEventMsg = TestUtil.readModelFromFile("payment-manager/bizEvent.json", BizEvent.class);
		bizEvtMsgList.add (bizEventMsg);

		Map<String, Object>[] properties = new HashMap[1];
		@SuppressWarnings("unchecked")
		OutputBinding<List<BizEventsViewUser>> bizEventUserView = mock(OutputBinding.class);
		OutputBinding<List<BizEventsViewGeneral>> bizEventGeneralView = mock(OutputBinding.class);
		OutputBinding<List<BizEventsViewCart>> bizEventCartView = mock(OutputBinding.class);

		assertEquals(StatusType.DONE, bizEvtMsgList.get(0).getEventStatus());
		// test execution
		function.processBizEventEnrichment(bizEvtMsgList, properties, bizEventUserView, bizEventGeneralView, bizEventCartView, context);

		// test assertion -> this line means the call was successful
		assertTrue(true);
		verify(bizEventUserView).setValue(argThat(view -> view.size() == 1));
		verify(bizEventGeneralView).setValue(argThat(view -> view.size() == 1));
		verify(bizEventCartView).setValue(argThat(view -> view.size() == 1));
	}

	
	@Test
	void runKo_differentSize() throws BizEventToViewConstraintViolationException, IOException  {
		// test precondition
		Logger logger = Logger.getLogger("BizEventEnrichment-test-logger");
		when(context.getLogger()).thenReturn(logger);
		when(context.getInvocationId()).thenReturn("123");

		List<BizEvent> bizEvtMsgList = new ArrayList<>();
		BizEvent bizEventMsg = TestUtil.readModelFromFile("payment-manager/bizEvent.json", BizEvent.class);
		bizEvtMsgList.add (bizEventMsg);

		Map<String, Object>[] properties = new HashMap[0];
		@SuppressWarnings("unchecked")
		OutputBinding<List<BizEventsViewUser>> bizEventUserView = mock(OutputBinding.class);
		OutputBinding<List<BizEventsViewGeneral>> bizEventGeneralView = mock(OutputBinding.class);
		OutputBinding<List<BizEventsViewCart>> bizEventCartView = mock(OutputBinding.class);

		// test execution
		function.processBizEventEnrichment(bizEvtMsgList, properties, bizEventUserView, bizEventGeneralView, bizEventCartView, context);

		// test assertion -> this line means the call was successful
		assertTrue(true);
		verify(bizEventDeadLetterService).uploadToDeadLetter(anyString(), any(LocalDateTime.class), eq("123"), eq("different-size-error"), 
				eq(bizEvtMsgList), eq("biz-events-views-dead-letter"));
	}

	@Test
	void handleLastRetryTest()  {
		// test precondition
		Logger logger = Logger.getLogger("BizEventEnrichment-test-logger");
		when(context.getLogger()).thenReturn(logger);
		List<BizEvent> bizEvtMsg = new ArrayList<>();
		bizEvtMsg.add (BizEvent.builder().id("123").build());
		
		
		BizEventDeadLetterServiceImpl bizEventDeadLetterServiceImpl = new BizEventDeadLetterServiceImpl();

		bizEventDeadLetterServiceImpl.handleLastRetry(context, "id-test-1", LocalDateTime.now(), "test",
				bizEvtMsg, "dead-letter-container", telemetryClient);

		// test assertion -> this line means the call was successful
		assertTrue(true);
		verify(telemetryClient).trackEvent(anyString());
	}

	@Test
	void runKo_genericException()  {
		// test precondition
		Logger logger = Logger.getLogger("BizEventEnrichment-test-logger");
		when(context.getLogger()).thenReturn(logger);

		// incomplete BizEvent obj --> NullPointerException getPaymentInfo() is null
		List<BizEvent> bizEvtMsg = new ArrayList<>();
		bizEvtMsg.add(BizEvent.builder().id("123").build());

		Map<String, Object>[] properties = new HashMap[1];
		@SuppressWarnings("unchecked")
		OutputBinding<List<BizEventsViewUser>> bizEventUserView = mock(OutputBinding.class);
		OutputBinding<List<BizEventsViewGeneral>> bizEventGeneralView = mock(OutputBinding.class);
		OutputBinding<List<BizEventsViewCart>> bizEventCartView = mock(OutputBinding.class);


		// test execution
		assertThrows(Exception.class, () -> function.processBizEventEnrichment(bizEvtMsg, properties, bizEventUserView, bizEventGeneralView, bizEventCartView, context));
	}
	
	@Test
	void runKo_genericExceptionLastRetry() {
		// test precondition
		Logger logger = Logger.getLogger("BizEventEnrichment-test-logger");
		when(context.getLogger()).thenReturn(logger);
		when(context.getRetryContext()).thenReturn(retryContext);
		// set to max retry value
		when(retryContext.getRetrycount()).thenReturn(10);

		// incomplete BizEvent obj --> NullPointerException getPaymentInfo() is null
		List<BizEvent> bizEvtMsg = new ArrayList<>();
		bizEvtMsg.add(BizEvent.builder().id("123").build());

		Map<String, Object>[] properties = new HashMap[1];
		@SuppressWarnings("unchecked")
		OutputBinding<List<BizEventsViewUser>> bizEventUserView = mock(OutputBinding.class);
		OutputBinding<List<BizEventsViewGeneral>> bizEventGeneralView = mock(OutputBinding.class);
		OutputBinding<List<BizEventsViewCart>> bizEventCartView = mock(OutputBinding.class);


		// test execution
		assertThrows(Exception.class, () -> function.processBizEventEnrichment(bizEvtMsg, properties, bizEventUserView, bizEventGeneralView, bizEventCartView, context));
		// check the borderline case of two handle retries
		verify(bizEventDeadLetterService).handleLastRetry(eq(context), anyString(), any(LocalDateTime.class), eq("last-retry-input"), eq(bizEvtMsg), anyString(), any(TelemetryClient.class));
		verify(bizEventDeadLetterService).handleLastRetry(eq(context), anyString(), any(LocalDateTime.class), eq("exception-output"), anyList(), anyString(), any(TelemetryClient.class));
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
