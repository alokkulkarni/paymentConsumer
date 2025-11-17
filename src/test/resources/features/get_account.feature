Feature: Account Retrieval
  As a customer
  I want to retrieve my account details
  So that I can view my account information

  Background:
    Given the payment consumer service is running
    And the external services are available

  Scenario: Retrieve account for existing customer
    When a customer "CUST001" requests their account details
    Then the response status should be 200
    And the response field "customerId" should be "CUST001"
    And the response should contain field "accountNumber"
    And the response should contain field "balance"

  Scenario: Retrieve account for non-existent customer
    When a customer "CUST999" requests their account details
    Then the response status should be 404
