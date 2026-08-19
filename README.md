# Project Meridian

## Meridian Lending Platform

Meridian is a multi-product digital lending platform centered on its flagship Salary Advance workflow, with streamlined workflows for Unsecured Consumer Loan and Collateral Loan. It supports the lending lifecycle — application submission, document upload, OCR-assisted document processing, checklist handling, manual document review, controlled review and approval, customer acceptance, manual disbursement confirmation, repayment tracking, full-balance settlement, administrative account closure, and audit tracking — while helping lending teams operate with clearer and more consistent processes.

At its core, Meridian uses one generic lending core shared across all loan products, with product-specific behavior handled through loan product policies and strategies. The platform is built around practical financial software concerns such as auditability, security, data integrity, controlled status transitions, approval controls, document traceability, and clear operational workflows.

Built with Java, Spring Boot, PostgreSQL, and React, Meridian adopts Domain-Driven Design and a Modular Monolith architecture with clearly defined bounded contexts. This approach enables rapid delivery today while preserving a clear evolutionary path toward distributed services as business requirements grow.

---

## Architecture

| Principle | Implementation |
|---|---|
| **Architecture Style** | Modular Monolith (Spring Modulith) |
| **Internal Design** | Hexagonal Architecture (Ports & Adapters) |
| **Domain Modeling** | Domain-Driven Design (Bounded Contexts) |
| **Dependency Direction** | Inward-only — Infrastructure adapters → Application ports/services → Domain |
| **Boundary Enforcement** | Spring Modulith + ArchUnit fitness functions |
| **Module Communication** | Synchronous application/public contracts and transactional event coordination where atomic consistency is required; durable asynchronous events use Spring Modulith with the Transactional Outbox pattern |
| **Future Evolution** | Bounded contexts and published contracts preserve selective extraction options; services are extracted only when stable boundaries, scale, or operational ownership justify it |

### Architecture Diagram

```text
┌──────────────────────────────────────────────────────────────────────────────┐
│                                   CLIENTS                                    │
│                 React SPA (Vite)  ·  Admin Panel  ·  Mobile (Future)         │
└──────────────────────────────────────┬───────────────────────────────────────┘
                                       │ HTTPS + JWT (RS256)
                                       ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                         API EDGE / SECURITY LAYER                            │
│                    (Spring Security Filter Chain — embedded)                 │
│                                                                              │
│       JWT Authentication (RS256)  ·  RBAC / Method Security  ·  CORS         │
│                                                                              │
│   ┌──────────────────────────────────────────────────────────────────────┐   │
│   │ Springdoc OpenAPI (auto-generated, /swagger-ui)                      │   │
│   └──────────────────────────────────────────────────────────────────────┘   │
└──────────────────────────────────────┬───────────────────────────────────────┘
                                       │
┌──────────────────────────────────────▼──────────────────────────────────────┐
│                    MODULAR MONOLITH (Spring Boot + Spring Modulith)         │
│                                                                             │
│  ┌──────────────────┐  ┌──────────────────┐  ┌───────────────────────────┐  │
│  │ Identity & Access│  │ Customer         │  │ Partner Management        │  │
│  │                  │  │ Management       │  │                           │  │
│  │ • User/Role      │  │ • Profiles       │  │ • Partner companies       │  │
│  │ • JWT issuance   │  │ • Verification   │  │ • Partner employees       │  │
│  │ • RBAC actions   │  │   status         │  │ • Monthly employee import │  │
│  │ • Refresh tokens │  │ • Bank info      │  │ • Reusable employee links │  │
│  │                  │  │ • AES-256-GCM    │  │                           │  │
│  │                  │  │   PII encryption │  │                           │  │
│  └──────────────────┘  └──────────────────┘  └───────────────────────────┘  │
│                                                                             │
│  ┌──────────────────┐  ┌──────────────────┐  ┌───────────────────────────┐  │
│  │ Loan Core /      │  │ Approval         │  │ Document Management       │  │
│  │ Lending Lifecycle│  │ Workflow         │  │                           │  │
│  │ • Applications   │  │ • Recommendations│  │ • Upload                  │  │
│  │ • Products/policy│  │ • Decisions      │  │ • Checklist               │  │
│  │ • Review/correct.│  │ • Maker-checker  │  │ • OCR trigger             │  │
│  │ • Offers/contracts│ │ • Decision trail │  │ • Review/readiness        │  │
│  │ • Loan accounts  │  │                  │  │                           │  │
│  │ • Repayments     │  │                  │  │                           │  │
│  └──────────────────┘  └──────────────────┘  └───────────────────────────┘  │
│                                                                             │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │ Audit & Compliance Controls                                          │   │
│  │ • Immutable event log  • Controlled JSONB payloads  • Audit trail    │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  ══════════════ Synchronous contracts / transactional coordination ═══════  │
│  ══════════════ Transactional Outbox · Spring Modulith + PostgreSQL ══════  │
│  ══════════════ Cross-cutting: MDC Logging · Metrics · ArchUnit ══════════  │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
                  ┌────────────────────┼────────────────────┐
                  ▼                    ▼                    ▼
      ┌──────────────────┐   ┌────────────────┐   ┌──────────────────────┐
      │ PostgreSQL       │   │ File Storage   │   │ OCR Service          │
      │                  │   │                │   │                      │
      │ • Module-owned   │   │ • Document     │   │ • Python + FastAPI   │
      │   tables         │   │   uploads      │   │ • Vietnamese TrOCR   │
      │ • Event publish. │   │ • OCR input    │   │ • Async job workers  │
      │ • Audit evidence │   │   artifacts    │   │ • Result persistence │
      │ • Op. outcomes   │   │                │   │ • Shared secret auth │
      │ • Job queue      │   │                │   │                      │
      └──────────────────┘   └────────────────┘   └──────────────────────┘
```

### Bounded Contexts

| Context | Role | Key Entities |
|---|---|---|
| **Identity & Access** | Authentication, authorization, RBAC, and session-token lifecycle | `User`, `Role`, `RefreshToken` |
| **Customer Management** | Customer profile, verification status, bank account information | `Customer`, `CustomerProfile`, `CustomerBankAccount` |
| **Partner Management** | Partner company and employee data, imports, and reusable Salary Advance employment links | `PartnerCompany`, `PartnerEmployee`, `PartnerEmployeeImportBatch`, `CustomerPartnerEmployeeLink` |
| **Loan Core / Lending Lifecycle** | Products, applications, review cycles, corrections, offers, contracts, activation, servicing, repayment, settlement, and closure | `LoanApplication`, `LoanProduct`, `ApprovedOffer`, `LoanContract`, `LoanAccount`, `RepaymentSchedule` |
| **Approval Workflow** | Immutable Loan Officer recommendations and Approver decisions with maker-checker controls | `ReviewRecommendation`, `ApprovalDecision` |
| **Document Management** | Upload, checklist management, immutable versions, manual document review, processing readiness, and OCR-assisted processing | `Document`, `DocumentChecklist`, `DocumentChecklistItem`, `OcrJob`, `OcrResult` |
| **Audit & Compliance Controls** | Append-only business action history with controlled structured evidence | `AuditEvent` |

---

## Key Features

### Core Platform
- **Loan Application Lifecycle** — State machine–driven origination through disbursement, followed by LoanAccount servicing after activation
- **Salary Advance Workflow** — Employer-linked salary advance with Partner Company, Partner Employee, and eligibility verification support
- **Unsecured Consumer Loan Workflow** — Customer-owned origination, required evidence, positive and negative manual verification, structured correction and re-verification, review and approval, executable flat-rate pricing, correction cancellation, outstanding-debt protection, immutable offers, operational contracts, activation, repayment, overdue servicing, settlement, and closure
- **Collateral Loan Workflow** — Customer-owned origination, immutable submitted asset facts, ownership evidence, numbered manual-verification cycles, document-only correction and re-verification, verified-only Loan Officer review, exact-request flat-rate pricing, independent approval, immutable monthly-installment offers, and Customer response through `CONTRACT_PENDING`
- **Controlled Review & Approval Workflow** — Loan-owned review and correction lifecycle with immutable Loan Officer recommendations, independent Approver decisions, customer acceptance, and maker-checker controls
- **Operational Contract Readiness** — Immutable accepted-term and repayment snapshots, protected destination capture, Customer acknowledgment, structured blockers, controlled destination refresh, and Accounting confirmation
- **Manual Disbursement Activation** - Idempotent Accounting confirmation creates an active LoanAccount and final dated schedule atomically, with Salary Advance exposure conversion only for Salary Advance
- **Salary Advance Servicing Lifecycle** — Deterministic repayment, overdue evaluation, contractual payoff, payment-backed Administrative Full-Balance Settlement, and separate administrative LoanAccount closure
- **Document Upload & Management** — Checklist handling, metadata, storage abstraction, manual review, waiver, replacement, readiness checks, and OCR-assisted processing
- **JWT Authentication & RBAC** — RS256 tokens, refresh rotation, role/action permission model
- **Idempotent Financial Operations** — Operation-specific request identities, transactional replay protection, semantic replay validation, and persisted outcomes for critical mutations
- **Immutable Audit Trail** — Append-only business audit records with controlled structured JSONB payloads and ordered lifecycle history
- **Structured Logging** — JSON-formatted logs with request correlation (userId, loanId, traceId)
- **Data Encryption** — AES-256-GCM encryption at rest for sensitive personal and financial data

### User Roles

| Role | Representative Permissions | Notes |
|---|---|---|
| **Customer** | `loan:submit`, `loan:read:own`, `loan:cancel:own`, `partner:employee:verify:own`, `document:upload:own`, `document:read:own`, `loan:offer:respond:own`, `loan:contract:acknowledge:own` | Self-service only; service layer enforces ownership |
| **Loan Officer** | `loan:read`, `loan:review`, `approval:recommend`, `document:review`, `customer:read`, `loan:correction:staff` | Reviews applications and evidence, records recommendations, and handles authorized Staff correction work |
| **Approver** | `loan:read`, `approval:decide`, `loan:settlement:approve`, `document:read`, `audit:read` | Records independent application decisions and performs authorized Loan-owned Administrative Full-Balance Settlement |
| **Accounting Officer** | `loan:contract:prepare`, `loan:contract:read`, `loan:disbursement:prepare`, `loan:disburse`, `repayment:update`, `loan:account:close`, `loan:read` | Prepares contracts, confirms readiness/manual transfer evidence, records authorized repayments, and closes eligible settled LoanAccounts |
| **Back-Office Admin** | `loan:product:manage`, `partner:read`, `partner:manage`, `identity:user:manage`, `admin:config`, `audit:read` | Manages products, partner data, internal users, and MVP configuration |

---

## Technology Stack

### Backend

| Technology | Purpose |
|---|---|
| **Java 25** | LTS runtime with virtual threads and pattern matching |
| **Spring Boot 4.1.x** | Application framework |
| **Spring Modulith** | Module boundary support, event publication, and Transactional Outbox persistence backed by PostgreSQL |
| **Spring Security** | Authentication & authorization |
| **Spring Data JPA / Hibernate** | Data persistence |
| **Flyway** | Versioned database migrations |
| **ArchUnit** | Architectural fitness function testing |
| **JWT (RS256)** | Stateless authentication with asymmetric signing |
| **Springdoc OpenAPI** | Auto-generated API documentation from annotations |

### Frontend

| Technology | Purpose |
|---|---|
| **React** | UI framework |
| **Vite** | Build tooling |

### OCR / Document Intelligence

| Technology | Purpose |
|---|---|
| **Python + FastAPI** | OCR service and asynchronous document-processing worker |
| **Vietnamese TrOCR** | OCR-assisted extraction for uploaded documents |

### Database

| Technology | Purpose |
|---|---|
| **PostgreSQL** | Primary relational store for ACID transactions, concurrency control, durable event publication, and asynchronous job queues (`SKIP LOCKED`) |

### Infrastructure

| Technology | Purpose |
|---|---|
| **Docker Compose** | Local development and deployment |
| **GitHub Actions** | CI pipeline (build, test, architecture verification) |
| **SLF4J + Logback** | Structured JSON logging |

### Development Tools

| Tool | Purpose |
|---|---|
| **Git / GitHub** | Version control |
| **Postman** | API testing |

---

## Roadmap

### Phase 1 — Core Lending MVP

- [x] Common loan application lifecycle with state machine, product catalog, and product-policy framework
- [x] Customer profile, bank-account readiness, Partner Employee verification, and Salary Advance eligibility
- [x] Salary Advance submission, limit reservation, verification snapshots, and concurrency protection
- [x] Controlled review, approval, maker-checker, audit, and status-history workflows
- [x] Versioned document upload, checklist, review, correction, and guarded resubmission
- [x] Approved-offer generation, expiry, customer acceptance, and decline
- [x] Contract readiness and immutable disbursement preparation
- [x] Manual disbursement confirmation, LoanAccount activation, and final repayment schedule generation for Salary Advance
- [x] Salary Advance repayment posting/tracking, exact principal exposure release, overdue transitions, contractual payoff to `SETTLED`, and secured servicing reads
- [x] Payment-backed Administrative Full-Balance Settlement and separate LoanAccount closure for Salary Advance
- [x] Unsecured Consumer Loan origination and evidence foundation
- [x] Unsecured Consumer Loan positive and negative manual verification, structured correction, re-verification, and review/recommendation integration through `APPROVAL_PENDING`
- [x] Unsecured Consumer Loan exact-request pricing, approval, immutable offer generation, expiry, Customer acceptance, and decline
- [x] Unsecured Consumer Loan operational contract, acknowledgment, readiness, manual disbursement, LoanAccount activation, and final monthly schedule
- [x] Unsecured Consumer Loan partial and early repayment, overdue evaluation and cure, contractual payoff, exact full-balance administrative settlement, and administrative closure
- [x] Unsecured Consumer Loan correction cancellation and product-scoped outstanding-debt origination protection
- [x] Collateral Loan origination, structured facts, required ownership-evidence checklist, and pending manual-verification foundation
- [x] Collateral Loan manual verification, document-only correction, re-verification, and review/recommendation through `APPROVAL_PENDING`
- [x] Collateral Loan exact-request pricing, all four Approver actions, immutable monthly-installment offer generation, expiry, Customer acceptance, and decline
- [ ] Collateral Loan contract, activation, and final dated schedule workflow
- [ ] Collateral Loan repayment/servicing policy and execution
- [x] JWT authentication and permission-based RBAC
- [x] Idempotent critical workflow operations
- [x] Flyway migrations, Spring Modulith structure, event-publication persistence, and architecture verification
- [ ] Startup replay and recovery for incomplete event publications
- [ ] Docker Compose for PostgreSQL and the application
- [ ] Structured JSON logging with request and business correlation
- [x] GitHub Actions build and verification pipeline

### Phase 2 — OCR-Assisted Document Processing

- [ ] Containerized Python FastAPI OCR service
- [ ] Vietnamese TrOCR model integration
- [ ] Whole-page text detection, line segmentation, and reading-order reconstruction
- [ ] PostgreSQL-backed asynchronous OCR jobs and result persistence
- [ ] Manual review UI for OCR-assisted document results

### Phase 3 — Customer and Staff Experience

- [ ] Customer web portal for profile, applications, documents, offers, and loan tracking
- [ ] Staff web portal for review, approval, correction, disbursement, and repayment operations
- [ ] Back-office administration for products, partners, users, and configuration

### Phase 4 — Operational Maturity

- [ ] Production object storage, malware scanning, retention, and recovery controls
- [ ] Redis for distributed rate limiting, session controls, and appropriate ephemeral caching
- [ ] Prometheus metrics and Grafana dashboards
- [ ] OpenTelemetry distributed tracing
- [ ] Performance profiling, load testing, and security hardening

### Phase 5 — Analytics and Risk

- [ ] Elasticsearch-backed loan search and audit-log analytics
- [ ] Reporting dashboards
- [ ] Rule-based risk assessment engine
- [ ] Loan eligibility scoring

### Future Considerations

- Notification service for email, SMS, and in-app messages
- Mobile application support
- Payroll provider, employer API, payment gateway, bank transfer, and credit-bureau integrations
- Repayment reversal/refund, unapplied cash, suspense processing, waiver/write-off, and bank reconciliation
- Multi-level and configurable approval workflows
- Selective microservice extraction where justified, with Kafka-backed event streaming

#### Financial Ledger and Accounting

- Double-entry accounting ledger
- Journal-entry engine with debit and credit posting
- Chart of accounts management
- Automated disbursement and repayment posting
- Financial reconciliation and balance validation
- Accounting audit reports

---

## Project Structure

```text
com.meridian.platform/
├── shared/                  # Shared kernel (minimal)
│   ├── domain/              # Shared value objects and common domain exceptions
│   ├── application/         # Cross-cutting application abstractions
│   └── infrastructure/      # Config, persistence helpers, and web infrastructure
│
├── identity/                # IAM bounded context
│   ├── domain/              # User, Role, permissions, and domain events
│   ├── application/         # Application ports plus auth & user management use cases
│   └── infrastructure/      # Controllers, Spring Security/JWT, and JPA adapters
│
├── customer/                # Customer bounded context
├── partner/                 # Partner company and employee import bounded context
├── loan/                    # Lending lifecycle: products, applications, review, activation, servicing
├── approval/                # Recommendation and decision records
├── document/                # Document checklist, versions, review, readiness, and OCR integration
├── audit/                   # Audit & compliance controls
└── notification/            # Optional later
```

Feature modules use Meridian's Practical Hexagonal Architecture at the level each module needs:

```text
module/
├── domain/
│   ├── model/               # Entities, value objects, enums
│   ├── service/             # Domain services
│   ├── event/               # Domain events
│   └── exception/           # Business/domain exceptions
├── application/
│   ├── port/
│   │   ├── in/              # Use case interfaces (driving ports)
│   │   └── out/             # Repository & external service ports (driven ports)
│   ├── service/             # Use case implementations
│   ├── dto/                 # Request/response DTOs
│   └── mapper/              # Domain ↔ DTO mapping
└── infrastructure/
    ├── adapter/
    │   ├── in/web/           # REST controllers
    │   └── out/              # Persistence, client, event, and storage adapters
    └── config/               # Module-specific configuration
```

Not every module contains every package shown above. Full, Moderate, and Simplified module profiles preserve the same inward dependency direction while using only the structure each module needs. Detailed source-layout rules are documented in [MER-ARCH-002](docs/architecture/MER-ARCH-002-project-structure.md), and enforceable dependency rules are documented in [MER-ARCH-003](docs/architecture/MER-ARCH-003-dependency-rules.md).

---

## Documentation

- [Business requirements and workflows](docs/business/MER-BIZ-001-business-requirements-and-workflows.md)
- [Bounded contexts and ownership](docs/architecture/MER-ARCH-001-bounded-contexts.md)
- [Project structure](docs/architecture/MER-ARCH-002-project-structure.md)
- [Dependency rules and architecture enforcement](docs/architecture/MER-ARCH-003-dependency-rules.md)
- [OCR architecture](docs/architecture/MER-ARCH-005-ocr-architecture.md)
- [API request flows and runtime dependencies](docs/architecture/MER-ARCH-006-api-request-flow-and-dependencies.md)
- [Data model and ERD](docs/database/MER-DB-001-data-model-and-erd.md) and [current physical schema snapshot](docs/database/MER-DB-CURRENT-SCHEMA.sql)
- [API endpoint and scenario guide](docs/api/MER-API-001-endpoints-and-postman-scenarios.md)
- [Follow-up register](docs/project/MER-TRACK-001-follow-up-register.md)

---

## License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.
