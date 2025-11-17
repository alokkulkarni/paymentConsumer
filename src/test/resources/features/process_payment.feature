Feature: Process Payments
  As a payment consumer
  I want to process payments for customers
  So that funds can be transferred to beneficiaries

  Background:
    Given the payment consumer service is running
    And the external services are available

  Scenario: Successfully process a valid payment
    When a customer processes a payment of "100.00" "USD" from account "ACC001" to account "ACC002"
    Then the response status should be 201
    And the response should contain field "transactionId"
    And the response should contain field "status"
    
  Scenario: Reject payment with insufficient balance
    When a customer processes a payment of "999999.00" "USD" from account "ACC001" to account "ACC002"
    Then the response status should be 400
    
  Scenario: Reject payment for non-existent customer
    When a customer processes a payment of "100.00" "USD" from account "ACC999" to account "ACC002"
    Then the response status should be 400
    
  Scenario: Validate payment request with negative amount
    When a customer processes a payment of "-100.00" "USD" from account "ACC001" to account "ACC002"
    Then the response status should be 400
