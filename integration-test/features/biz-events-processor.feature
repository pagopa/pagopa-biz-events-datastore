Feature: Test AzureFunction linked with EventHub Trigger (input) and Cosmos DB (output)

  Background:

  Scenario: The biz event incoming from EventHub is processed by function to DB 
    Given a random Event published on EventHub
    When the random Event is reached in the datastore
    Then the datastore returns the 1 event instance