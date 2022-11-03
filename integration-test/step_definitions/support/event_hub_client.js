const {post} = require("./common");
const crypto = require("crypto");

const namespace       = process.env.EVENT_HUB_NAMESPACE;
const eventHub        = process.env.EVENT_HUB_NAME;
const eventHubSender  = process.env.EVENT_HUB_SENDER;
const eventHubKey     = process.env.EVENT_HUB_KEY;

function publishEvent(event) {
    const path = `${namespace}.servicebus.windows.net`; // service bus path
    const tokenSAS = createSharedAccessToken("sb://"+path, eventHubSender, eventHubKey)
    const headers = getEventHUBAPIHeaders(tokenSAS, path, 'application/json');
    const url = `https://${path}/${eventHub}/messages`;

    return post(url, event, headers);
}

function getEventHUBAPIHeaders(authorizationToken, host, contentType){

    return {'Authorization': authorizationToken,
        'Host': host,
        'Content-Type': contentType
    };
}

function createSharedAccessToken(uri, saName, saKey) {
    if (!uri || !saName || !saKey) {
        throw "Missing required parameter";
    }
    var encoded = encodeURIComponent(uri);
    var now = new Date();
    var day = 60*60*24;
    var ttl = Math.round(now.getTime() / 1000) + day;
    var signature = encoded + '\n' + ttl;
    var hash = crypto.createHmac('sha256', saKey).update(signature, 'utf8').digest('base64');
    return 'SharedAccessSignature sr=' + encoded + '&sig=' + encodeURIComponent(hash) + '&se=' + ttl + '&skn=' + saName;
}


module.exports = {
    publishEvent
}
