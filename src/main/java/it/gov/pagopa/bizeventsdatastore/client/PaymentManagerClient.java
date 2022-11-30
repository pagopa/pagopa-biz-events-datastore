package it.gov.pagopa.bizeventsdatastore.client;

import java.io.IOException;
import java.lang.reflect.Type;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpBackOffUnsuccessfulResponseHandler;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpResponseException;
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
	private static final String GET_PAYMENT_EVENT_DETAILS = "/payment-manager/events/v1/payment-events/%s";
	
	private final HttpTransport httpTransport = new NetHttpTransport();
	private final JsonFactory jsonFactory = new GsonFactory();
    private final String paymentManagerHost = System.getenv("PM_CLIENT_HOST"); // https://api.uat.platform.pagopa.it/payment-manager/pp-restapi-server/v4
    private final String apiKey = System.getenv("PM_API_KEY");
    
    // Retry ExponentialBackOff config 
    private final boolean enableRetry = 
    		System.getenv("ENABLE_CLIENT_RETRY") != null ? Boolean.parseBoolean(System.getenv("ENABLE_CLIENT_RETRY")) : Boolean.FALSE;
    private final int initialIntervalMillis = 
    		System.getenv("INITIAL_INTERVAL_MILLIS") != null ? Integer.parseInt(System.getenv("INITIAL_INTERVAL_MILLIS")) : 500;
    private final int maxElapsedTimeMillis = 
    		System.getenv("MAX_ELAPSED_TIME_MILLIS") != null ? Integer.parseInt(System.getenv("MAX_ELAPSED_TIME_MILLIS")) : 1000;
    private final int maxIntervalMillis  = 
    		System.getenv("MAX_INTERVAL_MILLIS") != null ? Integer.parseInt(System.getenv("MAX_INTERVAL_MILLIS")) : 1000;
    private final double multiplier  = 
    		System.getenv("MULTIPLIER") != null ? Double.parseDouble(System.getenv("MULTIPLIER")) : 1.5;
    private final double randomizationFactor  = 
    		System.getenv("RANDOMIZATION_FACTOR") != null ? Double.parseDouble(System.getenv("RANDOMIZATION_FACTOR")) : 0.5;

    public static PaymentManagerClient getInstance() {
        if (instance == null) {
            instance = new PaymentManagerClient();
        }
        return instance;
    }
    
	public WrapperTransactionDetails getPMEventDetails(String idPayment) throws IOException, IllegalArgumentException, PM5XXException, PM4XXException {
    	
    	GenericUrl url = new GenericUrl(paymentManagerHost + String.format(GET_PAYMENT_EVENT_DETAILS, idPayment));
    	
    	HttpRequest request = this.buildGetRequestToPM(url);
    	
    	if (enableRetry) {
    		this.setRequestRetry(request);
    	}

    	return this.executeCallToPM(request);
    }
	
	public HttpRequest buildGetRequestToPM(GenericUrl url) throws IOException {

		HttpRequestFactory requestFactory = httpTransport.createRequestFactory(
				(HttpRequest request) -> 
				request.setParser(new JsonObjectParser(jsonFactory))
				);

		HttpRequest request = requestFactory.buildGetRequest(url);
		HttpHeaders headers = request.getHeaders();
		headers.set("Ocp-Apim-Subscription-Key", apiKey);
		return request;
	}
	
	public void setRequestRetry(HttpRequest request) {
		/** 
		 * Retry section config
		 */
		ExponentialBackOff backoff = new ExponentialBackOff.Builder()
				.setInitialIntervalMillis(initialIntervalMillis)
				.setMaxElapsedTimeMillis(maxElapsedTimeMillis)
				.setMaxIntervalMillis(maxIntervalMillis)
				.setMultiplier(multiplier)
				.setRandomizationFactor(randomizationFactor)
				.build();

		// Exponential Backoff is turned off by default in HttpRequest -> it's necessary include an instance of HttpUnsuccessfulResponseHandler to the HttpRequest to activate it
		// The default back-off on anabnormal HTTP response is BackOffRequired.ON_SERVER_ERROR (5xx) 
		request.setUnsuccessfulResponseHandler(
				new HttpBackOffUnsuccessfulResponseHandler(backoff));
	}
	
	public WrapperTransactionDetails executeCallToPM(HttpRequest request) throws IOException, IllegalArgumentException, PM5XXException, PM4XXException {
		
		Type type = new TypeToken<WrapperTransactionDetails>() {}.getType();
	 	
    	WrapperTransactionDetails wrapperTD = null;
    	
    	try {
    		HttpResponse res = request.execute();
    		wrapperTD = (WrapperTransactionDetails) res.parseAs(type);
    	} catch (HttpResponseException e) {
    		if (e.getStatusCode() / 100 == 4) {
        		String message = String.format("Error %s calling the service URL %s", e.getStatusCode(), request.getUrl());
        		throw new PM4XXException(message);
        		
        	} else if (e.getStatusCode() / 100 == 5) {
        		String message = String.format("Error %s calling the service URL %s", e.getStatusCode(), request.getUrl());
        		throw new PM5XXException(message); 
        		
        	}
    	}
    	
    	return wrapperTD;
	}
}
