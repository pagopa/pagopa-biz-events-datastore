package it.gov.pagopa.bizeventsdatastore;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

import it.gov.pagopa.bizeventsdatastore.exception.AppException;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    BizEventToDataStore function;

    @Mock
    ExecutionContext context;
    
    @Mock
    RetryContext retryContext;
    

    @Test
    void runOk() throws AppException {
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
        
        doReturn(null).when(function).findByBizEventId(anyString(), any(Logger.class));
        doReturn("OK").when(function).saveBizEventId(anyString(), any(Logger.class));

        // test execution
        function.processBizEvent(bizEvtMsg, properties, document, context);

        // test assertion -> this line means the call was successful
        assertTrue(true);
    }

    @Test
    void runLastRetry() throws AppException {
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

        doReturn(null).when(function).findByBizEventId(anyString(), any(Logger.class));
        doReturn("OK").when(function).saveBizEventId(anyString(), any(Logger.class));

        // test execution
        function.processBizEvent(bizEvtMsg, properties, document, context);

        // test assertion -> this line means the call was successful
        assertTrue(true);
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
        
        doReturn(null).when(function).findByBizEventId(anyString(), any(Logger.class));
        doReturn("OK").when(function).saveBizEventId(anyString(), any(Logger.class));

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
        
        doReturn(null).when(function).findByBizEventId(anyString(), any(Logger.class));
        doReturn("OK").when(function).saveBizEventId(anyString(), any(Logger.class));

        // test execution
        function.processBizEvent(bizEvtMsgList, properties, document, context);

        // test assertion -> this line means the call was successful
        assertTrue(true);
    }
    
    @Test
    void runKo_differentSize() throws AppException {
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
    void runBizEventAlreadyInCache() throws AppException {
        // test precondition
        Logger logger = Logger.getLogger("BizEventToDataStore-test-logger");
        when(context.getLogger()).thenReturn(logger);
        
        List<BizEvent> bizEvtMsg = new ArrayList<>();
        bizEvtMsg.add (BizEvent.builder().id("123").build());
        
        Map<String, Object>[] properties = new HashMap[1];
        @SuppressWarnings("unchecked")
        OutputBinding<List<BizEvent>> document = (OutputBinding<List<BizEvent>>)mock(OutputBinding.class);
        
        doReturn("123").when(function).findByBizEventId(anyString(), any(Logger.class));

        // test execution
        function.processBizEvent(bizEvtMsg, properties, document, context);

        // test assertion -> this line means the call was successful
        assertTrue(true);
    }
    
    @Test
    void runBizEventRedisException() throws AppException {
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
    void handleLastRetryTest() throws AppException {
        // test precondition
        Logger logger = Logger.getLogger("BizEventToDataStore-test-logger");
        when(context.getLogger()).thenReturn(logger);

        List<BizEvent> bizEvtMsg = new ArrayList<>();
        bizEvtMsg.add (BizEvent.builder().id("123").build());

        function.handleLastRetry(context, "id-test-1", LocalDateTime.now(), "test", bizEvtMsg);

        // test assertion -> this line means the call was successful
        assertTrue(true);
    }

    @Test
    void runKo_nullPointerException() throws AppException {
        // test precondition
        Logger logger = Logger.getLogger("BizEventToDataStore-test-logger");
        when(context.getLogger()).thenReturn(logger);

        List<BizEvent> bizEvtMsg = new ArrayList<>();
        bizEvtMsg.add (BizEvent.builder().id("123").build());

        Map<String, Object>[] properties = new HashMap[1];
        @SuppressWarnings("unchecked")
        OutputBinding<List<BizEvent>> document = (OutputBinding<List<BizEvent>>)mock(OutputBinding.class);

        doReturn(null).when(function).findByBizEventId(anyString(), any(Logger.class));

        // test execution
        assertThrows(NullPointerException.class, () -> function.processBizEvent(bizEvtMsg, properties, document, context));
    }

    @Test
    void runKo_genericException() throws AppException {
        // test precondition
        Logger logger = Logger.getLogger("BizEventToDataStore-test-logger");
        when(context.getLogger()).thenReturn(logger);

        List<BizEvent> bizEvtMsg = new ArrayList<>();
        bizEvtMsg.add(BizEvent.builder().id("123").build());

        Map<String, Object>[] properties = new HashMap[1];
        @SuppressWarnings("unchecked")
        OutputBinding<List<BizEvent>> document = (OutputBinding<List<BizEvent>>)mock(OutputBinding.class);

        doThrow(new ArithmeticException()).when(function).findByBizEventId(anyString(), any(Logger.class));

        // test execution
        assertThrows(Exception.class, () -> function.processBizEvent(bizEvtMsg, properties, document, context));
    }
}
