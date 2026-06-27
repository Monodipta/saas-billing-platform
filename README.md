# Multi-Tenant SaaS Billing & Subscription Platform

A backend-first, event-driven billing engine that handles subscription lifecycle management,
real-time usage metering, idempotent invoice generation, and mock payment processing — modeled
on how internal billing engines work at companies like Stripe Billing and Chargebee.

> **Portfolio project** demonstrating production-style backend engineering:
> correctness under concurrency, idempotency, multi-tenant data isolation, and event-driven consistency.

---

## Documentation

| Document | Description |
|---|---|
| [Architecture](./docs/ARCHITECTURE.md) | System design, service responsibilities, data flows |
| [Project Guide](./docs/PROJECT_GUIDE.md) | Build order, folder structure, git strategy, local dev setup |
| [PRD](./docs/PRD_SaaS_Billing_Platform.md) | Product requirements, goals, target metrics |
| [Architecture Diagram](./docs/architecture-diagram.png) | Visual service communication map |

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.x, Spring Cloud Gateway |
| Build | Maven (multi-module) |
| Messaging | Apache Kafka + Avro schemas |
| Caching | Redis |
| Database | PostgreSQL |
| Service-to-service | gRPC (Invoice ↔ Aggregator) |
| Notification service | Python 3.11 / FastAPI |
| Frontend | React 19, Redux Toolkit, Vite |
| Containers | Docker, Docker Compose |
| Orchestration | Kubernetes + Helm (Phase 7) |

---

## Services

| Service | Port | Responsibility |
|---|---|---|
| API Gateway | 8080 | JWT validation, routing, rate limiting |
| Auth Service | 8085 | Login, registration, JWT signing |
| Billing Service | 8081 | Plans, subscriptions, state machine |
| Usage Aggregator | 8083 | Kafka consumer, Redis + PostgreSQL writer |
| Invoice Service | 8082 | Cycle-close job, PDF invoice generation |
| Payment Service | 8084 | Mock gateway, idempotent webhook handler |
| Notification Service | 8086 | Billing event consumer, mock email delivery |
| Frontend | 5173 | Admin dashboard (React) |

---

## Quick Start

```bash
# 1. Clone the repo
git clone https://github.com/YOUR_USERNAME/saas-billing-platform.git
cd saas-billing-platform

# 2. Copy environment config
cp .env.example .env

# 3. Start infrastructure
docker-compose up -d postgres redis kafka zookeeper

# 4. Build all Java services
mvn clean install -DskipTests

# 5. Run a service
mvn spring-boot:run -pl services/api-gateway
```

See [Project Guide](./docs/PROJECT_GUIDE.md) for full setup instructions.

---

## Status

🚧 **In Development** — Phase 0: Project bootstrap complete.
