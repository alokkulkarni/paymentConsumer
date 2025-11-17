package com.alok.payment.paymentConsumer.controller;

import com.alok.payment.paymentConsumer.dto.PaymentRequest;
import com.alok.payment.paymentConsumer.dto.PaymentResponse;
import com.alok.payment.paymentConsumer.model.Account;
import com.alok.payment.paymentConsumer.model.Beneficiary;
import com.alok.payment.paymentConsumer.service.PaymentConsumerService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Main controller for payment consumer API
 * Orchestrates customer account retrieval, beneficiary management, and payments
 */
@RestController
@RequestMapping("/api/v1/consumer")
public class PaymentConsumerController {
    
    private static final Logger log = LoggerFactory.getLogger(PaymentConsumerController.class);
    
    private final PaymentConsumerService paymentConsumerService;
    
    public PaymentConsumerController(PaymentConsumerService paymentConsumerService) {
        this.paymentConsumerService = paymentConsumerService;
    }
    
    /**
     * Get customer account details
     * 
     * @param customerId Customer ID
     * @return Account details
     */
    @GetMapping("/accounts/{customerId}")
    public ResponseEntity<Account> getAccountDetails(@PathVariable String customerId) {
        log.info("REST request to get account details for customer: {}", customerId);
        
        if (customerId == null || customerId.trim().isEmpty()) {
            log.warn("Invalid customer ID provided");
            return ResponseEntity.badRequest().build();
        }
        
        Account account = paymentConsumerService.getAccountDetails(customerId);
        
        if (account == null) {
            log.warn("Account not found for customer: {}", customerId);
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(account);
    }
    
    /**
     * Get beneficiaries for a customer
     * 
     * @param customerId Customer ID
     * @param accountNumber Account number (optional)
     * @return List of beneficiaries
     */
    @GetMapping("/beneficiaries")
    public ResponseEntity<List<Beneficiary>> getBeneficiaries(
            @RequestParam(required = true) String customerId,
            @RequestParam(required = false) String accountNumber) {
        log.info("REST request to get beneficiaries for customer: {}, account: {}", 
                customerId, accountNumber);
        
        if (customerId == null || customerId.trim().isEmpty()) {
            log.warn("Invalid customer ID provided");
            return ResponseEntity.badRequest().build();
        }
        
        List<Beneficiary> beneficiaries = paymentConsumerService.getBeneficiaries(customerId, accountNumber);
        
        if (beneficiaries == null || beneficiaries.isEmpty()) {
            log.info("No beneficiaries found for customer: {}", customerId);
            return ResponseEntity.ok(List.of());
        }
        
        return ResponseEntity.ok(beneficiaries);
    }
    
    /**
     * Process a payment
     * 
     * @param paymentRequest Payment request details
     * @return Payment response
     */
    @PostMapping("/payments")
    public ResponseEntity<PaymentResponse> processPayment(@Valid @RequestBody PaymentRequest paymentRequest) {
        log.info("REST request to process payment for customer: {} from {} to {} for amount {}", 
                paymentRequest.getCustomerId(),
                paymentRequest.getFromAccount(),
                paymentRequest.getToAccount(),
                paymentRequest.getAmount());
        
        PaymentResponse response = paymentConsumerService.processPayment(paymentRequest);
        
        if (response == null) {
            log.error("Payment service returned null response");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        
        // Return appropriate status based on payment result
        if (response.getStatus() != null && 
            response.getStatus().toString().contains("COMPLETED")) {
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } else if (response.getStatus() != null && 
                   (response.getStatus().toString().contains("FAILED") ||
                    response.getStatus().toString().contains("FRAUD") ||
                    response.getStatus().toString().contains("INSUFFICIENT"))) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        } else {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
        }
    }
    
    /**
     * Get payment status
     * 
     * @param transactionId Transaction ID
     * @param customerId Customer ID
     * @return Payment response with current status
     */
    @GetMapping("/payments/{transactionId}")
    public ResponseEntity<PaymentResponse> getPaymentStatus(
            @PathVariable String transactionId,
            @RequestParam(required = true) String customerId) {
        log.info("REST request to get payment status for transaction: {} and customer: {}", 
                transactionId, customerId);
        
        if (transactionId == null || transactionId.trim().isEmpty()) {
            log.warn("Invalid transaction ID provided");
            return ResponseEntity.badRequest().build();
        }
        
        if (customerId == null || customerId.trim().isEmpty()) {
            log.warn("Invalid customer ID provided");
            return ResponseEntity.badRequest().build();
        }
        
        PaymentResponse response = paymentConsumerService.getPaymentStatus(transactionId, customerId);
        
        if (response == null) {
            log.warn("Payment not found for transaction: {}", transactionId);
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Health check endpoint
     * 
     * @return Health status
     */
    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        log.debug("Health check request received");
        return ResponseEntity.ok("Payment Consumer Service is running");
    }
}
