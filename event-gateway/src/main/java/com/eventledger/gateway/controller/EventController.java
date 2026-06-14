package com.eventledger.gateway.controller;

import com.eventledger.gateway.dto.EventRequestDto;
import com.eventledger.gateway.dto.EventResponseDto;
import com.eventledger.gateway.dto.HealthResponseDto;
import com.eventledger.gateway.service.EventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @PostMapping("/events")
    @ResponseStatus(HttpStatus.CREATED)
    public EventResponseDto submitEvent(@Valid @RequestBody EventRequestDto request) {
        return eventService.processEvent(request);
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
