package com.alok.payment.paymentConsumer.bdd.config;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

/**
 * Cucumber Spring configuration with TestContainers setup.
 * Initializes all containers in a static block to ensure they're ready before Cucumber scenarios run.
 */
@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class CucumberSpringConfiguration {
    
    private static final Network network = Network.newNetwork();
    
    // Container declarations
    static PostgreSQLContainer<?> accountsDb;
    static PostgreSQLContainer<?> beneficiariesDb;
    static GenericContainer<?> redis;
    static GenericContainer<?> beneficiariesService;
    static PostgreSQLContainer<?> paymentProcessorDb;
    static GenericContainer<?> paymentProcessorService;
    
    // Static block to initialize containers before Spring context
    static {
        // Payment Consumer Database
        accountsDb = new PostgreSQLContainer<>(
                DockerImageName.parse("ghcr.io/alokkulkarni/testcontainers-registry/testcontainers/postgres:16-alpine")
                        .asCompatibleSubstituteFor("postgres"))
                .withNetwork(network)
                .withNetworkAliases("postgres-accounts")
                .withDatabaseName("accounts_test")
                .withUsername("test")
                .withPassword("test")
                .withInitScript("init.db");
        accountsDb.start();
        
        // Beneficiaries Service Infrastructure
        beneficiariesDb = new PostgreSQLContainer<>(
                DockerImageName.parse("ghcr.io/alokkulkarni/testcontainers-registry/testcontainers/postgres:16-alpine")
                        .asCompatibleSubstituteFor("postgres"))
                .withNetwork(network)
                .withNetworkAliases("postgres-beneficiaries")
                .withDatabaseName("beneficiaries_test")
                .withUsername("test")
                .withPassword("test")
                .withInitScript("db/beneficiaries-init.db");
        beneficiariesDb.start();
        
        redis = new GenericContainer<>(
                DockerImageName.parse("ghcr.io/alokkulkarni/testcontainers-registry/testcontainers/redis:7-alpine")
                        .asCompatibleSubstituteFor("redis"))
                .withNetwork(network)
                .withNetworkAliases("redis")
                .withExposedPorts(6379);
        redis.start();
        
        beneficiariesService = new GenericContainer<>(
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
                        .withStartupTimeout(Duration.ofMinutes(2)));
        beneficiariesService.start();
        
        // Payment Processor Service Infrastructure
        paymentProcessorDb = new PostgreSQLContainer<>(
                DockerImageName.parse("ghcr.io/alokkulkarni/testcontainers-registry/testcontainers/postgres:16-alpine")
                        .asCompatibleSubstituteFor("postgres"))
                .withNetwork(network)
                .withNetworkAliases("postgres-paymentprocessor")
                .withDatabaseName("paymentprocessor_test")
                .withUsername("test")
                .withPassword("test")
                .withInitScript("db/paymentprocessor-init.sql");
        paymentProcessorDb.start();
        
        paymentProcessorService = new GenericContainer<>(
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
                        .withStartupTimeout(Duration.ofMinutes(2)));
        paymentProcessorService.start();
    }
    
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
}

