package it.gov.pagopa.bizeventsdatastore.client;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpBackOffUnsuccessfulResponseHandler;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonObjectParser;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.ExponentialBackOff;
import com.google.gson.reflect.TypeToken;

import it.gov.pagopa.bizeventsdatastore.model.PaymentEvent;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;


@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class PaymentManagerClient {

	private static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
	private static final JsonFactory JSON_FACTORY = new GsonFactory();
    private static final String GET_PAYMENT_EVENT_DETAILS = "/be/payment-events/%s";
    private static PaymentManagerClient instance = null;
    private static final String PAYMENT_MANAGER_HOST = "https://api.uat.platform.pagopa.it/payment-manager/pp-restapi-server/v4"; //System.getenv("PM_HOST"); // https://api.uat.platform.pagopa.it/payment-manager/pp-restapi-server/v4
    private static final String API_KEY = "";

    public static PaymentManagerClient getInstance() {
        if (instance == null) {
            instance = new PaymentManagerClient();
        }
        return instance;
    }
    
    public List<PaymentEvent> getPMEventDetails(String idPayment) throws IOException, IllegalArgumentException {
    	
    	final String authorizationHeader = "Bearer " + API_KEY;
    	
    	HttpRequestFactory requestFactory = HTTP_TRANSPORT.createRequestFactory(
          (HttpRequest request) -> {
            request.setParser(new JsonObjectParser(JSON_FACTORY));
          });
    	
    	GenericUrl url = new GenericUrl(PAYMENT_MANAGER_HOST + String.format(GET_PAYMENT_EVENT_DETAILS, idPayment));
    	
    	HttpRequest request = requestFactory.buildGetRequest(url);
    	HttpHeaders headers = request.getHeaders();
    	headers.set("Authorization", authorizationHeader);
    	
/** 
 * Retry section config
 */
    	ExponentialBackOff backoff = new ExponentialBackOff.Builder()
    			  .setInitialIntervalMillis(500)
    			  .setMaxElapsedTimeMillis(9000)
    			  .setMaxIntervalMillis(6000)
    			  .setMultiplier(1.5)
    			  .setRandomizationFactor(0.5)
    			  .build();

    	// Exponential Backoff is turned off by default in HttpRequest -> it's necessary include an instance of HttpUnsuccessfulResponseHandler to the HttpRequest to activate it
    	// The default back-off on anabnormal HTTP response is BackOffRequired.ON_SERVER_ERROR (5xx) 
        request.setUnsuccessfulResponseHandler(
    			  new HttpBackOffUnsuccessfulResponseHandler(backoff));
/** 
 * END Retry section config
 */
    	
    	Type type = new TypeToken<List<PaymentEvent>>() {}.getType();
    	
    	@SuppressWarnings("unchecked")
		List<PaymentEvent> paymentEvents = (List<PaymentEvent>) request
    		      .execute()
    		      .parseAs(type);
    	
    	return paymentEvents;
    }
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
/*
    public int createDebtPosition(Logger logger, String idPa, PaymentPositionModel body, String requestId) {
        try {
            logger.log(Level.INFO, () -> "[CuCreateDebtPositionFunction GPD - createDebtPosition][requestId=" + requestId + "]  Calling GPD service: " + idPa);
            Client client = ClientBuilder.newClient();
            Response response = client
                    .register(JacksonJaxbJsonProvider.class)
                    .target(gpdHost + String.format(POST_DEBT_POSITIONS, idPa))
                    .request()
                    .header("X-Request-Id", requestId)
                    .accept(MediaType.APPLICATION_JSON)
                    .post(Entity.json(body));
            client.close();
            logger.log(Level.INFO, () -> "[CuCreateDebtPositionFunction GPD - createDebtPosition][requestId=" + requestId + "] HTTP status: " + response.getStatus());
            return response.getStatus();
        } catch (Exception e) {
            logger.log(Level.SEVERE, () -> "[CuCreateDebtPositionFunction ERROR - createDebtPosition][requestId=" + requestId + "] error during the GPD call " + e.getMessage() + " "
                    + e.getCause());
            return -1;
        }
    }

    public int publishDebtPosition(Logger logger, String idPa, String iupd, String requestId) {
        try {
            logger.log(Level.INFO, () -> "[CuCreateDebtPositionFunction GPD - publishDebtPosition][requestId=" + requestId + "] Calling GPD service: " + idPa + "; " + iupd);
            Client client = ClientBuilder.newClient();
            Response response = client
                    .register(JacksonJaxbJsonProvider.class)
                    .target(gpdHost + String.format(PUBLISH_DEBT_POSITIONS, idPa, iupd))
                    .request()
                    .header("X-Request-Id", requestId)
                    .accept(MediaType.APPLICATION_JSON)
                    .post(Entity.json(null));
            client.close();
            logger.log(Level.INFO, () -> "[CuCreateDebtPositionFunction GPD - publishDebtPosition][requestId=" + requestId + "] HTTP status: " + response.getStatus());
            return response.getStatus();
        } catch (Exception e) {
            logger.log(Level.SEVERE, () -> "[CuCreateDebtPositionFunction ERROR - publishDebtPosition][requestId=" + requestId + "] error during the GPD call " + e.getMessage() + " "
                    + e.getCause());
            return -1;
        }
    }
    */
}
