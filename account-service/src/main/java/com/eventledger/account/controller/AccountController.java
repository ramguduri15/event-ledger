package com.eventledger.account.controller;

import com.eventledger.account.dto.AccountDetailDto;
import com.eventledger.account.dto.BalanceResponseDto;
import com.eventledger.account.dto.HealthResponseDto;
import com.eventledger.account.dto.TransactionRequestDto;
import com.eventledger.account.dto.TransactionResponseDto;
import com.eventledger.account.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping("/accounts/{accountId}/transactions")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponseDto recordTransaction(
            @PathVariable("accountId") String accountId,
            @Valid @RequestBody TransactionRequestDto request) {
        return accountService.recordTransaction(accountId, request);
    }

    @GetMapping("/accounts/{accountId}/balance")
    public BalanceResponseDto getBalance(@PathVariable("accountId") String accountId) {
        return accountService.getBalance(accountId);
    }

    @GetMapping("/accounts/{accountId}")
    public AccountDetailDto getAccountDetail(@PathVariable("accountId") String accountId) {
        return accountService.getAccountDetail(accountId);
    }

    @GetMapping("/health")
    public HealthResponseDto health() {
        return accountService.checkHealth();
    }
}
