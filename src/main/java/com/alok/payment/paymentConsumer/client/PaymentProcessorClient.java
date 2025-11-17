package com.alok.payment.paymentConsumer.client;

import com.alok.payment.paymentConsumer.dto.PaymentResponse;
import com.alok.payment.paymentConsumer.exception.PaymentProcessingException;
import com.alok.payment.paymentConsumer.exception.ServiceUnavailableException;
import com.alok.payment.paymentConsumer.model.PaymentStatus;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Client service for Payment Processor API
 * Includes circuit breaker, retry, and timeout handling
 */
@Service
public class PaymentProcessorClient {
    
    private static final Logger log = LoggerFactory.getLogger(PaymentProcessorClient.class);
    
    private final RestTemplate restTemplate;
    private final String paymentProcessorServiceUrl;
    private final String paymentProcessorBasePath;
    
    public PaymentProcessorClient(
            RestTemplate restTemplate,
            @Value("${external.services.payment-processor.url:http://localhost:8081}") String paymentProcessorServiceUrl,
            @Value("${external.services.payment-processor.base-path:/api/payments}") String paymentProcessorBasePath) {
        this.restTemplate = restTemplate;
        this.paymentProcessorServiceUrl = paymentProcessorServiceUrl;
        this.paymentProcessorBasePath = paymentProcessorBasePath;
    }
    
    /**
     * Process payment with circuit breaker protection
     * 
     * @param paymentRequest Payment request details
     * @return Payment response
     */
    @CircuitBreaker(name = "paymentProcessorService", fallbackMethod = "processPaymentFallback")
    @Retry(name = "paymentProcessorService")
    public PaymentResponse processPayment(Map<String, Object> paymentRequest) {
        if (paymentRequest == null) {
            throw new IllegalArgumentException("Payment request cannot be null");
        }
        
        // Validate required fields
        if (!paymentRequest.containsKey("fromAccount") || paymentRequest.get("fromAccount") == null) {
            throw new IllegalArgumentException("From account is required");
        }
        if (!paymentRequest.containsKey("toAccount") || paymentRequest.get("toAccount") == null) {
            throw new IllegalArgumentException("To account is required");
        }
        if (!paymentRequest.containsKey("amount") || paymentRequest.get("amount") == null) {
            throw new IllegalArgumentException("Amount is required");
        }
        
        log.info("Processing payment from {} to {} for amount {}", 
                paymentRequest.get("fromAccount"), 
                paymentRequest.get("toAccount"), 
                paymentRequest.get("amount"));
        
        try {
            String url = paymentProcessorServiceUrl + paymentProcessorBasePath;
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(paymentRequest, headers);
            
            ResponseEntity<PaymentResponse> response = restTemplate.postForEntity(
                    url,
                    requestEntity,
                    PaymentResponse.class
            );
            
            PaymentResponse paymentResponse = response.getBody();
            
            if (paymentResponse == null) {
                log.error("Received null response from payment processor service");
                throw new PaymentProcessingException("Payment processor returned null response");
            }
            
            log.info("Payment processed with status: {} and transaction ID: {}", 
                    paymentResponse.getStatus(), 
                    paymentResponse.getTransactionId());
            
            return paymentResponse;
            
        } catch (RestClientException ex) {
            log.error("Error calling payment processor service: {}", ex.getMessage(), ex);
            throw new ServiceUnavailableException("Payment Processor", 
                    "Failed to process payment: " + ex.getMessage(), ex);
        }
    }
    
    /**
     * Get payment status with circuit breaker protection
     * 
     * @param transactionId Transaction ID
     * @return Payment response with status
     */
    @CircuitBreaker(name = "paymentProcessorService", fallbackMethod = "getPaymentStatusFallback")
    @Retry(name = "paymentProcessorService")
    public PaymentResponse getPaymentStatus(String transactionId) {
        if (transactionId == null || transactionId.trim().isEmpty()) {
            throw new IllegalArgumentException("Transaction ID cannot be null or empty");
        }
        
        log.info("Fetching payment status for transaction: {}", transactionId);
        
        try {
            String url = paymentProcessorServiceUrl + paymentProcessorBasePath + "/" + transactionId;
            
            ResponseEntity<PaymentResponse> response = restTemplate.getForEntity(
                    url,
                    PaymentResponse.class
            );
            
            PaymentResponse paymentResponse = response.getBody();
            
            if (paymentResponse == null) {
                log.warn("Received null response for transaction ID: {}", transactionId);
                return null;
            }
            
            log.info("Successfully retrieved status for transaction: {}", transactionId);
            return paymentResponse;
            
        } catch (RestClientException ex) {
            log.error("Error calling payment processor service for transaction {}: {}", 
                    transactionId, ex.getMessage(), ex);
            throw new ServiceUnavailableException("Payment Processor", 
                    "Failed to retrieve payment status: " + ex.getMessage(), ex);
        }
    }
    
    /**
     * Fallback method when payment processing fails
     */
    private PaymentResponse processPaymentFallback(Map<String, Object> paymentRequest, Throwable throwable) {
        log.error("Fallback triggered for processPayment due to: {}", throwable.getMessage());
        
        PaymentResponse fallbackResponse = new PaymentResponse();
        fallbackResponse.setStatus(PaymentStatus.FAILED);
        fallbackResponse.setMessage("Payment processing failed");
        fallbackResponse.setFailureReason("Payment processor service is currently unavailable. Please try again later.");
        
        if (paymentRequest != null) {
            fallbackResponse.setFromAccount((String) paymentRequest.get("fromAccount"));
            fallbackResponse.setToAccount((String) paymentRequest.get("toAccount"));
        }
        
        throw new ServiceUnavailableException("Payment Processor", 
                "Payment processor service is currently unavailable", throwable);
    }
    
    /**
     * Fallback method when payment status retrieval fails
     */
    private PaymentResponse getPaymentStatusFallback(String transactionId, Throwable throwable) {
        log.error("Fallback triggered for getPaymentStatus due to: {}", throwable.getMessage());
        throw new ServiceUnavailableException("Payment Processor", 
                "Payment processor service is currently unavailable", throwable);
    }
}
