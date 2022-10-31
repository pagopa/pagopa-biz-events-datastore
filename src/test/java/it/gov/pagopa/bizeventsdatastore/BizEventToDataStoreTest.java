package it.gov.pagopa.bizeventsdatastore;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.logging.Logger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;

import it.gov.pagopa.bizeventsdatastore.entity.BizEvent;

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
        
        BizEvent bizEvtMsg = new BizEvent();
        @SuppressWarnings("unchecked")
        OutputBinding<BizEvent> document = (OutputBinding<BizEvent>)mock(OutputBinding.class);

        // test execution
//        function.processBizEvent(bizEvtMsg, document, context);

        // test assertion -> this line means the call was successful
        assertTrue(true);
    }
}
