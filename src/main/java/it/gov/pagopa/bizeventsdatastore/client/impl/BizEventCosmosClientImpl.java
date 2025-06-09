package it.gov.pagopa.bizeventsdatastore.client.impl;


import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.util.CosmosPagedIterable;
import it.gov.pagopa.bizeventsdatastore.client.BizEventCosmosClient;
import it.gov.pagopa.bizeventsdatastore.entity.BizEvent;
import it.gov.pagopa.bizeventsdatastore.entity.enumeration.StatusType;
import it.gov.pagopa.bizeventsdatastore.exception.BizEventNotFoundException;

/**
 * Client for the CosmosDB database
 */
public class BizEventCosmosClientImpl implements BizEventCosmosClient {

    private static BizEventCosmosClientImpl instance;

    private final String databaseId = System.getenv("COSMOS_DB_NAME");
    private final String containerId = System.getenv("COSMOS_DB_CONTAINER_NAME");

    private final CosmosClient cosmosClient;

    private BizEventCosmosClientImpl() {
        String azureKey = System.getenv("COSMOS_DB_PRIMARY_KEY");
        String serviceEndpoint = System.getenv("COSMOS_DB_URI");

        this.cosmosClient = new CosmosClientBuilder()
                .endpoint(serviceEndpoint)
                .key(azureKey)
                .buildClient();
    }

    BizEventCosmosClientImpl(CosmosClient cosmosClient) {
        this.cosmosClient = cosmosClient;
    }

    public static BizEventCosmosClientImpl getInstance() {
        if (instance == null) {
            instance = new BizEventCosmosClientImpl();
        }
        return instance;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BizEvent getBizEventDocument(String eventId) throws BizEventNotFoundException {
        CosmosDatabase cosmosDatabase = this.cosmosClient.getDatabase(databaseId);
        CosmosContainer cosmosContainer = cosmosDatabase.getContainer(containerId);

        //Build query
        String query = String.format("SELECT * FROM c WHERE c.eventStatus IN ('%s','%s') AND c.id = '%s'", StatusType.DONE, StatusType.INGESTED, eventId);

        //Query the container
        CosmosPagedIterable<BizEvent> queryResponse = cosmosContainer
                .queryItems(query, new CosmosQueryRequestOptions(), BizEvent.class);

        if (queryResponse.iterator().hasNext()) {
            return queryResponse.iterator().next();
        }
        throw new BizEventNotFoundException("Document not found in the defined container");
    }
}
