package it.gov.pagopa.bizeventsdatastore.client.impl;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosPatchOperations;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import it.gov.pagopa.bizeventsdatastore.client.BizEventCosmosClient;
import it.gov.pagopa.bizeventsdatastore.entity.BizEvent;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewCart;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewGeneral;
import it.gov.pagopa.bizeventsdatastore.entity.view.BizEventsViewUser;
import it.gov.pagopa.bizeventsdatastore.exception.BizEventNotFoundException;

import java.util.List;

/**
 * {@inheritDoc}
 */
public class BizEventCosmosClientImpl implements BizEventCosmosClient {

    private static final String VIEW_USER_CONTAINER_NAME = "biz-events-view-user";
    private static final String VIEW_GENERAL_CONTAINER_NAME = "biz-events-view-general";
    private static final String VIEW_CART_CONTAINER_NAME = "biz-events-view-cart";
    private static final String OTHER_BIZ_EVENTS_OF_PAYMENT_QUERY =
            "SELECT VALUE c.id FROM c WHERE c.creditor.idPA = @idPA AND c.debtorPosition.iuv = @iuv AND c.id != @id";
    private static final int HTTP_CONFLICT = 409;

    private static final Gson GSON = new Gson();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean createBizEventIfAbsent(BizEvent bizEvent) {
        CosmosContainer cosmosContainer = this.cosmosClient.getDatabase(databaseId).getContainer(containerId);
        try {
            cosmosContainer.createItem(toDocument(bizEvent), new PartitionKey(bizEvent.getId()), new CosmosItemRequestOptions());
            return true;
        } catch (CosmosException e) {
            if (e.getStatusCode() != HTTP_CONFLICT) {
                throw e;
            }
            return false;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> findOtherBizEventIdsOfPayment(String idPA, String iuv, String bizEventId) {
        SqlQuerySpec querySpec = new SqlQuerySpec(OTHER_BIZ_EVENTS_OF_PAYMENT_QUERY, List.of(
                new SqlParameter("@idPA", idPA),
                new SqlParameter("@iuv", iuv),
                new SqlParameter("@id", bizEventId)));
        return this.cosmosClient.getDatabase(databaseId).getContainer(containerId)
                .queryItems(querySpec, new CosmosQueryRequestOptions(), String.class)
                .stream()
                .toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void upsertBizEventViewGeneral(BizEventsViewGeneral viewGeneral) {
        this.cosmosClient.getDatabase(databaseId).getContainer(VIEW_GENERAL_CONTAINER_NAME)
                .upsertItem(toDocument(viewGeneral), new PartitionKey(viewGeneral.getTransactionId()), new CosmosItemRequestOptions());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void upsertBizEventViewCart(BizEventsViewCart viewCart) {
        this.cosmosClient.getDatabase(databaseId).getContainer(VIEW_CART_CONTAINER_NAME)
                .upsertItem(toDocument(viewCart), new PartitionKey(viewCart.getTransactionId()), new CosmosItemRequestOptions());
    }

    /**
     * Converts the object using Gson, as the Functions output bindings do, so that a document written with the SDK has
     * the same field names of a document written with a binding (with Jackson some of them would be different)
     */
    private JsonNode toDocument(Object item) {
        try {
            return OBJECT_MAPPER.readTree(GSON.toJson(item));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Unable to convert the document to JSON", e);
        }
    }
}