package com.eventledger.account.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountDetailDto {

    private String accountId;
    private Instant createdAt;
    private List<TransactionResponseDto> transactions;
}
