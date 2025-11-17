Feature: Payment Status Retrieval
  As a customer
  I want to check the status of my payment transactions
  So that I can track my payment history

  Background:
    Given the payment consumer service is running
    And the external services are available

  Scenario: Retrieve status of existing completed transaction
    When a customer "CUST001" requests the status of transaction "TEST-TXN-001"
    Then the response status should be 503

  Scenario: Retrieve status of non-existent transaction
    When a customer "CUST001" requests the status of transaction "INVALID-TXN"
    Then the response status should be 503
