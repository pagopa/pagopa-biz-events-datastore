package it.gov.pagopa.bizeventsdatastore;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;

import it.gov.pagopa.bizeventsdatastore.client.PaymentManagerClient;
import it.gov.pagopa.bizeventsdatastore.entity.BizEvent;
import it.gov.pagopa.bizeventsdatastore.entity.enumeration.StatusType;
import it.gov.pagopa.bizeventsdatastore.exception.PM4XXException;
import it.gov.pagopa.bizeventsdatastore.exception.PM5XXException;
import it.gov.pagopa.bizeventsdatastore.model.TransactionDetails;
import it.gov.pagopa.bizeventsdatastore.util.TestUtil;

@ExtendWith(MockitoExtension.class)
class BizEventEnrichmentTest {

	@Spy
	BizEventEnrichment function;
	
	@Mock
    ExecutionContext context; 
	
	TransactionDetails wrapperTD;
	
	
	@AfterEach
	public void teardown() throws Exception {
	   // reset singleton
	   Field instance = PaymentManagerClient.class.getDeclaredField("instance");
	   instance.setAccessible(true);
	   instance.set(null, null);
	}
	
	@Test
    void runOk() throws IOException, IllegalArgumentException, PM5XXException, PM4XXException {
		
		PaymentManagerClient pmClient = mock(PaymentManagerClient.class);
		
		// set mock instance in the singleton
		BizEventEnrichmentTest.setMock(pmClient);
		
		// precondition
		TransactionDetails wrapperTD = TestUtil.readModelFromFile("payment-manager/transactionDetails.json", TransactionDetails.class);
		lenient().when(pmClient.getPMEventDetails(anyString())).thenReturn(wrapperTD);
		
        Logger logger = Logger.getLogger("BizEventEnrichment-test-logger");
        when(context.getLogger()).thenReturn(logger);
        
        List<BizEvent> bizEvtMsgList = new ArrayList<>();
        BizEvent bizEventMsg = TestUtil.readModelFromFile("payment-manager/bizEvent.json", BizEvent.class);
        bizEvtMsgList.add (bizEventMsg);
        @SuppressWarnings("unchecked")
        OutputBinding<List<BizEvent>> BizEventToEH = (OutputBinding<List<BizEvent>>)mock(OutputBinding.class);
        @SuppressWarnings("unchecked")
        OutputBinding<List<BizEvent>> BizEventToCosmos = (OutputBinding<List<BizEvent>>)mock(OutputBinding.class);

        // test execution
        function.processBizEventEnrichment(bizEvtMsgList, BizEventToEH, BizEventToCosmos, context);;

        // test assertion -> this line means the call was successful
        assertTrue(true);
    }
	
	@Test
    void runException() throws IOException, IllegalArgumentException, PM5XXException, PM4XXException {
		
		PaymentManagerClient pmClient = mock(PaymentManagerClient.class);
		
		// set mock instance in the singleton
		BizEventEnrichmentTest.setMock(pmClient);
		
		// precondition
		when(pmClient.getPMEventDetails(anyString())).thenThrow(new RuntimeException("test exception"));
		
        Logger logger = Logger.getLogger("BizEventEnrichment-test-logger");
        
        List<BizEvent> bizEvtMsgList = new ArrayList<>();
        BizEvent bizEventMsg = TestUtil.readModelFromFile("payment-manager/bizEvent.json", BizEvent.class);
        bizEvtMsgList.add (bizEventMsg);
        
        StatusType result = function.enrichBizEvent(bizEventMsg, logger).getEventStatus();
        
        assertEquals(StatusType.FAILED, result);
    }
	
	@Test
    void runMaxRetry() throws IOException, IllegalArgumentException, PM5XXException, PM4XXException {
		
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
        @SuppressWarnings("unchecked")
        OutputBinding<List<BizEvent>> BizEventToEH = (OutputBinding<List<BizEvent>>)mock(OutputBinding.class);
        @SuppressWarnings("unchecked")
        OutputBinding<List<BizEvent>> BizEventToCosmos = (OutputBinding<List<BizEvent>>)mock(OutputBinding.class);

        // test execution
        function.processBizEventEnrichment(bizEvtMsgList, BizEventToEH, BizEventToCosmos, context);;

        // test assertion -> this line means the call was successful
        assertTrue(true);
    }
	
	@Test
    void run401() throws IOException, NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
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

        StatusType result = function.enrichBizEvent(bizEventMsg, logger).getEventStatus();
     
        assertEquals(StatusType.FAILED, result);
        
        wireMockServer.stop();
    }
	
	@Test
    void run404() throws IOException, NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
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

        StatusType result = function.enrichBizEvent(bizEventMsg, logger).getEventStatus();
     
        assertEquals(StatusType.FAILED, result);
        
        wireMockServer.stop();
    }
	
	@Test
    void run500() throws IOException, NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
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

        BizEvent result = function.enrichBizEvent(bizEventMsg, logger);
     
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

}
