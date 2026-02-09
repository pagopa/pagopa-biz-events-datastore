const { CosmosClient } = require("@azure/cosmos");

const cosmos_db_uri = process.env.COSMOS_DB_URI; // the cosmos account URI
const databaseId = process.env.COSMOS_DB_NAME;  // es. db
const containerId = process.env.COSMOS_DB_CONTAINER_NAME; // es. biz-events
const viewUserContainerId = process.env.COSMOS_DB_VIEW_USER_CONTAINER_NAME; // es. biz-events-view-user
const viewGeneralContainerId = process.env.COSMOS_DB_VIEW_GENERAL_CONTAINER_NAME; // es. biz-events-view-general
const viewCartContainerId = process.env.COSMOS_DB_VIEW_CART_CONTAINER_NAME; // es. biz-events-view-cart
const primaryKey = process.env.COSMOS_DB_PRIMARY_KEY;  // the cosmos accont Connection Primary Key
const cosmos_db_conn_string = `AccountEndpoint=${cosmos_db_uri};AccountKey=${primaryKey};`;

const client = new CosmosClient(cosmos_db_conn_string);
const bizContainer = client.database(databaseId).container(containerId);
const viewUserContainer = client.database(databaseId).container(viewUserContainerId);
const viewGeneralContainer = client.database(databaseId).container(viewGeneralContainerId);
const viewCartContainer = client.database(databaseId).container(viewCartContainerId);

async function getBizEventById(id) {
    return await bizContainer.items
        .query({
            query: "SELECT * from c WHERE c.id=@id",
            parameters: [{ name: "@id", value: id }]
        })
        .fetchAll();
}

async function getViewUserByTransactionId(id) {
    return await viewUserContainer.items
        .query({
            query: "SELECT * from c WHERE c.transactionId=@id",
            parameters: [{ name: "@id", value: id }]
        })
        .fetchAll();
}

async function getViewGeneralByTransactionId(id) {
    return await viewGeneralContainer.items
        .query({
            query: "SELECT * from c WHERE c.transactionId=@id",
            parameters: [{ name: "@id", value: id }]
        })
        .fetchAll();
}

async function getViewCartByTransactionId(id) {
    return await viewCartContainer.items
        .query({
            query: "SELECT * from c WHERE c.transactionId=@id",
            parameters: [{ name: "@id", value: id }]
        })
        .fetchAll();
}

async function deleteBizEvent(id) {
    try {
        return await bizContainer.item(id, id).delete();
    } catch (error) {
        if (error.code !== 404) {
            console.log(error)
        }
    }
}

async function deleteViewUserByTransactionId(id) {
    let documents = await getViewUserByTransactionId(id);
    for (const doc of documents.resources) {
        try {
            await viewUserContainer.item(doc.id, doc.taxCode).delete();
        } catch (error) {
            if (error.code !== 404) {
                console.log(error)
            }
        }
    }
}

async function deleteViewGeneralByTransactionId(id) {
    let documents = await getViewGeneralByTransactionId(id);
    for (const doc of documents.resources) {
        try {
            await viewGeneralContainer.item(doc.id, doc.transactionId).delete();
        } catch (error) {
            if (error.code !== 404) {
                console.log(error)
            }
        }
    }
}

async function deleteViewCartByTransactionId(id) {
    let documents = await getViewCartByTransactionId(id);
    for (const doc of documents.resources) {
        try {
            await viewCartContainer.item(doc.id, doc.transactionId).delete();
        } catch (error) {
            if (error.code !== 404) {
                console.log(error)
            }
        }
    }
}


module.exports = {
    getBizEventById, 
    deleteBizEvent,
    getViewUserByTransactionId,
    deleteViewUserByTransactionId,
    getViewGeneralByTransactionId,
    deleteViewGeneralByTransactionId,
    getViewCartByTransactionId,
    deleteViewCartByTransactionId
}