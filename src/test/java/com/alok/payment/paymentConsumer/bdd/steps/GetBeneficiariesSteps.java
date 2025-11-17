package com.alok.payment.paymentConsumer.bdd.steps;

import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

/**
 * BDD step definitions for beneficiaries retrieval scenarios.
 */
public class GetBeneficiariesSteps {
    
    @Autowired
    private CommonSteps commonSteps;
    
    @When("a customer with id {string} requests their beneficiaries")
    public void aCustomerWithIdRequestsTheirBeneficiaries(String customerId) {
        try {
            ResponseEntity<String> response = commonSteps.getRestClient().get()
                    .uri("/api/v1/consumer/beneficiaries?customerId=" + customerId)
                    .retrieve()
                    .toEntity(String.class);
            
            commonSteps.setLastResponse(response);
        } catch (Exception e) {
            commonSteps.setLastException(e);
        }
    }
}
