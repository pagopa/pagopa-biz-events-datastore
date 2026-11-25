package it.gov.pagopa.bizeventsdatastore;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.RetryContext;

import it.gov.pagopa.bizeventsdatastore.entity.BizEvent;
import it.gov.pagopa.bizeventsdatastore.entity.enumeration.StatusType;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewCart;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewGeneral;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewUser;
import it.gov.pagopa.bizeventsdatastore.exception.BizEventToViewConstraintViolationException;
import it.gov.pagopa.bizeventsdatastore.model.BizEventToViewResult;
import it.gov.pagopa.bizeventsdatastore.service.BizEventCosmosService;
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

	@Mock
	private BizEventCosmosService bizEventCosmosService;
	
	@SystemStub
	private EnvironmentVariables environment = new EnvironmentVariables("ENABLE_TRANSACTION_LIST_VIEW", "true");


	@Test
	void runOk() throws BizEventToViewConstraintViolationException, IOException  {
		when(context.getRetryContext()).thenReturn(retryContext);
		when(retryContext.getRetrycount()).thenReturn(5);
		BizEventToViewResult viewResult = buildBizEventToViewResult();
		when(bizEventToViewService.mapBizEventToView(any())).thenReturn(viewResult);

		List<BizEvent> bizEvtMsgList = new ArrayList<>();
		BizEvent bizEventMsg = TestUtil.readModelFromFile("payment-manager/bizEvent.json", BizEvent.class);
		bizEvtMsgList.add (bizEventMsg);

		assertEquals(StatusType.DONE, bizEvtMsgList.get(0).getEventStatus());
		// test execution
		function.processBizEventEnrichment(bizEvtMsgList, context);

		// test assertion -> this line means the call was successful
		assertTrue(true);
		verify(bizEventCosmosService).saveBizEventViewUsersPreservingVisibility(argThat(view -> view.size() == 1));
		verify(bizEventCosmosService, times(1)).upsertBizEventViewGeneral(any());
		verify(bizEventCosmosService, times(1)).upsertBizEventViewCart(any());
	}


	@Test
	void runOk_bizEventFromChangeFeedIsNotModified() throws BizEventToViewConstraintViolationException, IOException  {
		BizEventToViewResult viewResult = buildBizEventToViewResult();
		when(bizEventToViewService.mapBizEventToView(any())).thenReturn(viewResult);

		BizEvent bizEventMsg = TestUtil.readModelFromFile("payment-manager/bizEvent.json", BizEvent.class);
		Long timestamp = 1700000000000L;
		Map<String, Object> properties = Map.of("serviceIdentifier", "NDP003PROD");
		String iur = bizEventMsg.getDebtorPosition().getIur();
		bizEventMsg.setTimestamp(timestamp);
		bizEventMsg.setProperties(properties);
		List<BizEvent> bizEvtMsgList = List.of(bizEventMsg);

		// test execution
		function.processBizEventEnrichment(bizEvtMsgList, context);

		// test assertion -> the views are generated from the persisted biz-event without re-enriching it
		verify(bizEventToViewService).mapBizEventToView(argThat(be ->
				timestamp.equals(be.getTimestamp())
				&& properties.equals(be.getProperties())
				&& Objects.equals(iur, be.getDebtorPosition().getIur())));
		verify(bizEventCosmosService).saveBizEventViewUsersPreservingVisibility(argThat(view -> view.size() == 1));
		verify(bizEventCosmosService, times(1)).upsertBizEventViewGeneral(any());
		verify(bizEventCosmosService, times(1)).upsertBizEventViewCart(any());
	}

	@Test
	void runOk_onlyDoneAndIngestedBizEventsGenerateViews() throws BizEventToViewConstraintViolationException, IOException {
		BizEventToViewResult viewResult = buildBizEventToViewResult();
		when(bizEventToViewService.mapBizEventToView(any())).thenReturn(viewResult);

		List<BizEvent> bizEvtMsgList = new ArrayList<>();
		for (StatusType status : StatusType.values()) {
			BizEvent bizEventMsg = TestUtil.readModelFromFile("payment-manager/bizEvent.json", BizEvent.class);
			bizEventMsg.setId("id-" + status);
			bizEventMsg.setEventStatus(status);
			bizEvtMsgList.add(bizEventMsg);
		}

		// test execution
		function.processBizEventEnrichment(bizEvtMsgList, context);

		// test assertion -> NA, RETRY and FAILED biz-events are skipped
		ArgumentCaptor<BizEvent> mappedCaptor = ArgumentCaptor.forClass(BizEvent.class);
		verify(bizEventToViewService, times(2)).mapBizEventToView(mappedCaptor.capture());
		Set<StatusType> mappedStatuses = mappedCaptor.getAllValues().stream()
				.map(BizEvent::getEventStatus)
				.collect(Collectors.toSet());
		assertEquals(Set.of(StatusType.DONE, StatusType.INGESTED), mappedStatuses);
		verify(bizEventCosmosService).saveBizEventViewUsersPreservingVisibility(argThat(view -> view.size() == 2));
		verify(bizEventCosmosService, times(2)).upsertBizEventViewGeneral(any());
		verify(bizEventCosmosService, times(2)).upsertBizEventViewCart(any());
	}

	@Test
	void runOk_noEligibleBizEventsDoesNotWriteViews() throws BizEventToViewConstraintViolationException, IOException {
		BizEvent bizEventMsg = TestUtil.readModelFromFile("payment-manager/bizEvent.json", BizEvent.class);
		bizEventMsg.setEventStatus(StatusType.RETRY);
		List<BizEvent> bizEvtMsgList = List.of(bizEventMsg);

		// test execution
		function.processBizEventEnrichment(bizEvtMsgList, context);

		// test assertion -> nothing is mapped nor written
		verify(bizEventToViewService, never()).mapBizEventToView(any());
		verify(bizEventCosmosService, never()).saveBizEventViewUsersPreservingVisibility(anyList());
		verify(bizEventCosmosService, never()).upsertBizEventViewGeneral(any());
		verify(bizEventCosmosService, never()).upsertBizEventViewCart(any());
	}

	@Test
	void runKo_saveViewUserException() throws BizEventToViewConstraintViolationException, IOException {
		BizEventToViewResult viewResult = buildBizEventToViewResult();
		when(bizEventToViewService.mapBizEventToView(any())).thenReturn(viewResult);
		doThrow(new RuntimeException("cosmos error")).when(bizEventCosmosService).saveBizEventViewUsersPreservingVisibility(anyList());

		List<BizEvent> bizEvtMsgList = List.of(TestUtil.readModelFromFile("payment-manager/bizEvent.json", BizEvent.class));

		// test execution -> the error is rethrown to trigger the retry policy
		assertThrows(RuntimeException.class, () -> function.processBizEventEnrichment(bizEvtMsgList, context));
	}

	@Test
	void runOk_userViewsAreSavedAfterTheGeneralAndCartViews() throws BizEventToViewConstraintViolationException, IOException {
		BizEventToViewResult viewResult = buildBizEventToViewResult();
		when(bizEventToViewService.mapBizEventToView(any())).thenReturn(viewResult);
		List<BizEvent> bizEvtMsgList = List.of(TestUtil.readModelFromFile("payment-manager/bizEvent.json", BizEvent.class));

		function.processBizEventEnrichment(bizEvtMsgList, context);

		InOrder inOrder = inOrder(bizEventCosmosService);
		inOrder.verify(bizEventCosmosService).upsertBizEventViewGeneral(viewResult.getGeneralView());
		inOrder.verify(bizEventCosmosService).upsertBizEventViewCart(viewResult.getCartView());
		inOrder.verify(bizEventCosmosService).saveBizEventViewUsersPreservingVisibility(viewResult.getUserViewList());
	}

	@Test
	void runKo_saveGeneralViewExceptionDoesNotSaveTheUserViews() throws BizEventToViewConstraintViolationException, IOException {
		BizEventToViewResult viewResult = buildBizEventToViewResult();
		when(bizEventToViewService.mapBizEventToView(any())).thenReturn(viewResult);
		doThrow(new RuntimeException("cosmos error")).when(bizEventCosmosService).upsertBizEventViewGeneral(any());
		List<BizEvent> bizEvtMsgList = List.of(TestUtil.readModelFromFile("payment-manager/bizEvent.json", BizEvent.class));

		// test execution -> the error is rethrown to trigger the retry policy
		assertThrows(RuntimeException.class, () -> function.processBizEventEnrichment(bizEvtMsgList, context));

		verify(bizEventCosmosService, never()).saveBizEventViewUsersPreservingVisibility(anyList());
	}

	@Test
	void handleLastRetryTest()  {
		List<BizEvent> bizEvtMsg = new ArrayList<>();
		bizEvtMsg.add (BizEvent.builder().id("123").build());
		
		
		BizEventDeadLetterServiceImpl bizEventDeadLetterServiceImpl = new BizEventDeadLetterServiceImpl();

		bizEventDeadLetterServiceImpl.handleLastRetry(context, "id-test-1", LocalDateTime.now(), "test",
				bizEvtMsg, "dead-letter-container", telemetryClient);

		// test assertion -> this line means the call was successful
		assertTrue(true);
		verify(telemetryClient).trackEvent(contains("[LAST RETRY]"));
	}

	@Test
	void runKo_genericException() throws BizEventToViewConstraintViolationException  {
		when(bizEventToViewService.mapBizEventToView(any())).thenThrow(new NullPointerException());
		List<BizEvent> bizEvtMsg = new ArrayList<>();
		bizEvtMsg.add(BizEvent.builder().id("123").build());


		// test execution
		assertThrows(Exception.class, () -> function.processBizEventEnrichment(bizEvtMsg, context));
	}
	
	@Test
	void runKo_genericExceptionLastRetry() throws BizEventToViewConstraintViolationException {
		// test precondition
		when(context.getRetryContext()).thenReturn(retryContext);
		// set to max retry value
		when(retryContext.getRetrycount()).thenReturn(10);
		when(bizEventToViewService.mapBizEventToView(any())).thenThrow(new NullPointerException());

		List<BizEvent> bizEvtMsg = new ArrayList<>();
		bizEvtMsg.add(BizEvent.builder().id("123").eventStatus(StatusType.DONE).build());


		// test execution
		assertThrows(Exception.class, () -> function.processBizEventEnrichment(bizEvtMsg, context));
		verify(bizEventDeadLetterService).handleLastRetry(eq(context), anyString(), any(LocalDateTime.class), eq("last-retry-input"), eq(bizEvtMsg), anyString(), any(TelemetryClient.class));
		verify(bizEventDeadLetterService, never()).handleLastRetry(eq(context), anyString(), any(LocalDateTime.class), eq("exception-output"), anyList(), anyString(), any(TelemetryClient.class));
	}

	@Test
	void runKo_lastRetrySavesOnlyTheEventsThatGenerateViews() throws BizEventToViewConstraintViolationException {
		when(context.getRetryContext()).thenReturn(retryContext);
		when(retryContext.getRetrycount()).thenReturn(10);
		when(bizEventToViewService.mapBizEventToView(any())).thenThrow(new NullPointerException());

		BizEvent done = BizEvent.builder().id("done").eventStatus(StatusType.DONE).build();
		BizEvent ingested = BizEvent.builder().id("ingested").eventStatus(StatusType.INGESTED).build();
		BizEvent na = BizEvent.builder().id("na").eventStatus(StatusType.NA).build();
		BizEvent retry = BizEvent.builder().id("retry").eventStatus(StatusType.RETRY).build();
		BizEvent failed = BizEvent.builder().id("failed").eventStatus(StatusType.FAILED).build();

		assertThrows(Exception.class, () -> function.processBizEventEnrichment(List.of(done, na, ingested, retry, failed), context));

		verify(bizEventDeadLetterService).handleLastRetry(eq(context), anyString(), any(LocalDateTime.class), eq("last-retry-input"),
				eq(List.of(done, ingested)), anyString(), any(TelemetryClient.class));
	}

	@Test
	void runOk_lastRetryWithoutEventsThatGenerateViewsDoesNotWriteTheDeadLetter() {
		when(context.getRetryContext()).thenReturn(retryContext);
		when(retryContext.getRetrycount()).thenReturn(10);

		BizEvent na = BizEvent.builder().id("na").eventStatus(StatusType.NA).build();

		assertDoesNotThrow(() -> function.processBizEventEnrichment(List.of(na), context));

		verify(bizEventDeadLetterService, never()).handleLastRetry(any(), anyString(), any(LocalDateTime.class), anyString(), anyList(),
				anyString(), any(TelemetryClient.class));
	}

	@Test
	void runOk_invalidEventDoesNotBlockTheBatch() throws BizEventToViewConstraintViolationException {
		BizEvent invalid = BizEvent.builder().id("invalid").build();
		BizEvent valid = BizEvent.builder().id("valid").build();
		BizEventToViewResult views = buildBizEventToViewResult();
		when(bizEventToViewService.mapBizEventToView(invalid)).thenThrow(new BizEventToViewConstraintViolationException("invalid", List.of()));
		when(bizEventToViewService.mapBizEventToView(valid)).thenReturn(views);
		when(bizEventDeadLetterService.uploadToDeadLetter(anyString(), any(LocalDateTime.class), any(), eq("validation-error"), eq(List.of(invalid)), anyString()))
				.thenReturn(true);

		assertDoesNotThrow(() -> function.processBizEventEnrichment(List.of(invalid, valid), context));

		verify(bizEventCosmosService).saveBizEventViewUsersPreservingVisibility(views.getUserViewList());
		verify(bizEventCosmosService).upsertBizEventViewGeneral(views.getGeneralView());
		verify(bizEventCosmosService).upsertBizEventViewCart(views.getCartView());
	}

	@Test
	void runKo_invalidEventNotSavedInDeadLetterIsRetried() throws BizEventToViewConstraintViolationException {
		BizEvent invalid = BizEvent.builder().id("invalid").build();
		when(bizEventToViewService.mapBizEventToView(invalid)).thenThrow(new BizEventToViewConstraintViolationException("invalid", List.of()));
		when(bizEventDeadLetterService.uploadToDeadLetter(anyString(), any(LocalDateTime.class), any(), eq("validation-error"), eq(List.of(invalid)), anyString()))
				.thenReturn(false);

		assertThrows(BizEventToViewConstraintViolationException.class,
				() -> function.processBizEventEnrichment(List.of(invalid), context));
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
