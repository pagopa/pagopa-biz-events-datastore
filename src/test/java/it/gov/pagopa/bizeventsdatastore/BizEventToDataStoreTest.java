package it.gov.pagopa.bizeventsdatastore;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.microsoft.applicationinsights.TelemetryClient;
import it.gov.pagopa.bizeventsdatastore.entity.enumeration.StatusType;
import it.gov.pagopa.bizeventsdatastore.exception.AppException;
import it.gov.pagopa.bizeventsdatastore.service.BizEventDeadLetterService;
import it.gov.pagopa.bizeventsdatastore.service.RedisCacheService;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.RetryContext;

import it.gov.pagopa.bizeventsdatastore.entity.BizEvent;
import it.gov.pagopa.bizeventsdatastore.entity.DebtorPosition;
import it.gov.pagopa.bizeventsdatastore.entity.InfoECommerce;
import it.gov.pagopa.bizeventsdatastore.entity.PaymentInfo;
import it.gov.pagopa.bizeventsdatastore.entity.TransactionDetails;
import it.gov.pagopa.bizeventsdatastore.entity.Transfer;
import it.gov.pagopa.bizeventsdatastore.util.TestUtil;

@ExtendWith(MockitoExtension.class)
class BizEventToDataStoreTest {

    @Spy
    @InjectMocks
    BizEventToDataStore function;

    @Mock
    ExecutionContext context;
    
    @Mock
    RetryContext retryContext;

    @Mock
    RedisCacheService redisCacheService;

    @Mock
    BizEventDeadLetterService bizEventDeadLetterService;

    @Mock
    TelemetryClient telemetryClient;


    @Test
    void runOk()  {
        // test precondition
        Logger logger = Logger.getLogger("BizEventToDataStore-test-logger");
        when(context.getLogger()).thenReturn(logger);
        when(context.getRetryContext()).thenReturn(retryContext);
        when(retryContext.getRetrycount()).thenReturn(5);
        
        PaymentInfo pi = PaymentInfo.builder().IUR("iur").build();
        DebtorPosition dp = DebtorPosition.builder().iuv("iuv").build();
        InfoECommerce iec = InfoECommerce.builder()
        		.brand("VISA")
        		.brandLogo("https://dev.checkout.pagopa.it/assets/creditcard/carta_visa.png")
        		.clientId("CHECKOUT")
        		.paymentMethodName("Carte")
        		.type("CP")
        		.build();
        TransactionDetails td = TransactionDetails.builder().info(iec).build();
        
        List<BizEvent> bizEvtMsg = new ArrayList<>();
        bizEvtMsg.add (BizEvent.builder().id("123").paymentInfo(pi).debtorPosition(dp).transactionDetails(td).build());
        
        Map<String, Object>[] properties = new HashMap[1];
        @SuppressWarnings("unchecked")
        OutputBinding<List<BizEvent>> document = (OutputBinding<List<BizEvent>>)mock(OutputBinding.class);
        
        doReturn(null).when(redisCacheService).findByBizEventId(anyString(), anyString(), any(Logger.class));
        doReturn("OK").when(redisCacheService).saveBizEventId(anyString(), anyString(), any(Logger.class));

        assertEquals(StatusType.NA, bizEvtMsg.get(0).getEventStatus());
        // test execution
        function.processBizEvent(bizEvtMsg, properties, document, context);

        // test assertion -> this line means the call was successful
        assertTrue(true);
        assertEquals(StatusType.DONE, bizEvtMsg.get(0).getEventStatus());
    }

    @Test
    void runECommerceOk() throws IOException, AppException {
        // test precondition
        Logger logger = Logger.getLogger("BizEventToDataStore-test-logger");
        when(context.getLogger()).thenReturn(logger);

        List<BizEvent> bizEvtMsgList = new ArrayList<>();
        BizEvent bizEventMsg = TestUtil.readModelFromFile("payment-manager/bizEventECommerce.json", BizEvent.class);
        bizEvtMsgList.add (bizEventMsg);

        Map<String, Object>[] properties = new HashMap[1];
        @SuppressWarnings("unchecked")
        OutputBinding<List<BizEvent>> document = (OutputBinding<List<BizEvent>>)mock(OutputBinding.class);

        doReturn(null).when(redisCacheService).findByBizEventId(anyString(),anyString(), any(Logger.class));
        doReturn("OK").when(redisCacheService).saveBizEventId(anyString(),anyString(), any(Logger.class));

        // test execution
        function.processBizEvent(bizEvtMsgList, properties, document, context);

        // test assertion -> this line means the call was successful
        assertTrue(true);
    }

    @Test
    void runModelType1Ok() throws IOException, AppException {
        // test precondition
        Logger logger = Logger.getLogger("BizEventToDataStore-test-logger");
        when(context.getLogger()).thenReturn(logger);

        List<BizEvent> bizEvtMsgList = new ArrayList<>();
        BizEvent bizEventMsg = TestUtil.readModelFromFile("payment-manager/bizEventModelType1.json", BizEvent.class);


        assertThat(bizEventMsg.getTransferList(), hasItem(
        		Matchers.<Transfer>hasProperty("IUR", is("iur1111111111"))
        ));
        assertThat(bizEventMsg.getTransferList(), hasItem(
        		Matchers.<Transfer>hasProperty("IUR", is("iur2222222222"))
        ));

        bizEvtMsgList.add (bizEventMsg);

        Map<String, Object>[] properties = new HashMap[1];
        @SuppressWarnings("unchecked")
        OutputBinding<List<BizEvent>> document = (OutputBinding<List<BizEvent>>)mock(OutputBinding.class);

        doReturn(null).when(redisCacheService).findByBizEventId(anyString(),anyString(), any(Logger.class));
        doReturn("OK").when(redisCacheService).saveBizEventId(anyString(),anyString(), any(Logger.class));

        // test execution
        function.processBizEvent(bizEvtMsgList, properties, document, context);

        // test assertion -> this line means the call was successful
        assertTrue(true);
    }

    @Test
    void runKo_differentSize()  {
        // test precondition
        Logger logger = Logger.getLogger("BizEventToDataStore-test-logger");
        when(context.getLogger()).thenReturn(logger);

        List<BizEvent> bizEvtMsg = new ArrayList<>();
        bizEvtMsg.add (new BizEvent());

        Map<String, Object>[] properties = new HashMap[0];
        @SuppressWarnings("unchecked")
        OutputBinding<List<BizEvent>> document = (OutputBinding<List<BizEvent>>)mock(OutputBinding.class);

        // test execution
        function.processBizEvent(bizEvtMsg, properties, document, context);

        // test assertion -> this line means the call was successful
        assertTrue(true);
    }

    @Test
    void runBizEventAlreadyInCache()  {
        // test precondition
        Logger logger = Logger.getLogger("BizEventToDataStore-test-logger");
        when(context.getLogger()).thenReturn(logger);

        List<BizEvent> bizEvtMsg = new ArrayList<>();
        bizEvtMsg.add (BizEvent.builder().id("123").build());

        Map<String, Object>[] properties = new HashMap[1];
        @SuppressWarnings("unchecked")
        OutputBinding<List<BizEvent>> document = (OutputBinding<List<BizEvent>>)mock(OutputBinding.class);

        doReturn("123").when(redisCacheService).findByBizEventId(anyString(),anyString(), any(Logger.class));

        // test execution
        function.processBizEvent(bizEvtMsg, properties, document, context);

        // test assertion -> this line means the call was successful
        assertTrue(true);
    }

    @Test
    void runBizEventRedisException()  {
        // test precondition
        Logger logger = Logger.getLogger("BizEventToDataStore-test-logger");
        when(context.getLogger()).thenReturn(logger);

        PaymentInfo pi = PaymentInfo.builder().IUR("iur").build();
        DebtorPosition dp = DebtorPosition.builder().iuv("iuv").build();

        List<BizEvent> bizEvtMsg = new ArrayList<>();
        bizEvtMsg.add (BizEvent.builder().id("123").paymentInfo(pi).debtorPosition(dp).build());

        Map<String, Object>[] properties = new HashMap[1];
        @SuppressWarnings("unchecked")
        OutputBinding<List<BizEvent>> document = (OutputBinding<List<BizEvent>>)mock(OutputBinding.class);

        // test execution
        function.processBizEvent(bizEvtMsg, properties, document, context);

        // test assertion -> this line means the call was successful
        assertTrue(true);
    }

    @Test
    void handleLastRetryTest()  {
        // test precondition

        List<BizEvent> bizEvtMsg = new ArrayList<>();
        bizEvtMsg.add (BizEvent.builder().id("123").build());

        bizEventDeadLetterService.handleLastRetry(context, "id-test-1", LocalDateTime.now(), "test",
                bizEvtMsg, "dead-letter-container", telemetryClient);

        // test assertion -> this line means the call was successful
        assertTrue(true);
    }

    @Test
    void runKo_genericException()  {
        // test precondition
        Logger logger = Logger.getLogger("BizEventToDataStore-test-logger");
        when(context.getLogger()).thenReturn(logger);

        List<BizEvent> bizEvtMsg = new ArrayList<>();
        bizEvtMsg.add(BizEvent.builder().id("123").build());

        Map<String, Object>[] properties = new HashMap[1];
        @SuppressWarnings("unchecked")
        OutputBinding<List<BizEvent>> document = (OutputBinding<List<BizEvent>>)mock(OutputBinding.class);

        doThrow(new ArithmeticException()).when(redisCacheService).findByBizEventId(anyString(),anyString(), any(Logger.class));

        // test execution
        assertThrows(Exception.class, () -> function.processBizEvent(bizEvtMsg, properties, document, context));
    }

    @Test
    void runKo_genericExceptionLastRetry() {
        // test precondition
        Logger logger = Logger.getLogger("BizEventToDataStore-test-logger");
        when(context.getLogger()).thenReturn(logger);
        when(context.getRetryContext()).thenReturn(retryContext);
        when(retryContext.getRetrycount()).thenReturn(10);

        List<BizEvent> bizEvtMsg = new ArrayList<>();
        bizEvtMsg.add(BizEvent.builder().id("123").build());

        Map<String, Object>[] properties = new HashMap[1];
        @SuppressWarnings("unchecked")
        OutputBinding<List<BizEvent>> document = (OutputBinding<List<BizEvent>>)mock(OutputBinding.class);

        doThrow(new ArithmeticException()).when(redisCacheService).findByBizEventId(anyString(),anyString(), any(Logger.class));

        // test execution
        assertThrows(Exception.class, () -> function.processBizEvent(bizEvtMsg, properties, document, context));
    }

    @Test
    void runLastRetry() {
        // test precondition
        Logger logger = Logger.getLogger("BizEventToDataStore-test-logger");
        when(context.getLogger()).thenReturn(logger);
        when(context.getRetryContext()).thenReturn(retryContext);
        when(retryContext.getRetrycount()).thenReturn(10);

        PaymentInfo pi = PaymentInfo.builder().IUR("iur").build();
        DebtorPosition dp = DebtorPosition.builder().iuv("iuv").build();
        InfoECommerce iec = InfoECommerce.builder()
                .brand("VISA")
                .brandLogo("https://dev.checkout.pagopa.it/assets/creditcard/carta_visa.png")
                .clientId("CHECKOUT")
                .paymentMethodName("Carte")
                .type("CP")
                .build();
        TransactionDetails td = TransactionDetails.builder().info(iec).build();

        List<BizEvent> bizEvtMsg = new ArrayList<>();
        bizEvtMsg.add (BizEvent.builder().id("123").paymentInfo(pi).debtorPosition(dp).transactionDetails(td).build());

        Map<String, Object>[] properties = new HashMap[1];
        @SuppressWarnings("unchecked")
        OutputBinding<List<BizEvent>> document = (OutputBinding<List<BizEvent>>)mock(OutputBinding.class);

        doReturn(null).when(redisCacheService).findByBizEventId(anyString(),anyString(), any(Logger.class));
        doReturn("OK").when(redisCacheService).saveBizEventId(anyString(),anyString(), any(Logger.class));

        // test execution
        function.processBizEvent(bizEvtMsg, properties, document, context);

        // test assertion -> this line means the call was successful
        assertTrue(true);
    }
}
