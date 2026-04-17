package com.supplier.documents.upload.infrastructure.exceptionhandler;

import com.supplier.documents.upload.domain.exception.DocumentNotFoundException;
import com.supplier.documents.upload.domain.exception.DocumentValidationException;
import com.supplier.documents.upload.domain.exception.DomainException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
// import org.springframework.web.bind.support.WebExchangeBindingException; // TODO: Fix for WebFlux
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler for REST controllers.
 * Maps domain exceptions to HTTP responses.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @ExceptionHandler(DocumentValidationException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleValidationException(DocumentValidationException ex) {
        log.warn("[ExceptionHandler] Validation error: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.builder()
                .code(ex.getErrorCode())
                .message(ex.getMessage())
                .timestamp(LocalDateTime.now().format(FORMATTER))
                .build();
        return Mono.just(ResponseEntity.badRequest().body(error));
    }

    @ExceptionHandler(DocumentNotFoundException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleNotFoundException(DocumentNotFoundException ex) {
        log.warn("[ExceptionHandler] Document not found: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.builder()
                .code(ex.getErrorCode())
                .message(ex.getMessage())
                .timestamp(LocalDateTime.now().format(FORMATTER))
                .build();
        return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(error));
    }

    // TODO: Fix for WebFlux - WebExchangeBindingException is from Spring MVC
    // @ExceptionHandler(WebExchangeBindingException.class)
    // public Mono<ResponseEntity<ValidationErrorResponse>> handleWebExchangeBindingException(WebExchangeBindingException ex) {
    //     log.warn("[ExceptionHandler] Request validation error: {}", ex.getMessage());
    //
    //     Map<String, String> errors = new HashMap<>();
    //     ex.getError().getAllErrors().forEach(error -> {
    //         String fieldName = ((FieldError) error).getField();
    //         String errorMessage = error.getDefaultMessage();
    //         errors.put(fieldName, errorMessage);
    //     });
    //
    //     ValidationErrorResponse error = ValidationErrorResponse.builder()
    //             .code("VALIDATION_ERROR")
    //             .message("Request validation failed")
    //             .fieldErrors(errors)
    //             .timestamp(LocalDateTime.now().format(FORMATTER))
    //             .build();
    //
    //     return Mono.just(ResponseEntity.badRequest().body(error));
    // }

    @ExceptionHandler(DomainException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleDomainException(DomainException ex) {
        log.error("[ExceptionHandler] Domain error: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.builder()
                .code(ex.getErrorCode())
                .message(ex.getMessage())
                .timestamp(LocalDateTime.now().format(FORMATTER))
                .build();
        return Mono.just(ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(error));
    }

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<ErrorResponse>> handleGenericException(Exception ex) {
        log.error("[ExceptionHandler] Unexpected error: {}", ex.getMessage(), ex);
        ErrorResponse error = ErrorResponse.builder()
                .code("INTERNAL_ERROR")
                .message("An unexpected error occurred")
                .timestamp(LocalDateTime.now().format(FORMATTER))
                .build();
        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error));
    }

    // DTOs for error responses
    @lombok.Builder
    @lombok.Data
    public static class ErrorResponse {
        private String code;
        private String message;
        private String timestamp;
    }

    @lombok.Builder
    @lombok.Data
    public static class ValidationErrorResponse {
        private String code;
        private String message;
        private Map<String, String> fieldErrors;
        private String timestamp;
    }
}