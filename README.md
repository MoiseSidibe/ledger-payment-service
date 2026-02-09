# Ledger Payment Service

A production-grade payment and ledger service demonstrating transactional correctness, concurrency safety, and reliable event-driven architecture.

---

## Overview

This service provides a REST API for managing account balances and processing three types of financial transactions:

- **DEBIT**: Withdraw funds from an account
- **CREDIT**: Deposit funds into an account  
- **INTERNAL_TRANSFER**: Transfer funds between two accounts

### Key Features

- ✅ **Transactional Correctness**: Balance updates and payment records are persisted atomically
- ✅ **Concurrency Safety**: Deadlock prevention and race condition handling for concurrent transactions
- ✅ **Idempotency**: Duplicate payment prevention via UUID-based idempotency keys
- ✅ **Reliable Event Delivery**: Transactional outbox pattern with db-scheduler for guaranteed Kafka publishing
- ✅ **Event Ordering**: Kafka partitioning ensures ordered event delivery per account
- ✅ **Audit Trail**: Complete payment history with pagination support

---

## Scope

### In Scope

- Three transaction types: DEBIT, CREDIT, INTERNAL_TRANSFER
- Account balance management with validation (positive balances only, no overdrafts)
- Idempotent payment processing
- Payment history with pagination
- Transactional outbox pattern for event publishing
- Kafka event notifications with proper partitioning
- RESTful API with OpenAPI/Swagger documentation
- PostgreSQL persistence with Flyway migrations
- Cucumber component tests
- Actuator metrics and health checks

### Out of Scope

This is a demonstration project. The following are intentionally **not** implemented:

- **Authentication & Authorization**: No security layer (use API gateway in production)
- **Rate Limiting**: No API throttling
- **Currency & FX**: Single currency (amounts have no currency code), no foreign exchange rates
- **Account Types**: No distinction between personal, business, savings, checking accounts
- **Transaction Limits**: No daily/monthly transaction limits or amount caps
- **Compliance & Regulations**: No KYC, AML compliance checks
- **Fees & Charges**: No transaction fees, overdraft fees, or service charges
- **Scheduled Payments**: No recurring payments or future-dated transactions
- **Refunds & Reversals**: No payment cancellation or reversal mechanism
- **Webhooks**: No webhook notifications for external systems
- **Distributed Tracing**: No OpenTelemetry/Zipkin integration
- **Advanced Monitoring**: Basic metrics only (extend with Grafana/Prometheus in production)
- **Multi-tenancy**: Single tenant design
- **Event Sourcing**: Simple CRUD, not event-sourced
- **CQRS**: Read and write models are not separated
- **Distributed Transactions**: No Saga pattern for cross-service transactions
- **Schema Versioning**: No event versioning strategy
- **Advanced Kafka Features**: No dead letter queues, consumer groups, or retry policies
- **Caching**: No Redis or distributed cache
- **Multi-region**: No disaster recovery or geo-replication

---

## Technology Stack

| Component | Technology | Version |
|-----------|-----------|---------|
| **Language** | Java | 21 |
| **Framework** | Spring Boot | 4.0.2 |
| **Database** | PostgreSQL | Latest |
| **Migrations** | Flyway | Latest |
| **Messaging** | Apache Kafka | Latest |
| **Scheduler** | db-scheduler | 14.0.3 |
| **API Docs** | springdoc-openapi | 2.7.0 |
| **Metrics** | Micrometer + Actuator | 4.0.2 |
| **BDD Tests** | Cucumber | 7.20.1 |
| **Build** | Gradle | 9.3.0 |

---

## Quick Start

### Prerequisites

- **Java 21** (JDK 21+)
- **Docker** and **Docker Compose**
- **curl** or any HTTP client

### 1. Start Infrastructure

Start PostgreSQL and Kafka using Docker Compose:

```bash
docker-compose up -d
```

This starts:
- PostgreSQL on `localhost:5432`
- Kafka on `localhost:9092`

### 2. Build the Application

```bash
./gradlew clean build
```

### 3. Run the Application

```bash
./gradlew bootRun
```

The service will start on `http://localhost:8080`

### 4. Verify It's Running

```bash
curl http://localhost:8080/actuator/health
```

Expected response:
```json
{
  "status": "UP"
}
```

---

## Testing the Application

### Default Test Accounts

The application starts with three pre-configured accounts (via Flyway migration):

| Account ID | Initial Balance |
|------------|----------------|
| ACC001     | 1000.00        |
| ACC002     | 5000.00        |
| ACC003     | 100.00         |

### Swagger UI

Open your browser and navigate to:

**http://localhost:8080/swagger-ui.html**

The Swagger UI provides:
- Interactive API documentation
- "Try it out" feature for testing endpoints
- Automatic idempotency key generation (for local testing only)

### API Examples

#### 1. Check Account Balance

```bash
curl http://localhost:8080/accounts/ACC001
```

Response:
```json
{
  "accountId": "ACC001",
  "balance": 1000.00
}
```

#### 2. Create a DEBIT Transaction (Withdrawal)

```bash
curl -X POST http://localhost:8080/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{
    "type": "DEBIT",
    "fromAccountId": "ACC001",
    "amount": 100.00
  }'
```

Response:
```json
{
  "paymentId": "550e8400-e29b-41d4-a716-446655440000",
  "type": "DEBIT",
  "fromAccountId": "ACC001",
  "toAccountId": null,
  "amount": 100.00,
  "status": "COMPLETED",
  "createdAt": "2026-02-08T10:30:00Z"
}
```

#### 3. Create a CREDIT Transaction (Deposit)

```bash
curl -X POST http://localhost:8080/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{
    "type": "CREDIT",
    "toAccountId": "ACC002",
    "amount": 50.00
  }'
```

#### 4. Create an INTERNAL_TRANSFER

```bash
curl -X POST http://localhost:8080/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{
    "type": "INTERNAL_TRANSFER",
    "fromAccountId": "ACC001",
    "toAccountId": "ACC002",
    "amount": 200.00
  }'
```

#### 5. Get Payment History for an Account

```bash
curl "http://localhost:8080/payments/history/ACC001?page=0&size=20"
```

Response:
```json
{
  "payments": [
    {
      "paymentId": "550e8400-e29b-41d4-a716-446655440000",
      "type": "DEBIT",
      "fromAccountId": "ACC001",
      "toAccountId": null,
      "amount": 100.00,
      "status": "COMPLETED",
      "direction": "OUT",
      "createdAt": "2026-02-08T10:30:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "numberOfElements": 1,
  "totalElements": 5,
  "totalPages": 1
}
```

#### 6. Test Idempotency (Duplicate Prevention)

```bash
# First request - succeeds
IDEM_KEY=$(uuidgen)
curl -X POST http://localhost:8080/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $IDEM_KEY" \
  -d '{"type": "DEBIT", "fromAccountId": "ACC001", "amount": 10.00}'

# Second request with same key - fails with 409
curl -X POST http://localhost:8080/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $IDEM_KEY" \
  -d '{"type": "DEBIT", "fromAccountId": "ACC001", "amount": 10.00}'
```

#### 7. Test Insufficient Funds

```bash
curl -X POST http://localhost:8080/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{
    "type": "DEBIT",
    "fromAccountId": "ACC001",
    "amount": 999999.00
  }'
```

Response (400 Bad Request):
```json
{
  "error": "INSUFFICIENT_FUNDS",
  "message": "Insufficient funds in account ACC001 for amount 999999.0"
}
```

### Running Automated Tests

#### Unit Tests
```bash
./gradlew test --tests "*Test"
```

#### Cucumber Component Tests
```bash
./gradlew test --tests "CucumberTestRunner"
```

#### All Tests
```bash
./gradlew test
```

Tests use **Testcontainers** and will automatically start PostgreSQL and Kafka containers.

---

## Architecture

### High-Level Design

```
┌──────────────┐
│   Client     │
└──────┬───────┘
       │ HTTP
       ▼
┌──────────────────────────────────────┐
│      REST API Layer                  │
│  - PaymentController                 │
│  - AccountController                 │
│  - GlobalExceptionHandler            │
└──────┬───────────────────────────────┘
       │
       ▼
┌──────────────────────────────────────┐
│      Service Layer                   │
│  - PaymentService                    │
│  - AccountService                    │
│  - OutboxEventService                │
└──────┬───────────────────────────────┘
       │
       ▼
┌──────────────────────────────────────┐
│   Payment Strategy (Factory)         │
│  - DebitPaymentStrategy              │
│  - CreditPaymentStrategy             │
│  - InternalTransferPaymentStrategy   │
└──────┬───────────────────────────────┘
       │
       ▼
┌──────────────────────────────────────┐
│   Infrastructure Layer               │
│  - JPA Repositories                  │
│  - MapStruct Mappers                 │
└──────┬───────────────────────────────┘
       │
       ▼
   PostgreSQL

Background Process:
┌──────────────────────────────────────┐
│   OutboxScheduler (db-scheduler)     │
│  - Polls every 2 seconds             │
│  - Publishes events to Kafka         │
│  - Updates event status              │
└──────┬───────────────────────────────┘
       │
       ▼
    Kafka
```

### Domain Model

#### Payment State Machine
```
CREATED → COMPLETED
```

Payment state transitions are explicit and validated in the domain model.

#### Core Entities

**Account**
- `accountId` (String, PK)
- `balance` (BigDecimal)
- Timestamps: `createdAt`, `updatedAt`

**Payment**
- `paymentId` (String, PK, UUID)
- `type` (DEBIT | CREDIT | INTERNAL_TRANSFER)
- `fromAccountId` (nullable)
- `toAccountId` (nullable)
- `amount` (BigDecimal)
- `status` (CREATED | COMPLETED)
- `idempotencyKey` (String, unique)
- Timestamps: `createdAt`, `updatedAt`

**OutboxEvent**
- `eventId` (String, PK, UUID)
- `aggregateId` (Payment ID)
- `partitionKey` (Account ID for ordering)
- `eventType` ("PaymentCompleted")
- `payload` (JSON)
- `status` (NEW | SENT | FAILED)
- Timestamps: `createdAt`, `updatedAt`

### Transactional Outbox Pattern

The outbox pattern ensures reliable event delivery to Kafka:

1. **Transaction Phase**: Payment creation and outbox event write happen in a single database transaction
2. **Polling Phase**: db-scheduler polls for NEW events every 2 seconds
3. **Publishing Phase**: Events are published to Kafka with account ID as partition key
4. **Acknowledgment Phase**: Successfully published events are marked as SENT

**Benefits:**
- Payment correctness is independent of Kafka availability
- No event loss even if Kafka is down during payment creation
- Guaranteed eventual delivery
- Ordered event delivery per account (via partition key)

**Trade-offs:**
- Polling introduces latency (2 seconds)

---

## Concurrency Strategy

### 1. Deadlock Prevention for Internal Transfers

**Problem**: Two concurrent transfers between the same accounts can deadlock:
- Thread 1: ACC001 → ACC002
- Thread 2: ACC002 → ACC001

**Solution**: Lock accounts in alphabetical order
```java
String firstAccountId = fromAccountId.compareTo(toAccountId) < 0 ? fromAccountId : toAccountId;
String secondAccountId = fromAccountId.compareTo(toAccountId) < 0 ? toAccountId : fromAccountId;

// Lock in consistent order
SELECT account_id FROM accounts 
WHERE account_id IN (firstAccountId, secondAccountId) 
FOR UPDATE;
```

### 2. Optimistic Concurrency with Balance Check

**Strategy**: For DEBIT and INTERNAL_TRANSFER, use atomic update with balance check:

```sql
UPDATE accounts 
SET balance = balance - :amount 
WHERE account_id = :accountId 
  AND balance >= :amount;
```

If `updated rows = 0`, query the account:
- If account doesn't exist → `AccountNotFoundException`
- If account exists → `InsufficientFundsException`

This approach:
- ✅ Prevents double spending
- ✅ Handles concurrent payments safely
- ✅ No version field needed

### 3. Idempotency

**Client Responsibility**: Must provide `Idempotency-Key` header (UUID format)

**Server Behavior**:
- Unique constraint on `payments.idempotency_key`
- Duplicate key returns `409 IDEMPOTENCY_CONFLICT`
- Enables safe client retries

### 4. Virtual Threads

Spring Boot 4.0 with virtual threads (Project Loom) enabled:
```yaml
spring:
  threads:
    virtual:
      enabled: true
```

Benefits:
- Better resource utilization for I/O-bound operations
- Higher throughput without additional complexity

---

## Design Principles

### 1. Strategy Pattern for Payment Types

Each transaction type has its own strategy:
- `DebitPaymentStrategy` - Validates and deducts from source account
- `CreditPaymentStrategy` - Adds funds to destination account
- `InternalTransferPaymentStrategy` - Locks both accounts, validates, and transfers

**Benefits:**
- Easy to add new payment types
- Each strategy has single responsibility
- Testable in isolation

### 2. Clean Separation of Concerns

```
API Layer        → Input validation, HTTP concerns
Service Layer    → Business logic, orchestration
Strategy Layer   → Payment type-specific logic
Repository Layer → Data access
Domain Layer     → Business rules, state machines
```

### 3. Fail Fast

- Validate input at API boundary
- Check account existence before processing
- Validate sufficient funds before deducting
- Use Bean Validation (`@Valid`, `@NotNull`)

### 4. Idempotent APIs

All mutation operations require idempotency keys:
- Enables safe retries
- Prevents duplicate payments
- Required for distributed systems

---


## Observability

### Actuator Endpoints

- `/actuator/health` - Overall health status
- `/actuator/health/liveness` - Kubernetes liveness probe
- `/actuator/health/readiness` - Kubernetes readiness probe
- `/actuator/metrics` - Application metrics
- `/actuator/prometheus` - Prometheus format metrics

### Metrics

Simple metrics using Micrometer annotations:
- `api.payment.create` - Payment API response time
- `payment.create` - Payment service execution time (tagged by type)
- `outbox.process` - Outbox processing time
- `payment.strategy.*` - Strategy execution times

---

## Database Schema


### Accounts Table
```sql
CREATE TABLE accounts (
    account_id VARCHAR(50) PRIMARY KEY,
    balance DECIMAL(19, 2) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
```

### Payments Table
```sql
CREATE TABLE payments (
    payment_id VARCHAR(36) PRIMARY KEY,
    type VARCHAR(20) NOT NULL,
    from_account_id VARCHAR(50),
    to_account_id VARCHAR(50),
    amount DECIMAL(19, 2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    idempotency_key VARCHAR(36) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    FOREIGN KEY (from_account_id) REFERENCES accounts(account_id),
    FOREIGN KEY (to_account_id) REFERENCES accounts(account_id)
);
```

### Outbox Events Table
```sql
CREATE TABLE outbox_events (
    event_id VARCHAR(36) PRIMARY KEY,
    aggregate_id VARCHAR(36) NOT NULL,
    partition_key VARCHAR(50) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_outbox_status ON outbox_events(status, created_at);
```
# ledger-payment-service
