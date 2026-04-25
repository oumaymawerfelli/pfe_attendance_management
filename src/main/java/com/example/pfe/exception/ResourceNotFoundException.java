package com.example.pfe.exception;

public class ResourceNotFoundException extends RuntimeException {

    // Constructor 1: Simple message
    public ResourceNotFoundException(String message) {
        super(message);
    }


    // Constructor 2: Smart constructor that builds a formatted message
    public ResourceNotFoundException(String resourceName, String fieldName, Object fieldValue) {
        super(String.format("%s not found with %s : '%s'",
                resourceName, fieldName, fieldValue));
    }
}