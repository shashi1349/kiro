# Splitwise-Lite

A Spring Boot REST API for tracking shared group expenses, with an algorithm to
**simplify the resulting debts into the minimum number of settlement transactions.**

> Inspired by Splitwise. Built as a portfolio project to practice clean Java
> backend engineering: layered architecture, JPA, JWT auth, validation,
> centralized error handling, and test-driven core logic.

---

## Why this project

Splitting expenses sounds trivial, but doing it correctly forces you to confront
a stack of real backend concerns:

- **Money math** that must be exact to the cent (no floating-point drift).
- **Authorization** scoped to group membership.
- **Atomic writes** when an expense and its shares must persist together.
- **A real algorithm** — given everyone's net balance, produce the *fewest*
  settlement transfers needed to bring every member to zero.

The result is a small but production-shaped service: easy to run, easy to
read, and built to be talked about in an interview.

---

## Tech stack

| Layer            | Choice                                              |
|------------------|-----------------------------------------------------|
| Language         | Java 21                                             |
| Framework        | Spring Boot 3.3 (Web, Validation, Data JPA, Security) |
| Auth             | Stateless JWT (HS256, JJWT 0.12)                    |
| Persistence      | PostgreSQL (production), H2 in PostgreSQL mode (dev/tests) |
| Migrations       | Flyway                                              |
| Build            | Maven                                               |
| Testing          | JUnit 5, Mockito, AssertJ, Spring Boot Test         |
| Money math       | `BigDecimal` (scale 2), integer-cents internally    |

---

## Quick start

```bash
# 1. Build and run unit tests
mvn test

# 2. Start the server (uses in-memory H2 by default — zero infra needed)
mvn spring-boot:run

# 3. The API is now live on http://localhost:8080
```

To run against PostgreSQL instead:

```bash
SPRING_PROFILES_ACTIVE=postgres \
DB_URL=jdbc:postgresql://localhost:5432/splitwise \
DB_USERNAME=splitwise DB_PASSWORD=splitwise \
JWT_SECRET=your-32-byte-or-longer-secret-here \
mvn spring-boot:run
```

---

## End-to-end demo (curl)

A complete walkthrough: register two users, create a group, add an expense,
view balances, and ask the server for the optimal settle-up plan.

```bash
# Register Alice and capture the JWT
TOKEN_A=$(curl -s -X POST localhost:8080/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"alice@example.com","name":"Alice","password":"password123"}' \
  | jq -r '.accessToken')

# Register Bob
curl -s -X POST localhost:8080/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"bob@example.com","name":"Bob","password":"password123"}' >/dev/null

# Alice creates a group (she is auto-enrolled as member)
GROUP_ID=$(curl -s -X POST localhost:8080/groups \
  -H "Authorization: Bearer $TOKEN_A" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Goa Trip","description":"December weekend"}' \
  | jq -r '.id')

# Alice invites Bob
curl -s -X POST localhost:8080/groups/$GROUP_ID/members \
  -H "Authorization: Bearer $TOKEN_A" \
  -H 'Content-Type: application/json' \
  -d '{"email":"bob@example.com"}'

# Alice paid 1000 for hotel; equal split between the two of them
curl -s -X POST localhost:8080/groups/$GROUP_ID/expenses \
  -H "Authorization: Bearer $TOKEN_A" \
  -H 'Content-Type: application/json' \
  -d '{
    "paidBy": 1,
    "description": "Hotel",
    "amount": 1000.00,
    "splitType": "EQUAL",
    "splits": [{"userId": 1}, {"userId": 2}]
  }'

# Net balances (Alice +500, Bob -500)
curl -s localhost:8080/groups/$GROUP_ID/balances \
  -H "Authorization: Bearer $TOKEN_A"

# Optimal settle-up: one transfer of 500 from Bob to Alice
curl -s localhost:8080/groups/$GROUP_ID/settle-up \
  -H "Authorization: Bearer $TOKEN_A"
```

---

## API reference

| Method | Path                                | Description                                |
|--------|-------------------------------------|--------------------------------------------|
| POST   | `/auth/register`                    | Create a new user, returns a JWT           |
| POST   | `/auth/login`                       | Authenticate, returns a JWT                |
| POST   | `/groups`                           | Create a group (creator auto-enrolled)     |
| GET    | `/groups`                           | List groups the current user belongs to    |
| GET    | `/groups/{id}`                      | Get one group                              |
| GET    | `/groups/{id}/members`              | List members                               |
| POST   | `/groups/{id}/members`              | Invite an existing user by email           |
| POST   | `/groups/{id}/expenses`             | Add an expense (EQUAL / EXACT / PERCENT)   |
| GET    | `/groups/{id}/expenses`             | List expenses with their shares            |
| GET    | `/groups/{id}/balances`             | Net balance per user (creditors first)     |
| POST   | `/groups/{id}/settlements`          | Record a manual payment                    |
| GET    | `/groups/{id}/settlements`          | List settlements                           |
| GET    | `/groups/{id}/settle-up`            | **Optimal** transfer plan to clear debts   |

All endpoints except `/auth/**` require an `Authorization: Bearer <jwt>` header.
Errors use [RFC 7807 Problem Details](https://datatracker.ietf.org/doc/html/rfc7807).

---

## Architecture

```
                       HTTP
                         |
           +-------------v---------------+
           |  Spring Security filter     |
           |  - JwtAuthenticationFilter  |  validates token, sets userId as principal
           +-------------+---------------+
                         |
                         v
   +---------------------+----------------------+
   |          @RestController layer             |
   |   AuthController, GroupController,         |   thin: parse DTOs,
   |   ExpenseController, BalanceController,    |   delegate to services
   |   SettlementController                     |
   +---------------------+----------------------+
                         |
                         v
   +---------------------+----------------------+
   |          @Service application layer        |
   |   AuthService, GroupService,               |   business rules,
   |   ExpenseService, BalanceService,          |   @Transactional boundaries,
   |   SettlementService                        |   uses pure components below
   |                                            |
   |   SplitCalculator, DebtSimplifier          |   pure logic, no I/O,
   |                                            |   easy to unit-test
   +---------------------+----------------------+
                         |
                         v
   +---------------------+----------------------+
   |          Domain + Repository layer          |
   |   User, Group, GroupMember, Expense,        |   JPA entities,
   |   ExpenseShare, Settlement                  |   Spring Data interfaces
   +---------------------+----------------------+
                         |
                         v
              PostgreSQL  /  H2 (dev mode)
              (Flyway-managed schema)
```

Every feature follows the same package-by-feature shape: `api/` (controller +
DTOs), `application/` (service + pure helpers), `domain/` (entities + repos).
Cross-cutting code lives in `common/` (security and error handling).

---

## Domain model

```
User ─┬─< GroupMember >─┬─ Group ─< Expense >─< ExpenseShare >─ User
      └────────< Settlement (from, to, amount) >────────┘
```

- A **User** can belong to many **Groups** through **GroupMember**.
- A **Group** has many **Expenses**, each paid by one user and divided into
  many **ExpenseShare** rows (one per participant).
- A **Settlement** is a recorded payment between two users in a group;
  it does not delete any expense, it just offsets a debt.

---

## The settle-up algorithm

Given the net balances of every group member (positive = owed money,
negative = owes money), what is the **minimum number of cash transfers**
needed to bring every balance to zero?

**Two-heap greedy:**

1. Push every positive balance into a max-heap of *creditors*.
2. Push every negative balance (as its absolute value) into a max-heap of *debtors*.
3. While both heaps are non-empty:
   - Pop the largest creditor `C` and the largest debtor `D`.
   - Emit a transfer `D → C` of `min(C, D)`.
   - If anything remains, push it back.

**Properties:**

- At most **N − 1** transfers for **N** users with non-zero balance — every
  iteration zeroes out at least one of them.
- **O(N log N)** time complexity.
- All math is performed in integer cents to guarantee that the sum of
  emitted transfers equals the absolute total credit to the cent.

The general "minimum cash flow" problem is NP-hard. This greedy is the
practical algorithm production apps use; it is optimal in most everyday
cases and never far off in the worst case. See `DebtSimplifier.java`
and the seven-test suite in `DebtSimplifierTest`.

---

## Project structure

```
splitwise-lite/
├── pom.xml
├── README.md
└── src/
    ├── main/
    │   ├── java/com/shashikiranreddy/splitwise/
    │   │   ├── SplitwiseLiteApplication.java
    │   │   ├── common/
    │   │   │   ├── error/GlobalExceptionHandler.java
    │   │   │   └── security/{SecurityConfig, JwtService, JwtAuthenticationFilter}.java
    │   │   ├── auth/      {api, application}/
    │   │   ├── user/      domain/{User, UserRepository}.java
    │   │   ├── group/     {api, application, domain}/
    │   │   ├── expense/   {api, application, domain}/   ← SplitCalculator
    │   │   ├── balance/   {api, application}/
    │   │   └── settlement/{api, application, domain}/  ← DebtSimplifier
    │   └── resources/
    │       ├── application.yml
    │       └── db/migration/V1__init.sql
    └── test/
        └── java/com/shashikiranreddy/splitwise/
            ├── expense/application/SplitCalculatorTest.java
            ├── settlement/application/DebtSimplifierTest.java
            └── balance/application/BalanceServiceTest.java
```

---

## Design decisions and trade-offs

- **Package by feature, not by layer.** Each feature owns its own `api`,
  `application`, and `domain` packages. Adding a new feature is local.
- **Flat foreign keys instead of `@ManyToOne` associations.** Entities hold
  `Long groupId` and `Long userId` rather than full object references. This
  avoids accidental lazy-loading surprises and keeps services boring and
  predictable; the cost is no JPQL graph navigation, which I have not needed.
- **All money is `BigDecimal(scale=2)`, computed in integer cents inside
  algorithms.** Floating-point currency math is a classic subtle bug. The
  calculator and simplifier both move the decimal point internally and only
  return `BigDecimal` at the boundary.
- **JWT subject is the userId.** The filter decodes the token, sets the
  userId as the `Authentication` principal, and controllers read it via
  `@AuthenticationPrincipal Long userId`. No DB lookup per request.
- **Settlements are appended, never edited.** Balances are always recomputed
  from `expenses − settlements`. This keeps the audit trail intact and makes
  "undo a settlement" a row delete instead of a balance patch.
- **RFC 7807 Problem Details** for every error. One handler, consistent
  shape, no hand-rolled error JSON sprinkled around the controllers.
- **H2 in PostgreSQL mode for local dev.** A single Flyway migration runs on
  both engines, so I am not testing against a different schema than I deploy.

---

## Testing

```bash
mvn test          # 24 unit tests, < 5 seconds
```

| Suite                   | What it covers                                              |
|-------------------------|-------------------------------------------------------------|
| `SplitCalculatorTest`   | EQUAL / EXACT / PERCENT splits + validation paths           |
| `DebtSimplifierTest`    | Greedy correctness, ≤ N−1 invariant, cent precision         |
| `BalanceServiceTest`    | Aggregation across expenses + settlements; member auth check |

Mockito is used only where I/O is real (`BalanceServiceTest`). The two
algorithm classes are pure functions and need no mocks.

---

## What I would build next

- **Integration tests** with `@SpringBootTest` + Testcontainers Postgres.
- **Refresh tokens** and password reset.
- **Multi-currency** with FX rate snapshot per expense.
- **Soft delete** on expenses with an audit-trail table.
- **OpenAPI** docs via `springdoc-openapi`.
- **Rate limiting** on `/auth/**` to slow down credential stuffing.
- **GitHub Actions CI** running `mvn verify` on every push.

---

## License

MIT.
