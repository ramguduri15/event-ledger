package com.eventledger.gateway.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "event_records")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventRecord {

    @Id
    @Column(nullable = false, updatable = false)
    private String eventId;

    @Column(nullable = false)
    private String accountId;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private Instant eventTimestamp;

    @Column(nullable = false)
    private Instant receivedAt;

    /**
     * JSON-serialized map of arbitrary key-value metadata supplied by the caller.
     */
    @Column(columnDefinition = "TEXT")
    private String metadata;

    /**
     * Processing outcome: PROCESSED, FAILED, or DUPLICATE.
     */
    @Column(nullable = false)
    private String status;
}
