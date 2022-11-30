package it.gov.pagopa.bizeventsdatastore.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.lang.reflect.Field;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.github.tomakehurst.wiremock.WireMockServer;

import it.gov.pagopa.bizeventsdatastore.exception.PM4XXException;
import it.gov.pagopa.bizeventsdatastore.exception.PM5XXException;
import it.gov.pagopa.bizeventsdatastore.model.WrapperTransactionDetails;
import it.gov.pagopa.bizeventsdatastore.util.TestUtil;

@ExtendWith(MockitoExtension.class)
class PaymentManagerClientTest {
	
	@Spy
	PaymentManagerClient pmClient;
	
	@Test
    void getInstance() {
        var result = PaymentManagerClient.getInstance();
        Assertions.assertNotNull(result);
    }
	
	@Test
	void getPMEventDetails() throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, SecurityException, IOException, PM5XXException, PM4XXException {
		
		WrapperTransactionDetails wrapperTD = TestUtil.readModelFromFile("payment-manager/transactionDetails.json", WrapperTransactionDetails.class);
		
		WireMockServer wireMockServer = new WireMockServer(8881);
        wireMockServer.start();
        
        configureFor(wireMockServer.port());
        stubFor(get(urlEqualTo("/payment-manager/events/v1/payment-events/123"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(TestUtil.toJson(wrapperTD))));
        
        Field host = PaymentManagerClient.class.getDeclaredField("paymentManagerHost");
        host.setAccessible(true); // Suppress Java language access checking
        host.set(pmClient, "http://localhost:8881");
        
        Field retry = PaymentManagerClient.class.getDeclaredField("enableRetry");
        retry.setAccessible(true); // Suppress Java language access checking
        retry.set(pmClient, true);
        
        WrapperTransactionDetails wtd = pmClient.getPMEventDetails("123");
        
        assertNotNull(wtd);
        assertNotNull(wtd.getTransactionDetails());
        assertEquals("0",wtd.getTransactionDetails().getUser().getUserId());
        assertEquals("OK",wtd.getTransactionDetails().getPaymentAuthorizationRequest().getAuthOutcome());
        assertEquals("0",wtd.getTransactionDetails().getWallet().getIdWallet()); 
        
        wireMockServer.stop();
	}
	
	@Test
	void getPMEventDetails_404() throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException, IOException, PM5XXException  {
		
		WireMockServer wireMockServer = new WireMockServer(8881);
        wireMockServer.start();
        
        configureFor(wireMockServer.port());
        stubFor(get(urlEqualTo("/payment-manager/events/v1/payment-events/123"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")));
        
        Field host = PaymentManagerClient.class.getDeclaredField("paymentManagerHost");
        host.setAccessible(true); // Suppress Java language access checking
        host.set(pmClient, "http://localhost:8881");
        
        Field retry = PaymentManagerClient.class.getDeclaredField("enableRetry");
        retry.setAccessible(true); // Suppress Java language access checking
        retry.set(pmClient, true);
        
        try {
        
        	pmClient.getPMEventDetails("123");
        	// an exception had to be raised
            fail(); 
        
        } catch (PM4XXException e) {
        	// expected exception
        	assertTrue(!e.getMessage().isEmpty());
        }
        
        wireMockServer.stop();

	}
	
	@Test
	void getPMEventDetails_500() throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException, IOException, PM4XXException  {
		
		WireMockServer wireMockServer = new WireMockServer(8881);
        wireMockServer.start();
        
        configureFor(wireMockServer.port());
        stubFor(get(urlEqualTo("/payment-manager/events/v1/payment-events/123"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")));
        
        Field host = PaymentManagerClient.class.getDeclaredField("paymentManagerHost");
        host.setAccessible(true); // Suppress Java language access checking
        host.set(pmClient, "http://localhost:8881");
        
        Field retry = PaymentManagerClient.class.getDeclaredField("enableRetry");
        retry.setAccessible(true); // Suppress Java language access checking
        retry.set(pmClient, true);
        
        try {
        
        	pmClient.getPMEventDetails("123");
        	// an exception had to be raised
            fail(); 
        
        } catch (PM5XXException e) {
        	// expected exception
        	assertTrue(!e.getMessage().isEmpty());
        }
        
        wireMockServer.stop();

	}

}
