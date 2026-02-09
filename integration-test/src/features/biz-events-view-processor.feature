Feature: All about payment events consumed by Azure Function biz-event-enrichment

  Scenario: a biz event published on eventhub is stored into biz event view datastore
    Given a biz event with client id "IO" and user type "G" is published on eventhub
    When biz event has been properly stored into datastore after 30000 ms
    Then the datastore returns the event
    And the view datastore returns 2 view user
    And the view datastore returns the view general
    And the view datastore returns the view cart

  Scenario: a biz event of a payment from WISP of an authenticated payer published on eventhub is stored into biz event view datastore
    Given a biz event with client id "WISP" and user type "G" is published on eventhub
    When biz event has been properly stored into datastore after 30000 ms
    Then the datastore returns the event
    And the view datastore returns 2 view user
    And the view datastore returns the view general
    And the view datastore returns the view cart

  Scenario: a biz event of a payment from checkout of an authenticated payer published on eventhub is stored into biz event view datastore
    Given a biz event with client id "CHECKOUT" and user type "REGISTERED" is published on eventhub
    When biz event has been properly stored into datastore after 30000 ms
    Then the datastore returns the event
    And the view datastore returns 2 view user
    And the view datastore returns the view general
    And the view datastore returns the view cart

  Scenario: a biz event of a payment from checkout cart of an authenticated payer published on eventhub is stored into biz event view datastore
    Given a biz event with client id "CHECKOUT_CART" and user type "REGISTERED" is published on eventhub
    When biz event has been properly stored into datastore after 30000 ms
    Then the datastore returns the event
    And the view datastore returns 2 view user
    And the view datastore returns the view general
    And the view datastore returns the view cart

  Scenario: a biz event of a payment from checkout of an authenticated payer published on eventhub is stored into biz event view datastore
    Given a biz event with client id "CHECKOUT" and user type "GUEST" is published on eventhub
    When biz event has been properly stored into datastore after 30000 ms
    Then the datastore returns the event
    And the view datastore returns 1 view user
    And the view datastore returns the view general
    And the view datastore returns the view cart

  Scenario: a biz event of a payment from checkout cart of an authenticated payer published on eventhub is stored into biz event view datastore
    Given a biz event with client id "CHECKOUT_CART" and user type "GUEST" is published on eventhub
    When biz event has been properly stored into datastore after 30000 ms
    Then the datastore returns the event
    And the view datastore returns 1 view user
    And the view datastore returns the view general
    And the view datastore returns the view cart