package it.gov.pagopa.bizeventsdatastore.client.impl;


import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.PartitionKey;
import it.gov.pagopa.bizeventsdatastore.client.BizEventCosmosClient;
import it.gov.pagopa.bizeventsdatastore.entity.BizEvent;
import it.gov.pagopa.bizeventsdatastore.exception.BizEventNotFoundException;


/**
 * {@inheritDoc}
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

        try {
            return cosmosContainer.readItem(eventId, new PartitionKey(eventId), BizEvent.class).getItem();
        } catch (CosmosException e) {
            throw new BizEventNotFoundException("Document not found in the defined container", e);
        }
    }
}
