import http from 'k6/http';
import crypto from 'k6/crypto';
import encoding from 'k6/encoding';

const authorizationType      = "master"
const authorizationVersion   = "1.0";
const cosmosDBApiVersion     = "2018-12-31";

export function getDocumentById(cosmosDbURI, databaseId, containerId, authorizationSignature, id) {
    const path = `dbs/${databaseId}/colls/${containerId}/docs`;
    const resourceLink = `dbs/${databaseId}/colls/${containerId}`;
    const resourceType = "docs";
    const date = new Date().toUTCString();
    const verb = 'post';
    const authorizationToken = getCosmosDBAuthorizationToken(verb,authorizationType,authorizationVersion,authorizationSignature,resourceType,resourceLink,date);

    let partitionKeyArray = [];
    let headers = getCosmosDBAPIHeaders(authorizationToken, date, partitionKeyArray, 'application/query+json');

    const query = {
        "query": "SELECT * FROM c where c.id=@id",
        "parameters": [
            {
                "name":"@id",
                "value": id
            }
        ]
    };

    const body = JSON.stringify(query);

    return http.post(cosmosDbURI+path, body, {headers});
}

export function deleteDocument(cosmosDbURI, databaseId, containerId, authorizationSignature, id) {
    const path = `dbs/${databaseId}/colls/${containerId}/docs/${id}`;
    const resourceLink = path;
    const resourceType = "docs"
    const date = new Date().toUTCString();
    const verb = 'delete';
    const partitionKeyArray = "[\""+id+"\"]";

    let authorizationToken = getCosmosDBAuthorizationToken(verb,authorizationType,authorizationVersion,authorizationSignature,resourceType,resourceLink,date);
    let headers = getCosmosDBAPIHeaders(authorizationToken, date, partitionKeyArray, 'application/json');

    return http.del(cosmosDbURI+path, null, {headers});
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
    let key = encoding.b64decode(authorizationSignature);
    let text = (verb || "").toLowerCase() + "\n" +
        (resourceType || "").toLowerCase() + "\n" +
        (resourceLink || "") + "\n" +
        dateUtc.toLowerCase() + "\n\n";
    let hmacSha256 = crypto.createHMAC("sha256", key);
    hmacSha256.update(text);
    // Build autorization token, encode it and return
    return encodeURIComponent("type=" + autorizationType + "&ver=" + autorizationVersion + "&sig=" + hmacSha256.digest("base64"));
}
