# Project Meridian

## Meridian Lending Platform

Meridian is a multi-product digital lending platform built around a common lending lifecycle and product-specific policies. Salary Advance is the flagship workflow, with streamlined Unsecured Consumer Loan and Collateral Loan workflows built on the same lending core.

Meridian helps lending teams manage the full journey from application through servicing and closure. Product workflows combine automated processing with controlled human review where business decisions or document verification require it. The platform is designed around practical financial software concerns such as auditability, security, data integrity, controlled state transitions, maker-checker controls, document traceability, and clear operational ownership.

The complete Meridian platform combines a Java and Spring Boot modular monolith, backed by PostgreSQL, with React/Vite client applications and Python/FastAPI OCR processing. Meridian applies Domain-Driven Design and Practical Hexagonal Architecture within clearly defined bounded contexts. This approach enables rapid delivery within a cohesive platform while preserving clear boundaries and an evolutionary path toward selective distributed service extraction when justified by business requirements, scale, or operational ownership.

---

## Key Features

### Lending Products

* **Salary Advance**: Uses Partner-linked employment evidence with a product-specific eligibility, limit, and exposure model.
* **Unsecured Consumer Loan**: Uses income, bank-statement, and employment evidence with manual verification, correction, and flat-rate pricing.
* **Collateral Loan**: Uses structured Collateral information with Document-owned ownership evidence and manual verification.

All three products use Meridian's common application, approval, contract, activation, servicing, and closure lifecycle while retaining product-specific rules.

### Platform Capabilities

* **Common Lending Lifecycle**: A shared `LoanApplication` lifecycle coordinates submission, verification, review, approval, Customer response, contract readiness, disbursement, and `LoanAccount` activation.
* **Document and OCR-Assisted Processing**: Document owns document checklists, uploads, immutable versions, review, replacement, waiver, and processing readiness. OCR-assisted processing remains advisory, while authorized Document review remains authoritative.
* **Review, Correction, and Approval**: Loan owns application review and correction workflows. Loan Officers record recommendations, while Approvers make independent decisions under maker-checker controls.
* **Offers, Contracts, and Disbursement**: Loan preserves accepted lending terms through immutable offers, versioned contracts, Customer acknowledgment, readiness checks, and controlled disbursement activation.
* **Loan Servicing**: Salary Advance, UCL, and Collateral Loan share repayment, overdue evaluation and cure, contractual payoff, payment-backed Administrative Full-Balance Settlement, and separate administrative closure.
* **Identity and Access Control**: RS256 access tokens use persistent configured signing keys, while opaque HttpOnly refresh tokens rotate through Identity-owned sessions with reuse detection. Password login applies configurable consecutive-failure lockout without exposing account existence or invalidating established sessions. Login and refresh also use configurable single-instance per-remote-address request throttling. Current-session logout revokes the presented refresh family and durably invalidates the presented access token. Permission-based RBAC, Customer ownership checks, and purpose-limited cross-context contracts protect Customer and Staff operations.
* **Transactional Safety**: Critical financial commands use atomic state changes, operation-specific request identities, semantic replay validation, and concurrency controls.
* **Immutable Audit Trail**: Ordered lifecycle history and append-only, PII-safe business audit evidence preserve traceability.
* **Sensitive Data Protection**: AES-GCM protects selected Customer-sensitive values and immutable Loan disbursement bank-account snapshots at rest, while purpose-limited access and restricted or masked responses limit PII exposure.

### User Roles

| Role | Representative capabilities and responsibilities |
|---|---|
| **Customer** | Manages their own profile and bank accounts, verifies Salary Advance employment, submits applications, completes Customer-owned corrections, uploads documents, responds to offers, acknowledges contracts, and reads their own application and LoanAccount state. |
| **Loan Officer** | Reviews application facts and documents, requests Customer or Staff correction, performs authorized document-review actions, and records the recommendation for independent decision. |
| **Approver** | Records the independent application decision, may return work for review or correction, and performs authorized Loan-owned Administrative Full-Balance Settlement. |
| **Accounting Officer** | Prepares operational contracts, confirms readiness and external transfer evidence, records authorized repayments, and closes eligible settled LoanAccounts. |
| **Back-Office Admin** | Administers products, Partner data and imports, internal users, role assignments, and operational configuration. |

---

## Architecture

### Architecture Principles

| Principle | Implementation |
|---|---|
| **Architecture Style** | Modular Monolith (Spring Modulith) |
| **Internal Design** | Hexagonal Architecture (Ports & Adapters) |
| **Domain Modeling** | Domain-Driven Design (Bounded Contexts) |
| **Dependency Direction** | Inward-only — Infrastructure adapters → Application ports/services → Domain |
| **Boundary Enforcement** | Architecture documents define the intended module law. Current ArchUnit tests enforce core layer, security, and shared-kernel rules. |
| **Module Communication** | Narrow public application contracts support synchronous collaboration. Transaction-aware coordination preserves atomic outcomes, while durable asynchronous delivery requires explicit retry, recovery, and idempotency. |
| **Future Evolution** | Bounded contexts and published contracts preserve selective extraction options; services are extracted only when stable boundaries, scale, or operational ownership justify it |

### Complete Platform Architecture

```text
┌──────────────────────────────────────────────────────────────────────────────┐
│                                   CLIENTS                                    │
│         React/Vite Customer Portal  ·  Staff/Admin  ·  Mobile (Future)       │
└──────────────────────────────────────┬───────────────────────────────────────┘
                                       │ HTTPS
                                       ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                         API EDGE / SECURITY LAYER                            │
│              Spring Security  ·  JWT (RS256)  ·  RBAC  ·  CORS               │
└──────────────────────────────────────┬───────────────────────────────────────┘
                                       │
┌──────────────────────────────────────▼──────────────────────────────────────┐
│                    MODULAR MONOLITH (Spring Boot + Spring Modulith)         │
│                                                                             │
│  ┌────────────────────┐ ┌────────────────────┐ ┌────────────────────────┐   │
│  │ Identity & Access  │ │ Customer Management│ │ Partner Management     │   │
│  └────────────────────┘ └────────────────────┘ └────────────────────────┘   │
│                                                                             │
│  ┌────────────────────┐ ┌────────────────────┐ ┌────────────────────────┐   │
│  │ Loan Core /        │ │ Approval Workflow  │ │ Document Management    │   │
│  │ Lending Lifecycle  │ │                    │ │ + OCR Boundary         │   │
│  └────────────────────┘ └────────────────────┘ └────────────────────────┘   │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │ Audit & Compliance Controls                                         │    │
│  │ Append-only PII-safe audit evidence · Ordered lifecycle history     │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │ Notification · Templates · Delivery requests · Channels · Status    │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                             │
│       Public contracts · Transaction-aware coordination · Events            │
│       Spring Modulith transactional event-publication persistence           │
│       Logging foundation · Actuator observability · ArchUnit                │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
                  ┌────────────────────┼────────────────────┐
                  ▼                    ▼                    ▼
      ┌──────────────────┐   ┌────────────────┐   ┌──────────────────────┐
      │ PostgreSQL       │   │ File Storage   │   │ OCR Service          │
      │ Module data      │   │ Document       │   │ Python + FastAPI     │
      │ Event evidence   │   │ uploads and    │   │ Vietnamese TrOCR     │
      │ Audit evidence   │   │ OCR inputs     │   │ Advisory processing  │
      └──────────────────┘   └────────────────┘   └──────────────────────┘
```

### Bounded Contexts

| Context | Responsibility |
|---|---|
| **Identity & Access** | User identity, credentials, JWT access, roles, permissions, and the complete platform's session lifecycle. |
| **Customer Management** | Customer lifecycle, profile readiness, mutable source bank accounts, ownership controls, and protection of Customer data. |
| **Partner Management** | Partner Companies, Partner Employees, monthly imports, employment matching, and reusable Salary Advance employment links. |
| **Loan Core / Lending Lifecycle** | Product policies, LoanApplication state, product verification, review and correction cycles, offers, contracts, activation, LoanAccount servicing, contractual payoff, Administrative Full-Balance Settlement, and administrative closure. |
| **Approval Workflow** | Immutable Loan Officer recommendations, independent Approver decisions, decision authority, and maker-checker evidence. |
| **Document Management** | Checklists, uploads, immutable versions, manual document review, readiness, storage, and advisory OCR-assisted processing. |
| **Audit & Compliance Controls** | Append-only, PII-safe evidence of important business actions and compliance-oriented history. |
| **Notification** | Message templates, notification requests, channel selection, delivery attempts, delivery status, retry policy, and Customer or Staff communication preferences. |

---

## Technology Stack

### Backend

| Technology | Purpose                                                                                    |
|---|--------------------------------------------------------------------------------------------|
| **Java 25** | LTS runtime with virtual threads and pattern matching                                      |
| **Spring Boot 4.1.0** | Application framework                                                                      |
| **Spring Modulith 2.1.0** | Module organization, observability support, and transactional event-publication persistence |
| **Spring Security** | Authentication & authorization                                                             |
| **Spring Data JPA / Hibernate** | Data persistence                                                                           |
| **Flyway** | Versioned database migrations                                                              |
| **ArchUnit** | Executable fitness functions for core layer, security, and shared-kernel rules             |
| **JWT (RS256)** | Access-token authentication with asymmetric signing                                      |
| **Springdoc OpenAPI** | Generated OpenAPI documentation and Swagger UI for the backend API                         |

### Frontend

| Technology | Purpose |
|---|---|
| **React** | Customer, Staff, and administrative web experiences |
| **Vite** | Frontend build tooling |

### OCR / Document Intelligence

| Technology | Purpose                                                        |
|---|----------------------------------------------------------------|
| **Python + FastAPI** | OCR service and asynchronous document-processing worker        |
| **Vietnamese TrOCR** | Advisory OCR-assisted extraction for uploaded documents        |

### Database

| Technology | Purpose |
|---|---|
| **PostgreSQL** | Primary relational store for ACID transactions, concurrency control, durable event publication, and asynchronous job queues (SKIP LOCKED) |

### Infrastructure

| Technology | Purpose                                              |
|---|------------------------------------------------------|
| **Docker Compose** | Local application and PostgreSQL environment         |
| **GitHub Actions** | CI pipeline (build, test, architecture verification) |
| **SLF4J + Logback** | Structured JSON logging                              |

### Development Tools

| Tool | Purpose |
|---|---|
| **Git / GitHub** | Version control |
| **Postman** | API testing |

---

## Roadmap

### Phase 1 — Core Lending MVP

- [x] Common loan application lifecycle with state machine, product catalog, and product-policy framework
- [x] Customer profile and bank-account readiness, Partner employment evidence, and Salary Advance eligibility and exposure foundations
- [x] Product-aware document checklists, immutable versions, manual review, correction, and guarded resubmission
- [x] Loan Officer review, independent approval, maker-checker controls, audit evidence, and ordered lifecycle history
- [x] Approved offers, Customer response, operational contracts, readiness, manual disbursement, LoanAccount activation, and final schedules
- [x] Salary Advance lifecycle through repayment, overdue cure, contractual payoff, Administrative Full-Balance Settlement, administrative closure, and exact product-exposure effects
- [x] UCL lifecycle through evidence, verification and correction, approval, exact-request pricing, activation, servicing, closure, product-scoped outstanding protection, and zero Salary Advance exposure
- [x] Collateral Loan lifecycle through structured facts, ownership evidence, numbered verification, document-only correction, exact-request pricing, activation, servicing, closure, and zero Salary Advance exposure
- [x] Persistent-key JWT/RBAC, configurable password-login lockout, rotating refresh-token sessions, current-session token invalidation, command-specific idempotency, transactional and concurrency controls, Flyway/PostgreSQL persistence, Spring Modulith event publication, and GitHub Actions verification
- [x] Startup replay and recovery for incomplete event publications
- [x] Application containerization and a complete local Compose environment with persistent PostgreSQL and Document storage
- [x] Structured JSON logging with request and business correlation
- [x] Generated OpenAPI documentation, Swagger UI, and explicit frontend CORS configuration

### Phase 2 — OCR-Assisted Document Processing

- [ ] Containerized Python FastAPI OCR service
- [ ] Vietnamese TrOCR model integration
- [ ] Whole-page text detection, line segmentation, and reading-order reconstruction
- [ ] PostgreSQL-backed asynchronous OCR jobs and result persistence
- [ ] Authorized manual review experience for OCR-assisted document results

### Phase 3 — Customer and Staff Experience

- [ ] Customer web portal for profile, applications, documents, offers, and loan tracking
- [ ] Staff web portal for review, approval, correction, disbursement, and repayment operations
- [ ] Back-office administration for products, partners, users, and configuration

### Phase 4 — Operational Maturity

- [ ] Production object storage, malware scanning, retention, and recovery controls
- [ ] Evaluate Redis for distributed rate limiting, session controls, and short-lived caching
- [ ] Prometheus metrics and Grafana dashboards
- [ ] OpenTelemetry distributed tracing
- [ ] Performance profiling, load testing, and security hardening

### Phase 5 — Analytics and Risk

- [ ] Evaluate Elasticsearch-backed loan search and audit-log analytics
- [ ] Reporting dashboards
- [ ] Rule-based risk assessment engine
- [ ] Loan eligibility scoring

### Future Considerations

- Notification service for email, SMS, and in-app messages
- Mobile application support
- Payroll provider, employer API, payment gateway, bank transfer, and credit-bureau integrations
- Repayment reversal/refund, unapplied cash, suspense processing, waiver/write-off, and bank reconciliation
- Multi-level and configurable approval workflows
- Selective service extraction where justified, with Kafka considered only when durable cross-service streaming needs emerge

### Future — Financial Ledger and Accounting

- Double-entry accounting ledger
- Journal-entry engine with debit and credit posting
- Chart of accounts management
- Automated disbursement and repayment posting
- Financial reconciliation and balance validation
- Accounting audit reports

---

## Project Structure

```text
meridian-finance/
├── meridian-platform/       # Java/Spring backend, Flyway migrations, and PostgreSQL Compose
├── docs/                    # Business, architecture, API, database, and project documentation
└── .github/workflows/       # Continuous integration workflows
```

The active backend modules under `com.meridian.platform` are:

```text
shared · identity · customer · partner · loan · approval · document · audit
```

`shared` is a technical shared kernel, not a bounded context. Feature modules use Meridian's Practical Hexagonal Architecture with only the packages each module needs. [MER-ARCH-002](docs/architecture/MER-ARCH-002-project-structure.md) defines source and package structure; [MER-ARCH-003](docs/architecture/MER-ARCH-003-dependency-rules.md) defines legal dependencies and architecture enforcement.

---

## Local Docker Compose

Docker is the only runtime prerequisite for the local Compose path. Compose builds Meridian with the Maven wrapper, starts PostgreSQL 16, runs Flyway through normal Spring Boot startup, and persists PostgreSQL and Document filesystem data in named volumes.

1. Change to `meridian-platform`.
2. Copy `.env.example` to `.env`.
3. Set `POSTGRES_PASSWORD`, the three Base64-encoded symmetric key values, and the matching JWT private/public key pair in `.env`.
4. Run `docker compose up --build`.
5. Verify `http://localhost:8080/api/v1/health`, `http://localhost:8080/v3/api-docs`, and `http://localhost:8080/swagger-ui.html`.

Generate each local symmetric key with one of these commands and run the selected command three times:

```bash
openssl rand -base64 32
```

```powershell
[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
```

`MERIDIAN_CUSTOMER_ENCRYPTION_KEY` and `MERIDIAN_LOAN_DISBURSEMENT_SNAPSHOT_KEYS_LOCAL` must each decode to exactly 32 bytes. `MERIDIAN_CUSTOMER_FINGERPRINT_KEY` must decode to at least 32 bytes. The local disbursement-snapshot active key ID is `local`, and `MERIDIAN_LOAN_DISBURSEMENT_SNAPSHOT_KEYS_LOCAL` supplies that key-ring entry.

Generate one matching RSA-2048 signing pair in PowerShell. The command emits the exact `.env` entries Meridian consumes: a Base64-encoded PKCS#8 private key and a Base64-encoded X.509 SubjectPublicKeyInfo public key.

```powershell
$rsa = [Security.Cryptography.RSA]::Create(2048)
"MERIDIAN_JWT_PRIVATE_KEY=$([Convert]::ToBase64String($rsa.ExportPkcs8PrivateKey()))"
"MERIDIAN_JWT_PUBLIC_KEY=$([Convert]::ToBase64String($rsa.ExportSubjectPublicKeyInfo()))"
```

Keep both lines from the same command run. Meridian fails startup when either value is missing, malformed, weaker than RSA-2048, or not part of the same key pair.

`MERIDIAN_FRONTEND_ALLOWED_ORIGINS` accepts a comma-separated list of explicit frontend origins and defaults to `http://localhost:5173`. Wildcard origins are rejected.

`MERIDIAN_ACCOUNT_LOCKOUT_MAX_FAILED_ATTEMPTS` defaults to `5`, and `MERIDIAN_ACCOUNT_LOCKOUT_DURATION` defaults to `15m`. Both values must be positive. The policy applies to new Customer and Staff password logins; it does not revoke established access or refresh credentials.

`MERIDIAN_LOGIN_RATE_LIMIT_MAX_REQUESTS` defaults to `10`, and `MERIDIAN_LOGIN_RATE_LIMIT_WINDOW` defaults to `1m`. `MERIDIAN_REFRESH_RATE_LIMIT_MAX_REQUESTS` defaults to `30`, and `MERIDIAN_REFRESH_RATE_LIMIT_WINDOW` defaults to `1m`. All four values must be positive. These policies throttle login and refresh requests per effective servlet remote address and application instance; the Phase 4 Redis evaluation continues to track distributed rate limiting.

`MERIDIAN_REFRESH_TOKEN_LIFETIME` defaults to `7d`. The refresh cookie is HttpOnly, uses `SameSite=Strict`, and is restricted to `/api/v1/auth` so refresh and current-session logout can receive it. Local HTTP uses `MERIDIAN_REFRESH_TOKEN_COOKIE_SECURE=false`; deployed HTTPS environments must set it to `true`. Browser clients must send credentialed authentication requests from an explicitly allowed frontend origin.

`.env` contains local secrets and must not be committed.

Stop the stack with `docker compose down`. This preserves the named PostgreSQL and Document volumes. Adding `-v` deletes both local data volumes and should be reserved for an intentionally disposable environment.

---

## Documentation

### Business

- [Business requirements and workflows](docs/business/MER-BIZ-001-business-requirements-and-workflows.md)

### Architecture

- [Bounded contexts and ownership](docs/architecture/MER-ARCH-001-bounded-contexts.md)
- [Project structure](docs/architecture/MER-ARCH-002-project-structure.md)
- [Dependency rules and architecture enforcement](docs/architecture/MER-ARCH-003-dependency-rules.md)
- [API error catalogue](docs/architecture/MER-ARCH-004-api-error-catalog.md)
- [OCR-assisted document-processing architecture](docs/architecture/MER-ARCH-005-ocr-architecture.md)
- [API request flows and runtime dependencies](docs/architecture/MER-ARCH-006-api-request-flow-and-dependencies.md)

### API

- [Endpoint and scenario guide](docs/api/MER-API-001-endpoints-and-postman-scenarios.md)
- [Postman collection](docs/api/Meridian-Platform.postman_collection.json)

### Database

- [Logical data model and ERD](docs/database/MER-DB-001-data-model-and-erd.md)
- [Current physical-schema snapshot](docs/database/MER-DB-CURRENT-SCHEMA.sql)

### Project

- [Follow-up register](docs/project/MER-TRACK-001-follow-up-register.md)

### Development

- [Commit convention](docs/development/commit-convention.md)

---

## License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.
