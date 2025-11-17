package com.alok.payment.paymentConsumer.bdd.steps;

import com.alok.payment.paymentConsumer.dto.PaymentRequest;
import com.alok.payment.paymentConsumer.model.PaymentType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import io.cucumber.java.en.Then;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BDD step definitions for common operations.
 */
public class CommonSteps {
    
    @LocalServerPort
    private int port;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    private RestClient restClient;
    private ResponseEntity<String> lastResponse;
    private Exception lastException;
    private String baseUrl;
    
    @Given("the payment consumer service is running")
    public void thePaymentConsumerServiceIsRunning() {
        baseUrl = "http://localhost:" + port;
        restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
        
        // Verify service health
        ResponseEntity<String> response = restClient.get()
                .uri("/actuator/health")
                .retrieve()
                .toEntity(String.class);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
    
    @Given("the payment consumer service is available")
    public void thePaymentConsumerServiceIsAvailable() {
        thePaymentConsumerServiceIsRunning();
    }
    
    @Given("the external services are available")
    public void theExternalServicesAreAvailable() {
        // External services are validated via TestContainers health checks
        // No additional validation needed
    }
    
    @Then("the response status should be {int}")
    public void theResponseStatusShouldBe(int expectedStatus) {
        if (lastException != null) {
            if (lastException instanceof HttpClientErrorException clientError) {
                assertThat(clientError.getStatusCode().value()).isEqualTo(expectedStatus);
            } else if (lastException instanceof HttpServerErrorException serverError) {
                assertThat(serverError.getStatusCode().value()).isEqualTo(expectedStatus);
            } else {
                throw new AssertionError("Unexpected exception type: " + lastException.getClass().getName());
            }
        } else {
            assertThat(lastResponse).isNotNull();
            assertThat(lastResponse.getStatusCode().value()).isEqualTo(expectedStatus);
        }
    }
    
    @Then("the response should contain field {string}")
    public void theResponseShouldContainField(String fieldName) throws Exception {
        assertThat(lastResponse).isNotNull();
        assertThat(lastResponse.getBody()).isNotNull();
        
        Map<String, Object> responseMap = objectMapper.readValue(lastResponse.getBody(), Map.class);
        assertThat(responseMap).containsKey(fieldName);
    }
    
    @Then("the response field {string} should be {string}")
    public void theResponseFieldShouldBe(String fieldName, String expectedValue) throws Exception {
        assertThat(lastResponse).isNotNull();
        assertThat(lastResponse.getBody()).isNotNull();
        
        Map<String, Object> responseMap = objectMapper.readValue(lastResponse.getBody(), Map.class);
        assertThat(responseMap.get(fieldName)).isEqualTo(expectedValue);
    }
    
    // Protected methods for other step classes to access
    protected RestClient getRestClient() {
        return restClient;
    }
    
    protected void setLastResponse(ResponseEntity<String> response) {
        this.lastResponse = response;
        this.lastException = null;
    }
    
    protected void setLastException(Exception exception) {
        this.lastException = exception;
        this.lastResponse = null;
    }
    
    protected ResponseEntity<String> getLastResponse() {
        return lastResponse;
    }
    
    protected Exception getLastException() {
        return lastException;
    }
}
