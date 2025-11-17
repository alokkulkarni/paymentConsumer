package com.alok.payment.paymentConsumer.client;

import com.alok.payment.paymentConsumer.exception.ServiceUnavailableException;
import com.alok.payment.paymentConsumer.model.Beneficiary;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collections;
import java.util.List;

/**
 * Client service for Beneficiaries API
 * Includes circuit breaker, retry, and timeout handling
 */
@Service
public class BeneficiariesClient {
    
    private static final Logger log = LoggerFactory.getLogger(BeneficiariesClient.class);
    
    private final RestTemplate restTemplate;
    private final String beneficiariesServiceUrl;
    private final String beneficiariesBasePath;
    
    public BeneficiariesClient(
            RestTemplate restTemplate,
            @Value("${external.services.beneficiaries.url:http://localhost:8080}") String beneficiariesServiceUrl,
            @Value("${external.services.beneficiaries.base-path:/api/v1/beneficiaries}") String beneficiariesBasePath) {
        this.restTemplate = restTemplate;
        this.beneficiariesServiceUrl = beneficiariesServiceUrl;
        this.beneficiariesBasePath = beneficiariesBasePath;
    }
    
    /**
     * Get beneficiaries for a customer with circuit breaker protection
     * 
     * @param customerId Customer ID
     * @param accountNumber Account number (optional)
     * @return List of beneficiaries
     */
    @CircuitBreaker(name = "beneficiariesService", fallbackMethod = "getBeneficiariesFallback")
    @Retry(name = "beneficiariesService")
    public List<Beneficiary> getBeneficiaries(String customerId, String accountNumber) {
        if (customerId == null || customerId.trim().isEmpty()) {
            throw new IllegalArgumentException("Customer ID cannot be null or empty");
        }
        
        log.info("Fetching beneficiaries for customer: {}, account: {}", customerId, accountNumber);
        
        try {
            String url = beneficiariesServiceUrl + beneficiariesBasePath;
            
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url)
                    .queryParam("customerId", customerId);
            
            if (accountNumber != null && !accountNumber.trim().isEmpty()) {
                builder.queryParam("accountNumber", accountNumber);
            }
            
            ResponseEntity<List<Beneficiary>> response = restTemplate.exchange(
                    builder.toUriString(),
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<Beneficiary>>() {}
            );
            
            List<Beneficiary> beneficiaries = response.getBody();
            
            if (beneficiaries == null) {
                log.warn("Received null response from beneficiaries service");
                return Collections.emptyList();
            }
            
            log.info("Successfully retrieved {} beneficiaries", beneficiaries.size());
            return beneficiaries;
            
        } catch (RestClientException ex) {
            log.error("Error calling beneficiaries service: {}", ex.getMessage(), ex);
            throw new ServiceUnavailableException("Beneficiaries", 
                    "Failed to retrieve beneficiaries: " + ex.getMessage(), ex);
        }
    }
    
    /**
     * Get a specific beneficiary by ID with circuit breaker protection
     * 
     * @param beneficiaryId Beneficiary ID
     * @param customerId Customer ID
     * @return Beneficiary details or null if not found
     */
    @CircuitBreaker(name = "beneficiariesService", fallbackMethod = "getBeneficiaryByIdFallback")
    @Retry(name = "beneficiariesService")
    public Beneficiary getBeneficiaryById(Long beneficiaryId, String customerId) {
        if (beneficiaryId == null) {
            throw new IllegalArgumentException("Beneficiary ID cannot be null");
        }
        if (customerId == null || customerId.trim().isEmpty()) {
            throw new IllegalArgumentException("Customer ID cannot be null or empty");
        }
        
        log.info("Fetching beneficiary ID: {} for customer: {}", beneficiaryId, customerId);
        
        try {
            String url = beneficiariesServiceUrl + beneficiariesBasePath + "/" + beneficiaryId;
            
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url)
                    .queryParam("customerId", customerId);
            
            ResponseEntity<Beneficiary> response = restTemplate.exchange(
                    builder.toUriString(),
                    HttpMethod.GET,
                    null,
                    Beneficiary.class
            );
            
            Beneficiary beneficiary = response.getBody();
            
            if (beneficiary == null) {
                log.warn("Received null response for beneficiary ID: {}", beneficiaryId);
                return null;
            }
            
            log.info("Successfully retrieved beneficiary ID: {}", beneficiaryId);
            return beneficiary;
            
        } catch (RestClientException ex) {
            log.error("Error calling beneficiaries service for ID {}: {}", beneficiaryId, ex.getMessage(), ex);
            throw new ServiceUnavailableException("Beneficiaries", 
                    "Failed to retrieve beneficiary: " + ex.getMessage(), ex);
        }
    }
    
    /**
     * Fallback method when beneficiaries service is unavailable
     */
    private List<Beneficiary> getBeneficiariesFallback(String customerId, String accountNumber, Throwable throwable) {
        log.error("Fallback triggered for getBeneficiaries due to: {}", throwable.getMessage());
        throw new ServiceUnavailableException("Beneficiaries", 
                "Beneficiaries service is currently unavailable", throwable);
    }
    
    /**
     * Fallback method when beneficiary by ID call fails
     */
    private Beneficiary getBeneficiaryByIdFallback(Long beneficiaryId, String customerId, Throwable throwable) {
        log.error("Fallback triggered for getBeneficiaryById due to: {}", throwable.getMessage());
        throw new ServiceUnavailableException("Beneficiaries", 
                "Beneficiaries service is currently unavailable", throwable);
    }
}
