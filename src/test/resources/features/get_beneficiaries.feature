Feature: Get Beneficiaries
  As a payment consumer
  I want to retrieve beneficiaries for a customer
  So that I can process payments to them

  Background:
    Given the payment consumer service is available

  Scenario: Retrieve beneficiaries for customer with multiple beneficiaries
    When a customer with id "CUST002" requests their beneficiaries
    Then the response status should be 200
    
  Scenario: Handle request for non-existent customer
    When a customer with id "CUST999" requests their beneficiaries
    Then the response status should be 404
