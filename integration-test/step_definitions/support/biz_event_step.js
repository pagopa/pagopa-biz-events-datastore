const assert = require('assert');
const {randomEvent, sleep} = require("./common");
const {publishEvent} = require("./event_hub_client");
const {queryEventById} = require("./datastore_client");
const {Given, When, Then} = require('@cucumber/cucumber');

let event
let responseToCheck;

// Given

Given('a random Event published on EventHub', async function () {
    event = randomEvent();
    console.log("Random event with id: " + event.id + " published");
    responseToCheck =  await publishEvent(event);
    assert.strictEqual(responseToCheck.status, 201);
});

// When

When('the random Event is reached in the datastore', async function () {
    sleep(500) // boundary time spent by azure function to process event
    
    responseToCheck = await queryEventById(event.id);
});

// Then

Then('the datastore returns the {int} event instance', function (count) {
    assert.strictEqual(responseToCheck.data._count, count);
});
