package it.gov.pagopa.bizeventsdatastore;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import it.gov.pagopa.bizeventsdatastore.model.AppInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Optional;
import java.util.Properties;


/**
 * Azure Functions with Azure Http trigger.
 */
public class Info {

    private final Logger logger = LoggerFactory.getLogger(Info.class);

    private final String serviceName = System.getenv().getOrDefault("SERVICE_NAME", "");

    /**
     * This function will be invoked when a Http Trigger occurs
     *
     */
    @FunctionName("Info")
    public HttpResponseMessage run(
            @HttpTrigger(name = "InfoTrigger",
                    methods = {HttpMethod.GET},
                    route = "info",
                    authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context
    ) {

        return request.createResponseBuilder(HttpStatus.OK)
                .header("Content-Type", "application/json")
                .body(getInfo())
                .build();
    }

    public synchronized AppInfo getInfo() {
        String version = null;
        try (InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream("application.properties")) {
            Properties properties = new Properties();
            if (inputStream != null) {
                properties.load(inputStream);
                version = properties.getProperty("version", null);
            }
        } catch (Exception e) {
            logger.warn("Impossible to retrieve information from application.properties file.", e);
        }
        return AppInfo.builder().version(version).environment("azure-fn").name(serviceName).build();
    }

}
