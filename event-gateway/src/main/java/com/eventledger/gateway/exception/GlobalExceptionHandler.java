package com.eventledger.gateway.exception;

import com.eventledger.gateway.dto.ErrorResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EventNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponseDto handleEventNotFound(EventNotFoundException ex) {
        log.warn("Event not found: {}", ex.getMessage());
        return buildError("EVENT_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(AccountServiceUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ErrorResponseDto handleAccountServiceUnavailable(AccountServiceUnavailableException ex) {
        log.error("account-service unavailable: {}", ex.getMessage());
        return buildError("ACCOUNT_SERVICE_UNAVAILABLE", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponseDto handleValidationErrors(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .sorted()
                .collect(Collectors.joining("; "));
        log.warn("Validation failed: {}", details);
        return buildError("VALIDATION_ERROR", details);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponseDto handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return buildError("INTERNAL_ERROR", "An unexpected error occurred");
    }

    private ErrorResponseDto buildError(String code, String message) {
        String traceId = MDC.get("traceId");
        if (traceId == null) {
            traceId = UUID.randomUUID().toString();
        }
        return ErrorResponseDto.builder()
                .error(code)
                .message(message)
                .timestamp(Instant.now().toString())
                .traceId(traceId)
                .build();
    }
}
