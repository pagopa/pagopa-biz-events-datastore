Feature: All about payment events consumed by Azure Function biz-event-processor

  Scenario: a biz event published on eventhub is stored into datastore
    Given a random biz event is published on eventhub
    When biz event has been properly stored into datastore after 15000 ms
    Then the datastore returns the event
  
  Scenario: a biz event published on eventhub is skipped by cache
    Given a random biz event is published on eventhub
    When biz event has been properly stored into datastore after 15000 ms
    Then the datastore returns the event
    When the eventhub sends the same biz event again
    Then the datastore returns the not updated event