package org.example.protushybrid.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.WebRequest;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Global exception handler for all REST controllers.
 * Provides consistent error responses and proper HTTP status codes.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handle resource not found exceptions
     */
    @ExceptionHandler({
        RuntimeException.class
    })
    public ResponseEntity<Map<String, Object>> handleNotFoundException(RuntimeException ex, WebRequest request) {
        // Check if it's a "not found" exception by message
        String message = ex.getMessage();
        if (message != null && (message.contains("not found") || message.contains("Not found"))) {
            log.warn("Resource not found: {}", message);
            return buildErrorResponse(HttpStatus.NOT_FOUND, "Resource not found", message, request);
        }

        // Otherwise treat as internal error
        return handleGeneralException(ex, request);
    }

    /**
     * Handle illegal argument exceptions
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(IllegalArgumentException ex, WebRequest request) {
        log.warn("Invalid argument: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Invalid request parameter", ex.getMessage(), request);
    }

    /**
     * Handle illegal state exceptions
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalStateException(IllegalStateException ex, WebRequest request) {
        log.error("Illegal state: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.CONFLICT, "Operation not allowed in current state", ex.getMessage(), request);
    }

    /**
     * Handle unsupported operation exceptions
     */
    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<Map<String, Object>> handleUnsupportedOperationException(UnsupportedOperationException ex, WebRequest request) {
        log.warn("Unsupported operation: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.NOT_IMPLEMENTED, "Operation not supported", ex.getMessage(), request);
    }

    /**
     * Handle all other exceptions
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneralException(Exception ex, WebRequest request) {
        log.error("Unexpected error occurred", ex);
        return buildErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Internal server error",
            "An unexpected error occurred. Please try again or contact support.",
            request
        );
    }

    /**
     * Build standardized error response
     */
    private ResponseEntity<Map<String, Object>> buildErrorResponse(
            HttpStatus status,
            String error,
            String message,
            WebRequest request) {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", OffsetDateTime.now().toString());
        body.put("status", status.value());
        body.put("error", error);
        body.put("message", message);
        body.put("path", request.getDescription(false).replace("uri=", ""));

        return new ResponseEntity<>(body, status);
    }
}
