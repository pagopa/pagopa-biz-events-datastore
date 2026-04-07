package it.gov.pagopa.bizeventsdatastore.client.impl;


import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.PartitionKey;
import it.gov.pagopa.bizeventsdatastore.client.BizEventCosmosClient;
import it.gov.pagopa.bizeventsdatastore.entity.BizEvent;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewCart;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewGeneral;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewUser;
import it.gov.pagopa.bizeventsdatastore.exception.BizEventNotFoundException;


/**
 * {@inheritDoc}
 */
public class BizEventCosmosClientImpl implements BizEventCosmosClient {

    private static BizEventCosmosClientImpl instance;

    private final String databaseId = System.getenv("COSMOS_DB_NAME");
    private final String containerId = System.getenv("COSMOS_DB_CONTAINER_NAME");
    private final String viewUserContainerId = System.getenv("COSMOS_DB_VIEW_USER_CONTAINER_NAME");
    private final String viewGeneralContainerId = System.getenv("COSMOS_DB_VIEW_GENERAL_CONTAINER_NAME");
    private final String viewCartContainerId = System.getenv("COSMOS_DB_VIEW_CART_CONTAINER_NAME");

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

    /**
     * {@inheritDoc}
     */
    @Override
    public CosmosItemResponse<BizEventsViewUser> upsertBizEventViewUser(BizEventsViewUser viewUser) {
        CosmosDatabase cosmosDatabase = this.cosmosClient.getDatabase(databaseId);
        CosmosContainer cosmosContainer = cosmosDatabase.getContainer(viewUserContainerId);

        return cosmosContainer.upsertItem(viewUser);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CosmosItemResponse<BizEventsViewGeneral> upsertBizEventViewGeneral(BizEventsViewGeneral viewGeneral) {
        CosmosDatabase cosmosDatabase = this.cosmosClient.getDatabase(databaseId);
        CosmosContainer cosmosContainer = cosmosDatabase.getContainer(viewGeneralContainerId);

        return cosmosContainer.upsertItem(viewGeneral);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CosmosItemResponse<BizEventsViewCart> upsertBizEventViewCart(BizEventsViewCart viewCart) {
        CosmosDatabase cosmosDatabase = this.cosmosClient.getDatabase(databaseId);
        CosmosContainer cosmosContainer = cosmosDatabase.getContainer(viewCartContainerId);

        return cosmosContainer.upsertItem(viewCart);
    }
}
