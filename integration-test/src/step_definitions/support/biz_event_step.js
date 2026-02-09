const assert = require('assert');
const {createEvent, sleep} = require("./common");
const {publishEvent} = require("./event_hub_client");
const {
    getBizEventById, 
    deleteBizEvent,
    getViewUserByTransactionId,
    deleteViewUserByTransactionId,
    getViewGeneralByTransactionId,
    deleteViewGeneralByTransactionId,
    getViewCartByTransactionId,
    deleteViewCartByTransactionId
} = require("./datastore_client");
const {After, Given, When, Then, setDefaultTimeout} = require('@cucumber/cucumber');
const {makeIdMix, makeIdNumber} = require("./utility/helpers")

let eventId;
let eventCreationTimestamp;

setDefaultTimeout(360 * 1000);

// After each Scenario

After(async function () {
    // remove event
    await deleteBizEvent(eventId)
    await deleteViewUserByTransactionId(eventId);
    await deleteViewGeneralByTransactionId(eventId);
    await deleteViewCartByTransactionId(eventId);
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
    let responseToCheck = await getBizEventById(eventId);
    eventCreationTimestamp = responseToCheck.resources[0]._ts;
    assert.strictEqual(responseToCheck.resources[0].id, eventId);
});

Then('the datastore returns the not updated event', async function () {
    responseToCheck = await getBizEventById(eventId);
    assert.strictEqual(responseToCheck.resources[0].id, eventId);
    assert.strictEqual(responseToCheck.resources[0]._ts, eventCreationTimestamp);
});

Given('a biz event with client id {string} and user type {string} is published on eventhub', async function (client_id, user_type) {
  eventId = makeIdMix(15);
    
    const event = createEvent(eventId, client_id, user_type);
    let responseToCheck =  await publishEvent(event);
    
    assert.strictEqual(responseToCheck.status, 201)
})

Then('the view datastore returns {int} view user', async function (expectedCount) {
  let responseToCheck = await getViewUserByTransactionId(eventId);
  assert.strictEqual(responseToCheck.resources.length, expectedCount);
})

Then('the view datastore returns the view general', async function () {
  let responseToCheck = await getViewGeneralByTransactionId(eventId);
  assert.strictEqual(responseToCheck.resources.length, 1);
})

Then('the view datastore returns the view cart', async function () {
  let responseToCheck = await getViewCartByTransactionId(eventId);
  assert.strictEqual(responseToCheck.resources.length, 1);
})