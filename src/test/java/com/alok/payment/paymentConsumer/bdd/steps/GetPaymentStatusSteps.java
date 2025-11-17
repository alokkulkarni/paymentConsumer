package com.alok.payment.paymentConsumer.bdd.steps;

import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

/**
 * BDD step definitions for payment status retrieval scenarios.
 */
public class GetPaymentStatusSteps {
    
    @Autowired
    private CommonSteps commonSteps;
    
    @When("a customer {string} requests the status of transaction {string}")
    public void aCustomerRequestsTheStatusOfTransaction(String customerId, String transactionId) {
        try {
            ResponseEntity<String> response = commonSteps.getRestClient().get()
                    .uri("/api/v1/consumer/payments/" + transactionId + "?customerId=" + customerId)
                    .retrieve()
                    .toEntity(String.class);
            
            commonSteps.setLastResponse(response);
        } catch (Exception e) {
            commonSteps.setLastException(e);
        }
    }
}
