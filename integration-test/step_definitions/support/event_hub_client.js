const {post, createSharedAccessToken} = require("./common");
const dotenv = require("dotenv")

dotenv.config()

function publishEvent(event) {
    const namespace = process.env.NAMESPACE;
    const tokenSAS = createSharedAccessToken("sb://" + namespace + ".servicebus.windows.net/", process.env.EVENT_HUB_SENDER, process.env.EVENT_HUB_KEY)

    const headers = { 
        'Authorization': tokenSAS,
        'Host': namespace + '.servicebus.windows.net',
        'Content-Type': 'application/json'
    };

    const url = "https://" + namespace + ".servicebus.windows.net/" + process.env.EVENT_HUB_PATH + "/messages"

    return post(url, headers, event);
}

module.exports = {publishEvent}
