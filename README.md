# Project Meridian

## Meridian Lending Platform

Meridian is a multi-product lending platform built around a common lending lifecycle and product-specific policies. Salary Advance is the flagship workflow, with Unsecured Consumer Loan and Collateral Loan workflows built on the same lending core.

Meridian covers the lending journey from origination through servicing and closure across Customer-digital and Staff-assisted channels. Product workflows combine automated processing with controlled human review. The platform is designed around practical financial software concerns such as auditability, security, data integrity, controlled state transitions, maker-checker separation, document traceability, and clear operational ownership.

---

## Live Platform

Meridian is a fictional portfolio/demo, not a real financial service. Visitors must use fictional information; submitted information is still handled as private. See [MER-DEP-001](docs/deployment/MER-DEP-001-identity-bootstrap-and-demo-boundary.md) for the detailed demo policy.

| Surface | Live address | Access |
|---|---|---|
| **Customer Web** | [meridianfinance.dev](https://meridianfinance.dev) | Customer self-service - Public registration |
| **Internal Web** | [internal.meridianfinance.dev](https://internal.meridianfinance.dev) | Staff workspace - Available to trusted reviewers by request |
| **API** | [api.meridianfinance.dev](https://api.meridianfinance.dev) | Backend API edge |

Customer Web and Internal Web are served through Cloudflare Pages, while API traffic is routed through an authenticated Cloudflare Tunnel to the AWS backend runtime.

Note: OCR is implemented as a platform capability but is intentionally disabled in the public portfolio runtime.

---

## Platform Overview

### Lending Products

* **Salary Advance**: Uses Partner-linked employment evidence with a product-specific eligibility, limit, and exposure model.
* **Unsecured Consumer Loan**: Uses income, bank-statement, and employment evidence with manual verification, correction, and flat-rate pricing.
* **Collateral Loan**: Uses structured Collateral information with Document-owned ownership evidence and manual verification.

All three products use Meridian's common application, approval, contract, activation, servicing, and closure lifecycle while retaining product-specific rules.

### Lending Lifecycle and Capabilities

```text
Customer-digital / Staff-assisted origination → evidence and verification
→ Loan Officer review and recommendation → independent Approver decision
→ offer and Customer response → contract and readiness
→ manual disbursement and activation → servicing and repayment → settlement and closure
```

* **Digital and Staff-Assisted Origination**: Customer Web supports direct Customer origination for all three products. Staff Web supports paper-originated UCL and Collateral Loan through assisted Customer intake, paper evidence capture, Customer-sourced corrections, and evidenced Customer decisions. The authenticated Staff user remains the actor while the Customer remains the business subject, and a branch-originated application remains Staff-assisted rather than switching to Customer Web. Salary Advance origination remains Customer-digital only.
* **Document and OCR-Assisted Processing**: Document owns document checklists, uploads, immutable versions, review, replacement, waiver, and processing readiness. OCR-assisted processing remains advisory, while authorized Document review remains authoritative.
* **Review, Correction, and Approval**: Loan owns application review and correction workflows. Loan Officers record recommendations, while Approvers make independent decisions under maker-checker controls.
* **Offers, Contracts, and Disbursement**: Loan preserves accepted lending terms through immutable offers, versioned contracts, Customer acknowledgment, readiness checks, and controlled disbursement activation.
* **Loan Servicing**: Salary Advance, UCL, and Collateral Loan share repayment, overdue evaluation and cure, contractual payoff, payment-backed Administrative Full-Balance Settlement, and separate administrative closure.
* **Identity and Access Control**: Identity manages Customer and internal-user authentication, session lifecycle, account recovery, roles, permissions, and access boundaries. A Staff-assisted Customer may exist without a Customer Web login; authorized Staff remain the authenticated actor while the Customer remains the business subject. Permission-based RBAC, Customer ownership checks, and purpose-limited contracts protect Customer and internal operations.

### User Roles

| Role | Representative capabilities and responsibilities |
|---|---|
| **Customer** | Owns profile and bank-account data and provides application facts, evidence, corrections, offer decisions, and contract acknowledgments. Customer-digital users record those actions directly; branch Customers provide them through authorized Staff. Salary Advance employment verification remains Customer-digital. |
| **Loan Officer** | Performs authorized Staff-assisted UCL and Collateral Loan intake and origination, coordinates Customer-sourced branch corrections, reviews application facts and documents, performs authorized document-review actions, and records the recommendation for independent decision. |
| **Approver** | Records the independent application decision, may return work for review or correction, and performs authorized Loan-owned Administrative Full-Balance Settlement. |
| **Accounting Officer** | Prepares operational contracts, confirms readiness and external transfer evidence, records authorized repayments, and closes eligible settled LoanAccounts. |
| **Back-Office Admin** | Administers products, Partner data and imports, internal users, and predefined role assignments. |

---

## Engineering Highlights

* **Transactional correctness and concurrency**: Critical financial commands use atomic state changes, operation-specific request identities, semantic replay validation, and concurrency controls.
* **Evidence and auditability**: Document versions remain immutable, lifecycle history is ordered, and business audit evidence is append-only and PII-safe.
* **Security and data protection**: Identity owns authentication and RBAC, while Customer ownership checks and purpose-limited contracts constrain access. AES-GCM protects selected Customer-sensitive values and immutable Loan disbursement bank-account snapshots at rest.
* **Human-authoritative OCR**: The Python/FastAPI OCR subsystem produces advisory extracted results for Staff review. Reviewed values flow through the existing Customer and Loan commands instead of bypassing domain rules.
* **Deployment and recovery discipline**: The deployed portfolio uses Cloudflare edge routing, a Tunnel-only AWS backend runtime, fail-closed ClamAV upload scanning, non-root containers, persistent encrypted storage, exact release identity, matched backups, and isolated restore verification.

---

## Architecture

### Architecture Principles

| Principle | Implementation |
|---|---|
| **Architecture Style** | Modular Monolith (Spring Modulith) |
| **Internal Design** | Hexagonal Architecture (Ports & Adapters) |
| **Domain Modeling** | Domain-Driven Design (Bounded Contexts) |
| **Dependency Direction** | Inward-only — Infrastructure adapters → Application ports/services → Domain |
| **Boundary Enforcement** | Architecture documents define the intended module law. ArchUnit tests enforce core layer, security, and shared-kernel rules. |
| **Module Communication** | Narrow public application contracts support synchronous collaboration. Transaction-aware coordination preserves atomic outcomes, while durable asynchronous delivery requires explicit retry, recovery, and idempotency. |
| **Selective Extraction** | Bounded contexts and published contracts preserve selective extraction options. A service is extracted only when stable boundaries, scale, or operational ownership justify it. |

### Complete Platform Architecture

```text
┌──────────────────────────────────────────────────────────────────────────────┐
│                                   CLIENTS                                    │
│                   React/Vite Customer Web  ·  Internal Web                   │
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
│  │ Notification · Controlled Identity security email over SMTP         │    │
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
| **Identity & Access** | Customer registration, existing-Customer digital access, User identity, credentials and email-verification state, JWT access, roles, permissions, and the complete platform's session lifecycle. |
| **Customer Management** | Customer lifecycle, profile readiness, mutable source bank accounts, ownership controls, and protection of Customer data. |
| **Partner Management** | Partner Companies, Partner Employees, monthly imports, employment matching, and reusable Salary Advance employment links. |
| **Loan Core / Lending Lifecycle** | Product policies, LoanApplication state, product verification, review and correction cycles, offers, contracts, activation, LoanAccount servicing, contractual payoff, Administrative Full-Balance Settlement, and administrative closure. |
| **Approval Workflow** | Immutable Loan Officer recommendations, independent Approver decisions, decision authority, and maker-checker evidence. |
| **Document Management** | Checklists, uploads, immutable versions, manual document review, readiness, storage, and advisory OCR-assisted processing. |
| **Audit & Compliance Controls** | Append-only, PII-safe evidence of important business actions and compliance-oriented history. |
| **Notification** | Controlled rendering and post-commit SMTP delivery for Identity security email. Broader channels, preferences, durable delivery status, and retry management belong to future scope. |

---

## Technology Stack

### Backend

| Technology | Purpose |
|---|---|
| **Java 25** | Backend runtime |
| **Spring Boot 4.1.0** | Application framework |
| **Spring Modulith 2.1.0** | Module organization, observability support, and transactional event-publication persistence |
| **Spring Security** | Authentication & authorization |
| **Spring Mail** | Notification-owned SMTP delivery for controlled Identity security email |
| **Spring Data JPA / Hibernate** | Data persistence |
| **Flyway** | Versioned database migrations |
| **ArchUnit** | Executable fitness functions for core layer, security, and shared-kernel rules |
| **JWT (RS256)** | Access-token authentication with asymmetric signing |
| **Springdoc OpenAPI** | Generated OpenAPI documentation and Swagger UI for the backend API |

### Frontend

| Technology | Purpose |
|---|---|
| **React** | Customer Web, plus Staff Web and Back-Office Administration within Internal Web |
| **Vite** | Frontend build tooling |

### OCR / Document Intelligence

| Technology | Purpose |
|---|---|
| **Python + FastAPI** | Provider-neutral OCR service, operational health API, and PostgreSQL-backed worker |
| **Google Document AI Enterprise Document OCR** | Selected advisory OCR provider adapter for controlled intake evidence |

### Database

| Technology | Purpose |
|---|---|
| **PostgreSQL** | Primary relational store for ACID transactions, concurrency control, durable event publication, and asynchronous job queues (SKIP LOCKED) |

### Infrastructure

| Technology | Purpose |
|---|---|
| **Docker Compose** | Local application, PostgreSQL, Mailpit SMTP capture, ClamAV, and opt-in OCR worker profile |
| **GitHub Actions** | CI pipeline (build, test, architecture verification) |
| **SLF4J + Logback** | Structured JSON logging |

### Development Tools

| Tool | Purpose |
|---|---|
| **Git / GitHub** | Version control |
| **Postman** | API testing |

---

## Run Meridian Locally

Meridian local development uses Docker Compose for the backend environment and separate Vite development servers for Customer Web and Internal Web.

**Requirements:** Docker, plus Node.js 22 and npm 10 or newer for the frontends.

### Backend

From `meridian-platform/`, copy `.env.example` to the ignored `.env`, fill the required local values, then start the backend environment:

```bash
cd meridian-platform
docker compose up --build
```

Compose starts Meridian with PostgreSQL 16, Mailpit, and ClamAV. Flyway runs during Spring Boot startup, and PostgreSQL and Document data persist in named Docker volumes.

Useful local endpoints:

- API: `http://localhost:8080/api/v1`
- Backend health: `http://localhost:8080/api/v1/health`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Mailpit: `http://localhost:8025`

### Customer Web

```bash
cd customer-web
npm ci
# Copy .env.example to .env for the local backend API.
npm run dev
```

Customer Web runs at `http://localhost:5173`. See [`customer-web/README.md`](customer-web/README.md) for frontend configuration and verification commands.

### Internal Web

```bash
cd internal-web
npm ci
# Copy .env.example to .env for the local backend API.
npm run dev
```

Internal Web runs at `http://localhost:5174`. See [`internal-web/README.md`](internal-web/README.md) for Staff/Admin setup, frontend configuration, and verification commands.

### Local Accounts and Optional OCR

Customers register through Customer Web and verify their email through Mailpit. To establish the first Back-Office Admin and provision Staff accounts, follow [MER-DEP-001](docs/deployment/MER-DEP-001-identity-bootstrap-and-demo-boundary.md).

OCR is optional for local development:

```bash
cd meridian-platform
docker compose --profile ocr up --build
```

The OCR profile requires its dedicated result-encryption key, Google Document AI configuration, and Application Default Credentials. Without the OCR profile, manual Staff intake remains available.

Use separate browser profiles for simultaneous Customer and Internal sessions while [MER-FU-049](docs/project/MER-TRACK-001-follow-up-register.md#mer-fu-049---isolate-customer-web-and-internal-web-browser-sessions) remains deferred.

Stop the backend environment with `docker compose down`. Named PostgreSQL and Document volumes are preserved; `docker compose down -v` removes them.

<details>
<summary>Generate required local secrets</summary>

The default backend environment requires a database password, three separate symmetric values, and one matching RSA signing pair. Keep them only in the ignored `meridian-platform/.env`.

Generate a Base64-encoded 32-byte value and run the command three times for Customer encryption, Customer fingerprinting, and the Loan disbursement-snapshot key:

```bash
openssl rand -base64 32
```

Or in PowerShell:

```powershell
[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
```

Generate the matching RSA-2048 JWT signing pair in PowerShell:

```powershell
$rsa = [Security.Cryptography.RSA]::Create(2048)
"MERIDIAN_JWT_PRIVATE_KEY=$([Convert]::ToBase64String($rsa.ExportPkcs8PrivateKey()))"
"MERIDIAN_JWT_PUBLIC_KEY=$([Convert]::ToBase64String($rsa.ExportSubjectPublicKeyInfo()))"
```

When enabling OCR, generate one additional Base64-encoded 32-byte value for `MERIDIAN_OCR_RESULT_ENCRYPTION_KEY`.

</details>

---

## Roadmap

The accepted Meridian MVP is complete across all three lending products, Customer Web, Staff Web, Back-Office Administration, and OCR-assisted intake. OCR remains disabled in the public portfolio runtime. Future items below are hardening and expansion considerations, not delivery commitments or missing core MVP functionality. The [follow-up register](docs/project/MER-TRACK-001-follow-up-register.md) owns detailed item status, priority, rationale, and remaining scope.

### Delivered MVP Milestones

#### Phase 1 — Core Lending MVP

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

#### Phase 2 — OCR-Assisted Document Processing

- [x] Containerized provider-neutral Python FastAPI OCR service with health/readiness endpoints
- [x] Google Document AI Enterprise Document OCR provider adapter
- [x] Provider-normalized page, line, token, confidence, and layout preservation
- [x] Explicit Staff request for an exact current intake version with PostgreSQL-backed claim, lease, retry, and encrypted result persistence
- [x] Authorized Staff review/correction experience for OCR-assisted results
- [x] Application of explicitly reviewed suggestions through existing Customer and Loan commands

#### Phase 3 — Customer and Internal Web Experience

- [x] Customer Web for profile, applications, documents, offers, and loan tracking
- [x] Staff Web lending operations inside Internal Web for review, approval, correction, disbursement, repayment, settlement, and administrative closure
- [x] Back-Office Administration inside Internal Web for products, Partner administration, and internal users

#### Delivered Operational Maturity

- [x] Customer-level manual identity verification with immutable evidence and new Salary Advance, UCL, and Collateral origination readiness
- [x] Public portfolio document controls: mandatory private ClamAV scanning with fail-closed uploads, persistent document storage on encrypted EBS, and matched backup/recovery procedures with isolated restore acceptance

### Future Hardening — Remaining Phase 4 Scope

- [ ] Broader production document hardening: production-grade object storage, formal retention, legal hold, retirement/purge, key lifecycle, and production-grade disaster recovery ([MER-FU-032](docs/project/MER-TRACK-001-follow-up-register.md#mer-fu-032---harden-document-storage-scanning-and-retention))
- [ ] Evaluate Redis for distributed rate limiting, session controls, and short-lived caching
- [ ] Prometheus metrics and Grafana dashboards
- [ ] OpenTelemetry distributed tracing
- [ ] Performance profiling, load testing, and security hardening
- [ ] Stronger mechanical module-boundary enforcement ([MER-FU-042](docs/project/MER-TRACK-001-follow-up-register.md#mer-fu-042---enforce-meridian-public-module-boundaries-in-source-and-architecture-tests))
- [ ] Executable API error semantic normalization ([MER-FU-043](docs/project/MER-TRACK-001-follow-up-register.md#mer-fu-043---normalize-executable-api-error-identifier-semantics))
- [ ] Same-browser Customer/Internal session isolation ([MER-FU-049](docs/project/MER-TRACK-001-follow-up-register.md#mer-fu-049---isolate-customer-web-and-internal-web-browser-sessions))

### Future Product and Platform Expansion

#### Phase 5 — Analytics and Risk

- [ ] Evaluate Elasticsearch-backed loan search and audit-log analytics
- [ ] Reporting dashboards
- [ ] Rule-based risk assessment engine
- [ ] Loan eligibility scoring

#### Product and Integration Considerations

- Generic Back-Office configuration administration after an approved use case and contract exist
- Extend Notification beyond Identity security email to SMS and in-app messages, preferences, durable delivery status, and retry management
- Mobile application support
- Payroll provider, employer API, payment gateway, bank transfer, and credit-bureau integrations
- Repayment reversal/refund, unapplied cash, suspense processing, waiver/write-off, and bank reconciliation
- Multi-level and configurable approval workflows
- Selective service extraction where justified, with Kafka considered only when durable cross-service streaming needs emerge

#### Financial Ledger and Accounting

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

### Deployment

- [Identity bootstrap and public demo boundary](docs/deployment/MER-DEP-001-identity-bootstrap-and-demo-boundary.md)
- [Cloudflare edge and AWS runtime](docs/deployment/MER-DEP-002-cloudflare-edge-and-aws-runtime.md)
- [Portfolio operations and recovery](docs/deployment/MER-DEP-003-portfolio-operations-and-recovery.md)

### Project

- [Follow-up register](docs/project/MER-TRACK-001-follow-up-register.md)

### Development

- [Commit convention](docs/development/commit-convention.md)

---

## License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.
