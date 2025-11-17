package com.alok.payment.paymentConsumer.bdd.steps;

import com.alok.payment.paymentConsumer.dto.PaymentRequest;
import com.alok.payment.paymentConsumer.model.PaymentType;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

/**
 * BDD step definitions for payment processing scenarios.
 */
public class ProcessPaymentSteps {
    
    @Autowired
    private CommonSteps commonSteps;
    
    @When("a customer processes a payment of {string} {string} from account {string} to account {string}")
    public void aCustomerProcessesPayment(String amount, String currency, String fromAccount, String toAccount) {
        // Use actual destination account that exists in payment processor (ACC002, ACC003, ACC004)
        PaymentRequest request = new PaymentRequest(
            "CUST001",
            fromAccount,
            toAccount,  // Use the toAccount from feature file (should be ACC002, ACC003, etc.)
            new BigDecimal(amount),
            currency,
            PaymentType.DOMESTIC_TRANSFER
        );
        request.setBeneficiaryId(1L);
        
        try {
            ResponseEntity<String> response = commonSteps.getRestClient().post()
                    .uri("/api/v1/consumer/payments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toEntity(String.class);
            
            commonSteps.setLastResponse(response);
        } catch (Exception e) {
            commonSteps.setLastException(e);
        }
    }    @When("a customer {string} processes a payment with invalid amount of ${double}")
    public void aCustomerProcessesPaymentWithInvalidAmount(String customerId, double amount) {
        PaymentRequest request = new PaymentRequest(customerId, "ACC001", "BEN001", 
                new BigDecimal(String.valueOf(amount)), "USD", PaymentType.DOMESTIC_TRANSFER);
        request.setBeneficiaryId(1L);
        
        try {
            ResponseEntity<String> response = commonSteps.getRestClient().post()
                    .uri("/api/v1/consumer/payments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toEntity(String.class);
            
            commonSteps.setLastResponse(response);
        } catch (Exception e) {
            commonSteps.setLastException(e);
        }
    }
}
