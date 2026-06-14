package com.eventledger.gateway.controller;

import com.eventledger.gateway.dto.EventRequestDto;
import com.eventledger.gateway.dto.EventResponseDto;
import com.eventledger.gateway.dto.HealthResponseDto;
import com.eventledger.gateway.service.EventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    /**
     * Returns 201 for new events, 200 for idempotent duplicates.
     */
    @PostMapping("/events")
    public ResponseEntity<EventResponseDto> submitEvent(@Valid @RequestBody EventRequestDto request) {
        EventResponseDto response = eventService.processEvent(request);
        HttpStatus status = "DUPLICATE".equals(response.getStatus()) ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(response);
    }

    @GetMapping("/events/{id}")
    public EventResponseDto getEvent(@PathVariable("id") String id) {
        return eventService.getEventById(id);
    }

    @GetMapping("/events")
    public List<EventResponseDto> getEventsByAccount(@RequestParam("account") String accountId) {
        return eventService.getEventsByAccount(accountId);
    }

    @GetMapping("/health")
    public HealthResponseDto health() {
        return eventService.checkHealth();
    }
}
