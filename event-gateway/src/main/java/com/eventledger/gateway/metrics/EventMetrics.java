package com.eventledger.gateway.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Centralizes all custom Micrometer counters for event-gateway.
 * Counters are exposed via /actuator/metrics.
 */
@Component
public class EventMetrics {

    private final Counter totalEventsReceived;
    private final Counter duplicateEvents;
    private final Counter accountServiceCallFailures;

    public EventMetrics(MeterRegistry registry) {
        this.totalEventsReceived = Counter.builder("events.received.total")
                .description("Total number of POST /events requests received")
                .register(registry);

        this.duplicateEvents = Counter.builder("events.duplicate.total")
                .description("Number of events rejected due to duplicate eventId")
                .register(registry);

        this.accountServiceCallFailures = Counter.builder("events.account_service.failures.total")
                .description("Number of failures when calling account-service")
                .register(registry);
    }

    public void incrementTotalEventsReceived() {
        totalEventsReceived.increment();
    }

    public void incrementDuplicateEvents() {
        duplicateEvents.increment();
    }

    public void incrementAccountServiceCallFailures() {
        accountServiceCallFailures.increment();
    }
}
