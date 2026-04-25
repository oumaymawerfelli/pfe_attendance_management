package com.example.pfe.exception;

public class BusinessException extends RuntimeException {// It's a type of RuntimeException

    public BusinessException(String message) {
        super(message); // Pass the message to the parent class (RuntimeException)
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}