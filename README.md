# SecureBank API

## Overview

SecureBank is a RESTful banking API built with Spring Boot 3. It allows customers to register, open
accounts (cheque or savings), and perform deposits, withdrawals, and transfers — all secured with
JWT-based authentication. It was built to demonstrate SOLID design principles, layered Spring
architecture, and production-ready practices (Docker, Swagger, JPA, BCrypt) in a realistic
banking domain.

---

## Architecture

```
Client (curl / Swagger UI / Frontend)
        │
        ▼
  [ Controller ]          ← HTTP only: parse request, call service, return response
        │
        ▼
 [ Service Interface ]    ← business contract (AuthService, AccountService, TransactionService)
        │
        ▼
 [ ServiceImpl ]          ← business logic lives here; annotated @Transactional where needed
        │
        ▼
  [ Repository ]          ← Spring Data JPA interfaces (no SQL written manually)
        │
        ▼
  [ PostgreSQL ]          ← persistent storage (H2 swapped in for tests)
        │
  [ Spring Security ]     ← cross-cutting: JwtFilter intercepts every /api/** request
```

---

## SOLID Principles Applied

### Single Responsibility
Every class has exactly one reason to change. `AuthController` only handles HTTP concerns (parsing
JSON, returning status codes). All business logic — checking for duplicate usernames, hashing
passwords, generating tokens — lives in `AuthServiceImpl`. `JwtUtil` is solely responsible for
token creation and validation; it knows nothing about users or HTTP.

### Open/Closed
The system is open for extension but closed for modification. `TransactionServiceImpl` handles
`DEPOSIT`, `WITHDRAWAL`, and `TRANSFER` without any modification to `AccountServiceImpl`. Adding a
new transaction type (e.g., `REVERSAL`) requires a new code path in `TransactionServiceImpl` only —
no existing classes need changing. Similarly, new account types can be added to the `AccountType`
enum without touching service logic.

### Liskov Substitution
Tests and controllers program against interfaces (`AccountService`, `TransactionService`,
`AuthService`), never against the concrete `*Impl` classes. This means any implementation can be
substituted — for example, `MockMvc` tests replace `AuthServiceImpl` with a Mockito stub and the
controller behaves identically. Spring itself wires the concrete implementation via the interface
contract.

### Interface Segregation
There is no single `BankingService` god interface. `AccountService` handles accounts only;
`TransactionService` handles transactions only; `AuthService` handles registration and login only.
A class that needs to create accounts does not need to know about transaction history, and vice
versa. Controllers only depend on the interface they actually use.

### Dependency Inversion
High-level modules (controllers) depend on abstractions (interfaces), not on low-level
implementations. Constructor injection is used throughout — no `@Autowired` on fields. This makes
dependencies explicit, prevents hidden coupling, and allows the test framework to inject mocks
without reflection tricks:

```java
// AuthController — depends on the interface, not the implementation
public AuthController(AuthService authService) {
    this.authService = authService;
}
```

---

## Tech Stack

| Technology | Version | Why it was chosen |
|---|---|---|
| Spring Boot | 3.2 | Reduces boilerplate; auto-configures JPA, Security, Validation |
| Spring Security | 6.x | Industry-standard; integrates cleanly with JWT filter chain |
| Spring Data JPA | 3.x | Removes hand-written SQL for CRUD; supports derived query methods |
| Hibernate | 6.x | JPA provider; handles dialect differences between PostgreSQL and H2 |
| PostgreSQL | 15 | Production-grade relational DB; strong ACID guarantees for banking data |
| H2 | in-memory | Zero-config test database; swapped in via `application-test.properties` |
| jjwt | 0.12.3 | Actively maintained JWT library with clean builder API for Spring Boot 3 |
| BCrypt | (via Spring Security) | Adaptive hashing with built-in salt; resistant to rainbow table attacks |
| Springdoc OpenAPI | 2.3 | Auto-generates Swagger UI from annotations; supports JWT bearer auth |
| JUnit 5 + Mockito | 5.x | Standard Java test stack; `@ExtendWith(MockitoExtension.class)` for unit tests |
| MockMvc | (via Spring Boot Test) | Integration-tests controllers in-process without a live server |
| Docker + Compose | latest | Reproducible environment; single command to spin up API + database |
| Maven | 3.x | Mature build tool; managed by Spring Boot parent POM for version alignment |

---

## Getting Started

### Prerequisites

- Java 17+
- Maven 3.8+ (or use the Maven wrapper if added)
- Docker + Docker Compose

### Run with Docker Compose

```bash
# 1. Clone the repository
git clone <repo-url>
cd securebank-api

# 2. Set your JWT secret (must be at least 32 characters)
export JWT_SECRET="your-very-long-secret-key-here-at-least-256-bits"

# 3. Build and start all services (API + PostgreSQL)
docker compose up --build

# 4. Open Swagger UI
# http://localhost:8080/swagger-ui.html
```

The first `docker compose up` will:
1. Pull the `postgres:15-alpine` image
2. Build the Spring Boot app image (multi-stage: compile then package)
3. Start PostgreSQL, wait for its healthcheck to pass
4. Start the API, which auto-creates the schema via `spring.jpa.hibernate.ddl-auto=update`

### Run Locally

```bash
# Requires a running PostgreSQL instance on localhost:5432
# with database "securebank", user "securebank", password "securebank"

export JWT_SECRET="your-very-long-secret-key-here"
mvn spring-boot:run
```

---

## API Reference

| Method | Path | Auth required | Description |
|---|---|---|---|
| POST | `/api/auth/register` | None | Register a new customer; returns JWT |
| POST | `/api/auth/login` | None | Login; returns JWT |
| GET | `/api/accounts` | JWT (CUSTOMER) | List own accounts |
| POST | `/api/accounts` | JWT (CUSTOMER) | Open a new account |
| GET | `/api/accounts/{id}` | JWT (owner or ADMIN) | Get account details |
| DELETE | `/api/accounts/{id}` | JWT (ADMIN only) | Close / delete an account |
| POST | `/api/transactions/deposit` | JWT | Deposit funds into an account |
| POST | `/api/transactions/withdraw` | JWT | Withdraw funds from an account |
| POST | `/api/transactions/transfer` | JWT | Transfer funds between two accounts |
| GET | `/api/transactions/{accountId}` | JWT (owner or ADMIN) | Paginated transaction history |

---

## Sample Requests

### Register

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username": "alice", "email": "alice@example.com", "password": "secret123"}'
```

### Login

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "alice", "password": "secret123"}'
# Response: {"token": "eyJhbGci..."}
```

### Create Account

```bash
curl -X POST http://localhost:8080/api/accounts \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"type": "CHEQUE"}'
```

### Deposit

```bash
curl -X POST http://localhost:8080/api/transactions/deposit \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"accountId": 1, "amount": 500.00}'
```

### Transfer

```bash
curl -X POST http://localhost:8080/api/transactions/transfer \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"fromAccountId": 1, "toAccountId": 2, "amount": 200.00}'
```

---

## Testing

```bash
# Run all tests
mvn test

# Run a specific test class
mvn test -Dtest=AuthControllerTest
mvn test -Dtest=TransactionServiceTest

# Verbose pass/fail output
mvn test | grep -E "Tests run|FAIL|ERROR|BUILD"
```

**What is covered:**

| Layer | Type | Tool |
|---|---|---|
| `AuthServiceImpl` | Unit | Mockito — stubs `UserRepository`, `JwtUtil` |
| `AccountServiceImpl` | Unit | Mockito — stubs `AccountRepository`, `UserRepository` |
| `TransactionServiceImpl` | Unit | Mockito — tests deposit, withdraw, transfer, insufficient funds |
| `AuthController` | Integration | MockMvc + `@SpringBootTest` + H2 |
| `AccountController` | Integration | MockMvc — verifies 401 without JWT, 200 with valid JWT |
| `TransactionController` | Integration | MockMvc — end-to-end deposit and transfer error flows |

All integration tests use H2 in-memory via `@ActiveProfiles("test")` and `application-test.properties`.
Target coverage: 70%+ on the service layer.

---

## Design Decisions

### Why `@Transactional` on transfers
A transfer is two operations: debit `fromAccount` and credit `toAccount`. If the credit fails after
the debit, the customer loses money with no record. `@Transactional` on `TransactionServiceImpl.transfer`
wraps both writes in a single database transaction — if anything throws, Hibernate rolls back both,
leaving balances unchanged.

### Why constructor injection over field injection
Field injection (`@Autowired` on a private field) hides dependencies and makes classes impossible
to instantiate without a Spring context. Constructor injection makes dependencies explicit in the
signature, allows the IDE to detect circular dependencies at compile time, and lets unit tests pass
mocks in without any reflection magic.

### Why service interfaces instead of concrete classes
Controllers depend on `AccountService`, not `AccountServiceImpl`. This means:
- Tests can inject a Mockito stub without starting the full context.
- A future implementation (e.g., `CachedAccountServiceImpl`) can be swapped behind the same interface without changing the controller.
- It enforces a clean boundary: the interface is the contract, the impl is the detail.

### Why JWT over sessions
HTTP sessions require server-side state (a session store that all instances must share). JWT tokens
are self-contained and stateless — any instance of the API can validate a token using only the
shared secret. This makes horizontal scaling trivial and avoids sticky-session routing in Docker/K8s.

---

## Future Improvements

- **Kafka for transaction event streaming** — publish a `TransactionCompletedEvent` after every
  successful deposit/withdrawal/transfer so downstream services (notifications, audit, analytics)
  can consume events without coupling to the API.
- **Rate limiting on auth endpoints** — prevent brute-force attacks on `/api/auth/login` using
  Bucket4j or Spring Cloud Gateway rate limiting.
- **Audit log / transaction rollback history** — store an immutable append-only audit trail of
  every balance change, separate from the `transactions` table, for regulatory compliance.
- **Refresh token support** — issue short-lived access tokens (15 min) and long-lived refresh
  tokens so users don't have to re-login every 24 hours but the blast radius of a leaked access
  token is minimised.