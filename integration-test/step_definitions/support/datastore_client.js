const {post, getAuthorizationTokenUsingMasterKey} = require("./common");
const dotenv = require("dotenv")

dotenv.config()

function queryEventById(id) {
    const dateStringUTC = new Date().toUTCString();

    masterKey = getAuthorizationTokenUsingMasterKey(
        'post',
        'docs',
        process.env.RESOURCE_LINK,
        dateStringUTC,
        process.env.COSMOS_KEY
    )

    const headers = { 
        'Authorization': masterKey,
        'x-ms-date': dateStringUTC,
        'x-ms-version': '2018-12-31',
        'Accept': 'application/json',
        'Content-Type': 'application/query+json',
        'x-ms-documentdb-isquery': 'True',
        'x-ms-query-enable-crosspartition': 'True'
    };

    const query = {  
        "query": "SELECT * FROM c where c.id=\"" + id + "\"",
        "parameters": []
    }

    return post(process.env.COLLECTION_URL, headers, query);
}

module.exports = {queryEventById}
