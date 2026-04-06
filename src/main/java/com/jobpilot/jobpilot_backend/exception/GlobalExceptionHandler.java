package com.jobpilot.jobpilot_backend.exception;

import com.jobpilot.jobpilot_backend.common.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationErrors(
            MethodArgumentNotValidException ex) {

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String field = ((FieldError) error).getField();
            errors.put(field, error.getDefaultMessage());
        });

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Validation failed: " + errors));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Invalid email or password"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception ex) {

        log.error("Unhandled exception [{}]: {}", ex.getClass().getSimpleName(), ex.getMessage(), ex);

        String userMessage = buildUserFriendlyMessage(ex);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(userMessage));
    }

    // Helpers

    private String buildUserFriendlyMessage(Exception ex) {
        String className = ex.getClass().getSimpleName();
        String raw = ex.getMessage() != null ? ex.getMessage() : "";

        if (raw.contains("Host system is missing dependencies")
                || raw.contains("playwright")
                || raw.contains("Chromium")
                || raw.contains("chromium")
                || className.contains("Playwright")) {
            return "Browser automation is not available on this server. " +
                    "Scraping requires the Playwright browser dependencies to be installed. " +
                    "Please check the server setup or run locally.";
        }

        if (raw.contains("Timeout") && (raw.contains("page") || raw.contains("selector"))) {
            return "The browser timed out while loading the job portal. " +
                    "The portal may be slow or blocking automated access. Please try again later.";
        }

        if (className.contains("JpaSystemException")
                || className.contains("DataIntegrityViolationException")
                || className.contains("TransactionSystemException")) {
            return "A database error occurred. Please try again.";
        }


        return "An unexpected error occurred. Please try again or contact support.";
    }
}