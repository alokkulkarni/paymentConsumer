package com.alok.payment.paymentConsumer.service;

import com.alok.payment.paymentConsumer.model.Account;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing customer accounts
 * In-memory implementation for demo purposes
 * In production, this would connect to an account database or service
 */
@Service
public class AccountService {
    
    private static final Logger log = LoggerFactory.getLogger(AccountService.class);
    
    // In-memory storage for demo purposes
    private final Map<String, Account> accountsById = new ConcurrentHashMap<>();
    
    public AccountService() {
        // Initialize with some demo accounts
        initializeDemoAccounts();
    }
    
    /**
     * Get account by customer ID
     * 
     * @param customerId Customer ID
     * @return Account or null if not found
     */
    public Account getAccountByCustomerId(String customerId) {
        if (customerId == null || customerId.trim().isEmpty()) {
            log.warn("Null or empty customer ID provided");
            return null;
        }
        
        Account account = accountsById.get(customerId);
        
        if (account == null) {
            log.warn("No account found for customer ID: {}", customerId);
        } else {
            log.info("Found account for customer ID: {}", customerId);
        }
        
        return account;
    }
    
    /**
     * Create or update an account
     * 
     * @param account Account to save
     * @return Saved account
     */
    public Account saveAccount(Account account) {
        if (account == null) {
            throw new IllegalArgumentException("Account cannot be null");
        }
        if (account.getCustomerId() == null || account.getCustomerId().trim().isEmpty()) {
            throw new IllegalArgumentException("Customer ID cannot be null or empty");
        }
        
        accountsById.put(account.getCustomerId(), account);
        log.info("Account saved for customer ID: {}", account.getCustomerId());
        
        return account;
    }
    
    /**
     * Initialize demo accounts for testing
     */
    private void initializeDemoAccounts() {
        // Demo account 1
        Account account1 = new Account(
                "CUST001",
                "ACC001",
                "SAVINGS",
                new BigDecimal("10000.00"),
                "USD",
                "ACTIVE"
        );
        account1.setCustomerName("John Doe");
        account1.setEmail("john.doe@example.com");
        account1.setPhoneNumber("+1234567890");
        accountsById.put(account1.getCustomerId(), account1);
        
        // Demo account 2
        Account account2 = new Account(
                "CUST002",
                "ACC002",
                "CHECKING",
                new BigDecimal("5000.00"),
                "USD",
                "ACTIVE"
        );
        account2.setCustomerName("Jane Smith");
        account2.setEmail("jane.smith@example.com");
        account2.setPhoneNumber("+1234567891");
        accountsById.put(account2.getCustomerId(), account2);
        
        // Demo account 3
        Account account3 = new Account(
                "CUST003",
                "ACC003",
                "SAVINGS",
                new BigDecimal("15000.00"),
                "USD",
                "ACTIVE"
        );
        account3.setCustomerName("Bob Johnson");
        account3.setEmail("bob.johnson@example.com");
        account3.setPhoneNumber("+1234567892");
        accountsById.put(account3.getCustomerId(), account3);
        
        log.info("Initialized {} demo accounts", accountsById.size());
    }
}
