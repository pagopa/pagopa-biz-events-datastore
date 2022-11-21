const {post, del} = require("./common");
const cryptojs = require("crypto-js");


const cosmos_db_uri = process.env.COSMOS_DB_URI; // the cosmos account URI
const databaseId             = process.env.DATABASE_ID;  // es. db
const containerId            = process.env.CONTAINER_ID; // es. biz-events
const authorizationSignature = process.env.COSMOS_DB_PRIMARY_KEY;  // the cosmos accont Connection Primary Key
const authorizationType      = "master"
const authorizationVersion   = "1.0";
const cosmosDBApiVersion     = "2018-12-31";

function getDocumentById(id) {
    const path = `dbs/${databaseId}/colls/${containerId}/docs`;
    const resourceLink = `dbs/${databaseId}/colls/${containerId}`;
    // resource type (colls, docs...)
    const resourceType = "docs";
    const date = new Date().toUTCString();
    // request method (a.k.a. verb) to build text for authorization token
    const verb = 'post';
    const authorizationToken = getCosmosDBAuthorizationToken(verb,authorizationType,authorizationVersion,authorizationSignature,resourceType,resourceLink,date);

    let partitionKeyArray = [];
    const headers = getCosmosDBAPIHeaders(authorizationToken, date, partitionKeyArray, 'application/query+json');

    const body = {
        "query": "SELECT * FROM c where c.id=@id",
        "parameters": [
            {
                "name":"@id",
                "value": id
            }
        ]
    }

    return post(cosmos_db_uri+path, body, headers)
}

function deleteDocument(id) {
    let path = `dbs/${databaseId}/colls/${containerId}/docs/${id}`;
    let resourceLink = path;
    // resource type (colls, docs...)
    let resourceType = "docs"
    let date = new Date().toUTCString();
    // request method (a.k.a. verb) to build text for authorization token
    let verb = 'delete';
    let authorizationToken = getCosmosDBAuthorizationToken(verb,authorizationType,authorizationVersion,authorizationSignature,resourceType,resourceLink,date);

    let partitionKeyArray = "[\""+id+"\"]";
    let headers = getCosmosDBAPIHeaders(authorizationToken, date, partitionKeyArray, 'application/json');

    return del(cosmos_db_uri+path, headers);
}


function getCosmosDBAPIHeaders(authorizationToken, date, partitionKeyArray, contentType){

    return {'Accept': 'application/json',
        'Content-Type': contentType,
        'Authorization': authorizationToken,
        'x-ms-version': cosmosDBApiVersion,
        'x-ms-date': date,
        'x-ms-documentdb-isquery': 'true',
        'x-ms-query-enable-crosspartition': 'true',
        'x-ms-documentdb-partitionkey': partitionKeyArray
    };
}

function getCosmosDBAuthorizationToken(verb, autorizationType, autorizationVersion, authorizationSignature, resourceType, resourceLink, dateUtc) {
    // Decode authorization signature
    let key = cryptojs.enc.Base64.parse(authorizationSignature);
    // Build string to be encrypted and used as signature.
    // See: https://docs.microsoft.com/en-us/rest/api/cosmos-db/access-control-on-cosmosdb-resources
    let text = (verb || "").toLowerCase() + "\n" +
        (resourceType || "").toLowerCase() + "\n" +
        (resourceLink || "") + "\n" +
        dateUtc.toLowerCase() + "\n\n";
    // Build key to authorize request.
    let signature = cryptojs.HmacSHA256(text, key);
    // Code key as base64 to be sent.
    let signature_base64 = cryptojs.enc.Base64.stringify(signature);

    // Build autorization token, encode it and return
    return encodeURIComponent("type=" + autorizationType + "&ver=" + autorizationVersion + "&sig=" + signature_base64);
}


module.exports = {
    getDocumentById, deleteDocument
}