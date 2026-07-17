NotifierX is a distributed notification and alerting engine designed to handle multi-channel communication (Email, SMS, In-App notifications) at scale. Built with Spring Boot, the platform enforces consumer-tier rate limits, provides seamless third-party provider failovers, and maintains an immutable audit trail for transactional reliability.

---

## Design Pattern

I wanted to avoid standard CRUD paradigms and instead focus on some design patterns I learned a few semesters ago during my stay in university:

*   **Strategy Pattern:** Dynamically encapsulates notification provider mechanics.
*   **Factory Pattern:** Manages dynamic instantiations of specific communication channel routers,  based on user subscriptions and real-time payload configurations.
*   **Template Method Pattern:** Defines a standard pipeline for notif lifecycles (Validate Request -> Token Bucket Rate Limit Check -> Route & Dispatch -> Log Audit Ledger).
*   **Token Bucket Algorithm (Redis):** High-throughput, distributed rate-limiting logic per API consumer tier to lessen network spam / system overloading.

---

## Project Directory

```text
notifierx-backend/
├── src/main/java/com/project/notifierx/
│   ├── config/          # Infrastructure configurations (Redis, Database, Security)
│   ├── controllers/     # REST Endpoints (API request mapping & ingestion)
│   ├── domain/          # Core Domain Entities (User, AuditLedger, SubscriptionTier)
│   ├── exception/       # Centralized Global Error Handling & custom domains
│   ├── repository/      # Spring Data JPA Repositories (PostgreSQL mappings)
│   ├── service/         # Core business orchestration & pipeline mechanics
│   └── strategies/      # Factory & Strategy Pattern implementations for routing
└── src/main/resources/
    └── application.yml  # Application properties and pipeline threshold rules
```

---

## Environment Setup

To clone and run, ensure your machine meets the following environment baselines:

* **Java Development Kit:** JDK 21 (LTS)
* **Build Tooling:** Apache Maven 3.9+
* **Containerization:** Docker Desktop (WSL 2 backend enabled)

### Getting Started

1. Clone repo:
```bash
git clone [https://github.com/YOUR_GITHUB_USERNAME/notifierx-backend.git](https://github.com/YOUR_GITHUB_USERNAME/notifierx-backend.git)
cd notifierx-backend
```

2. Launch the Redis and PostgreSQL dependencies in the background using Docker:
```bash
docker run --name notifierx-db -e POSTGRES_PASSWORD=secret -p 5432:5432 -d postgres
docker run --name notifierx-cache -p 6379:6379 -d redis
```

3. Build:
```bash
mvn clean package
```

4. Run app:
```bash
mvn spring-boot:run
```