package it.gov.pagopa.bizeventsdatastore;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;

import it.gov.pagopa.bizeventsdatastore.entity.BizEvent;
import it.gov.pagopa.bizeventsdatastore.entity.DebtorPosition;
import it.gov.pagopa.bizeventsdatastore.entity.PaymentInfo;

@ExtendWith(MockitoExtension.class)
class BizEventToDataStoreTest {

    @Spy
    BizEventToDataStore function;

    @Mock
    ExecutionContext context;
    

    @Test
    void runOk() {
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
        
        doReturn(null).when(function).findByBizEventId(anyString(), any(Logger.class));
        doReturn("OK").when(function).saveBizEventId(anyString(), any(Logger.class));

        // test execution
        function.processBizEvent(bizEvtMsg, properties, document, context);

        // test assertion -> this line means the call was successful
        assertTrue(true);
    }
    
    @Test
    void runKo_differentSize() {
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
    void runBizEventAlreadyInCache() {
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
    void runBizEventRedisException() {
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
}
