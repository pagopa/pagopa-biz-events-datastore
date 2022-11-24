import http from 'k6/http';
import crypto from 'k6/crypto';


export function publishEvent(namespace, eventHubName, eventHubSender, eventHubKey, event) {
    const path = `${namespace}.servicebus.windows.net`; // service bus path
    const url = `https://${path}/${eventHubName}/messages`;

    const tokenSAS = createSharedAccessToken("sb://"+path, eventHubSender, eventHubKey)
    const body = JSON.stringify(event);
    let headers = getEventHUBAPIHeaders(tokenSAS, path, 'application/json');

    return http.post(url, body, {headers});
}

function getEventHUBAPIHeaders(authorizationToken, host, contentType) {

    return {'Authorization': authorizationToken,
        'Host': host,
        'Content-Type': contentType
    };
}

function createSharedAccessToken(uri, saName, saKey) {
    if (!uri || !saName || !saKey) {
        throw "Missing required parameter";
    }
    let encoded = encodeURIComponent(uri);
    const now = new Date();
    const day = 60*60*24;
    const ttl = Math.round(now.getTime() / 1000) + day;
    let signature = encoded + '\n' + ttl;
    let hmacSha256 = crypto.createHMAC("sha256", saKey)
    hmacSha256.update(signature, 'utf8');

    return 'SharedAccessSignature sr=' + encoded + '&sig=' + encodeURIComponent(hmacSha256.digest('base64')) + '&se=' + ttl + '&skn=' + saName;
}