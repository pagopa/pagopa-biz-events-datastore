const assert = require('assert');
const {createEvent, sleep} = require("./common");
const {publishEvent} = require("./event_hub_client");
const {getDocumentById, deleteDocument} = require("./datastore_client");
const {After, Given, When, Then} = require('@cucumber/cucumber');

let eventId;


// After each Scenario

After(function () {
    // remove event
    deleteDocument(eventId)
});

// Given
Given('a random biz event with id {string} published on eventhub', async function (id) {
	eventId = id;
    // prior cancellation to avoid dirty cases
    await deleteDocument(id);
    console.log("eventId", eventId)
    const event = createEvent(eventId);
    console.log (event)
    let responseToCheck =  await publishEvent(event);

    assert.strictEqual(responseToCheck.status, 201);
});

// When
When('biz event has been properly stored into datastore after {int} ms', async function (time) {
    // boundary time spent by azure function to process event
    await sleep(time);
});

// Then

Then('the datastore returns the event with id {string}', async function (targetId) {
    responseToCheck = await getDocumentById(targetId);
    assert.strictEqual(responseToCheck.data.Documents[0].id, targetId);
});
