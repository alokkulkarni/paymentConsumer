package com.alok.payment.paymentConsumer.bdd.steps;

import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

/**
 * BDD step definitions for account retrieval scenarios.
 */
public class GetAccountSteps {
    
    @Autowired
    private CommonSteps commonSteps;
    
    @When("a customer {string} requests their account details")
    public void aCustomerRequestsTheirAccountDetails(String customerId) {
        try {
            ResponseEntity<String> response = commonSteps.getRestClient().get()
                    .uri("/api/v1/consumer/accounts/" + customerId)
                    .retrieve()
                    .toEntity(String.class);
            
            commonSteps.setLastResponse(response);
        } catch (Exception e) {
            commonSteps.setLastException(e);
        }
    }
}
