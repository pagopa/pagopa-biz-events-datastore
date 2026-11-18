package it.gov.pagopa.bizeventsdatastore.client.impl;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosPatchOperations;
import com.azure.cosmos.models.PartitionKey;
import it.gov.pagopa.bizeventsdatastore.client.BizEventCosmosClient;
import it.gov.pagopa.bizeventsdatastore.entity.BizEvent;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewUser;
import it.gov.pagopa.bizeventsdatastore.exception.BizEventNotFoundException;

/**
 * {@inheritDoc}
 */
public class BizEventCosmosClientImpl implements BizEventCosmosClient {

    private static final String VIEW_USER_CONTAINER_NAME = "biz-events-view-user";
    private static final int HTTP_CONFLICT = 409;

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
        return SingletonHolder.INSTANCE;
    }

    /**
     * Thread-safe lazy singleton holder
     */
    private static class SingletonHolder {
        static final BizEventCosmosClientImpl INSTANCE = new BizEventCosmosClientImpl();
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
            if (e.getStatusCode() == 404) {
                throw new BizEventNotFoundException("Document not found in the defined container", e);
            }
            throw e;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void saveBizEventViewUserPreservingVisibility(BizEventsViewUser viewUser) {
        CosmosContainer viewUserContainer = this.cosmosClient.getDatabase(databaseId).getContainer(VIEW_USER_CONTAINER_NAME);
        PartitionKey partitionKey = new PartitionKey(viewUser.getTaxCode());

        try {
            viewUserContainer.createItem(viewUser, partitionKey, new CosmosItemRequestOptions());
        } catch (CosmosException e) {
            if (e.getStatusCode() != HTTP_CONFLICT) {
                throw e;
            }
            CosmosPatchOperations patchOperations = CosmosPatchOperations.create()
                    .set("/transactionId", viewUser.getTransactionId())
                    .set("/transactionDate", viewUser.getTransactionDate())
                    .set("/isPayer", viewUser.isPayer())
                    .set("/isDebtor", viewUser.isDebtor());
            if (viewUser.isHidden()) {
                patchOperations.set("/hidden", true);
            }
            viewUserContainer.patchItem(viewUser.getId(), partitionKey, patchOperations, BizEventsViewUser.class);
        }
    }
}