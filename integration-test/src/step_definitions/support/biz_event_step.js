const assert = require('assert');
const {createEvent, sleep} = require("./common");
const {publishEvent} = require("./event_hub_client");
const {getDocumentById, deleteDocument} = require("./datastore_client");
const {After, Given, When, Then, setDefaultTimeout} = require('@cucumber/cucumber');
const {makeIdMix, makeIdNumber} = require("./utility/helpers")

let eventId;
let eventCreationTimestamp;

setDefaultTimeout(360 * 1000);


// After each Scenario

After(function () {
    // remove event
    deleteDocument(eventId)
});

// Given
Given('a random biz event is published on eventhub', async function () {
	eventId = makeIdMix(15);
    
    const event = createEvent(eventId);
    let responseToCheck =  await publishEvent(event);
    
    assert.strictEqual(responseToCheck.status, 201);
});

// When
When('biz event has been properly stored into datastore after {int} ms', async function (time) {
    // boundary time spent by azure function to process event
    await sleep(time);
});

// Given
When('the eventhub sends the same biz event again', async function () {    
    const event = createEvent(eventId);
    let responseToCheck =  await publishEvent(event);
    
    assert.strictEqual(responseToCheck.status, 201);
});

// Then
Then('the datastore returns the event', async function () {
    let responseToCheck = await getDocumentById(eventId);
    console.log(responseToCheck.data);
    eventCreationTimestamp = responseToCheck.data.Documents[0]._ts;
    assert.strictEqual(responseToCheck.data.Documents[0].id, eventId);
});

Then('the datastore returns the not updated event', async function () {
    responseToCheck = await getDocumentById(eventId);
    assert.strictEqual(responseToCheck.data.Documents[0].id, eventId);
    assert.strictEqual(responseToCheck.data.Documents[0]._ts, eventCreationTimestamp);
});
