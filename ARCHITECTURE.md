# NotifierX - Architecture & Engineering Guidelines

## 1. Project Mission & Core Capabilities
NotifierX is a high-reliability distributed notification dispatch engine. It provides a multi-tenant API for triggering alerts across multiple channels while enforcing rate limits, fault tolerance, and transactional auditing.

Key system capabilities:
* Multi-Channel Notification Ingestion (`EMAIL`, `SMS`, `IN_APP`).
* Distributed Token Bucket Rate Limiting per client tier backed by Redis.
* Dynamic Provider Routing using the Strategy and Factory patterns.
* Resilient Failover & Fallback mechanics for downstream provider errors.
* Immutable Transactional Auditing in PostgreSQL.

---

## 2. Engineering Standards & Conventions
To maintain clean code and professional software design:
* **Separation of Concerns:** Strict Layered Architecture (`Controller` -> `Service` -> `Strategy/Factory` -> `Repository`).
* **Design Patterns:**
  * `Strategy Pattern`: Encapsulate dispatching channels/providers.
  * `Factory Pattern`: Dynamically resolve appropriate delivery strategies.
  * `Template Method Pattern`: Standardize notification lifecycles (Validate -> Rate Limit -> Route -> Audit).
* **Domain Integrity:** Keep JPA entities focused; use DTOs/Records for all API request/response payloads.
* **Modern Java:** Use Java 21 features (Records, Pattern Matching, Sealed types where appropriate).
* **Defensive Error Handling:** Global exception handling (`@RestControllerAdvice`) returning structured RFC 7807 problem details.

---

## 3. Incremental Roadmap
* [x] **Milestone 0:** Infrastructure bootstrap (Docker, Postgres, Redis, App Config).
* [ ] **Milestone 1:** Core Domain Models & Repositories (`User`, `Tier`, `ChannelType`, `NotificationAudit`).
* [ ] **Milestone 2:** Redis-backed Token Bucket Rate Limiter.
* [ ] **Milestone 3:** Strategy & Factory Dispatch Architecture with mock downstream providers.
* [ ] **Milestone 4:** Notification Orchestration Service & Template Method Execution.
* [ ] **Milestone 5:** REST Controllers, API Validation, & Global Exception Handler.
* [ ] **Milestone 6:** Resiliency (Fallbacks/Retries) & Integration Tests.
