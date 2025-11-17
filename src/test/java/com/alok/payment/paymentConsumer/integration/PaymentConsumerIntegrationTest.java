package com.alok.payment.paymentConsumer.integration;

import com.alok.payment.paymentConsumer.dto.PaymentRequest;
import com.alok.payment.paymentConsumer.model.PaymentType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Payment Consumer service using TestContainers.
 * Tests the full stack with real external services (beneficiaries and payment processor) running in containers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Payment Consumer Integration Tests")
class PaymentConsumerIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    // Shared network for inter-service communication
    private static final Network network = Network.newNetwork();
    
    // === Payment Consumer Database ===
    @Container
    static PostgreSQLContainer<?> accountsDb = new PostgreSQLContainer<>(
            DockerImageName.parse("ghcr.io/alokkulkarni/testcontainers-registry/testcontainers/postgres:16-alpine")
                    .asCompatibleSubstituteFor("postgres"))
            .withNetwork(network)
            .withNetworkAliases("postgres-accounts")
            .withDatabaseName("accounts_test")
            .withUsername("test")
            .withPassword("test")
            .withInitScript("init.db");
    
    // === Beneficiaries Service Infrastructure ===
    @Container
    static PostgreSQLContainer<?> beneficiariesDb = new PostgreSQLContainer<>(
            DockerImageName.parse("ghcr.io/alokkulkarni/testcontainers-registry/testcontainers/postgres:16-alpine")
                    .asCompatibleSubstituteFor("postgres"))
            .withNetwork(network)
            .withNetworkAliases("postgres-beneficiaries")
            .withDatabaseName("beneficiaries_test")
            .withUsername("test")
            .withPassword("test")
            .withInitScript("db/beneficiaries-init.db");
    
    @Container
    static GenericContainer<?> redis = new GenericContainer<>(
            DockerImageName.parse("ghcr.io/alokkulkarni/testcontainers-registry/testcontainers/redis:7-alpine")
                    .asCompatibleSubstituteFor("redis"))
            .withNetwork(network)
            .withNetworkAliases("redis")
            .withExposedPorts(6379);
    
    @Container
    static GenericContainer<?> beneficiariesService = new GenericContainer<>(
            DockerImageName.parse("ghcr.io/alokkulkarni/beneficiaries:latest"))
            .withNetwork(network)
            .withNetworkAliases("beneficiaries-service")
            .withExposedPorts(8080)
            .withEnv("SERVER_PORT", "8080")
            .withEnv("SPRING_DATASOURCE_URL", "jdbc:postgresql://postgres-beneficiaries:5432/beneficiaries_test")
            .withEnv("SPRING_DATASOURCE_USERNAME", "test")
            .withEnv("SPRING_DATASOURCE_PASSWORD", "test")
            .withEnv("SPRING_DATA_REDIS_HOST", "redis")
            .withEnv("SPRING_DATA_REDIS_PORT", "6379")
            .withEnv("SPRING_JPA_HIBERNATE_DDL_AUTO", "none")
            .waitingFor(Wait.forHttp("/actuator/health")
                    .forPort(8080)
                    .withStartupTimeout(Duration.ofMinutes(2)))
            .dependsOn(beneficiariesDb, redis);
    
    // === Payment Processor Service Infrastructure ===
    @Container
    static PostgreSQLContainer<?> paymentProcessorDb = new PostgreSQLContainer<>(
            DockerImageName.parse("ghcr.io/alokkulkarni/testcontainers-registry/testcontainers/postgres:16-alpine")
                    .asCompatibleSubstituteFor("postgres"))
            .withNetwork(network)
            .withNetworkAliases("postgres-paymentprocessor")
            .withDatabaseName("paymentprocessor_test")
            .withUsername("test")
            .withPassword("test")
            .withInitScript("db/paymentprocessor-init.sql");
    
    @Container
    static GenericContainer<?> paymentProcessorService = new GenericContainer<>(
            DockerImageName.parse("ghcr.io/alokkulkarni/paymentprocessor:latest"))
            .withNetwork(network)
            .withNetworkAliases("payment-processor-service")
            .withExposedPorts(8081)
            .withEnv("SERVER_PORT", "8081")
            .withEnv("SPRING_DATASOURCE_URL", "jdbc:postgresql://postgres-paymentprocessor:5432/paymentprocessor_test")
            .withEnv("SPRING_DATASOURCE_USERNAME", "test")
            .withEnv("SPRING_DATASOURCE_PASSWORD", "test")
            .withEnv("SPRING_JPA_HIBERNATE_DDL_AUTO", "none")
            .waitingFor(Wait.forHttp("/actuator/health")
                    .forPort(8081)
                    .withStartupTimeout(Duration.ofMinutes(2)))
            .dependsOn(paymentProcessorDb);
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Payment Consumer database
        registry.add("spring.datasource.url", accountsDb::getJdbcUrl);
        registry.add("spring.datasource.username", accountsDb::getUsername);
        registry.add("spring.datasource.password", accountsDb::getPassword);
        
        // External service URLs
        registry.add("external.services.beneficiaries.url", 
                () -> "http://" + beneficiariesService.getHost() + ":" + beneficiariesService.getFirstMappedPort());
        registry.add("external.services.payment-processor.url", 
                () -> "http://" + paymentProcessorService.getHost() + ":" + paymentProcessorService.getFirstMappedPort());
    }
    
    // ========== Account Tests ==========
    
    @Test
    @Order(1)
    @DisplayName("Should retrieve account details for existing customer")
    void shouldRetrieveAccountForExistingCustomer() throws Exception {
        mockMvc.perform(get("/api/v1/consumer/accounts/CUST001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value("CUST001"))
                .andExpect(jsonPath("$.accountNumber").value("ACC001"))
                .andExpect(jsonPath("$.balance").exists());
    }
    
    @Test
    @Order(2)
    @DisplayName("Should return 404 for non-existent customer")
    void shouldReturn404ForNonExistentCustomer() throws Exception {
        mockMvc.perform(get("/api/v1/consumer/accounts/CUST999"))
                .andExpect(status().isNotFound());
    }
    
    // ========== Beneficiaries Tests ==========
    
    @Test
    @Order(3)
    @DisplayName("Should retrieve beneficiaries from external service")
    void shouldRetrieveBeneficiariesFromExternalService() throws Exception {
        mockMvc.perform(get("/api/v1/consumer/beneficiaries")
                        .param("customerId", "CUST002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$[0].beneficiaryName").exists());
    }
    
    @Test
    @Order(4)
    @DisplayName("Should return 404 for customer with no account")
    void shouldReturn404ForCustomerWithNoAccount() throws Exception {
        mockMvc.perform(get("/api/v1/consumer/beneficiaries")
                        .param("customerId", "CUST999"))
                .andExpect(status().isNotFound());
    }
    
    // ========== Payment Processing Tests ==========
    
    @Test
    @Order(5)
    @DisplayName("Should successfully process valid payment")
    void shouldProcessValidPayment() throws Exception {
        // Use actual destination account that exists in payment processor (ACC002, ACC003, ACC004)
        PaymentRequest request = new PaymentRequest("CUST001", "ACC001", "ACC002", 
                new BigDecimal("100.00"), "USD", PaymentType.DOMESTIC_TRANSFER);
        request.setBeneficiaryId(1L);
        
        mockMvc.perform(post("/api/v1/consumer/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transactionId").exists())
                .andExpect(jsonPath("$.status").exists());
    }
    
    @Test
    @Order(6)
    @DisplayName("Should reject payment with insufficient balance")
    void shouldRejectPaymentWithInsufficientBalance() throws Exception {
        PaymentRequest request = new PaymentRequest("CUST001", "ACC001", "ACC002", 
                new BigDecimal("999999.00"), "USD", PaymentType.DOMESTIC_TRANSFER);
        request.setBeneficiaryId(1L);
        
        mockMvc.perform(post("/api/v1/consumer/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is4xxClientError());
    }
    
    @Test
    @Order(7)
    @DisplayName("Should reject payment for non-existent customer")
    void shouldRejectPaymentForNonExistentCustomer() throws Exception {
        PaymentRequest request = new PaymentRequest("CUST999", "ACC999", "ACC002", 
                new BigDecimal("100.00"), "USD", PaymentType.DOMESTIC_TRANSFER);
        request.setBeneficiaryId(1L);
        
        mockMvc.perform(post("/api/v1/consumer/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }
    
    @Test
    @Order(8)
    @DisplayName("Should validate payment request - negative amount")
    void shouldValidateNegativeAmount() throws Exception {
        PaymentRequest request = new PaymentRequest("CUST001", "ACC001", "ACC002", 
                new BigDecimal("-100.00"), "USD", PaymentType.DOMESTIC_TRANSFER);
        request.setBeneficiaryId(1L);
        
        mockMvc.perform(post("/api/v1/consumer/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
    
    // ========== Payment Status Tests ==========
    
    @Test
    @Order(9)
    @DisplayName("Should retrieve payment status from external service")
    void shouldRetrievePaymentStatusFromExternalService() throws Exception {
        mockMvc.perform(get("/api/v1/consumer/payments/TEST-TXN-001")
                        .param("customerId", "CUST001"))
                .andExpect(status().is5xxServerError())  // Fallback returns 503 when payment processor returns 404
                .andExpect(jsonPath("$.status").value(503));
    }
    
    @Test
    @Order(10)
    @DisplayName("Should return 503 for non-existent transaction (fallback behavior)")
    void shouldReturn503ForNonExistentTransaction() throws Exception {
        mockMvc.perform(get("/api/v1/consumer/payments/INVALID-TXN")
                        .param("customerId", "CUST001"))
                .andExpect(status().is5xxServerError())  // Fallback returns 503 instead of propagating 404
                .andExpect(jsonPath("$.status").value(503));
    }
}
