package it.gov.pagopa.bizeventsdatastore.client;

import java.io.IOException;
import java.lang.reflect.Type;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpBackOffUnsuccessfulResponseHandler;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonObjectParser;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.ExponentialBackOff;
import com.google.gson.reflect.TypeToken;

import it.gov.pagopa.bizeventsdatastore.exception.PM4XXException;
import it.gov.pagopa.bizeventsdatastore.exception.PM5XXException;
import it.gov.pagopa.bizeventsdatastore.model.WrapperTransactionDetails;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;


@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class PaymentManagerClient {

	private static PaymentManagerClient instance = null;
	
	private static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
	private static final JsonFactory JSON_FACTORY = new GsonFactory();
    private static final String GET_PAYMENT_EVENT_DETAILS = "/payment-events/%s";
    private static final String PAYMENT_MANAGER_HOST = "https://api.uat.platform.pagopa.it/payment-manager/events/v1"; //System.getenv("PM_HOST"); // https://api.uat.platform.pagopa.it/payment-manager/pp-restapi-server/v4
    private static final String API_KEY = System.getenv("API_KEY");
    
    // Retry ExponentialBackOff config 
    private static final boolean ENABLE_RETRY = 
    		System.getenv("ENABLE_RETRY") != null ? Boolean.parseBoolean(System.getenv("ENABLE_RETRY")) : Boolean.FALSE;
    private static final int INITIAL_INTERVAL_MILLIS = 
    		System.getenv("INITIAL_INTERVAL_MILLIS") != null ? Integer.parseInt(System.getenv("INITIAL_INTERVAL_MILLIS")) : 500;
    private static final int MAX_ELAPSED_TIME_MILLIS = 
    		System.getenv("MAX_ELAPSED_TIME_MILLIS") != null ? Integer.parseInt(System.getenv("MAX_ELAPSED_TIME_MILLIS")) : 1000;
    private static final int MAX_INTERVAL_MILLIS  = 
    		System.getenv("MAX_INTERVAL_MILLIS") != null ? Integer.parseInt(System.getenv("MAX_INTERVAL_MILLIS")) : 1000;
    private static final double MULTIPLIER  = 
    		System.getenv("MULTIPLIER") != null ? Double.parseDouble(System.getenv("MULTIPLIER")) : 1.5;
    private static final double RANDOMIZATION_FACTOR  = 
    		System.getenv("RANDOMIZATION_FACTOR") != null ? Double.parseDouble(System.getenv("RANDOMIZATION_FACTOR")) : 0.5;

    public static PaymentManagerClient getInstance() {
        if (instance == null) {
            instance = new PaymentManagerClient();
        }
        return instance;
    }
    
	public WrapperTransactionDetails getPMEventDetails(String idPayment) throws IOException, IllegalArgumentException, PM5XXException, PM4XXException {
    	
    	//final String authorizationHeader = "Bearer " + API_KEY;
    	
    	HttpRequestFactory requestFactory = HTTP_TRANSPORT.createRequestFactory(
          (HttpRequest request) -> 
            request.setParser(new JsonObjectParser(JSON_FACTORY))
          );
    	
    	GenericUrl url = new GenericUrl(PAYMENT_MANAGER_HOST + String.format(GET_PAYMENT_EVENT_DETAILS, idPayment));
    	
    	HttpRequest request = requestFactory.buildGetRequest(url);
    	HttpHeaders headers = request.getHeaders();
    	//headers.set("Authorization", authorizationHeader);
    	headers.set("Ocp-Apim-Subscription-Key", API_KEY);
    	
/** 
 * Retry section config
 */
    	if (ENABLE_RETRY) {
    		ExponentialBackOff backoff = new ExponentialBackOff.Builder()
    				.setInitialIntervalMillis(INITIAL_INTERVAL_MILLIS)
    				.setMaxElapsedTimeMillis(MAX_ELAPSED_TIME_MILLIS)
    				.setMaxIntervalMillis(MAX_INTERVAL_MILLIS)
    				.setMultiplier(MULTIPLIER)
    				.setRandomizationFactor(RANDOMIZATION_FACTOR)
    				.build();

    		// Exponential Backoff is turned off by default in HttpRequest -> it's necessary include an instance of HttpUnsuccessfulResponseHandler to the HttpRequest to activate it
    		// The default back-off on anabnormal HTTP response is BackOffRequired.ON_SERVER_ERROR (5xx) 
    		request.setUnsuccessfulResponseHandler(
    				new HttpBackOffUnsuccessfulResponseHandler(backoff));
    	}
/** 
 * END Retry section config
 */
    	
    	Type type = new TypeToken<WrapperTransactionDetails>() {}.getType();
    	 	
    	WrapperTransactionDetails wrapperTD = null;
    	
    	HttpResponse res = request.execute();
    	
    	if (res.getStatusCode() / 100 == 4) {
    		String message = String.format("Error %s calling the service URL %s", res.getStatusCode(), url);
    		throw new PM4XXException(message);
    		
    	} else if (res.getStatusCode() / 100 == 5) {
    		String message = String.format("Error %s calling the service URL %s", res.getStatusCode(), url);
    		throw new PM5XXException(message); 
    		
    	} else {
    		wrapperTD = (WrapperTransactionDetails) res.parseAs(type);
    	}
    	
    	return wrapperTD;
    }
}
