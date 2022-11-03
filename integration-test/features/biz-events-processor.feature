Feature: All about payment events consumed by Azure Function biz-event-processor

  Scenario: a biz event published on eventhub is stored into datastore
    Given a random biz event with id "test-id-1" published on eventhub
    When biz event has been properly stored into datastore after 500 ms
    Then the datastore returns the event with id "test-id-1"