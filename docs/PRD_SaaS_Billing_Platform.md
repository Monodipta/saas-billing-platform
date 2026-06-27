Product Requirements Document (PRD) 

## Multi-Tenant SaaS Billing & Subscription Platform 

Document owner: Monodipta Maity Status: Draft v1.0 Last updated: June 2026 Project type: Personal portfolio project (industry ~~-~~ standard, CV ~~-r~~ eady) 

## 1. Overview 

A backend- ~~fi~~ rst, event ~~-~~ driven platform that lets a SaaS business manage subscription plans, meter usage, generate invoices, and process billing events reliably ~~—~~ modeled on how internal billing engines work at companies like Stripe Billing, Chargebee, or AWS Marketplace metering. The system is multi ~~-~~ tenant: each tenant (a company/customer) has isolated billing data, its own plan, and its own usage limits. 

The project is deliberately scoped to be infrastructur ~~e-~~ grade rather than UI ~~-~~ heavy ~~—~~ the goal is to produce something that demonstrates production ~~-s~~ tyle backend engineering: correctness under concurrency, idempotency, auditability, and event ~~-~~ driven consistency. 

## 2. Problem Statement 

Most personal/portfolio projects are CRUD apps with a UI. They rarely demonstrate: 

- e Handling money- ~~a~~ djacent logic safely (no double charges, no lost usage events) e Idempotent API design and webhook handling 

- e Event ~~-~~ driven usage aggregation at scale 

- e Miult ~~i-~~ tenant data isolation and access control 

This project exists to close that gap and produce a defensible, explainable system for interviews ~~—~~ something you can speak to in depth because you built every piece of it. 

## 3. Goals & Objectives 

Goal Description 

Gl Build a working subscription lifecycle (create, upgrade, downgrade, cancel, renew) 

G2 Meter tenant usage in near rea ~~l-~~ time via event streaming 

Goal Description 

G3 Generate accurate, idempotent invoices ona billing cycle 

- G4 Simulate payment processing via mock Stripe ~~-~~ style webhooks, handled safely 

- G5 Demonstrate multi ~~-~~ tenant security (JWT + tenan ~~t-~~ scoped authorization) 

- G6 Produce CV ~~-r~~ eady artifacts: clean repo, README, architecture notes, demo video/GIF 

Non- ~~g~~ oals: real payment processing, real money movement, multi ~~-~~ currency tax compliance, production ~~-s~~ cale deployment. 

## 4. Target Users / Personas 

Since this is a backend platform, "users" are modeled as roles within the system: 

- e Tenant Admin ~~—~~ manages their company's subscription, views invoices, views usage. 

- e Platform Admin ~~—~~ manages plan catalog, views all tenants, handles disputes/refunds (mocked). 

- e System (machine actor) ~~—~~ usage events arrive from "client services" simulating real product usage (e.g., API calls, storage consumed). 

## 5. Scope 

## In Scope 

- e Plan catalog management (Free / Pro / Enterprise tiers with configurable limits) 

- e Subscription lifecycle state machine (trial > active > pas ~~t_~~ due > canceled) e Usage event ingestion and aggregation (e.g., "API calls," "storage GB," "active seats") e Invoice generation (PDF) on billing cycle close 

- e Mock payment gateway + webhook handler (idempotent) 

- e Tenant ~~-~~ scoped JWT authentication and RBAC (Tenant Admin vs Platform Admin) 

- e Admin dashboard (minimal React UI) for plan/tenant/invoice visibility 

- e Notification service for billing events (invoice generated, payment failed, plan upgraded) 

## Out of Scope (v1) 

- e Real payment gateway integration (Stripe/Razorpay live mode) 

- e Tax calculation / mult ~~i-~~ currency 

- e Dunning email sequences beyonda single retry 

- e Mobile app 

- e SOC2 ~~-g~~ rade compliance tooling 

## 6. Functional Requirements 

## 6.1 Plan & Pricing Management 

- e FR1: Platform Admin can create/update plans with: name, price, billing interval (monthly/yearly), included usage quotas, overage rate per unit. 

- e FR2: Plans are versioned ~~—~~ changing a plan's price doesn't retroactively affect already ~~-~~ subscribed tenants until their next renewal. 

## 6.2 Subscription Lifecycle 

- e FR3:Tenant can subscribe to a plan, with an optional trial period (e.g., 14 days). 

- e FR4: Tenant can upgrade/downgrade plans; upgrades apply immediately with prorated charges, downgrades apply at next cycle. 

- ¢ FR5: System auto ~~-t~~ ransitions subscriptions: (trialing+ active), (on failed payment), (after N retries). 

- e FRG: Allstate transitions are logged with timestamp + reason (audit trail). 

## 6.3 Usage Metering 

- ¢ FR7: Client services emit usage events ((tenant ~~_i~~ d), (metric), (quantity), (timestamp)) to a Kafka topic. 

- e FR8: A usage ~~-a~~ ggregator service consumes events and maintains running totals per tenant per billing cycle in Redis (hot) and PostgreSQL (durable). 

- e FR9: Usage exceeding the plan's included quota is tracked separately for overage billing. 

## 6.4 Invoicing 

- e FR1O: At the end of each tenant's billing cycle, an Invoice Service computes: base plan fee + overage charges ~~—~~ any credits, and generates a PDF invoice. 

- e FR11: Invoice generation is idempotent ~~—~~ r ~~e-~~ running the job for the same cycle does not create duplicate invoices. 

e FR12: Invoices are stored and retrievable via API; tenants can view/download past invoices. 

## 6.5 Payments (Mocked) 

- e FR13: A mock payment gateway simulates charging a tenant's saved "payment method" and returns success/failure asynchronously via webhook. 

- e FR14: Webhook handler verifies a signature (simulated HMAC) and is idempotent ~~—~~ replayed/duplicate webhooks must not double ~~-p~~ rocess a payment. 

- e FR15: Failed payments trigger a retry policy (e.g., 3 attempts over 5 days) before subscription moves to(canceled). 

## 6.6 Tenant & Access Control 

- ¢ FR16: All APIs are JWT ~~-s~~ ecured; tokens carry and claims. 

- e FR17: Tenant Admins can only access their own tenant's data; Platform Admins can access all tenants (scoped via role check, not just token trust). 

## 6.7 Notifications 

- e FR18: Key billing events (invoice generated, payment failed, plan changed, trial ending in 3 days) publish events that a notification service consumes and "sends" (mocked email/log output). 

## 6.8 Admin Dashboard (minimal UI) 

- e FR19: A simple React dashboard to view tenants, current plan, usage ~~-t~~ o ~~-d~~ ate, and invoice history ~~—~~ enough to demo the system, not a polished product UI. 

## 7. Non-Functional Requirements 

**==> picture [480 x 156] intentionally omitted <==**

**----- Start of picture text -----**<br>
||||
|---|---|---|
|Category|Requirement|
|Idempotency|All payment/webhook/invoice operations must be safely retryable without|
|side|effects|
|Consistency|Usage totals must never be lost on service restart|(durable Kafka offsets +|
|DB persistence)|
|Security|JWT validation on every request; tenant data isolation enforced at query|
|level, not just API level|

**----- End of picture text -----**<br>


Category 

Requirement 

**==> picture [480 x 110] intentionally omitted <==**

**----- Start of picture text -----**<br>
||||||||
|---|---|---|---|---|---|---|
|Observability|Structured logs for every state transition; correlation IDs across services|
|Scalability (design|-|| Usage ingestion path should be horizontally scalable|(stateless consumers|
|level)|behind Kafka consumer groups)|
|Auditability|Every billin|g-|affecting|action|(plan change, invoice, payment attempt)|is|
|recorded immutably|

**----- End of picture text -----**<br>


## 8. System Components (high-level) 

(kept intentionally lightweight per your earlier preference — no deep architecture diagram, just the moving pieces) 

1. Billing Service (Java/Spring Boot) ~~—~~ plans, subscriptions, lifecycle state machine 

2. Usage Aggregator (Java or Python) ~~—~~ Kafka consumer, Redis + PostgreSQL writer 

3. Invoice Service (Java/Spring Boot) ~~—~~ cycle ~~-~~ close job, PDF generation 

4. Payment Webhook Handler (Spring Boot) ~~—~~ idempotent webhook processing 

5. Notification Service (Python/FastAPI or Node) ~~—~~ consumes billing events, mocks delivery 

6. API Gateway ~~—~~ JWT validation, routing, rate limiting 

7. Admin Dashboard (React + Redux Toolkit) ~~—~~ read ~~-~~ mostly UI 

## 9. Tech Stack Mapping (to your CV) 

**==> picture [462 x 216] intentionally omitted <==**

**----- Start of picture text -----**<br>
||||||
|---|---|---|---|---|
|Component|Tech|CV Skill Reinforced|
|Billing Service|Java 21, Spring Boot|3,|Core backend, RDBMS|
|PostgreSQL|
|Usage Aggregator|Kafka+ Avro, Redis|Event streaming, caching|
|Invoice Service|Spring Boot, PDF generation|Ties to your Timoraa reportin|g-|engine|
|experience|
|Webhook Handler|Spring Boot,idempotency keys|_|Reliability patterns|
|Notification|FastAPI or Node/Express|Polyglot microservices|
|Service|

**----- End of picture text -----**<br>


Component 

Tech 

CV Skill Reinforced 

**==> picture [404 x 110] intentionally omitted <==**

**----- Start of picture text -----**<br>
|||||
|---|---|---|---|
|API Gateway|Spring Cloud Gateway,|Security, gateway patterns|
|JWT/OAuth2|
|Dashboard|React|19, Redux Toolkit,|Frontend|
|Tailwind|
|Deployment|Docker, Kubernetes, Helm|DevOps|

**----- End of picture text -----**<br>


## 10. Target Metrics 

## 10.1 Product / Functional Success Criteria 

- e 100% of subscription lifecycle transitions (trial>active>pas ~~t_~~ due>canceled) are correctly triggered automatically with no manual intervention in test runs. 

- e Oduplicate invoices generated across 100 repeated/forced re ~~-~~ runs of the billin ~~g-~~ cycle job (idempotency proof). 

- e Odouble ~~-c~~ harges across 100 simulated duplicate webhook deliveries. 

- e 100% of usage events are reflected in the tenant's usage total within 5 seconds of ingestion (near ~~-~~ rea ~~l-~~ time aggregation). 

## 10.2 Engineering / Technical Metrics 

**==> picture [478 x 250] intentionally omitted <==**

**----- Start of picture text -----**<br>
|||||||
|---|---|---|---|---|---|
|Metric|Target|
|API p95 latency|(read endpoints)|< 200ms|
|API p95 latency|(write endpoints, e.g.,|< 500ms|
|subscribe/upgrade)|
|Usage event ingestion throughput|> 500 events/sec sustained on local Kafka cluster|
|Webhook processing idempotency|100%|—|verified via automated duplicate|-|delivery|
|test|suite|
|Invoice generation idempotency|100%|—|verified via repeated job execution test|
|Test coverage|(core billing logic:|lifecycle,|275%|
|invoicing, webhook handling)|

**----- End of picture text -----**<br>


Metric 

Target 

System recovery 

Usage aggregator resumes from last committed Kafka offset with O data loss after forced restart 

## 10.3 Personal Learning / Portfolio Metrics 

- e Able to explain and defend every architectural decision (idempotency strategy, Saga/compensation logic if used, tenant isolation approach) in an interview without notes. 

- e Aclean public GitHub repo with README, setup instructions, and a 2 ~~-~~ 3 minute demo video/GIF. 

- e Atleast 2 CV bullet points derived directly from this project, each backed bya real, demonstrable metric (e.g., "handled 500+ events/sec with zero duplicate billing across idempotency stress tests"). 

## 11. Milestones (Suggested Phasing) 

**==> picture [481 x 226] intentionally omitted <==**

**----- Start of picture text -----**<br>
||||||||
|---|---|---|---|---|---|---|
|Phase|Deliverable|Rough Effort|
|Phase1|Plancatalog + subscription|lifecycle|(no billing yet)|1|-1|.5 weeks|
|Phase2|Kafka|-|based usage ingestion|+ aggregation|1 week|
|Phase3|Invoice generation|(idempotent, PDF output)|1 week|
|Phase4|Mock payment gateway + idempotent webhook handling|1 week|
|PhaseS|JWT auth, tenant|isolation, RBAC|3|-|4 days|
|Phase6|Notification|service|+|admin dashboard|1 week|
|Phase7|Testing, idempotency|stress|tests, documentation, demo recording|3|-|5 days|

**----- End of picture text -----**<br>


(Total: roughly 6-7 weeks at a steady part-time pace; compressible if focused full-time on weekends.) 

## 12. Risks & Assumptions 

e Risk: Scope creep toward a polished Ul instead of backend depth > Mitigate by 

capping dashboard work to Phase 6 only, after core billing logic is solid. 

- e Risk: Underestimating idempotency edge cases > Mitigate by writing the duplicate ~~-~~ delivery test suite before declaring webhook handling "done." 

- e Assumption: Payment gateway and SMS/email delivery are fully mocked; no real third ~~-~~ party billing API keys needed. 

- e Assumption: Single ~~-r~~ egion, local/dev ~~-~~ cluster deployment is sufficient ~~—~~ no need for multi ~~-~~ region HA for portfolio purposes. 

## 13. Future Enhancements (Post-v1, optional) 

- e Real Stripe/Razorpay tes ~~t~~ -mode integration 

- e Dunning email sequences with templated retries 

- e Usage ~~-~~ based pricing tiers with rea ~~l-~~ time quota alerts (e.g., "80% of quota used" notification) 

- e Multi ~~-~~ currency support 

- e Admin analytics: MRR, churn rate, cohort retention dashboards 

