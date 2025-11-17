package com.alok.payment.paymentConsumer.exception;

/**
 * Exception thrown for payment processing errors
 */
public class PaymentProcessingException extends RuntimeException {
    
    public PaymentProcessingException(String message) {
        super(message);
    }
    
    public PaymentProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
