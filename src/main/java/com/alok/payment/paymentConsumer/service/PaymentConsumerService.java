package com.alok.payment.paymentConsumer.service;

import com.alok.payment.paymentConsumer.client.BeneficiariesClient;
import com.alok.payment.paymentConsumer.client.PaymentProcessorClient;
import com.alok.payment.paymentConsumer.dto.PaymentRequest;
import com.alok.payment.paymentConsumer.dto.PaymentResponse;
import com.alok.payment.paymentConsumer.exception.PaymentProcessingException;
import com.alok.payment.paymentConsumer.exception.ResourceNotFoundException;
import com.alok.payment.paymentConsumer.model.Account;
import com.alok.payment.paymentConsumer.model.Beneficiary;
import com.alok.payment.paymentConsumer.model.PaymentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Main orchestration service for payment consumer
 * Coordinates calls to beneficiaries and payment processor services
 */
@Service
public class PaymentConsumerService {
    
    private static final Logger log = LoggerFactory.getLogger(PaymentConsumerService.class);
    
    private final BeneficiariesClient beneficiariesClient;
    private final PaymentProcessorClient paymentProcessorClient;
    private final AccountService accountService;
    
    public PaymentConsumerService(
            BeneficiariesClient beneficiariesClient,
            PaymentProcessorClient paymentProcessorClient,
            AccountService accountService) {
        this.beneficiariesClient = beneficiariesClient;
        this.paymentProcessorClient = paymentProcessorClient;
        this.accountService = accountService;
    }
    
    /**
     * Get account details for a customer
     * 
     * @param customerId Customer ID
     * @return Account details
     */
    public Account getAccountDetails(String customerId) {
        if (customerId == null || customerId.trim().isEmpty()) {
            throw new IllegalArgumentException("Customer ID cannot be null or empty");
        }
        
        log.info("Retrieving account details for customer: {}", customerId);
        
        Account account = accountService.getAccountByCustomerId(customerId);
        
        if (account == null) {
            throw new ResourceNotFoundException("Account not found for customer: " + customerId);
        }
        
        return account;
    }
    
    /**
     * Get beneficiaries for a customer
     * 
     * @param customerId Customer ID
     * @param accountNumber Account number (optional)
     * @return List of beneficiaries
     */
    public List<Beneficiary> getBeneficiaries(String customerId, String accountNumber) {
        if (customerId == null || customerId.trim().isEmpty()) {
            throw new IllegalArgumentException("Customer ID cannot be null or empty");
        }
        
        log.info("Retrieving beneficiaries for customer: {}, account: {}", customerId, accountNumber);
        
        // Validate customer has an account
        Account account = accountService.getAccountByCustomerId(customerId);
        if (account == null) {
            throw new ResourceNotFoundException("Account not found for customer: " + customerId);
        }
        
        // Fetch beneficiaries from beneficiaries service
        List<Beneficiary> beneficiaries = beneficiariesClient.getBeneficiaries(customerId, accountNumber);
        
        if (beneficiaries == null) {
            log.warn("Null beneficiaries list returned for customer: {}", customerId);
            throw new ResourceNotFoundException("No beneficiaries found for customer: " + customerId);
        }
        
        log.info("Found {} beneficiaries for customer: {}", beneficiaries.size(), customerId);
        return beneficiaries;
    }
    
    /**
     * Process a payment
     * 
     * @param paymentRequest Payment request
     * @return Payment response
     */
    public PaymentResponse processPayment(PaymentRequest paymentRequest) {
        if (paymentRequest == null) {
            throw new IllegalArgumentException("Payment request cannot be null");
        }
        
        log.info("Processing payment for customer: {} from {} to {} for amount {}", 
                paymentRequest.getCustomerId(),
                paymentRequest.getFromAccount(),
                paymentRequest.getToAccount(),
                paymentRequest.getAmount());
        
        // Validate customer and account
        validatePaymentRequest(paymentRequest);
        
        // If beneficiary ID is provided, validate it exists
        if (paymentRequest.getBeneficiaryId() != null) {
            validateBeneficiary(paymentRequest);
        }
        
        // Prepare payment request for payment processor
        Map<String, Object> processorRequest = buildPaymentProcessorRequest(paymentRequest);
        
        // Call payment processor
        PaymentResponse response = paymentProcessorClient.processPayment(processorRequest);
        
        if (response == null) {
            throw new PaymentProcessingException("Payment processor returned null response");
        }
        
        log.info("Payment processed with status: {} for customer: {}", 
                response.getStatus(), 
                paymentRequest.getCustomerId());
        
        return response;
    }
    
    /**
     * Get payment status
     * 
     * @param transactionId Transaction ID
     * @param customerId Customer ID
     * @return Payment response with status
     */
    public PaymentResponse getPaymentStatus(String transactionId, String customerId) {
        if (transactionId == null || transactionId.trim().isEmpty()) {
            throw new IllegalArgumentException("Transaction ID cannot be null or empty");
        }
        if (customerId == null || customerId.trim().isEmpty()) {
            throw new IllegalArgumentException("Customer ID cannot be null or empty");
        }
        
        log.info("Retrieving payment status for transaction: {} and customer: {}", 
                transactionId, customerId);
        
        // Validate customer exists
        Account account = accountService.getAccountByCustomerId(customerId);
        if (account == null) {
            throw new ResourceNotFoundException("Account not found for customer: " + customerId);
        }
        
        PaymentResponse response = paymentProcessorClient.getPaymentStatus(transactionId);
        
        if (response == null) {
            throw new ResourceNotFoundException("Payment not found for transaction ID: " + transactionId);
        }
        
        return response;
    }
    
    /**
     * Validate payment request
     */
    private void validatePaymentRequest(PaymentRequest paymentRequest) {
        // Validate customer exists
        Account account = accountService.getAccountByCustomerId(paymentRequest.getCustomerId());
        if (account == null) {
            throw new ResourceNotFoundException("Account not found for customer: " + paymentRequest.getCustomerId());
        }
        
        // Validate from account belongs to customer
        if (!account.getAccountNumber().equals(paymentRequest.getFromAccount())) {
            throw new PaymentProcessingException("From account does not belong to customer");
        }
        
        // Validate account is active
        if (account.getStatus() == null || !account.getStatus().equalsIgnoreCase("ACTIVE")) {
            throw new PaymentProcessingException("Account is not active");
        }
        
        // Validate sufficient balance (simplified check)
        if (account.getBalance() != null && 
            account.getBalance().compareTo(paymentRequest.getAmount()) < 0) {
            throw new PaymentProcessingException("Insufficient balance");
        }
    }
    
    /**
     * Validate beneficiary exists and belongs to customer
     */
    private void validateBeneficiary(PaymentRequest paymentRequest) {
        try {
            Beneficiary beneficiary = beneficiariesClient.getBeneficiaryById(
                    paymentRequest.getBeneficiaryId(), 
                    paymentRequest.getCustomerId()
            );
            
            if (beneficiary == null) {
                throw new ResourceNotFoundException(
                        "Beneficiary not found with ID: " + paymentRequest.getBeneficiaryId());
            }
            
            // Validate beneficiary account matches payment to account
            if (beneficiary.getBeneficiaryAccountNumber() != null && 
                !beneficiary.getBeneficiaryAccountNumber().equals(paymentRequest.getToAccount())) {
                throw new PaymentProcessingException(
                        "Beneficiary account number does not match payment to account");
            }
            
            // Validate beneficiary is active
            if (beneficiary.getStatus() == null || 
                !beneficiary.getStatus().equalsIgnoreCase("ACTIVE")) {
                throw new PaymentProcessingException("Beneficiary is not active");
            }
            
        } catch (ResourceNotFoundException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Error validating beneficiary: {}", ex.getMessage(), ex);
            // Continue without beneficiary validation if service is unavailable
            // This is a graceful degradation
        }
    }
    
    /**
     * Build payment processor request from payment request
     */
    private Map<String, Object> buildPaymentProcessorRequest(PaymentRequest paymentRequest) {
        Map<String, Object> request = new HashMap<>();
        request.put("fromAccount", paymentRequest.getFromAccount());
        request.put("toAccount", paymentRequest.getToAccount());
        request.put("amount", paymentRequest.getAmount());
        request.put("currency", paymentRequest.getCurrency());
        request.put("paymentType", paymentRequest.getPaymentType().toString());
        
        if (paymentRequest.getDescription() != null) {
            request.put("description", paymentRequest.getDescription());
        }
        
        return request;
    }
}
