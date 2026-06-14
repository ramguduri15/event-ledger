package com.eventledger.gateway;

import com.eventledger.gateway.dto.EventRequestDto;
import com.eventledger.gateway.entity.EventRecord;
import com.eventledger.gateway.repository.EventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@TestPropertySource(properties = "account.service.base-url=http://localhost:9090")
class EventGatewayIntegrationTest {

    private static WireMockServer wireMockServer;

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired EventRepository eventRepository;

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().port(9090));
        wireMockServer.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMockServer.stop();
    }

    @BeforeEach
    void resetWireMock() {
        wireMockServer.resetAll();
        stubAccountServiceSuccess();
    }

    // -------------------------------------------------------------------------
    // Test 1: POST /events — happy path returns 201
    // -------------------------------------------------------------------------
    @Test
    void postEvent_happyPath_returns201() throws Exception {
        EventRequestDto request = buildRequest("evt-happy-" + UUID.randomUUID(), "acct-001");

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventId", is(request.getEventId())))
                .andExpect(jsonPath("$.status", is("PROCESSED")))
                .andExpect(jsonPath("$.accountId", is("acct-001")));
    }

    // -------------------------------------------------------------------------
    // Test 2: POST /events — duplicate eventId returns 200 (idempotency)
    // -------------------------------------------------------------------------
    @Test
    void postEvent_duplicateEventId_returns200WithOriginalEvent() throws Exception {
        String eventId = "evt-dup-" + UUID.randomUUID();
        EventRequestDto request = buildRequest(eventId, "acct-dup");

        // First submission — 201
        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Second submission of same eventId — 200 with status DUPLICATE
        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId", is(eventId)))
                .andExpect(jsonPath("$.status", is("DUPLICATE")));
    }

    // -------------------------------------------------------------------------
    // Test 3: POST /events — missing eventId returns 400
    // -------------------------------------------------------------------------
    @Test
    void postEvent_missingEventId_returns400() throws Exception {
        EventRequestDto request = buildRequest(null, "acct-003");

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.traceId", notNullValue()));
    }

    // -------------------------------------------------------------------------
    // Test 4: POST /events — amount = 0 returns 400
    // -------------------------------------------------------------------------
    @Test
    void postEvent_zeroAmount_returns400() throws Exception {
        EventRequestDto request = EventRequestDto.builder()
                .eventId("evt-zero-" + UUID.randomUUID())
                .accountId("acct-004")
                .type("CREDIT")
                .amount(BigDecimal.ZERO)
                .currency("USD")
                .eventTimestamp(Instant.now())
                .build();

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("VALIDATION_ERROR")));
    }

    // -------------------------------------------------------------------------
    // Test 5: POST /events — invalid type returns 400
    // -------------------------------------------------------------------------
    @Test
    void postEvent_invalidType_returns400() throws Exception {
        EventRequestDto request = EventRequestDto.builder()
                .eventId("evt-type-" + UUID.randomUUID())
                .accountId("acct-005")
                .type("TRANSFER")
                .amount(new BigDecimal("10.00"))
                .currency("USD")
                .eventTimestamp(Instant.now())
                .build();

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("VALIDATION_ERROR")));
    }

    // -------------------------------------------------------------------------
    // Test 6: Circuit breaker — account-service 500 five times → 503
    // -------------------------------------------------------------------------
    @Test
    void postEvent_accountServiceRepeatedFailures_circuitBreakerOpens_returns503() throws Exception {
        wireMockServer.resetAll();
        wireMockServer.stubFor(
                WireMock.post(urlMatching("/accounts/.*/transactions"))
                        .willReturn(aResponse().withStatus(500).withBody("{\"error\":\"SERVER_ERROR\"}")));

        for (int i = 0; i < 5; i++) {
            EventRequestDto req = buildRequest("evt-cb-" + i + "-" + UUID.randomUUID(), "acct-cb");
            mockMvc.perform(post("/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.error", is("ACCOUNT_SERVICE_UNAVAILABLE")));
        }
    }

    // -------------------------------------------------------------------------
    // Test 7: GET /events/{id} — returns 200
    // -------------------------------------------------------------------------
    @Test
    void getEvent_existingId_returns200() throws Exception {
        String eventId = "evt-get-" + UUID.randomUUID();
        persistEvent(eventId, "acct-007", Instant.now());

        mockMvc.perform(get("/events/{id}", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId", is(eventId)));
    }

    // -------------------------------------------------------------------------
    // Test 8: GET /events/{id} — not found returns 404
    // -------------------------------------------------------------------------
    @Test
    void getEvent_unknownId_returns404() throws Exception {
        mockMvc.perform(get("/events/{id}", "evt-unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", is("EVENT_NOT_FOUND")));
    }

    // -------------------------------------------------------------------------
    // Test 9: GET /events?account= — ordered by eventTimestamp ASC
    // -------------------------------------------------------------------------
    @Test
    void getEventsByAccount_returnsSortedByEventTimestampAsc() throws Exception {
        String accountId = "acct-sort-" + UUID.randomUUID();
        Instant base = Instant.parse("2024-06-01T00:00:00Z");

        persistEvent("evt-T3", accountId, base.plusSeconds(200));
        persistEvent("evt-T1", accountId, base);
        persistEvent("evt-T2", accountId, base.plusSeconds(100));

        mockMvc.perform(get("/events").param("account", accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].eventId", is("evt-T1")))
                .andExpect(jsonPath("$[1].eventId", is("evt-T2")))
                .andExpect(jsonPath("$[2].eventId", is("evt-T3")));
    }

    // -------------------------------------------------------------------------
    // Test 10: Trace propagation — X-Trace-Id forwarded to account-service
    // -------------------------------------------------------------------------
    @Test
    void postEvent_traceIdPropagatedToAccountService() throws Exception {
        String traceId = UUID.randomUUID().toString();
        EventRequestDto request = buildRequest("evt-trace-" + UUID.randomUUID(), "acct-trace");

        mockMvc.perform(post("/events")
                        .header("X-Trace-Id", traceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        wireMockServer.verify(postRequestedFor(urlMatching("/accounts/.*/transactions"))
                .withHeader("X-Trace-Id", containing(traceId)));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private EventRequestDto buildRequest(String eventId, String accountId) {
        return EventRequestDto.builder()
                .eventId(eventId)
                .accountId(accountId)
                .type("CREDIT")
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .eventTimestamp(Instant.now())
                .metadata(Map.of("source", "test"))
                .build();
    }

    private void persistEvent(String eventId, String accountId, Instant eventTimestamp) {
        eventRepository.save(EventRecord.builder()
                .eventId(eventId)
                .accountId(accountId)
                .type("CREDIT")
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .eventTimestamp(eventTimestamp)
                .receivedAt(Instant.now())
                .status("PROCESSED")
                .build());
    }

    private void stubAccountServiceSuccess() {
        wireMockServer.stubFor(
                WireMock.post(urlMatching("/accounts/.*/transactions"))
                        .willReturn(aResponse()
                                .withStatus(201)
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\"id\":1,\"eventId\":\"ok\"}")));
    }
}
