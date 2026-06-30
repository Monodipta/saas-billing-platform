# Architecture Decision Records (ADRs)

> This file captures every significant architectural decision made during the project.
> Each ADR explains: what was decided, why, and what was rejected.
> Update this file every time a meaningful design choice is made.
>
> **Format:** ADR-NNN | Decision | Status | Date

---

## ADR-001 — Monorepo with Maven Multi-Module

**Date:** June 2026  
**Status:** ✅ Accepted

### Decision
Use a single Git repository (monorepo) with a Maven multi-module build structure.

### Reasoning
- All Java services share a `shared` module containing Avro schemas, protobuf definitions,
  and common utilities (JwtClaimsUtil, shared DTOs). A monorepo makes this dependency trivial.
- Cross-service refactoring happens in a single commit and PR instead of coordinating across repos.
- One `docker-compose.yml` and one CI pipeline covers the entire system.
- Better portfolio presentation: one clean GitHub repo, not five fragmented ones.

### Rejected Alternatives
- **Polyrepo (one repo per service):** Rejected — shared schema changes would require
  coordinating multiple repos and versioning the `shared` library as a published artifact.
- **Nx / Turborepo:** Rejected — these are JavaScript-first tools; Maven is the right choice
  for a Java/Spring Boot backbone.

### Consequences
- All Java service POMs have the root `pom.xml` as their parent.
- Python (notification-service) and React (frontend) manage their own dependencies independently
  inside the monorepo — they are not Maven modules.

---

## ADR-002 — Build Tool: Maven (not Gradle)

**Date:** June 2026  
**Status:** ✅ Accepted

### Decision
Use Apache Maven as the build tool for all Java services.

### Reasoning
- Maven's XML-based configuration is explicit and well-understood by Java interviewers.
- Spring Boot's official documentation and Spring Initializr use Maven as the default.
- `spring-boot-maven-plugin`, `avro-maven-plugin`, and `protobuf-maven-plugin` have
  mature, well-documented Maven integrations.
- Dependency management via `<dependencyManagement>` in the root POM is straightforward
  and produces predictable builds.

### Rejected Alternatives
- **Gradle (Kotlin DSL):** Better for large-scale monorepos and faster incremental builds,
  but adds complexity for a portfolio project where clarity matters more than build speed.

---

## ADR-003 — API Gateway: Spring Cloud Gateway

**Date:** June 2026  
**Status:** ✅ Accepted

### Decision
Use Spring Cloud Gateway as the single public entry point for all REST traffic.

### Reasoning
- Native integration with Spring Security for JWT filter chains.
- Reactive (WebFlux-based) — handles high concurrency with fewer threads.
- Route configuration is declarative (YAML or Java DSL) — easy to audit and change.
- Rate limiting via Redis integration is built in (RedisRateLimiter).
- All JWT validation and tenant context propagation happens here — downstream services
  receive enriched requests with `X-Tenant-ID` and `X-User-Role` headers.

### Consequences
- Downstream services NEVER validate JWTs themselves — they trust the headers added by the Gateway.
- The Gateway is the only service with an externally exposed port (8080).
- `/auth/**` routes bypass JWT validation — this is the deliberate exception for login/register.

---

## ADR-004 — JWT Authentication (Stateless, HS256)

**Date:** June 2026  
**Status:** ✅ Accepted

### Decision
Use stateless JWT authentication signed with HMAC-SHA256 (HS256).
JWTs carry: `sub` (user ID), `tenant_id`, `role`, `iat`, `exp`.

### Reasoning
- Stateless: no session store needed. Services scale horizontally without session affinity.
- JWT carries all identity context the downstream services need — no need to call the
  Auth Service on every request.
- HS256 with a strong shared secret is sufficient for a single-region portfolio project.
  (RSA/ES256 would be better for multi-party systems.)

### Rejected Alternatives
- **Session-based auth (server-side sessions):** Rejected — requires a shared session store
  (Redis), creates statefulness, and complicates horizontal scaling.
- **OAuth2 with external identity provider (Keycloak, Auth0):** Rejected — adds operational
  complexity that is out of scope for v1. Can be added as a future enhancement.
- **RS256 (asymmetric):** Deferred — the added complexity of key pair management is not
  warranted for a single-service signing scenario. Can be upgraded later.

### Consequences
- JWT secret is stored in environment variables — never hardcoded.
- Token expiry is set to 24 hours. No refresh token in v1 (out of scope).
- Compromised tokens cannot be revoked before expiry in v1 (token blacklist not implemented).

---

## ADR-005 — Multi-Tenant Isolation: Query-Level Enforcement

**Date:** June 2026  
**Status:** ✅ Accepted

### Decision
Enforce tenant data isolation at the database query level by including `tenant_id`
as a mandatory filter in every query, not just at the API routing level.

### Reasoning
- API-level isolation alone (e.g., checking the route) is insufficient — a bug in the
  routing logic could expose cross-tenant data.
- Query-level enforcement means even if the API layer has a bug, the DB query returns
  nothing for the wrong tenant because `WHERE tenant_id = ?` won't match.
- This is the pattern used by Stripe and similar multi-tenant SaaS platforms.

### Implementation
```java
// Every repository method includes tenant_id:
repo.findByIdAndTenantId(subscriptionId, tenantId);
// tenantId always comes from X-Tenant-ID header (set by the Gateway from the JWT)
```

### Rejected Alternatives
- **Row-Level Security (PostgreSQL RLS):** More powerful but adds complexity —
  requires setting the current tenant in the DB session. Deferred to future enhancement.
- **Separate database per tenant:** Not feasible at this scale and scope.

---

## ADR-006 — Usage Metering: Kafka + Redis + PostgreSQL

**Date:** June 2026  
**Status:** ✅ Accepted

### Decision
Usage events flow through Kafka into the Usage Aggregator, which performs atomic INCRBY
operations in Redis (hot path) and periodically flushes totals to PostgreSQL (durable store).

### Reasoning
- **Kafka:** Decouples event producers from the aggregator. Provides durable storage and
  replay. Partitioned by `tenant_id` for ordered per-tenant processing.
- **Redis INCRBY:** Atomic, O(1) increment. No lock contention even at high concurrency.
  Gives sub-5-second visibility into usage (target metric).
- **PostgreSQL flush:** Provides durability. Redis can be flushed/restarted without data loss.
  PostgreSQL is the authoritative source for invoice computation at cycle close.

### Why not write directly to PostgreSQL?
At 500+ events/sec, writing each event as a DB row creates high write pressure.
Redis absorbs the write burst; PostgreSQL receives periodic aggregate updates.

### Consequences
- Kafka offsets committed only AFTER successful Redis write → at-least-once delivery.
- Event-level deduplication (checking `event_id` in Redis SET) converts at-least-once
  to effectively exactly-once.
- If Redis is unavailable, the aggregator pauses (does not commit offsets) until recovery.

---

## ADR-007 — Invoice Service: gRPC for Internal Data Fetching

**Date:** June 2026  
**Status:** ✅ Accepted

### Decision
The Invoice Service fetches usage totals from the Usage Aggregator and plan details from
the Billing Service via gRPC (not REST) at billing cycle close time.

### Reasoning
- gRPC uses HTTP/2 and Protocol Buffers — significantly lower serialization overhead than
  JSON/REST for structured, typed data fetching.
- Protobuf schemas are strongly typed — schema mismatches are caught at compile time,
  not at runtime.
- This is an internal, synchronous, machine-to-machine call — gRPC is the right tool.
- Demonstrates polyglot skill: REST for external-facing APIs, gRPC for internal service calls.

### Rejected Alternatives
- **REST:** Would work, but JSON serialization overhead is unnecessary for an internal call.
- **Direct DB query (Invoice Service queries Usage Aggregator's DB):** Rejected — violates
  service ownership. Each service owns its own data; others must call its API.
- **Kafka event (async):** Invoice generation is synchronous — it needs the data immediately
  to compute the invoice. Async Kafka is not appropriate here.

---

## ADR-008 — Idempotency Strategy

**Date:** June 2026  
**Status:** ✅ Accepted

### Decision
Three separate idempotency strategies for the three critical operations:

| Operation | Strategy |
|---|---|
| Invoice generation | Check for existing invoice with `(tenant_id, billing_cycle_id)` UNIQUE constraint before computing |
| Webhook processing | Store `webhook_event_id` in `idempotency_keys` table with UNIQUE constraint. Duplicate → 200 OK, skip processing |
| Usage aggregation | Check `event_id` against Redis dedup SET (`SISMEMBER`) before INCRBY |

### Reasoning
- Idempotency is non-negotiable for money-adjacent operations. A double-charge or
  duplicate invoice is a correctness failure, not just a bug.
- Each strategy is tailored to the operation's nature:
  - Invoice: DB-level uniqueness (PostgreSQL enforces it)
  - Webhooks: Explicit key storage (mirrors Stripe's idempotency-key pattern)
  - Usage: Redis-based dedup (fast, appropriate for high-throughput event stream)

### Consequences
- Tests must verify idempotency explicitly: run the same operation N times, assert N=1 result.
- The `stress-test-webhooks.sh` script fires 100 duplicate webhooks and asserts exactly
  1 payment record was created.

---

## ADR-009 — Notification Service: Python / FastAPI

**Date:** June 2026  
**Status:** ✅ Accepted

### Decision
The Notification Service is implemented in Python 3.11 with FastAPI, not Java/Spring Boot.

### Reasoning
- Demonstrates polyglot microservices — a key architectural capability that shows up on CVs.
- The notification service is the simplest service (consume Kafka event → log/mock-send).
  Python + kafka-python is a natural fit for lightweight event consumers.
- FastAPI provides an `/actuator`-equivalent health endpoint with minimal boilerplate.

### Consequences
- This service is NOT a Maven module. It manages its own dependencies via `pyproject.toml`.
- In the Docker Compose final setup (Phase 7), it gets its own Dockerfile.

---

## ADR-010 — Infrastructure: Docker Compose for Local Dev

**Date:** June 2026  
**Status:** ✅ Accepted

### Decision
Use Docker Compose to run all stateful infrastructure locally (PostgreSQL, Redis, Kafka,
Zookeeper, Kafka UI, RedisInsight). Java services run via `mvn spring-boot:run` during
development — NOT in Docker.

### Reasoning
- Docker Compose gives a one-command infrastructure startup without managing local installs.
- Running Java services natively (not in Docker) enables fast iteration: change code →
  recompile → restart in seconds. Dockerizing services adds a build-image step that slows
  the dev loop.
- Services are Dockerized in Phase 7 for the final demo only.

### Port Assignments (no conflicts)

| Service | Host Port |
|---|---|
| PostgreSQL | 5432 |
| Redis | 6379 |
| RedisInsight | 5540 |
| Zookeeper | 2181 |
| Kafka (external) | 9092 |
| Kafka (internal) | 29092 |
| Kafka UI | 8090 |
| API Gateway (local) | 8080 |
| Auth Service (local) | 8085 |
| Billing Service (local) | 8081 |
| Usage Aggregator (local) | 8083 |
| Invoice Service (local) | 8082 |
| Payment Service (local) | 8084 |
| Notification Service (local) | 8086 |
| Frontend (Vite) | 5173 |

### Kafka Dual-Listener Configuration
Kafka runs with two listeners to avoid Docker networking issues:
- `INTERNAL://kafka:29092` — used by containers talking to Kafka (Kafka UI, notification-service in Phase 7)
- `EXTERNAL://localhost:9092` — used by Java services running locally on the host machine

---

## ADR-011 — Database: Single PostgreSQL Instance, Schema-Per-Service

**Date:** June 2026  
**Status:** ✅ Accepted

### Decision
Run a single PostgreSQL instance locally. Each service owns a dedicated PostgreSQL
**schema** (not a separate database instance) for logical isolation.

### Reasoning
- In production, each service would have its own database instance. But for local dev
  and portfolio purposes, one instance with schema isolation is practical and sufficient.
- Schema-per-service enforces the service boundary: no cross-schema joins are allowed.
  If Service A needs Service B's data, it calls Service B's API.
- Schema isolation makes it easy to migrate to separate instances later.

### Schemas
| Schema | Owner Service |
|---|---|
| `auth` | Auth Service |
| `billing` | Billing Service |
| `usage` | Usage Aggregator |
| `invoice` | Invoice Service |
| `payment` | Payment Service |
| `notification` | Notification Service |

### Consequences
- Each service's `application.yml` will specify its schema in the JDBC URL or via
  `spring.datasource.hikari.schema` property.
- Flyway migrations are scoped per-service — each service manages its own schema migrations.

---

## ADR-012 — JPA Entity Package Naming Convention

**Date:** June 2026  
**Status:** ✅ Accepted

### Decision
Use `.entity` (e.g., `com.billing.auth.entity`) instead of `.model` for JPA entity classes.

### Reasoning
- Industry standard for Spring Boot applications using JPA is to explicitly label database-mapped classes as entities.
- `.model` is often too generic and is frequently used for domain models or Data Transfer Objects (DTOs) that do not directly map to database tables.
- This creates clear boundaries: `.entity` for database, `.dto` for API payloads.

---

## ADR-013 — Database Migrations (Flyway vs ddl-auto)

**Date:** June 2026  
**Status:** ✅ Accepted

### Decision
Use **Flyway** for all database schema migrations in production and shared environments. Do not rely on Hibernate's `spring.jpa.hibernate.ddl-auto: update`.

### Reasoning
- `ddl-auto: update` is unpredictable and dangerous for production data. It does not handle column renames, deletions, or data transformations safely.
- Flyway enforces version-controlled SQL scripts (e.g., `V1__create_users.sql`). This guarantees that every environment (local, staging, production) has the exact same database state.
- Makes schema changes reviewable in Pull Requests (PRs).

### Consequences
- During initial scaffolding and learning phases, `ddl-auto` may be used locally for rapid prototyping.
- Before a service is considered "feature complete", all its tables must be defined in Flyway migration scripts located in `src/main/resources/db/migration`.

---

## ADR-014 — OTP Storage: Redis (not PostgreSQL)

**Date:** June 2026
**Status:** ✅ Accepted

### Decision
All OTP data (password reset OTPs, email verification OTPs, attempt counters) is stored in Redis — not in PostgreSQL tables.

### Reasoning
- OTPs are inherently temporary. Redis TTL handles expiry automatically — no cleanup job needed.
- Attempt counters use Redis `INCR`, which is atomic — no race conditions.
- PostgreSQL would require a cleanup job, an `expires_at` query on every check, and extra columns.
- Redis is the right tool for short-lived, high-speed, auto-expiring data.

### Rejected Alternatives
- **PostgreSQL `otps` table:** Rejected — overkill for temporary data. Requires manual cleanup and is slower.

---

## ADR-015 — JWT Revocation: Redis Blacklist via JTI

**Date:** June 2026
**Status:** ✅ Accepted

### Decision
On logout, store the JWT's `jti` (JWT ID) in Redis with a TTL equal to the token's remaining lifetime. Every request checks if the `jti` is blacklisted before trusting the token.

### Reasoning
- JWTs are stateless by nature — once issued, they're valid until expiry. Without a revocation mechanism, a stolen or logged-out token can still be used.
- Storing only the `jti` (not the full token) is memory-efficient.
- TTL = remaining lifetime = zero wasted memory after the token would have expired naturally.
- This is the industry-standard pattern for JWT logout in stateless systems.

### Consequences
- The API Gateway must check Redis blacklist on every authenticated request (one `GET` call — sub-millisecond).
- This adds a tiny Redis dependency to the Gateway, but it's necessary for proper security.

---

## ADR-016 — Refresh Token + Session Management

**Date:** June 2026
**Status:** ✅ Accepted

### Decision
Implement long-lived refresh tokens stored in the `refresh_tokens` table (one per device), paired with active session tracking in the `sessions` table.

### Reasoning
- Short-lived access tokens (15-30 min) + long-lived refresh tokens is the industry standard.
- Storing `device_info` and `ip_address` enables per-device logout and suspicious login detection.
- `is_revoked` flag allows targeted revocation (e.g., logout from all devices on password reset).
- Sessions table tracks `last_active_at` for 25k+ user activity monitoring.

### Consequences
- On password reset, all refresh tokens for the user are revoked — forces re-login on all devices.
- Refresh token rotation can be added as a future enhancement (v2).

---

## ADR-017 — Password Reset: Three-Step Flow with Session Token

**Date:** June 2026
**Status:** ✅ Accepted

### Decision
Password reset uses a 3-step flow: (1) Request OTP → (2) Verify OTP → get a short-lived `reset_session_token` → (3) Use token to set new password.

### Reasoning
- Combining OTP verification and password change in one step is less secure. An attacker who intercepts the OTP can immediately change the password.
- The intermediate `password_reset_sessions` token gates the actual password change — it's a one-time-use token valid for 15 minutes only.
- `is_used = true` flag prevents replay attacks.
- This mirrors how Stripe, GitHub, and other production systems handle password resets.

### Consequences
- Three API endpoints are required: `/auth/forgot-password`, `/auth/verify-otp`, `/auth/reset-password`.
- `password_reset_sessions` rows for used/expired tokens can be cleaned up by a scheduled job later.

---

*Add a new ADR every time a significant design decision is made.*
*Even "we decided NOT to do X" decisions are worth recording.*
