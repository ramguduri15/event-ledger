package com.eventledger.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionRequestDto {

    @NotBlank(message = "eventId must not be blank")
    private String eventId;

    @NotBlank(message = "type must not be blank")
    private String type;

    @NotNull(message = "amount must not be null")
    @Positive(message = "amount must be positive")
    private BigDecimal amount;

    @NotBlank(message = "currency must not be blank")
    private String currency;

    @NotNull(message = "eventTimestamp must not be null")
    private Instant eventTimestamp;

    private Map<String, Object> metadata;
}
