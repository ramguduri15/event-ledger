package com.eventledger.account;

import com.eventledger.account.dto.TransactionRequestDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class AccountServiceIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    // -------------------------------------------------------------------------
    // Test 1: POST transaction — happy path returns 201
    // -------------------------------------------------------------------------
    @Test
    void postTransaction_happyPath_returns201() throws Exception {
        String accountId = "acct-" + UUID.randomUUID();
        TransactionRequestDto request = creditRequest("evt-001", new BigDecimal("100.00"), "USD");

        mockMvc.perform(post("/accounts/{id}/transactions", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventId", is("evt-001")))
                .andExpect(jsonPath("$.type", is("CREDIT")))
                .andExpect(jsonPath("$.accountId", is(accountId)))
                .andExpect(jsonPath("$.amount", comparesEqualTo(100.00)))
                .andExpect(jsonPath("$.currency", is("USD")));
    }

    // -------------------------------------------------------------------------
    // Test 2: GET balance — correct SUM(CREDIT) - SUM(DEBIT)
    // -------------------------------------------------------------------------
    @Test
    void getBalance_correctlyComputesCreditMinusDebit() throws Exception {
        String accountId = "acct-balance-" + UUID.randomUUID();

        postTransaction(accountId, creditRequest("evt-c1", new BigDecimal("500.00"), "USD"));
        postTransaction(accountId, debitRequest("evt-d1", new BigDecimal("200.00"), "USD"));
        postTransaction(accountId, creditRequest("evt-c2", new BigDecimal("50.00"), "USD"));

        mockMvc.perform(get("/accounts/{id}/balance", accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId", is(accountId)))
                .andExpect(jsonPath("$.balance", comparesEqualTo(350.00)));
    }

    // -------------------------------------------------------------------------
    // Test 3: GET balance — correct even with out-of-order inserts
    // -------------------------------------------------------------------------
    @Test
    void getBalance_correctEvenWithOutOfOrderInserts() throws Exception {
        String accountId = "acct-ooo-" + UUID.randomUUID();
        Instant now = Instant.now();

        // Insert a later timestamp first, then earlier ones
        postTransaction(accountId, creditRequestAt("evt-late", new BigDecimal("300.00"), "USD", now));
        postTransaction(accountId, debitRequestAt("evt-early", new BigDecimal("100.00"), "USD", now.minusSeconds(60)));

        // Balance must always be 300 - 100 = 200 regardless of insert order
        mockMvc.perform(get("/accounts/{id}/balance", accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance", comparesEqualTo(200.00)));
    }

    // -------------------------------------------------------------------------
    // Test 4: GET account detail — transactions sorted by eventTimestamp ASC
    // -------------------------------------------------------------------------
    @Test
    void getAccountDetail_transactionsSortedByEventTimestampAsc() throws Exception {
        String accountId = "acct-sort-" + UUID.randomUUID();
        Instant base = Instant.parse("2024-01-01T00:00:00Z");

        postTransaction(accountId, creditRequestAt("evt-T3", new BigDecimal("30.00"), "USD", base.plusSeconds(200)));
        postTransaction(accountId, creditRequestAt("evt-T1", new BigDecimal("10.00"), "USD", base));
        postTransaction(accountId, creditRequestAt("evt-T2", new BigDecimal("20.00"), "USD", base.plusSeconds(100)));

        mockMvc.perform(get("/accounts/{id}", accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactions", hasSize(3)))
                .andExpect(jsonPath("$.transactions[0].eventId", is("evt-T1")))
                .andExpect(jsonPath("$.transactions[1].eventId", is("evt-T2")))
                .andExpect(jsonPath("$.transactions[2].eventId", is("evt-T3")));
    }

    // -------------------------------------------------------------------------
    // Test 5: GET health — returns UP/UP
    // -------------------------------------------------------------------------
    @Test
    void getHealth_returnsUp() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")))
                .andExpect(jsonPath("$.database", is("UP")));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void postTransaction(String accountId, TransactionRequestDto req) throws Exception {
        mockMvc.perform(post("/accounts/{id}/transactions", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    private TransactionRequestDto creditRequest(String eventId, BigDecimal amount, String currency) {
        return creditRequestAt(eventId, amount, currency, Instant.now());
    }

    private TransactionRequestDto debitRequest(String eventId, BigDecimal amount, String currency) {
        return debitRequestAt(eventId, amount, currency, Instant.now());
    }

    private TransactionRequestDto creditRequestAt(String eventId, BigDecimal amount, String currency, Instant ts) {
        return TransactionRequestDto.builder()
                .eventId(eventId)
                .type("CREDIT")
                .amount(amount)
                .currency(currency)
                .eventTimestamp(ts)
                .metadata(Map.of("source", "test"))
                .build();
    }

    private TransactionRequestDto debitRequestAt(String eventId, BigDecimal amount, String currency, Instant ts) {
        return TransactionRequestDto.builder()
                .eventId(eventId)
                .type("DEBIT")
                .amount(amount)
                .currency(currency)
                .eventTimestamp(ts)
                .metadata(Map.of())
                .build();
    }
}
