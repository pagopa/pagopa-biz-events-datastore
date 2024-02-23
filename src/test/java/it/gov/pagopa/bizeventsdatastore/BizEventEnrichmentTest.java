package it.gov.pagopa.bizeventsdatastore;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import it.gov.pagopa.bizeventsdatastore.client.PaymentManagerClient;
import it.gov.pagopa.bizeventsdatastore.entity.BizEvent;
import it.gov.pagopa.bizeventsdatastore.entity.enumeration.StatusType;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewCart;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewGeneral;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewUser;
import it.gov.pagopa.bizeventsdatastore.exception.PDVTokenizerException;
import it.gov.pagopa.bizeventsdatastore.exception.PM4XXException;
import it.gov.pagopa.bizeventsdatastore.exception.PM5XXException;
import it.gov.pagopa.bizeventsdatastore.model.BizEventToViewResult;
import it.gov.pagopa.bizeventsdatastore.model.pm.TransactionDetails;
import it.gov.pagopa.bizeventsdatastore.model.tokenizer.enumeration.ReasonErrorCode;
import it.gov.pagopa.bizeventsdatastore.service.BizEventToViewService;
import it.gov.pagopa.bizeventsdatastore.util.TestUtil;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@ExtendWith(SystemStubsExtension.class)
class BizEventEnrichmentTest {

	private BizEventEnrichment function;
	
	@Mock
    private ExecutionContext context;

    @Mock
    private OutputBinding<List<BizEvent>> bizEventToEH;

    @Mock
    private OutputBinding<List<BizEvent>> bizEventToCosmos;

    @Mock
    private OutputBinding<List<BizEventsViewUser>> userViewToCosmos;

    @Mock
    private OutputBinding<List<BizEventsViewGeneral>> generalViewToCosmos;

    @Mock
    private OutputBinding<List<BizEventsViewCart>> cartViewToCosmos;

    @Captor
    private ArgumentCaptor<List<BizEventsViewUser>> userViewCaptor;

    @Captor
    private ArgumentCaptor<List<BizEventsViewGeneral>> generalViewCaptor;

    @Captor
    private ArgumentCaptor<List<BizEventsViewCart>> cartViewCaptor;

    @Mock
    private BizEventToViewService bizEventToViewService;

    @SystemStub
    private EnvironmentVariables environment = new EnvironmentVariables("ENABLE_TRANSACTION_LIST_VIEW", "true");

    @BeforeEach
    void setUp() {
        function = spy(new BizEventEnrichment(bizEventToViewService));
    }

    @AfterEach
	public void teardown() throws Exception {
	   // reset singleton
	   Field instance = PaymentManagerClient.class.getDeclaredField("instance");
	   instance.setAccessible(true);
	   instance.set(null, null);
	}
	
	@Test
    void runOkWithoutInsertInViews() throws IOException, IllegalArgumentException, PM5XXException, PM4XXException {
		PaymentManagerClient pmClient = mock(PaymentManagerClient.class);

		// set mock instance in the singleton
		BizEventEnrichmentTest.setMock(pmClient);

		// precondition
		TransactionDetails wrapperTD = TestUtil.readModelFromFile("payment-manager/transactionDetails.json", TransactionDetails.class);
		lenient().when(pmClient.getPMEventDetails(anyString(), eq(""))).thenReturn(wrapperTD);
		
        Logger logger = Logger.getLogger("BizEventEnrichment-test-logger");
        when(context.getLogger()).thenReturn(logger);
        
        List<BizEvent> bizEvtMsgList = new ArrayList<>();
        BizEvent bizEventMsg = TestUtil.readModelFromFile("payment-manager/bizEvent.json", BizEvent.class);
        bizEvtMsgList.add (bizEventMsg);

        // test execution
        function.processBizEventEnrichment(bizEvtMsgList, bizEventToEH, bizEventToCosmos, userViewToCosmos, generalViewToCosmos, cartViewToCosmos, context);

        // test assertion -> this line means the call was successful
        assertTrue(true);
    }

	@Test
    @SneakyThrows
    void runOkWithInsertInViews() throws IllegalArgumentException {

		PaymentManagerClient pmClient = mock(PaymentManagerClient.class);

		// set mock instance in the singleton
		BizEventEnrichmentTest.setMock(pmClient);

		// precondition
		TransactionDetails wrapperTD = TestUtil.readModelFromFile("payment-manager/transactionDetails.json", TransactionDetails.class);
		lenient().when(pmClient.getPMEventDetails(anyString(), eq(""))).thenReturn(wrapperTD);


        Logger logger = Logger.getLogger("BizEventEnrichment-test-logger");
        when(context.getLogger()).thenReturn(logger);

        BizEventToViewResult viewResult = buildBizEventToViewResult();
        when(bizEventToViewService.mapBizEventToView(any(Logger.class), any())).thenReturn(viewResult);

        List<BizEvent> bizEvtMsgList = new ArrayList<>();
        BizEvent bizEventMsg = TestUtil.readModelFromFile("payment-manager/bizEvent.json", BizEvent.class);
        bizEvtMsgList.add (bizEventMsg);

        // test execution
        function.processBizEventEnrichment(bizEvtMsgList, bizEventToEH, bizEventToCosmos, userViewToCosmos, generalViewToCosmos, cartViewToCosmos, context);

        // test assertion -> this line means the call was successful
        assertTrue(true);

        verify(userViewToCosmos).setValue(userViewCaptor.capture());
        assertEquals(1, userViewCaptor.getValue().size());
        assertEquals(viewResult.getUserViewList().get(0), userViewCaptor.getValue().get(0));

        verify(generalViewToCosmos).setValue(generalViewCaptor.capture());
        assertEquals(1, generalViewCaptor.getValue().size());
        assertEquals(viewResult.getGeneralView(), generalViewCaptor.getValue().get(0));

        verify(cartViewToCosmos).setValue(cartViewCaptor.capture());
        assertEquals(1, cartViewCaptor.getValue().size());
        assertEquals(viewResult.getCartView(), cartViewCaptor.getValue().get(0));
    }

	@Test
    @SneakyThrows
    void runKOWithErrorInInsertInViews() throws IllegalArgumentException {
		PaymentManagerClient pmClient = mock(PaymentManagerClient.class);

		// set mock instance in the singleton
		BizEventEnrichmentTest.setMock(pmClient);

		// precondition
		TransactionDetails wrapperTD = TestUtil.readModelFromFile("payment-manager/transactionDetails.json", TransactionDetails.class);
		lenient().when(pmClient.getPMEventDetails(anyString(), eq(""))).thenReturn(wrapperTD);


        Logger logger = Logger.getLogger("BizEventEnrichment-test-logger");
        when(context.getLogger()).thenReturn(logger);

        doThrow(new PDVTokenizerException("Error", ReasonErrorCode.ERROR_PDV_IO.getCode()))
                .when(bizEventToViewService).mapBizEventToView(any(Logger.class), any());

        List<BizEvent> bizEvtMsgList = new ArrayList<>();
        BizEvent bizEventMsg = TestUtil.readModelFromFile("payment-manager/bizEvent.json", BizEvent.class);
        bizEvtMsgList.add (bizEventMsg);

        // test execution
        function.processBizEventEnrichment(bizEvtMsgList, bizEventToEH, bizEventToCosmos, userViewToCosmos, generalViewToCosmos, cartViewToCosmos, context);

        // test assertion -> this line means the call was successful
        assertTrue(true);

        verify(userViewToCosmos, never()).setValue(any());
        verify(generalViewToCosmos, never()).setValue(any());
        verify(cartViewToCosmos, never()).setValue(any());
    }

    @Test
    void runOkWithMethod() throws IOException, IllegalArgumentException, PM5XXException, PM4XXException {

        PaymentManagerClient pmClient = mock(PaymentManagerClient.class);

        // set mock instance in the singleton
        BizEventEnrichmentTest.setMock(pmClient);

        // precondition
        TransactionDetails wrapperTD = TestUtil.readModelFromFile("payment-manager/transactionDetails.json", TransactionDetails.class);
        lenient().when(pmClient.getPMEventDetails(anyString(), eq("PPAL"))).thenReturn(wrapperTD);

        Logger logger = Logger.getLogger("BizEventEnrichment-test-logger");
        when(context.getLogger()).thenReturn(logger);

        List<BizEvent> bizEvtMsgList = new ArrayList<>();
        BizEvent bizEventMsg = TestUtil.readModelFromFile("payment-manager/bizEvent.json", BizEvent.class);
        bizEvtMsgList.add (bizEventMsg);
        //Adding these two lines + json file to test temporary solution for transactionId field, can be removed in the future
        BizEvent bizEventMsgWithTD = TestUtil.readModelFromFile("payment-manager/bizEventWithTransactionDetails.json", BizEvent.class);
        bizEvtMsgList.add(bizEventMsgWithTD);
        BizEvent bizEventMsgPM = TestUtil.readModelFromFile("payment-manager/bizEventPM.json", BizEvent.class);
        bizEvtMsgList.add(bizEventMsgPM);

        // test execution
        function.processBizEventEnrichment(bizEvtMsgList, bizEventToEH, bizEventToCosmos, userViewToCosmos, generalViewToCosmos, cartViewToCosmos, context);

        // test assertion -> this line means the call was successful
        assertTrue(true);
    }

	@Test
    void runException() throws IOException, IllegalArgumentException, PM5XXException, PM4XXException {

		PaymentManagerClient pmClient = mock(PaymentManagerClient.class);

		// set mock instance in the singleton
		BizEventEnrichmentTest.setMock(pmClient);

		// precondition
		when(pmClient.getPMEventDetails(anyString(), eq(""))).thenThrow(new RuntimeException("test exception"));

        Logger logger = Logger.getLogger("BizEventEnrichment-test-logger");

        BizEvent bizEventMsg = TestUtil.readModelFromFile("payment-manager/bizEvent.json", BizEvent.class);

        StatusType result = function.enrichBizEvent(bizEventMsg, logger, "1").getEventStatus();

        assertEquals(StatusType.FAILED, result);
    }

	@Test
    void runMaxRetry() throws IOException, IllegalArgumentException {

		PaymentManagerClient pmClient = mock(PaymentManagerClient.class);

		// set mock instance in the singleton
		BizEventEnrichmentTest.setMock(pmClient);

        Logger logger = Logger.getLogger("BizEventEnrichment-test-logger");
        when(context.getLogger()).thenReturn(logger);

        List<BizEvent> bizEvtMsgList = new ArrayList<>();
        BizEvent bizEventMsg = TestUtil.readModelFromFile("payment-manager/bizEvent.json", BizEvent.class);
        // status Retry but with max attemps passed -> No action
        bizEventMsg.setEventStatus(StatusType.RETRY);
        bizEventMsg.setEventRetryEnrichmentCount(4);
        bizEvtMsgList.add (bizEventMsg);

        // test execution
        function.processBizEventEnrichment(bizEvtMsgList, bizEventToEH, bizEventToCosmos, userViewToCosmos, generalViewToCosmos, cartViewToCosmos, context);

        // test assertion -> this line means the call was successful
        assertTrue(true);
    }

	@Test
    void run401() throws IOException, NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
		PaymentManagerClient pmClient = PaymentManagerClient.getInstance();

        // test precondition
        Logger logger = Logger.getLogger("BizEventEnrichment-test-logger");

        BizEvent bizEventMsg = TestUtil.readModelFromFile("payment-manager/bizEvent.json", BizEvent.class);

        WireMockServer wireMockServer = new WireMockServer(8881);
        wireMockServer.start();

        configureFor(wireMockServer.port());
        stubFor(get(urlEqualTo("/payment-manager/events/v1/payment-events/"+bizEventMsg.getIdPaymentManager()))
                .willReturn(aResponse()
                        .withStatus(401)
                        .withHeader("Content-Type", "application/json")));

        Field host = PaymentManagerClient.class.getDeclaredField("paymentManagerHost");
        host.setAccessible(true); // Suppress Java language access checking
        host.set(pmClient, "http://localhost:8881");

        Field retry = PaymentManagerClient.class.getDeclaredField("enableRetry");
        retry.setAccessible(true); // Suppress Java language access checking
        retry.set(pmClient, true);

        StatusType result = function.enrichBizEvent(bizEventMsg, logger, "1").getEventStatus();

        assertEquals(StatusType.FAILED, result);

        wireMockServer.stop();
    }

	@Test
    void run404() throws IOException, NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
		PaymentManagerClient pmClient = PaymentManagerClient.getInstance();

        // test precondition
        Logger logger = Logger.getLogger("BizEventEnrichment-test-logger");

        BizEvent bizEventMsg = TestUtil.readModelFromFile("payment-manager/bizEvent.json", BizEvent.class);

        WireMockServer wireMockServer = new WireMockServer(8881);
        wireMockServer.start();

        configureFor(wireMockServer.port());
        stubFor(get(urlEqualTo("/payment-manager/events/v1/payment-events/"+bizEventMsg.getIdPaymentManager()))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")));

        Field host = PaymentManagerClient.class.getDeclaredField("paymentManagerHost");
        host.setAccessible(true); // Suppress Java language access checking
        host.set(pmClient, "http://localhost:8881");

        Field retry = PaymentManagerClient.class.getDeclaredField("enableRetry");
        retry.setAccessible(true); // Suppress Java language access checking
        retry.set(pmClient, true);

        StatusType result = function.enrichBizEvent(bizEventMsg, logger, "1").getEventStatus();

        assertEquals(StatusType.FAILED, result);

        wireMockServer.stop();
    }

	@Test
    void run500() throws IOException, NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
		PaymentManagerClient pmClient = PaymentManagerClient.getInstance();

        // test precondition
        Logger logger = Logger.getLogger("BizEventEnrichment-test-logger");

        BizEvent bizEventMsg = TestUtil.readModelFromFile("payment-manager/bizEvent.json", BizEvent.class);

        WireMockServer wireMockServer = new WireMockServer(8881);
        wireMockServer.start();

        configureFor(wireMockServer.port());
        stubFor(get(urlEqualTo("/payment-manager/events/v1/payment-events/"+bizEventMsg.getIdPaymentManager()))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")));

        Field host = PaymentManagerClient.class.getDeclaredField("paymentManagerHost");
        host.setAccessible(true); // Suppress Java language access checking
        host.set(pmClient, "http://localhost:8881");

        Field retry = PaymentManagerClient.class.getDeclaredField("enableRetry");
        retry.setAccessible(true); // Suppress Java language access checking
        retry.set(pmClient, true);

        BizEvent result = function.enrichBizEvent(bizEventMsg, logger, "1");

        assertEquals(StatusType.RETRY, result.getEventStatus());
        assertTrue (result.getEventRetryEnrichmentCount()>0);

        wireMockServer.stop();
    }

	private static void setMock(PaymentManagerClient mock) {
	    try {
	        Field instance = PaymentManagerClient.class.getDeclaredField("instance");
	        instance.setAccessible(true);
	        instance.set(instance, mock);
	    } catch (Exception e) {
	        throw new RuntimeException(e);
	    }
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
