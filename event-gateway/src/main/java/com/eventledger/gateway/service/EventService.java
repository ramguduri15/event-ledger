package com.eventledger.gateway.service;

import com.eventledger.gateway.client.AccountServiceClient;
import com.eventledger.gateway.dto.EventRequestDto;
import com.eventledger.gateway.dto.EventResponseDto;
import com.eventledger.gateway.dto.HealthResponseDto;
import com.eventledger.gateway.entity.EventRecord;
import com.eventledger.gateway.exception.AccountServiceUnavailableException;
import com.eventledger.gateway.exception.EventNotFoundException;
import com.eventledger.gateway.metrics.EventMetrics;
import com.eventledger.gateway.repository.EventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventService {

    private static final String STATUS_PROCESSED = "PROCESSED";
    private static final String STATUS_DUPLICATE = "DUPLICATE";
    private static final String STATUS_FAILED    = "FAILED";

    private final EventRepository eventRepository;
    private final AccountServiceClient accountServiceClient;
    private final EventMetrics eventMetrics;
    private final ObjectMapper objectMapper;

    @Transactional
    public EventResponseDto processEvent(EventRequestDto request) {
        eventMetrics.incrementTotalEventsReceived();

        // Idempotency check — return the existing record without calling account-service again
        Optional<EventRecord> existing = eventRepository.findById(request.getEventId());
        if (existing.isPresent()) {
            log.info("Duplicate event detected eventId={}", request.getEventId());
            eventMetrics.incrementDuplicateEvents();
            EventRecord duplicate = existing.get();
            duplicate.setStatus(STATUS_DUPLICATE);
            return toEventResponseDto(eventRepository.save(duplicate));
        }

        EventRecord record = EventRecord.builder()
                .eventId(request.getEventId())
                .accountId(request.getAccountId())
                .type(request.getType())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .eventTimestamp(request.getEventTimestamp())
                .receivedAt(Instant.now())
                .metadata(serializeMetadata(request.getMetadata()))
                .status(STATUS_PROCESSED)
                .build();

        eventRepository.save(record);
        log.info("Saved event eventId={} accountId={}", record.getEventId(), record.getAccountId());

        try {
            Map<String, Object> transactionPayload = buildTransactionPayload(request);
            accountServiceClient.postTransaction(request.getAccountId(), transactionPayload);
        } catch (AccountServiceUnavailableException e) {
            eventMetrics.incrementAccountServiceCallFailures();
            record.setStatus(STATUS_FAILED);
            eventRepository.save(record);
            throw e;
        }

        return toEventResponseDto(record);
    }

    @Transactional(readOnly = true)
    public EventResponseDto getEventById(String eventId) {
        return eventRepository.findById(eventId)
                .map(this::toEventResponseDto)
                .orElseThrow(() -> new EventNotFoundException(eventId));
    }

    @Transactional(readOnly = true)
    public List<EventResponseDto> getEventsByAccount(String accountId) {
        return eventRepository.findByAccountIdOrderByEventTimestampAsc(accountId)
                .stream()
                .map(this::toEventResponseDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public HealthResponseDto checkHealth() {
        String dbStatus;
        try {
            eventRepository.countAll();
            dbStatus = "UP";
        } catch (Exception e) {
            log.error("Gateway DB health check failed", e);
            dbStatus = "DOWN";
        }

        String accountServiceStatus = accountServiceClient.pingHealth();

        String overallStatus = ("UP".equals(dbStatus) && "UP".equals(accountServiceStatus)) ? "UP" : "DOWN";

        return HealthResponseDto.builder()
                .status(overallStatus)
                .database(dbStatus)
                .accountService(accountServiceStatus)
                .build();
    }

    // ---------------------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------------------

    private Map<String, Object> buildTransactionPayload(EventRequestDto request) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventId", request.getEventId());
        payload.put("type", request.getType());
        payload.put("amount", request.getAmount());
        payload.put("currency", request.getCurrency());
        payload.put("eventTimestamp", request.getEventTimestamp().toString());
        payload.put("metadata", request.getMetadata());
        return payload;
    }

    private String serializeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize metadata, storing null", e);
            return null;
        }
    }

    private Map<String, Object> deserializeMetadata(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize metadata, returning empty map", e);
            return Collections.emptyMap();
        }
    }

    private EventResponseDto toEventResponseDto(EventRecord record) {
        return EventResponseDto.builder()
                .eventId(record.getEventId())
                .accountId(record.getAccountId())
                .type(record.getType())
                .amount(record.getAmount())
                .currency(record.getCurrency())
                .eventTimestamp(record.getEventTimestamp())
                .receivedAt(record.getReceivedAt())
                .metadata(deserializeMetadata(record.getMetadata()))
                .status(record.getStatus())
                .build();
    }
}
