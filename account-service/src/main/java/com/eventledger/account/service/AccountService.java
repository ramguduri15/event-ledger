package com.eventledger.account.service;

import com.eventledger.account.dto.AccountDetailDto;
import com.eventledger.account.dto.BalanceResponseDto;
import com.eventledger.account.dto.HealthResponseDto;
import com.eventledger.account.dto.TransactionRequestDto;
import com.eventledger.account.dto.TransactionResponseDto;
import com.eventledger.account.entity.Account;
import com.eventledger.account.entity.Transaction;
import com.eventledger.account.exception.AccountNotFoundException;
import com.eventledger.account.exception.InvalidTransactionTypeException;
import com.eventledger.account.repository.AccountRepository;
import com.eventledger.account.repository.TransactionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private static final Set<String> VALID_TYPES = Set.of("CREDIT", "DEBIT");

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public TransactionResponseDto recordTransaction(String accountId, TransactionRequestDto request) {
        validateTransactionType(request.getType());

        ensureAccountExists(accountId);

        Transaction transaction = Transaction.builder()
                .accountId(accountId)
                .eventId(request.getEventId())
                .type(request.getType().toUpperCase())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .eventTimestamp(request.getEventTimestamp())
                .receivedAt(Instant.now())
                .metadata(serializeMetadata(request.getMetadata()))
                .build();

        Transaction saved = transactionRepository.save(transaction);
        log.info("Recorded {} transaction eventId={} accountId={} amount={} {}",
                saved.getType(), saved.getEventId(), accountId, saved.getAmount(), saved.getCurrency());

        return toTransactionResponseDto(saved);
    }

    @Transactional(readOnly = true)
    public BalanceResponseDto getBalance(String accountId) {
        requireAccountExists(accountId);

        List<Transaction> transactions = transactionRepository
                .findByAccountIdOrderByEventTimestampAsc(accountId);

        if (transactions.isEmpty()) {
            return BalanceResponseDto.builder()
                    .accountId(accountId)
                    .balance(BigDecimal.ZERO)
                    .currency("USD")
                    .build();
        }

        BigDecimal balance = transactionRepository.computeBalanceByAccountId(accountId);
        String currency = transactions.get(transactions.size() - 1).getCurrency();

        return BalanceResponseDto.builder()
                .accountId(accountId)
                .balance(balance)
                .currency(currency)
                .build();
    }

    @Transactional(readOnly = true)
    public AccountDetailDto getAccountDetail(String accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        List<TransactionResponseDto> transactionDtos = transactionRepository
                .findByAccountIdOrderByEventTimestampAsc(accountId)
                .stream()
                .map(this::toTransactionResponseDto)
                .toList();

        return AccountDetailDto.builder()
                .accountId(account.getAccountId())
                .createdAt(account.getCreatedAt())
                .transactions(transactionDtos)
                .build();
    }

    @Transactional(readOnly = true)
    public HealthResponseDto checkHealth() {
        try {
            transactionRepository.countAll();
            return HealthResponseDto.builder()
                    .status("UP")
                    .database("UP")
                    .build();
        } catch (Exception e) {
            log.error("Health check failed: database unreachable", e);
            return HealthResponseDto.builder()
                    .status("DOWN")
                    .database("DOWN")
                    .build();
        }
    }

    // ---------------------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------------------

    private void ensureAccountExists(String accountId) {
        if (!accountRepository.existsById(accountId)) {
            Account newAccount = Account.builder()
                    .accountId(accountId)
                    .createdAt(Instant.now())
                    .build();
            accountRepository.save(newAccount);
            log.info("Auto-created account accountId={}", accountId);
        }
    }

    private void requireAccountExists(String accountId) {
        if (!accountRepository.existsById(accountId)) {
            throw new AccountNotFoundException(accountId);
        }
    }

    private void validateTransactionType(String type) {
        if (type == null || !VALID_TYPES.contains(type.toUpperCase())) {
            throw new InvalidTransactionTypeException(type);
        }
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

    private TransactionResponseDto toTransactionResponseDto(Transaction t) {
        return TransactionResponseDto.builder()
                .id(t.getId())
                .accountId(t.getAccountId())
                .eventId(t.getEventId())
                .type(t.getType())
                .amount(t.getAmount())
                .currency(t.getCurrency())
                .eventTimestamp(t.getEventTimestamp())
                .receivedAt(t.getReceivedAt())
                .metadata(deserializeMetadata(t.getMetadata()))
                .build();
    }
}
