package com.eventledger.gateway.client;

import com.eventledger.gateway.dto.ErrorResponseDto;
import com.eventledger.gateway.dto.HealthResponseDto;
import com.eventledger.gateway.exception.AccountServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Dedicated HTTP client for all outbound calls to account-service.
 * All methods are protected by the "accountService" circuit breaker.
 */
@Slf4j
@Component
public class AccountServiceClient {

    private static final String CIRCUIT_BREAKER_NAME = "accountService";
    private static final String TRACE_HEADER = "X-Trace-Id";

    private final RestTemplate restTemplate;
    private final String accountServiceBaseUrl;

    public AccountServiceClient(RestTemplate restTemplate,
                                @Value("${account.service.base-url}") String accountServiceBaseUrl) {
        this.restTemplate = restTemplate;
        this.accountServiceBaseUrl = accountServiceBaseUrl;
    }

    /**
     * Posts a transaction to account-service.
     * Falls back to {@link #postTransactionFallback} when the circuit is open or a call fails.
     */
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "postTransactionFallback")
    public void postTransaction(String accountId, Map<String, Object> transactionPayload) {
        String url = accountServiceBaseUrl + "/accounts/" + accountId + "/transactions";
        HttpHeaders headers = buildHeaders();
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(transactionPayload, headers);

        log.info("Calling account-service POST {} accountId={}", url, accountId);
        restTemplate.exchange(url, HttpMethod.POST, request, Void.class);
    }

    /**
     * Pings the account-service health endpoint.
     * Returns "DOWN" string on any failure — never throws.
     */
    public String pingHealth() {
        try {
            String url = accountServiceBaseUrl + "/health";
            ResponseEntity<HealthResponseDto> response =
                    restTemplate.getForEntity(url, HealthResponseDto.class);
            HealthResponseDto body = response.getBody();
            return (body != null && "UP".equals(body.getStatus())) ? "UP" : "DOWN";
        } catch (Exception e) {
            log.warn("account-service health ping failed: {}", e.getMessage());
            return "DOWN";
        }
    }

    // ---------------------------------------------------------------------------
    // Circuit breaker fallback
    // ---------------------------------------------------------------------------

    @SuppressWarnings("unused")
    private void postTransactionFallback(String accountId,
                                         Map<String, Object> transactionPayload,
                                         Throwable cause) {
        log.error("Circuit breaker fallback triggered for accountId={} cause={}",
                accountId, cause.getMessage());
        throw new AccountServiceUnavailableException(
                "account-service is unavailable. Please try again later.");
    }

    // ---------------------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------------------

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        String traceId = MDC.get("traceId");
        if (traceId != null) {
            headers.set(TRACE_HEADER, traceId);
        }
        return headers;
    }
}
