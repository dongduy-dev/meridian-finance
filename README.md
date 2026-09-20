# Project Meridian

## Meridian Lending Platform

Meridian is a multi-product lending platform built around a common lending lifecycle and product-specific policies. Salary Advance is the flagship workflow, with streamlined Unsecured Consumer Loan and Collateral Loan workflows built on the same lending core.

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
* **Digital and Staff-Assisted Origination**: Customer Web supports direct Customer origination for all three products. Staff Web supports paper-originated UCL and Collateral Loan through assisted Customer intake, paper evidence capture, Customer-sourced corrections, and evidenced Customer decisions. The authenticated Staff user remains the actor while the Customer remains the business subject, and a branch-originated application remains Staff-assisted rather than switching to Customer Web. Salary Advance origination remains Customer-digital only.
* **Document and OCR-Assisted Processing**: Document owns document checklists, uploads, immutable versions, review, replacement, waiver, and processing readiness. OCR-assisted processing remains advisory, while authorized Document review remains authoritative.
* **Review, Correction, and Approval**: Loan owns application review and correction workflows. Loan Officers record recommendations, while Approvers make independent decisions under maker-checker controls.
* **Offers, Contracts, and Disbursement**: Loan preserves accepted lending terms through immutable offers, versioned contracts, Customer acknowledgment, readiness checks, and controlled disbursement activation.
* **Loan Servicing**: Salary Advance, UCL, and Collateral Loan share repayment, overdue evaluation and cure, contractual payoff, payment-backed Administrative Full-Balance Settlement, and separate administrative closure.
* **Identity and Access Control**: Identity manages Customer and internal-user authentication, session lifecycle, account recovery, roles, permissions, and access boundaries. A Staff-assisted Customer may exist without a Customer Web login; authorized Staff remain the authenticated actor while the Customer remains the business subject. Permission-based RBAC, Customer ownership checks, and purpose-limited contracts protect Customer and internal operations.
* **Transactional Safety**: Critical financial commands use atomic state changes, operation-specific request identities, semantic replay validation, and concurrency controls.
* **Immutable Audit Trail**: Ordered lifecycle history and append-only, PII-safe business audit evidence preserve traceability.
* **Sensitive Data Protection**: AES-GCM protects selected Customer-sensitive values and immutable Loan disbursement bank-account snapshots at rest, while purpose-limited access and restricted or masked responses limit PII exposure.

### User Roles

| Role | Representative capabilities and responsibilities |
|---|---|
| **Customer** | Owns profile and bank-account data and provides application facts, evidence, corrections, offer decisions, and contract acknowledgments. Customer-digital users record those actions directly; branch Customers provide them through authorized Staff. Salary Advance employment verification remains Customer-digital. |
| **Loan Officer** | Performs authorized Staff-assisted UCL and Collateral Loan intake and origination, coordinates Customer-sourced branch corrections, reviews application facts and documents, performs authorized document-review actions, and records the recommendation for independent decision. |
| **Approver** | Records the independent application decision, may return work for review or correction, and performs authorized Loan-owned Administrative Full-Balance Settlement. |
| **Accounting Officer** | Prepares operational contracts, confirms readiness and external transfer evidence, records authorized repayments, and closes eligible settled LoanAccounts. |
| **Back-Office Admin** | Administers products, Partner data and imports, internal users, and predefined role assignments. |

---

## Run Meridian Locally

Meridian local development uses one backend environment and two browser applications. Docker Compose runs the Java backend together with PostgreSQL and Mailpit; an opt-in `ocr` profile adds the Python OCR worker without making Google credentials a prerequisite for the default environment. Customer Web and Internal Web run separately with Vite and call the same backend API at `http://localhost:8080/api/v1`.

### Repository Layout

```text
meridian-finance/
├── meridian-platform/       # Java/Spring backend, Flyway migrations, and local Compose environment
├── ocr-service/             # Python/FastAPI OCR worker and provider adapters
├── customer-web/            # React/Vite Customer Web application
├── internal-web/            # Shared React/Vite Internal Web application
├── docs/                    # Business, architecture, API, database, frontend, and project documentation
└── .github/workflows/       # Backend, frontend, and OCR CI workflows
```

Backend modules under `com.meridian.platform` are:

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
              ┌───────────┬────────────┐
              ▼           ▼            ▼
         PostgreSQL    Mailpit    OCR worker (opt-in)
           :5432     SMTP :1025       :8090
                      UI   :8025
```

Compose does not start the frontend development servers. Both use `VITE_API_BASE_URL=http://localhost:8080/api/v1`, and the backend local CORS configuration allows the Customer Web and Internal Web origins.

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

The OCR worker is opt-in:

```bash
docker compose --profile ocr up --build
```

The profile mounts the Document volume read-only and remains not-ready without the dedicated `MERIDIAN_OCR_RESULT_ENCRYPTION_KEY`, Google project/location/processor configuration, and Application Default Credentials. Local credentials are mounted through a developer-owned Compose override; credential files are never committed. Manual Staff intake remains available while the worker is absent or not ready.

### Customer Web

`customer-web/` contains Meridian's responsive Customer Web application. [MER-FE-001](docs/frontend/MER-FE-001-customer-web-blueprint.md) defines its frontend architecture, state ownership, visual language, and accessibility baseline.

```bash
cd customer-web
npm ci
# Copy .env.example to .env when local configuration is needed.
npm run dev
```

Customer Web uses `http://localhost:5173` by default and calls the local backend through `VITE_API_BASE_URL=http://localhost:8080/api/v1`.

### Internal Web

`internal-web/` contains Meridian's shared Internal Web application. Staff Web handles Staff-assisted UCL and Collateral Loan intake and origination plus review, approval, correction, contract, disbursement, and repayment under `/staff/*`, while Back-Office Administration handles administrative capabilities under `/admin/*`. The two areas share authentication, session management, protected transport, responsive Internal Web chrome, and common UI foundations while keeping their feature routes, queries, commands, and authorization boundaries separate.

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

`meridian-platform/.env.example` is the local backend configuration inventory. At minimum, set `POSTGRES_PASSWORD`, the three required Base64-encoded symmetric key values, and a matching JWT private/public key pair before starting the default backend. Optional OCR processing and Staff review use a separate Base64-encoded 32-byte `MERIDIAN_OCR_RESULT_ENCRYPTION_KEY`; its absence keeps manual intake and backend startup available while OCR review fails closed.

Generate each local symmetric key with one of these commands and run the selected command three times for the default environment, or four times when configuring OCR:

```bash
openssl rand -base64 32
```

```powershell
[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
```

`MERIDIAN_CUSTOMER_ENCRYPTION_KEY`, `MERIDIAN_LOAN_DISBURSEMENT_SNAPSHOT_KEYS_LOCAL`, and `MERIDIAN_OCR_RESULT_ENCRYPTION_KEY` must each decode to exactly 32 bytes. `MERIDIAN_CUSTOMER_FINGERPRINT_KEY` must decode to at least 32 bytes. The local disbursement-snapshot active key ID is `local`.

Generate one matching RSA-2048 signing pair in PowerShell. The command emits the Base64-encoded PKCS#8 private key and X.509 SubjectPublicKeyInfo public key expected by Meridian:

```powershell
$rsa = [Security.Cryptography.RSA]::Create(2048)
"MERIDIAN_JWT_PRIVATE_KEY=$([Convert]::ToBase64String($rsa.ExportPkcs8PrivateKey()))"
"MERIDIAN_JWT_PUBLIC_KEY=$([Convert]::ToBase64String($rsa.ExportSubjectPublicKeyInfo()))"
```

Keep both values from the same command run. Meridian rejects missing, malformed, mismatched, or weaker signing keys.

The local backend allows `http://localhost:5173` and `http://localhost:5174` by default. Mailpit captures local verification and password-reset email without external SMTP credentials.

`.env` contains local secrets and must not be committed.

Stop the backend environment with `docker compose down`. Named PostgreSQL and Document volumes are preserved. `docker compose down -v` removes them and should be used only for an intentionally disposable local environment.

</details>

---

## Architecture

### Architecture Principles

| Principle | Implementation                                                                                                                                                                                                           |
|---|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Architecture Style** | Modular Monolith (Spring Modulith)                                                                                                                                                                                       |
| **Internal Design** | Hexagonal Architecture (Ports & Adapters)                                                                                                                                                                                |
| **Domain Modeling** | Domain-Driven Design (Bounded Contexts)                                                                                                                                                                                  |
| **Dependency Direction** | Inward-only — Infrastructure adapters → Application ports/services → Domain                                                                                                                                              |
| **Boundary Enforcement** | Architecture documents define the intended module law. ArchUnit tests enforce core layer, security, and shared-kernel rules.                                                                                             |
| **Module Communication** | Narrow public application contracts support synchronous collaboration. Transaction-aware coordination preserves atomic outcomes, while durable asynchronous delivery requires explicit retry, recovery, and idempotency. |
| **Selective Extraction** | Bounded contexts and published contracts preserve selective extraction options. A service is extracted only when stable boundaries, scale, or operational ownership justify it. |

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
      │ Event evidence   │   │ uploads and    │   │ Provider-neutral     │
      │ Audit evidence   │   │ OCR inputs     │   │ Google Document AI   │
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
| **Notification** | Message templates, delivery requests, channels, and delivery status. |

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
| **Python + FastAPI** | Provider-neutral OCR service, operational health API, and PostgreSQL-backed worker |
| **Google Document AI Enterprise Document OCR** | Selected advisory OCR provider adapter for controlled intake evidence |

### Database

| Technology | Purpose |
|---|---|
| **PostgreSQL** | Primary relational store for ACID transactions, concurrency control, durable event publication, and asynchronous job queues (SKIP LOCKED) |

### Infrastructure

| Technology | Purpose                                              |
|---|------------------------------------------------------|
| **Docker Compose** | Local application, PostgreSQL, Mailpit SMTP capture, and opt-in OCR worker profile |
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

- [x] Containerized provider-neutral Python FastAPI OCR service with health/readiness endpoints
- [x] Google Document AI Enterprise Document OCR provider adapter
- [x] Provider-normalized page, line, token, confidence, and layout preservation
- [x] Explicit Staff request for an exact current intake version with PostgreSQL-backed claim, lease, retry, and encrypted result persistence
- [x] Authorized Staff review/correction experience for OCR-assisted results
- [ ] Application of explicitly reviewed suggestions through existing Customer and Loan commands

### Phase 3 — Customer and Internal Web Experience

- [x] Customer Web for profile, applications, documents, offers, and loan tracking
- [x] Staff Web lending operations inside Internal Web for review, approval, correction, disbursement, repayment, settlement, and administrative closure
- [x] Back-Office Administration inside Internal Web for products, Partner administration, and internal users
- [ ] Generic Back-Office configuration administration after an approved use case and contract exist

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
