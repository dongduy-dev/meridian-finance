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
* **Identity and Access Control**: Identity manages Customer and internal-user authentication, secure session lifecycle, account recovery, roles, permissions, and access boundaries. Permission-based RBAC, Customer ownership checks, and purpose-limited contracts protect Customer and internal operations.
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

## Run Meridian Locally

Meridian local development uses one backend environment and two browser applications. Docker Compose runs the Java backend together with PostgreSQL and Mailpit. Customer Web and Internal Web run separately with Vite and call the same backend API at `http://localhost:8080/api/v1`.

### Repository Layout

```text
meridian-finance/
├── meridian-platform/       # Java/Spring backend, Flyway migrations, and local Compose environment
├── customer-web/            # React/Vite Customer Web application
├── internal-web/            # Shared React/Vite Internal Web application
├── docs/                    # Business, architecture, API, database, frontend, and project documentation
└── .github/workflows/       # Backend and frontend CI workflows
```

The active backend modules under `com.meridian.platform` are:

```text
shared · identity · customer · partner · loan · approval · document · audit · notification
```

`shared` is a technical shared kernel, not a bounded context. [MER-ARCH-002](docs/architecture/MER-ARCH-002-project-structure.md) defines source and package structure; [MER-ARCH-003](docs/architecture/MER-ARCH-003-dependency-rules.md) defines legal dependencies and architecture enforcement.

### Local Runtime Topology

```text
Browser
  │
  ├── Customer Web       http://localhost:5173
  │        │
  │        └──────────────┐
  │                       │
  └── Internal Web       http://localhost:5174
           │              │
           └──────────────┤
                          ▼
                 Meridian Platform
                 http://localhost:8080
                          │
                 ┌────────┴────────┐
                 ▼                 ▼
            PostgreSQL          Mailpit
              :5432          SMTP :1025
                              UI   :8025
```

The frontend development servers do not run inside the current Compose stack. Both use `VITE_API_BASE_URL=http://localhost:8080/api/v1`, and the backend local CORS configuration allows the Customer Web and Internal Web origins.

### Backend Environment

Docker is the only runtime prerequisite for the backend Compose path. Compose builds Meridian with the Maven wrapper, starts PostgreSQL 16 and Mailpit, runs Flyway during normal Spring Boot startup, and persists PostgreSQL and Document filesystem data in named volumes.

```bash
cd meridian-platform
# Copy .env.example to .env and fill the required local secrets.
docker compose up --build
```

Useful local endpoints:

- Backend health: `http://localhost:8080/api/v1/health`
- OpenAPI: `http://localhost:8080/v3/api-docs`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Mailpit UI: `http://localhost:8025`

### Customer Web

`customer-web/` contains Meridian's responsive Customer Web application. [MER-FE-001](docs/frontend/MER-FE-001-customer-web-blueprint.md) defines its frontend architecture, state ownership, visual language, accessibility baseline, and delivery sequence.

```bash
cd customer-web
npm ci
# Copy .env.example to .env when local configuration is needed.
npm run dev
```

Customer Web uses `http://localhost:5173` by default and calls the local backend through `VITE_API_BASE_URL=http://localhost:8080/api/v1`.

### Internal Web

`internal-web/` contains Meridian's shared Internal Web application. Staff Web handles lending operations under `/staff/*`, while Back-Office Administration handles administrative capabilities under `/admin/*`. The two areas share authentication, session management, protected transport, responsive Internal Web chrome, and common UI foundations while keeping their feature routes, queries, commands, and authorization boundaries separate.

[MER-FE-002](docs/frontend/MER-FE-002-staff-web-blueprint.md) defines Staff Web. [MER-FE-003](docs/frontend/MER-FE-003-back-office-administration-blueprint.md) defines Back-Office Administration.

```bash
cd internal-web
npm ci
# Copy .env.example to .env when local configuration is needed.
npm run dev
```

Internal Web uses `http://localhost:5174` and calls the same local backend through `VITE_API_BASE_URL=http://localhost:8080/api/v1`.

For either frontend, verification commands are `npm run lint`, `npm run typecheck`, `npm test`, and `npm run build`.

<details>
<summary>Local secret generation and runtime notes</summary>

`meridian-platform/.env.example` is the local backend configuration inventory. At minimum, set `POSTGRES_PASSWORD`, the three required Base64-encoded symmetric key values, and a matching JWT private/public key pair before starting the backend.

Generate each local symmetric key with one of these commands and run the selected command three times:

```bash
openssl rand -base64 32
```

```powershell
[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
```

`MERIDIAN_CUSTOMER_ENCRYPTION_KEY` and `MERIDIAN_LOAN_DISBURSEMENT_SNAPSHOT_KEYS_LOCAL` must each decode to exactly 32 bytes. `MERIDIAN_CUSTOMER_FINGERPRINT_KEY` must decode to at least 32 bytes. The local disbursement-snapshot active key ID is `local`.

Generate one matching RSA-2048 signing pair in PowerShell. The command emits the Base64-encoded PKCS#8 private key and X.509 SubjectPublicKeyInfo public key expected by Meridian:

```powershell
$rsa = [Security.Cryptography.RSA]::Create(2048)
"MERIDIAN_JWT_PRIVATE_KEY=$([Convert]::ToBase64String($rsa.ExportPkcs8PrivateKey()))"
"MERIDIAN_JWT_PUBLIC_KEY=$([Convert]::ToBase64String($rsa.ExportSubjectPublicKeyInfo()))"
```

Keep both values from the same command run. Meridian rejects missing, malformed, mismatched, or weaker signing keys.

The local backend allows `http://localhost:5173` and `http://localhost:5174` by default. Mailpit captures local verification and password-reset email without external SMTP credentials. Browser confirmation flows use the fragment token from the email as the JSON-body token for the corresponding confirmation endpoint; do not place the token in a backend query string.

`.env` contains local secrets and must not be committed.

Stop the backend environment with `docker compose down`. Named PostgreSQL and Document volumes are preserved. `docker compose down -v` removes them and should be used only for an intentionally disposable local environment.

</details>

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
│        React/Vite Customer Web  ·  Internal Web  ·  Mobile (Future)         │
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
| **Identity & Access** | Customer registration, User identity, credentials and email-verification state, JWT access, roles, permissions, and the complete platform's session lifecycle. |
| **Customer Management** | Customer lifecycle, profile readiness, mutable source bank accounts, ownership controls, and protection of Customer data. |
| **Partner Management** | Partner Companies, Partner Employees, monthly imports, employment matching, and reusable Salary Advance employment links. |
| **Loan Core / Lending Lifecycle** | Product policies, LoanApplication state, product verification, review and correction cycles, offers, contracts, activation, LoanAccount servicing, contractual payoff, Administrative Full-Balance Settlement, and administrative closure. |
| **Approval Workflow** | Immutable Loan Officer recommendations, independent Approver decisions, decision authority, and maker-checker evidence. |
| **Document Management** | Checklists, uploads, immutable versions, manual document review, readiness, storage, and advisory OCR-assisted processing. |
| **Audit & Compliance Controls** | Append-only, PII-safe evidence of important business actions and compliance-oriented history. |
| **Notification** | Controlled verification-email rendering and SMTP transport; broader channels, preferences, durable delivery status, and retry management remain future increments. |

---

## Technology Stack

### Backend

| Technology | Purpose                                                                                    |
|---|--------------------------------------------------------------------------------------------|
| **Java 25** | LTS runtime with virtual threads and pattern matching                                      |
| **Spring Boot 4.1.0** | Application framework                                                                      |
| **Spring Modulith 2.1.0** | Module organization, observability support, and transactional event-publication persistence |
| **Spring Security** | Authentication & authorization                                                             |
| **Spring Mail** | Notification-owned SMTP delivery for controlled Identity security email                  |
| **Spring Data JPA / Hibernate** | Data persistence                                                                           |
| **Flyway** | Versioned database migrations                                                              |
| **ArchUnit** | Executable fitness functions for core layer, security, and shared-kernel rules             |
| **JWT (RS256)** | Access-token authentication with asymmetric signing                                      |
| **Springdoc OpenAPI** | Generated OpenAPI documentation and Swagger UI for the backend API                         |

### Frontend

| Technology | Purpose |
|---|---|
| **React** | Customer Web, plus Staff Web and Back-Office Administration within Internal Web |
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
| **Docker Compose** | Local application, PostgreSQL, and Mailpit SMTP-capture environment |
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
- [x] Atomic Customer registration, digest-only Identity email verification and password reset, post-commit Notification SMTP delivery, enumeration-safe recovery requests, verification-gated login, and reset-time refresh-session revocation
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

### Phase 3 — Customer and Internal Web Experience

- [x] Customer Web for profile, applications, documents, offers, and loan tracking
- [x] Staff Web lending operations inside Internal Web for review, approval, correction, disbursement, repayment, settlement, and administrative closure
- [ ] Back-Office Administration inside Internal Web for products, partners, users, and configuration

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

- Extend Notification beyond Identity security email to SMS and in-app messages, preferences, durable delivery status, and retry management
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

### Frontend

- [Customer Web frontend blueprint](docs/frontend/MER-FE-001-customer-web-blueprint.md)
- [Staff Web frontend blueprint](docs/frontend/MER-FE-002-staff-web-blueprint.md)
- [Back-Office Administration frontend blueprint](docs/frontend/MER-FE-003-back-office-administration-blueprint.md)

### Project

- [Follow-up register](docs/project/MER-TRACK-001-follow-up-register.md)

### Development

- [Commit convention](docs/development/commit-convention.md)

---

## License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.
