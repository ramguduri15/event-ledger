# Event Ledger

A take-home project demonstrating production-quality microservice design with
Java 21, Spring Boot 3, Resilience4j, and structured JSON logging.

---

## Architecture Overview

```
 Client
   │
   │  POST /events
   │  GET  /events/{id}
   │  GET  /events?account=
   │  GET  /health
   ▼
┌──────────────────────────────┐
│       event-gateway          │  port 8080
│                              │
│  EventController             │
│  EventService                │
│  AccountServiceClient ───────┼──► POST /accounts/{id}/transactions
│  EventRepository (H2)        │     (X-Trace-Id forwarded)
│  Resilience4j CircuitBreaker │
│  TraceIdFilter (MDC)         │
└──────────────────────────────┘
              │
              │ HTTP (circuit-breaker protected)
              ▼
┌──────────────────────────────┐
│      account-service         │  port 8081
│                              │
│  AccountController           │
│  AccountService              │
│  AccountRepository (H2)      │
│  TransactionRepository (H2)  │
│  TraceIdFilter (MDC)         │
└──────────────────────────────┘
```

**Key design decisions:**

| Concern | Choice | Reason |
|---|---|---|
| Idempotency | eventId stored as PK in gateway DB | Guarantees exactly-once forwarding at the gateway layer |
| Balance | Computed on the fly (SUM CREDIT - SUM DEBIT) | Prevents balance drift from bugs; auditability is free |
| Resiliency | Circuit Breaker (see below) | Stops cascading failure; account-service can restart without taking down the gateway |
| Tracing | UUID propagated via X-Trace-Id + MDC | Correlates logs across both services without a distributed tracing agent |

---

## Prerequisites

| Tool | Minimum version |
|---|---|
| Java | 21 |
| Maven | 3.9+ |
| Docker + Docker Compose | 24+ |

---

## Quick Start with Docker Compose

> **Build first** — the Dockerfiles copy the fat JARs produced by Maven.

```bash
# 1. Build both services
mvn clean package -DskipTests

# 2. Start the stack
docker compose up --build

# account-service starts on :8081
# event-gateway starts on :8080 (waits until account-service health check passes)
```

Verify the stack is up:

```bash
curl http://localhost:8080/health
# {"status":"UP","database":"UP","accountService":"UP"}
```

---

## Running Manually (without Docker)

Open two terminals:

**Terminal 1 — account-service**

```bash
cd account-service
mvn spring-boot:run
# Listening on http://localhost:8081
```

**Terminal 2 — event-gateway**

```bash
cd event-gateway
mvn spring-boot:run
# Listening on http://localhost:8080
```

---

## Running Tests

```bash
# All tests (both modules)
mvn test

# Single module
mvn test -pl account-service
mvn test -pl event-gateway
```

account-service tests use an embedded H2 database — no external dependencies.
event-gateway tests use WireMock to stub account-service on port 9090.

---

## Resiliency Pattern: Circuit Breaker

### Why Circuit Breaker over Retry or Bulkhead?

**Retry** would make callers wait longer on a down-stream service, amplifying latency
and thread consumption. If account-service is truly unavailable, retrying every request
just makes things worse.

**Bulkhead** limits concurrency to a service but does nothing to stop calls when that
service is responding with errors — callers still hang waiting for timeouts.

**Circuit Breaker** (Resilience4j) takes a different approach: after 50% of calls fail
within a sliding window of 5, it *opens* the circuit for 10 seconds.  During that time
the gateway **immediately** returns HTTP 503 without touching account-service at all.
This gives account-service time to recover, prevents thread exhaustion in the gateway,
and makes the failure mode visible to clients (they can retry later instead of waiting).

Configuration (in `application.properties`):

```properties
resilience4j.circuitbreaker.instances.accountService.sliding-window-size=5
resilience4j.circuitbreaker.instances.accountService.failure-rate-threshold=50
resilience4j.circuitbreaker.instances.accountService.wait-duration-in-open-state=10s
```

---

## API Contract

### event-gateway (port 8080)

#### POST /events

Submit a new financial event.

```http
POST /events
Content-Type: application/json
X-Trace-Id: optional-uuid   (propagated to account-service)

{
  "eventId":        "evt-abc-123",
  "accountId":      "acct-xyz",
  "type":           "CREDIT",          // CREDIT | DEBIT
  "amount":         250.00,
  "currency":       "USD",
  "eventTimestamp": "2026-05-15T14:00:00Z",
  "metadata":       { "source": "mobile-app" }
}
```

**201 Created** (new event):

```json
{
  "eventId":        "evt-abc-123",
  "accountId":      "acct-xyz",
  "type":           "CREDIT",
  "amount":         250.00,
  "currency":       "USD",
  "eventTimestamp": "2026-05-15T14:00:00Z",
  "receivedAt":     "2026-05-15T14:00:00.123Z",
  "metadata":       { "source": "mobile-app" },
  "status":         "PROCESSED"
}
```

**200 OK** (duplicate eventId — idempotent):

Same body as above but `"status": "DUPLICATE"`.

**400 Bad Request** (validation failure):

```json
{
  "error":     "VALIDATION_ERROR",
  "message":   "type must be CREDIT or DEBIT",
  "timestamp": "2026-05-15T14:02:11Z",
  "traceId":   "550e8400-e29b-41d4-a716-446655440000"
}
```

**503 Service Unavailable** (circuit breaker open):

```json
{
  "error":     "ACCOUNT_SERVICE_UNAVAILABLE",
  "message":   "account-service is unavailable. Please try again later.",
  "timestamp": "2026-05-15T14:02:11Z",
  "traceId":   "550e8400-e29b-41d4-a716-446655440000"
}
```

---

#### GET /events/{id}

```http
GET /events/evt-abc-123
```

**200 OK** — `EventResponseDto` (same shape as POST 201 response)

**404 Not Found**:

```json
{
  "error":     "EVENT_NOT_FOUND",
  "message":   "Event not found: evt-abc-123",
  "timestamp": "2026-05-15T14:02:11Z",
  "traceId":   "550e8400-e29b-41d4-a716-446655440000"
}
```

---

#### GET /events?account={accountId}

```http
GET /events?account=acct-xyz
```

**200 OK** — array of `EventResponseDto`, ordered by `eventTimestamp` ASC.  
Returns an empty array `[]` if the account has no events.

---

#### GET /health

```http
GET /health
```

**200 OK**:

```json
{
  "status":         "UP",
  "database":       "UP",
  "accountService": "UP"
}
```

---

### account-service (port 8081)

#### POST /accounts/{accountId}/transactions

```http
POST /accounts/acct-xyz/transactions
Content-Type: application/json
X-Trace-Id: optional-uuid

{
  "eventId":        "evt-abc-123",
  "type":           "CREDIT",
  "amount":         250.00,
  "currency":       "USD",
  "eventTimestamp": "2026-05-15T14:00:00Z",
  "metadata":       { "source": "mobile-app" }
}
```

**201 Created** — `TransactionResponseDto`

Auto-creates the account if it does not yet exist.

---

#### GET /accounts/{accountId}/balance

```http
GET /accounts/acct-xyz/balance
```

**200 OK**:

```json
{
  "accountId": "acct-xyz",
  "balance":   750.00,
  "currency":  "USD"
}
```

Balance is computed on the fly as `SUM(CREDIT amounts) - SUM(DEBIT amounts)`.
A running balance is never stored.

**404 Not Found** if account does not exist.

---

#### GET /accounts/{accountId}

```http
GET /accounts/acct-xyz
```

**200 OK**:

```json
{
  "accountId": "acct-xyz",
  "createdAt": "2026-05-15T13:00:00Z",
  "transactions": [
    {
      "id": 1,
      "accountId": "acct-xyz",
      "eventId": "evt-abc-123",
      "type": "CREDIT",
      "amount": 250.00,
      "currency": "USD",
      "eventTimestamp": "2026-05-15T14:00:00Z",
      "receivedAt": "2026-05-15T14:00:00.123Z",
      "metadata": { "source": "mobile-app" }
    }
  ]
}
```

Transactions are sorted by `eventTimestamp` ASC.

---

#### GET /health

```http
GET /health
```

**200 OK**:

```json
{
  "status":   "UP",
  "database": "UP"
}
```
