# 🏗️ Multi-Tenant SaaS Billing & Subscription Platform
## Master Project Guide — Architecture, Structure & Setup

> **Document owner:** Monodipta Maity
> **Status:** Living Document — update as you build
> **Last updated:** June 2026
> **Read this before writing a single line of code.**

---

## 📑 Table of Contents

1. [Repo Strategy — Why Monorepo](#1-repo-strategy--why-monorepo)
2. [Full Folder Structure](#2-full-folder-structure)
3. [Where PRD & Architecture Live](#3-where-prd--architecture-live)
4. [How the API Gateway Works (Auth & Authorization Deep Dive)](#4-how-the-api-gateway-works)
5. [How the Aggregator Service Works (Data Flow Deep Dive)](#5-how-the-aggregator-service-works)
6. [Inter-Service Communication Map](#6-inter-service-communication-map)
7. [Step-by-Step: What To Do First](#7-step-by-step-what-to-do-first)
8. [Git Strategy](#8-git-strategy)
9. [Environment & Local Dev Setup](#9-environment--local-dev-setup)
10. [Target Metrics Reminder](#10-target-metrics-reminder)

---

## 1. Repo Strategy — Why Monorepo

### Decision: Monorepo with Maven Multi-Module Build

**Why not polyrepo (separate repos per service)?**

| Concern | Polyrepo | Monorepo ✅ |
|---|---|---|
| Shared protobuf/Avro schemas | Copy-paste hell | Single `shared/` module |
| Cross-service refactoring | Multiple PRs | One commit |
| Local dev / docker-compose | Complex | One `docker-compose.yml` |
| Portfolio impression | Fragmented | One clean GitHub repo |
| CI pipeline | N pipelines | One unified pipeline |

**Why not a full monorepo tool (Nx, Turborepo)?** — Those are JS-first. For a Java/Spring Boot backbone, a **Maven multi-module build** is a proven industry approach (used widely in enterprise Java shops and Spring Boot projects). Maven's parent POM model gives clean dependency management and a single `mvn` command to build everything. Python services get their own `pyproject.toml` inside their folder.

### What this means in practice
- One `git` repo: `saas-billing-platform`
- Root `pom.xml` (parent POM) coordinates all Java services as `<modules>`
- Each service is a **Maven module** with its own `pom.xml` that inherits from the parent
- `shared/` module contains Avro schemas, protobuf definitions, common DTOs — declared as a dependency in sibling POMs
- One root `docker-compose.yml` spins up everything locally
- One `.github/workflows/` folder for all CI
- Maven Wrapper (`mvnw` / `mvnw.cmd`) committed to the repo — no Maven install required on any machine

---

## 2. Full Folder Structure

```
saas-billing-platform/                          <- ROOT of monorepo
|
|-- docs/                                       <- ALL documentation lives here
|   |-- PRD_SaaS_Billing_Platform.md            <- Your PRD (moved here)
|   |-- architecture-diagram.png                <- Your architecture screenshot (moved here)
|   |-- ARCHITECTURE.md                         <- Written explanation of architecture
|   |-- API_GATEWAY.md                          <- Deep dive: gateway auth/authz
|   |-- AGGREGATOR.md                           <- Deep dive: aggregator data flow
|   |-- DATA_MODELS.md                          <- DB schema decisions
|   |-- DECISIONS.md                            <- Architecture Decision Records (ADRs)
|   +-- demo/                                   <- GIFs / demo video files
|
|-- services/                                   <- All backend microservices
|   |
|   |-- api-gateway/                            <- Spring Cloud Gateway (Java 21)
|   |   |-- src/
|   |   |   +-- main/java/com/billing/gateway/
|   |   |       |-- GatewayApplication.java
|   |   |       |-- filter/
|   |   |       |   |-- JwtAuthFilter.java      <- JWT validation filter
|   |   |       |   +-- TenantContextFilter.java <- Extracts tenant_id into header
|   |   |       |-- config/
|   |   |       |   |-- RouteConfig.java        <- Route definitions
|   |   |       |   +-- SecurityConfig.java
|   |   |       +-- exception/
|   |   |           +-- GlobalErrorHandler.java
|   |   |-- src/main/resources/
|   |   |   +-- application.yml
|   |   +-- pom.xml
|   |
|   |-- auth-service/                           <- Auth (Java 21 / Spring Boot)
|   |   |-- src/main/java/com/billing/auth/
|   |   |   |-- AuthApplication.java
|   |   |   |-- controller/
|   |   |   |   +-- AuthController.java         <- /auth/login, /auth/register
|   |   |   |-- service/
|   |   |   |   |-- AuthService.java
|   |   |   |   +-- JwtService.java             <- Signs & issues JWTs
|   |   |   |-- model/
|   |   |   |   +-- User.java                   <- tenant_id, role stored here
|   |   |   +-- repository/
|   |   |       +-- UserRepository.java
|   |   |-- src/main/resources/
|   |   |   +-- application.yml
|   |   +-- pom.xml
|   |
|   |-- billing-service/                        <- Core billing engine (Java 21 / Spring Boot)
|   |   |-- src/main/java/com/billing/billing/
|   |   |   |-- BillingApplication.java
|   |   |   |-- controller/
|   |   |   |   |-- SubscriptionController.java
|   |   |   |   +-- PlanController.java
|   |   |   |-- service/
|   |   |   |   |-- SubscriptionService.java    <- State machine lives here
|   |   |   |   |-- PlanService.java
|   |   |   |   +-- ProrationService.java       <- Upgrade/downgrade math
|   |   |   |-- statemachine/
|   |   |   |   +-- SubscriptionStateMachine.java <- trial->active->past_due->canceled
|   |   |   |-- model/
|   |   |   |   |-- Subscription.java
|   |   |   |   |-- Plan.java
|   |   |   |   +-- AuditLog.java               <- Every state transition logged
|   |   |   |-- repository/
|   |   |   +-- kafka/
|   |   |       +-- BillingEventProducer.java   <- Publishes billing events to Kafka
|   |   |-- src/main/resources/
|   |   |   +-- application.yml
|   |   +-- pom.xml
|   |
|   |-- usage-aggregator/                       <- Kafka consumer + Redis writer (Java 21)
|   |   |-- src/main/java/com/billing/aggregator/
|   |   |   |-- AggregatorApplication.java
|   |   |   |-- consumer/
|   |   |   |   +-- UsageEventConsumer.java     <- @KafkaListener
|   |   |   |-- service/
|   |   |   |   |-- AggregationService.java     <- INCRBY in Redis logic
|   |   |   |   +-- FlushService.java           <- Periodic Redis -> PostgreSQL flush
|   |   |   |-- model/
|   |   |   |   +-- UsageEvent.java             <- Avro deserialized event
|   |   |   +-- repository/
|   |   |       +-- UsageTotalRepository.java
|   |   |-- src/main/resources/
|   |   |   +-- application.yml
|   |   +-- pom.xml
|   |
|   |-- invoice-service/                        <- Invoice generation (Java 21 / Spring Boot)
|   |   |-- src/main/java/com/billing/invoice/
|   |   |   |-- InvoiceApplication.java
|   |   |   |-- job/
|   |   |   |   +-- BillingCycleJob.java        <- Scheduled idempotent job
|   |   |   |-- service/
|   |   |   |   |-- InvoiceService.java
|   |   |   |   +-- PdfGenerationService.java   <- iText/OpenPDF
|   |   |   |-- grpc/
|   |   |   |   |-- AggregatorGrpcClient.java   <- Fetches usage totals via gRPC
|   |   |   |   +-- PlanGrpcClient.java         <- Fetches plan details via gRPC
|   |   |   +-- model/
|   |   |       +-- Invoice.java
|   |   |-- src/main/resources/
|   |   |   +-- application.yml
|   |   +-- pom.xml
|   |
|   |-- payment-service/                        <- Mock payment + webhook handler (Java 21)
|   |   |-- src/main/java/com/billing/payment/
|   |   |   |-- PaymentApplication.java
|   |   |   |-- controller/
|   |   |   |   |-- PaymentController.java
|   |   |   |   +-- WebhookController.java      <- /webhooks/payment — idempotent
|   |   |   |-- service/
|   |   |   |   |-- MockGatewayService.java     <- Simulates Stripe-style response
|   |   |   |   |-- IdempotencyService.java     <- Checks/stores idempotency keys
|   |   |   |   +-- WebhookVerifier.java        <- HMAC signature verification
|   |   |   +-- kafka/
|   |   |       +-- PaymentEventProducer.java
|   |   |-- src/main/resources/
|   |   |   +-- application.yml
|   |   +-- pom.xml
|   |
|   +-- notification-service/                   <- Event consumer + mock email (Python / FastAPI)
|       |-- app/
|       |   |-- main.py
|       |   |-- consumer/
|       |   |   +-- kafka_consumer.py           <- Consumes billing-events topic
|       |   |-- service/
|       |   |   +-- notification_service.py     <- Logs / mock-sends email
|       |   +-- models/
|       |       +-- billing_event.py
|       |-- pyproject.toml
|       |-- Dockerfile
|       +-- requirements.txt
|
|-- frontend/                                   <- React 19 Admin Dashboard
|   |-- src/
|   |   |-- app/
|   |   |   +-- store.ts                        <- Redux Toolkit store
|   |   |-- features/
|   |   |   |-- tenants/
|   |   |   |-- plans/
|   |   |   |-- invoices/
|   |   |   +-- usage/
|   |   |-- components/
|   |   |-- pages/
|   |   +-- main.tsx
|   |-- package.json
|   |-- vite.config.ts
|   +-- Dockerfile
|
|-- shared/                                     <- Cross-service shared code (Java module)
|   |-- src/main/java/com/billing/shared/
|   |   |-- dto/                                <- Shared request/response DTOs
|   |   |-- exception/                          <- Common exception types
|   |   +-- security/
|   |       +-- JwtClaimsUtil.java              <- Shared JWT parsing utility
|   |-- src/main/avro/
|   |   +-- UsageEvent.avsc                     <- Avro schema — single source of truth
|   |-- src/main/proto/
|   |   |-- aggregator.proto                    <- gRPC: usage totals
|   |   +-- plan.proto                          <- gRPC: plan details
|   +-- pom.xml
|
|-- infra/                                      <- All infrastructure as code
|   |-- docker/
|   |   |-- kafka/
|   |   |   +-- kafka-setup.sh                  <- Create topics script
|   |   +-- postgres/
|   |       +-- init.sql                        <- DB schemas for all services
|   |-- k8s/                                    <- Kubernetes manifests (Phase 7)
|   |   |-- namespace.yaml
|   |   |-- api-gateway/
|   |   |-- billing-service/
|   |   +-- ...
|   +-- helm/                                   <- Helm charts (Phase 7)
|       +-- billing-platform/
|
|-- scripts/                                    <- Dev & test utility scripts
|   |-- seed-plans.sh                           <- Seeds plan catalog into DB
|   |-- simulate-usage.sh                       <- Fires fake usage events to Kafka
|   |-- trigger-billing-cycle.sh                <- Manually triggers invoice job
|   +-- stress-test-webhooks.sh                 <- Fires 100 duplicate webhooks
|
|-- .github/
|   +-- workflows/
|       |-- ci.yml                              <- Build + test all services
|       +-- lint.yml
|
|-- docker-compose.yml                          <- Spins up ALL services + infra locally
|-- docker-compose.override.yml                 <- Local overrides (dev secrets etc.)
|-- pom.xml                                     <- ROOT Maven parent POM (multi-module)
|-- .mvn/                                       <- Maven Wrapper metadata
|   +-- wrapper/
|       +-- maven-wrapper.properties
|-- mvnw                                        <- Maven Wrapper script (Unix)
|-- mvnw.cmd                                    <- Maven Wrapper script (Windows)
|-- .env.example                                <- Template for local secrets
|-- .gitignore
+-- README.md                                   <- The thing interviewers read first
```

---

## 3. Where PRD & Architecture Live

**Decision: Keep everything under `/docs/` in the repo root.**
This is standard practice — seen in Google, Stripe, Shopify open-source repos.

### What to do right now (before coding):

```bash
# After creating the repo folder, run:
mkdir docs
# Then manually move/copy:
# "PRD_SaaS_Billing & Subscription_Platform.md"  ->  docs/PRD.md
# "Screenshot 2026-06-27 134756.png"              ->  docs/architecture-diagram.png
```

Then in your `README.md`, link to them:

```markdown
## Documentation
- [Product Requirements Document](./docs/PRD.md)
- [Architecture Overview](./docs/ARCHITECTURE.md)
- [Architecture Diagram](./docs/architecture-diagram.png)
```

> **Why this matters for interviews:** When a recruiter or engineer opens your GitHub repo,
> they should immediately understand what this is, why it exists, and how it works — without
> asking you a single question. The `docs/` folder is that answer.

---

## 4. How the API Gateway Works

### Role: Single Entry Point, Zero Trust Enforcer

The API Gateway (Spring Cloud Gateway) does three things and three things only:
1. **Authenticate** every request (validate JWT)
2. **Enrich** the request (attach `X-Tenant-ID`, `X-User-Role` headers)
3. **Route** the request to the correct downstream service

It does NOT contain business logic. Ever.

### Authentication Flow (Step by Step)

```
STEP 1: Client sends request
─────────────────────────────
  Tenant UI ─► API Gateway
    POST /subscriptions/upgrade
    Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...

STEP 2: JwtAuthFilter runs (Global Pre-Filter)
──────────────────────────────────────────────
  2a. Extract token from Authorization header
  2b. Verify HMAC-SHA256 signature using shared secret key
  2c. Check `exp` claim — is token expired?
  2d. If ANY check fails → 401 Unauthorized (request dies here, NEVER reaches downstream)
  2e. If valid → decode claims: { sub, tenant_id, role }

STEP 3: TenantContextFilter enriches the request
──────────────────────────────────────────────────
  Adds headers before forwarding:
    X-Tenant-ID:  T-123
    X-User-Role:  TENANT_ADMIN
    X-User-ID:    user-uuid

STEP 4: Gateway routes to downstream
──────────────────────────────────────
  /subscriptions/**  →  billing-service:8081
  /invoices/**       →  invoice-service:8082
  /usage/**          →  usage-aggregator:8083 (read endpoints only)
  /admin/**          →  billing-service:8081  (role check: PLATFORM_ADMIN only)
  /auth/**           →  auth-service:8085     <- NO JWT check on this route (it's login)
```

### Authorization: Two Layers

```
LAYER 1 — Gateway Level (Coarse, Route-Based)
──────────────────────────────────────────────
  If route starts with /admin/** AND X-User-Role != PLATFORM_ADMIN
  → 403 Forbidden immediately

LAYER 2 — Service Level (Fine-Grained, Query-Based) ← THE IMPORTANT ONE
──────────────────────────────────────────────────────────────────────────
  Inside billing-service, every DB query is scoped:

  // SubscriptionService.java
  public Subscription getSubscription(String subscriptionId, String tenantId) {
      return repo.findByIdAndTenantId(subscriptionId, tenantId);
      //                              ^^^^^^^^^^^^^^^^
      //                              tenantId from X-Tenant-ID header
      //                              Even a valid token cannot see another tenant's data
  }

  This is the multi-tenant isolation guarantee.
  Even if Tenant A somehow gets Tenant B's subscription ID,
  the DB query returns nothing because tenant_id won't match.
```

### What the Auth Service Does (Different from Gateway)

```
Auth Service responsibilities:
  POST /auth/register  →  Create user, hash password (BCrypt), store tenant_id + role in DB
  POST /auth/login     →  Verify password → sign JWT → return token

Auth Service does NOT validate tokens on subsequent requests.
The Gateway does that statelesslessly using the shared signing key.
This is why the system is horizontally scalable — no session state anywhere.
```

### JWT Payload Structure

```json
{
  "sub": "550e8400-e29b-41d4-a716-446655440000",
  "tenant_id": "tenant-abc-123",
  "role": "TENANT_ADMIN",
  "iat": 1719481200,
  "exp": 1719567600
}
```

### Key Files to Build (In Order)

| File | What it does |
|---|---|
| `auth-service/service/JwtService.java` | Signs tokens — only place this happens |
| `api-gateway/filter/JwtAuthFilter.java` | Validates token, rejects 401 if bad |
| `api-gateway/filter/TenantContextFilter.java` | Adds X-Tenant-ID header downstream |
| `api-gateway/config/RouteConfig.java` | Defines all route mappings |
| `shared/security/JwtClaimsUtil.java` | Shared parsing utility used by all services |

---

## 5. How the Aggregator Service Works

### Role: Stateless Kafka Consumer, Redis/Postgres Writer

The Aggregator's only job is to consume usage events from Kafka and maintain accurate
running totals. It is purely a write-path service. It never serves reads directly to the UI.

### Full Data Flow (End to End)

```
SOURCE: Simulated client services (or your simulate-usage.sh script)
─────────────────────────────────────────────────────────────────────
Emit events directly to Kafka (bypasses API Gateway entirely):
{
  "tenant_id": "tenant-abc-123",
  "metric":    "api_calls",
  "quantity":  15,
  "timestamp": "2026-06-27T08:30:00Z",
  "event_id":  "evt-uuid-789"    <- for deduplication
}

STEP 1: Kafka Topic — usage-events
────────────────────────────────────
  Topic:      usage-events
  Partitions: 6 (partitioned by tenant_id for ordered processing per tenant)
  Schema:     Avro (defined in shared/src/main/avro/UsageEvent.avsc)
  Retention:  7 days

STEP 2: UsageEventConsumer.java (@KafkaListener)
──────────────────────────────────────────────────
  Reads a batch of events
  For each event:
    - Deserialize Avro → UsageEvent POJO
    - Check event_id against Redis SET for deduplication (SISMEMBER)
    - If duplicate → skip, log, commit offset
    - If new → pass to AggregationService

STEP 3: AggregationService.java — Redis Hot Path
──────────────────────────────────────────────────
  Redis key pattern:
    usage:{tenant_id}:{billing_cycle_id}:{metric}

  Example:
    usage:tenant-abc-123:2026-06:api_calls

  Operation:
    INCRBY usage:tenant-abc-123:2026-06:api_calls 15
    TTL: set to end of billing cycle (auto-expire old cycle data)

  Why Redis? O(1) atomic increment. No lock contention.
  25k users firing events simultaneously → Redis handles this easily.

STEP 4: FlushService.java — Periodic Durable Write
────────────────────────────────────────────────────
  Every 30 seconds (configurable):
    Scan Redis keys matching usage:*
    For each key → upsert into usage_totals table in PostgreSQL
    This is the durable record. Survives Redis restart.

STEP 5: Kafka Offset Commit
────────────────────────────
  Offsets committed AFTER successful Redis write (manual commit mode).
  If service crashes before commit → event reprocessed on restart.
  Combined with event-id deduplication in Step 2 → effectively exactly-once semantics.
```

### How the Frontend Gets Usage Data

```
Tenant UI asks: "How much API usage does my tenant have this month?"

Flow:
  Tenant UI → GET /usage/current → API Gateway (validates JWT, adds X-Tenant-ID)
            → billing-service read endpoint
            → Queries Redis first (hot, sub-millisecond)
            → Falls back to PostgreSQL if Redis miss
            → Returns: { metric: "api_calls", total: 4750, quota: 10000 }

The Aggregator itself does NOT have REST endpoints for UI reads.
Read endpoints live in billing-service.
This separation means the write path (Kafka consumer) and read path (REST API)
can scale independently.
```

### How Invoice Service Uses Aggregator's Data

```
At billing cycle close (BillingCycleJob runs):
  Invoice Service → gRPC call → Aggregator gRPC server
  Request:  { tenant_id, billing_cycle_id }
  Response: { api_calls: 12450, storage_gb: 87.3, active_seats: 5 }

  Invoice Service computes:
    Base plan fee:       $99.00
    Overage api_calls:   (12450 - 10000) = 2450 × $0.001 = $2.45
    Total:               $101.45

  Stores in PostgreSQL, generates PDF.
```

### Key Files to Build (In Order)

| File | What it does |
|---|---|
| `shared/src/main/avro/UsageEvent.avsc` | Avro schema — build this FIRST |
| `infra/docker/kafka/kafka-setup.sh` | Create usage-events topic (6 partitions) |
| `infra/docker/postgres/init.sql` | usage_totals table definition |
| `usage-aggregator/consumer/UsageEventConsumer.java` | @KafkaListener |
| `usage-aggregator/service/AggregationService.java` | Redis INCRBY logic |
| `usage-aggregator/service/FlushService.java` | Redis → PostgreSQL periodic flush |

---

## 6. Inter-Service Communication Map

```
[Tenant UI]
     |
     | REST (public, JWT in header)
     v
[API Gateway :8080]  <- Only public port
     |
     | REST (internal, enriched: X-Tenant-ID, X-User-Role)
     |─────────────────────────────────────┐
     v                                     v
[Auth Service :8085]          [Billing Service :8081]
Issues JWT on login           Plans, Subscriptions, State Machine
                                     |
                              Kafka  | publishes billing-events
                                     v
                              [Kafka Broker :9092]
                                     |
                    ┌────────────────┴────────────────┐
                    v                                  v
         [Usage Aggregator :8083]         [Notification Service :8086]
         Kafka consumer                   Python/FastAPI, Kafka consumer
         Redis + PostgreSQL               Mock email / log output

[Invoice Service :8082]
  Called by scheduled job (BillingCycleJob)
  Gets usage via gRPC ─► Usage Aggregator
  Gets plan via gRPC  ─► Billing Service
  Triggers payment    ─► Payment Service via REST

[Payment Service :8084]
  MockGatewayService
  WebhookController (idempotent)
  Publishes payment events to Kafka ─► Notification Service

Communication protocols:
  REST    External-facing or Gateway-to-service (HTTP/JSON)
  gRPC    Service-to-service (Invoice <-> Aggregator, Invoice <-> BillingService)
  Kafka   Async events (usage ingestion, billing event notifications)
```

---

## 7. Step-by-Step: What To Do First

Follow this exact order. Do NOT skip ahead.

### Phase 0 — Project Bootstrap (Do This Today)
```
[ ] 1. Create repo folder:
        mkdir saas-billing-platform
        cd saas-billing-platform

[ ] 2. Git init:
        git init
        git branch -M main

[ ] 3. Create folder skeleton:
        mkdir -p docs services/api-gateway services/auth-service services/billing-service
        mkdir -p services/usage-aggregator services/invoice-service services/payment-service
        mkdir -p services/notification-service frontend shared infra/docker/kafka
        mkdir -p infra/docker/postgres infra/k8s infra/helm scripts .github/workflows

[ ] 4. Move PRD + architecture diagram into docs/
        cp "../PRD_SaaS_Billing & Subscription_Platform.md" docs/PRD.md
        cp "../Screenshot 2026-06-27 134756.png" docs/architecture-diagram.png

[ ] 5. Create README.md skeleton (project name, what it is, link to docs)

[ ] 6. Bootstrap Maven multi-module project:
        # Generate Maven Wrapper (run once, no Maven install needed afterward)
        mvn wrapper:wrapper
        # This creates: mvnw, mvnw.cmd, .mvn/wrapper/maven-wrapper.properties

        # Root pom.xml declares all services as <modules>:
        # <modules>
        #   <module>shared</module>
        #   <module>services/api-gateway</module>
        #   <module>services/auth-service</module>
        #   <module>services/billing-service</module>
        #   <module>services/usage-aggregator</module>
        #   <module>services/invoice-service</module>
        #   <module>services/payment-service</module>
        # </modules>
        #
        # Root pom.xml also declares <dependencyManagement> with a Spring Boot BOM:
        # spring-boot-dependencies, spring-cloud-dependencies, versions pinned here.
        #
        # Each service pom.xml inherits: <parent>...root pom...</parent>

[ ] 7. Create docker-compose.yml with: PostgreSQL, Redis, Kafka, Zookeeper, Kafka UI

[ ] 8. Create .env.example with all required env vars

[ ] 9. Add .gitignore (see Section 8)

[ ] 10. First commit:
        git add .
        git commit -m "chore: project scaffold, maven multi-module setup and infra"

[ ] 11. Create GitHub repo → push:
        git remote add origin https://github.com/YOUR_USERNAME/saas-billing-platform.git
        git push -u origin main
```

### Phase 1 — Auth + Gateway (Foundation — nothing works without this)
```
[ ] 1. Build auth-service: User model, BCrypt password hashing, JWT signing
[ ] 2. Write JwtService.java — signs tokens with HS256
[ ] 3. Build api-gateway: JwtAuthFilter, TenantContextFilter, RouteConfig
[ ] 4. Test: POST /auth/login → get token → call any route → 200 OK
[ ] 5. Test: call route without token → 401 Unauthorized
[ ] 6. Test: call /admin/** as TENANT_ADMIN → 403 Forbidden
[ ] 7. Commit: "feat: auth service and jwt gateway filter"
```

### Phase 2 — Plan Catalog + Subscription Lifecycle
```
[ ] 1. Plan model + CRUD (Platform Admin only)
[ ] 2. Subscription model + state machine (trial->active->past_due->canceled)
[ ] 3. AuditLog model — every state transition persisted with timestamp + reason
[ ] 4. REST endpoints: POST /subscriptions, PATCH /subscriptions/{id}/upgrade, etc.
[ ] 5. Kafka producer: emit billing events on state changes
[ ] 6. Integration test: full lifecycle transition
[ ] 7. Commit: "feat: subscription lifecycle state machine with audit trail"
```

### Phase 3 — Usage Ingestion (Aggregator)
```
[ ] 1. Define Avro schema: shared/src/main/avro/UsageEvent.avsc
[ ] 2. Create Kafka topic: usage-events (6 partitions, via kafka-setup.sh)
[ ] 3. Build UsageEventConsumer with @KafkaListener (manual offset commit)
[ ] 4. Build AggregationService: Redis INCRBY with deduplication
[ ] 5. Build FlushService: Redis -> PostgreSQL upsert every 30s
[ ] 6. Test: fire 1000 events → verify totals in Redis match PostgreSQL
[ ] 7. Test: kill service mid-stream → restart → verify no data loss
[ ] 8. Commit: "feat: kafka usage ingestion and redis aggregation with durability"
```

### Phase 4 — Invoice Service
```
[ ] 1. Define gRPC protos for usage and plan data
[ ] 2. BillingCycleJob: @Scheduled, idempotent (check existing invoice before computing)
[ ] 3. Compute charges: base plan fee + overage calculation
[ ] 4. PDF generation with iText/OpenPDF
[ ] 5. Store invoice in PostgreSQL, expose GET /invoices/{id} and GET /invoices
[ ] 6. Test: run billing job 10 times → assert exactly 1 invoice created (idempotency test)
[ ] 7. Commit: "feat: idempotent invoice generation with pdf output"
```

### Phase 5 — Payment + Webhook Handler
```
[ ] 1. MockGatewayService: returns success/failure asynchronously
[ ] 2. WebhookController: POST /webhooks/payment
[ ] 3. IdempotencyService: store processed webhook IDs in DB with unique constraint
[ ] 4. WebhookVerifier: HMAC-SHA256 signature check
[ ] 5. Retry policy: 3 attempts over 5 days on failure → subscription to past_due
[ ] 6. Test: fire 100 duplicate webhooks → assert exactly 1 payment record created
[ ] 7. Commit: "feat: idempotent webhook handler with mock payment gateway"
```

### Phase 6 — Notification + Dashboard
```
[ ] 1. Python notification-service: consume billing-events Kafka topic
[ ] 2. Log/mock-send for: invoice.generated, payment.failed, plan.changed, trial.ending
[ ] 3. React 19 dashboard with Redux Toolkit: tenants list, usage chart, invoice history
[ ] 4. Wire dashboard to real API endpoints through the Gateway
[ ] 5. Commit: "feat: notification service and admin dashboard"
```

### Phase 7 — Testing, Stress Tests, Polish
```
[ ] 1. Run stress-test-webhooks.sh → verify idempotency under load
[ ] 2. Run simulate-usage.sh → verify 500+ events/sec throughput
[ ] 3. Write integration tests for full billing cycle end-to-end
[ ] 4. Add structured logging with correlation IDs across all services (MDC)
[ ] 5. Write docs/ARCHITECTURE.md and docs/DECISIONS.md
[ ] 6. Record 2-3 min demo video → put in docs/demo/
[ ] 7. Final commit: "docs: architecture documentation, adrs, and demo recording"
```

---

## 8. Git Strategy

### Branch Strategy (Trunk-Based — right for a solo project)

```
main                            <- always deployable, protected
  |── feat/phase-0-scaffold
  |── feat/auth-service
  |── feat/usage-aggregator
  |── feat/invoice-idempotency
  |── fix/webhook-duplicate-processing
  +── chore/docker-compose-setup
```

### Commit Message Convention (Conventional Commits)

```
feat:     new feature
fix:      bug fix
chore:    tooling, build, CI changes
docs:     documentation only
test:     adding or fixing tests
refactor: code change that is not a feature or fix
perf:     performance improvement

Examples:
  feat: add jwt validation filter to api gateway
  fix: prevent double increment on kafka redelivery
  test: idempotency stress test for webhook handler (100 duplicate deliveries)
  docs: add architecture decision record for redis flush strategy
  chore: add jacoco test coverage reporting to maven build
```

### .gitignore (Root)

```gitignore
# Java / Maven
target/
*.class
*.jar
*.war
*.ear

# Maven Wrapper cache (keep the wrapper itself, ignore downloaded Maven)
.mvn/wrapper/maven-wrapper.jar
# Uncomment the next line if you want to ignore the whole .mvn folder:
# .mvn/

# IntelliJ IDEA
.idea/
*.iml
*.iws
out/

# Eclipse / STS
.project
.classpath
.settings/

# VS Code
.vscode/

# Python
__pycache__/
*.pyc
.venv/
dist/
*.egg-info/

# Node / React
node_modules/
dist/
.next/

# Environment secrets
.env
.env.local
*.env

# Local docker overrides
docker-compose.override.yml

# OS
.DS_Store
Thumbs.db

# Logs
*.log
logs/

# Generated protobuf/avro
services/*/src/main/java/com/billing/**/generated/
shared/src/main/java/com/billing/**/generated/
```

---

## 9. Environment & Local Dev Setup

### Prerequisites

```
Java 21           Use SDKMAN:  sdk install java 21-tem
                  Or manually: https://adoptium.net/
Maven             NOT required to install separately.
                  Use the Maven Wrapper committed to the repo:
                    ./mvnw (Linux/Mac)  |  mvnw.cmd (Windows)
                  To bootstrap the wrapper on a fresh machine (one-time):
                    mvn wrapper:wrapper   (if Maven is available)
                    OR copy .mvn/ + mvnw/mvnw.cmd from another checkout.
Docker Desktop    For Kafka, Redis, PostgreSQL containers
Node 20+          For React frontend
Python 3.11+      For notification-service
```

### .env.example (create this at root)

```env
# JWT
JWT_SECRET=your-very-long-secret-key-at-least-256-bits-long
JWT_EXPIRY_HOURS=24

# PostgreSQL
POSTGRES_HOST=localhost
POSTGRES_PORT=5432
POSTGRES_DB=billing_platform
POSTGRES_USER=billing_user
POSTGRES_PASSWORD=billing_pass

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379

# Kafka
KAFKA_BOOTSTRAP_SERVERS=localhost:9092

# Service ports (informational)
GATEWAY_PORT=8080
AUTH_SERVICE_PORT=8085
BILLING_SERVICE_PORT=8081
AGGREGATOR_PORT=8083
INVOICE_SERVICE_PORT=8082
PAYMENT_SERVICE_PORT=8084
NOTIFICATION_PORT=8086
```

### Local Start (Everything)

```bash
# 1. Copy env file
cp .env.example .env
# Edit .env with your local values

# 2. Start infrastructure
docker-compose up -d postgres redis kafka zookeeper kafka-ui

# 3. Create Kafka topics
./infra/docker/kafka/kafka-setup.sh

# 4. Build all Java modules (compiles + runs tests for every service)
./mvnw clean install -DskipTests          # fast build, skip tests
./mvnw clean install                       # full build with tests

# 5. Run DB migrations
# Flyway runs automatically on Spring Boot startup.
# To run standalone against a running DB:
./mvnw flyway:migrate -pl services/billing-service

# 6. Start Java services (separate terminals, each in repo root)
./mvnw spring-boot:run -pl services/auth-service
./mvnw spring-boot:run -pl services/api-gateway
./mvnw spring-boot:run -pl services/billing-service
./mvnw spring-boot:run -pl services/usage-aggregator
./mvnw spring-boot:run -pl services/invoice-service
./mvnw spring-boot:run -pl services/payment-service

# Alternative: build a fat JAR and run it
./mvnw package -pl services/auth-service -DskipTests
java -jar services/auth-service/target/auth-service-*.jar

# 7. Start notification service
cd services/notification-service
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8086

# 8. Start frontend
cd frontend
npm install
npm run dev
# Open http://localhost:5173
```

### Service Port Map

| Service | Port | Notes |
|---|---|---|
| API Gateway | 8080 | Only port exposed to UI / external |
| Auth Service | 8085 | Internal only |
| Billing Service | 8081 | Internal only |
| Usage Aggregator | 8083 | Internal only |
| Invoice Service | 8082 | Internal only |
| Payment Service | 8084 | Internal only |
| Notification Service | 8086 | Internal only |
| Frontend | 5173 | Vite dev server |
| PostgreSQL | 5432 | |
| Redis | 6379 | |
| Kafka | 9092 | |
| Kafka UI (AKHQ) | 8090 | Dev only — visualize topics |

---

## 10. Target Metrics Reminder

Keep these in front of you while building. These are your definition of "done":

| Metric | Target | How to Verify |
|---|---|---|
| API read p95 latency | < 200ms | k6 or Artillery load test on GET endpoints |
| API write p95 latency | < 500ms | k6 load test on POST /subscriptions/upgrade |
| Usage event throughput | > 500 events/sec | simulate-usage.sh with timing loop |
| Webhook idempotency | 100% — zero double-charges | stress-test-webhooks.sh (100 duplicates) |
| Invoice idempotency | 100% — zero duplicate invoices | Run billing job 10x, count invoices in DB |
| Usage visibility latency | < 5 seconds after event | Fire event, poll /usage/current, measure |
| Test coverage (core logic) | >= 75% | `./mvnw verify` — JaCoCo report at `target/site/jacoco/index.html` |
| Kafka recovery | 0 data loss after restart | Kill aggregator mid-stream, restart, verify totals |

---

## Interview-Ready Talking Points

When asked "walk me through your architecture":

> "The system is a multi-tenant billing engine with an event-driven usage metering path and a
> synchronous billing lifecycle path. The API Gateway owns JWT validation and tenant context
> propagation — no downstream service trusts any request without the X-Tenant-ID header the
> gateway attaches. Tenant isolation is enforced at the query level, not just the API level.
>
> Usage events flow through Kafka into the aggregator, which does atomic INCRBY operations in
> Redis for the hot path and flushes durably to PostgreSQL. Offsets are committed only after
> successful writes, giving us at-least-once delivery. Combined with event-level deduplication,
> we get effectively exactly-once semantics.
>
> Invoice generation is a scheduled, idempotent job — it checks for an existing invoice before
> doing any computation, so re-running it 100 times produces exactly one invoice. Same principle
> applies to the webhook handler: we store processed webhook IDs as idempotency keys.
>
> The result is a system that handles 500+ events/sec, has 0 duplicate charges across stress
> tests, and every billing-affecting action is immutably logged for audit."

---

*This is a living document. Update DECISIONS.md every time you make an architectural choice.*
*Update this guide whenever the folder structure or service responsibilities change.*
