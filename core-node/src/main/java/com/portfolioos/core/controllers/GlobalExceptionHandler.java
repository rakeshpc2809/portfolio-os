package com.portfolioos.core.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalStateException(IllegalStateException ex) {
        log.error("Caught IllegalStateException: {}", ex.getMessage());
        
        Map<String, String> response = new HashMap<>();
        response.put("error", "Valuation unavailable");
        response.put("message", ex.getMessage());
        
        // Return 503 Service Unavailable if it's our critical valuation error
        if (ex.getMessage() != null && ex.getMessage().contains("CRITICAL VALUATION ERROR")) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
        }
        
        // Otherwise return 400 Bad Request for standard state exceptions
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
}
