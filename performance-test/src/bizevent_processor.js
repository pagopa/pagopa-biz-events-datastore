import { sleep, check } from 'k6';
import { SharedArray } from 'k6/data';

import { randomString, getPaymentEventForTest } from './modules/common.js'
import { getDocumentById, deleteDocument } from "./modules/datastore_client.js";
import { publishEvent } from "./modules/event_hub_client.js";

export let options = JSON.parse(open(__ENV.TEST_TYPE));

// read configuration
// note: SharedArray can currently only be constructed inside init code
// according to https://k6.io/docs/javascript-api/k6-data/sharedarray
const varsArray = new SharedArray('vars', function() {
	return JSON.parse(open(`./${__ENV.VARS}`)).environment;
});
// workaround to use shared array (only array should be used)
const vars = varsArray[0];
const cosmosDBURI = `${vars.cosmosDBURI}`;
const databaseID = `${vars.databaseID}`;
const containerID = `${vars.containerID}`;
const eventHubNamespace = `${vars.eventHubNamespace}`;
const eventHubName = `${vars.eventHubName}`;
const eventHubSender = `${vars.eventHubSender}`;
const cosmosDBPrimaryKey = `${__ENV.COSMOS_DB_SUBSCRIPTION_KEY}`;
const eventHubPrimaryKey = `${__ENV.EVENT_HUB_SUBSCRIPTION_KEY}`;
// boundary time (s) to process event: activate trigger, process function, upload event to datastore
const processTime = `${vars.processTime}`;

export function setup() {
	// 2. setup code (once)
	// The setup code runs, setting up the test environment (optional) and generating data
	// used to reuse code for the same VU

	// todo

	// precondition is moved to default fn because in this stage
	// __VU is always 0 and cannot be used to create env properly
}

// teardown the test data
export function teardown(data) {
	// todo
}

function postcondition(id) {
	// verify that published event have been stored properly in the datastore
	let tag = { datastoreMethod: "GetDocumentById" };
	let r = getDocumentById(cosmosDBURI, databaseID, containerID, cosmosDBPrimaryKey, id);

	console.log("GetDocumentById call, Status " + r.status);

	check(r, {
		"Assert published event is in the datastore": (_r) => r.json()._count === 1,
	}, tag);

	deleteDocument(cosmosDBURI, databaseID, containerID, cosmosDBPrimaryKey, id);
}

export default function() {
	// publish event
	let tag = { eventHubMethod: "PublishEvent" };
	const id = randomString(15, "abcdefghijklmnopqrstuvwxyz0123456789");
	let event = getPaymentEventForTest(id);

	let r = publishEvent(eventHubNamespace, eventHubName, eventHubSender, eventHubPrimaryKey, event);

	console.log("PublishEvent call, Status " + r.status);

	check(r, {
		'PublishEvent status is 201': (_r) => r.status === 201,
	}, tag);

	// if the event is published wait and check if it was correctly processed and stored in the datastore
	if (r.status === 201) {
		sleep(processTime);
		postcondition(id);
	}
}
