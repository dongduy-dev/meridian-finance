# Project Meridian

## Meridian Lending Platform

Meridian is a multi-product digital lending platform centered on Salary Advance, with streamlined workflows for Unsecured Consumer Loan and Collateral Loan. It supports the lending lifecycle — application submission, document upload, OCR-assisted document processing, checklist handling, manual document review, controlled review and approval, customer acceptance, manual disbursement confirmation, repayment tracking, and audit tracking — while helping lending teams operate with clearer and more consistent processes.

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
| **Module Communication** | Sync via application/public ports, async via Spring Modulith `ApplicationEvents` + Transactional Outbox |
| **Future Evolution** | Each module is designed to be independently extractable into a microservice with minimal impact on core business logic |

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
│   ┌──────────────┐   ┌──────────────┐   ┌──────────────┐   ┌────────────┐    │
│   │  JWT Auth    │   │  Caffeine    │   │ Idempotency  │   │    CORS    │    │
│   │  Filter      │   │ Rate Limiter │   │   Filter     │   │   Filter   │    │
│   │  (RS256)     │   │ (in-memory)  │   │ (DB-backed)  │   │            │    │
│   └──────────────┘   └──────────────┘   └──────────────┘   └────────────┘    │
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
│  │ • Refresh tokens │  │ • Bank info      │  │ • Import batches          │  │
│  │                  │  │ • AES-256-GCM    │  │                           │  │
│  │                  │  │   PII encryption │  │                           │  │
│  └──────────────────┘  └──────────────────┘  └───────────────────────────┘  │
│                                                                             │
│  ┌──────────────────┐  ┌──────────────────┐  ┌───────────────────────────┐  │
│  │ Loan Core /      │  │ Approval         │  │ Document Management       │  │
│  │ Origination      │  │ Workflow         │  │                           │  │
│  │ • Applications   │  │ • Review         │  │ • Upload                  │  │
│  │ • Products       │  │ • Approval       │  │ • Checklist               │  │
│  │ • Product policy │  │ • Maker-checker  │  │ • OCR trigger             │  │
│  │ • State machine  │  │ • Decision trail │  │ • Review/readiness        │  │
│  │ • Loan accounts  │  │                  │  │                           │  │
│  │ • Repayments     │  │                  │  │                           │  │
│  └──────────────────┘  └──────────────────┘  └───────────────────────────┘  │
│                                                                             │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │ Audit & Compliance Controls                                          │   │
│  │ • Immutable event log  • JSONB snapshots  • Compliance audit trail   │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  ══════════════ Spring Modulith ApplicationEvents ════════════════════════  │
│  ══════════════ Transactional Outbox (spring-modulith-events-jdbc) ═══════  │
│  ══════════════ Cross-cutting: MDC Logging · Metrics · ArchUnit ══════════  │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
                  ┌────────────────────┼────────────────────┐
                  ▼                    ▼                    ▼
      ┌──────────────────┐   ┌────────────────┐   ┌──────────────────────┐
      │ PostgreSQL       │   │ File Storage   │   │ OCR Service          │
      │                  │   │                │   │                      │
      │ • Module schemas │   │ • Document     │   │ • Python + FastAPI   │
      │ • Outbox table   │   │   uploads      │   │ • Vietnamese TrOCR   │
      │ • Audit log      │   │ • OCR input    │   │ • Async job workers  │
      │ • Idempotency    │   │   artifacts    │   │ • Result persistence │
      │ • Job queue      │   │                │   │ • Shared secret auth │
      └──────────────────┘   └────────────────┘   └──────────────────────┘
```

### Bounded Contexts

| Context | Role | Key Entities |
|---|---|---|
| **Identity & Access** | Authentication, authorization, RBAC | `User`, `Role`, `RefreshToken` |
| **Customer Management** | Customer profile, verification status, bank account information | `Customer`, `CustomerProfile`, `CustomerBankAccount` |
| **Partner Management** | Partner company and employee data for Salary Advance eligibility | `PartnerCompany`, `PartnerEmployee`, `PartnerEmployeeImportBatch` |
| **Loan Core / Origination** | Generic lending core — state machine, product policies, offers, disbursement, repayment | `LoanApplication`, `LoanProduct`, `LoanProductPolicy`, `LoanAccount`, `RepaymentSchedule` |
| **Approval Workflow** | Controlled review and approval workflow, maker-checker controls | `ReviewRecommendation`, `ApprovalDecision` |
| **Document Management** | Upload, checklist management, manual document review, planned OCR-assisted processing | `Document`, `DocumentChecklist`, `DocumentChecklistItem`, `OcrJob`, `OcrResult` |
| **Audit & Compliance Controls** | Immutable event log, cross-cutting business action history, compliance-oriented audit trail | `AuditEvent` |

---

## Key Features

### Core Platform
- **Loan Application Lifecycle** — State machine–driven origination with a shared lending core
- **Salary Advance Workflow** — Employer-linked salary advance with Partner Company, Partner Employee, and eligibility verification support
- **Streamlined Product Workflows** — Unsecured Consumer Loan and Collateral Loan support through shared lifecycle capabilities
- **Controlled Review & Approval Workflow** — Loan Officer review, Approver decision, customer acceptance, and maker-checker controls
- **Operational Contract Readiness** — Immutable accepted-term and repayment snapshots, protected destination capture, Customer acknowledgment, structured blockers, controlled destination refresh, and Accounting confirmation
- **Manual Disbursement Activation** - Idempotent Accounting confirmation creates an active LoanAccount, final dated schedule, and Salary Advance reserved-to-used conversion atomically
- **Document Upload & Management** — Checklist handling, metadata, storage abstraction, manual review, waiver, replacement, readiness checks, and OCR-assisted processing
- **JWT Authentication & RBAC** — RS256 tokens, refresh rotation, role/action permission model
- **Idempotent Financial Operations** — `Idempotency-Key` header processing for critical mutation endpoints
- **Immutable Audit Trail** — Append-only event logging with JSONB state snapshots
- **Structured Logging** — JSON-formatted logs with request correlation (userId, loanId, traceId)
- **Data Encryption** — AES-256-GCM encryption at rest for sensitive personal and financial data

### User Roles

| Role | Key Permissions | Notes |
|---|---|---|
| **Customer** | `loan:submit`, `loan:read` (own), `loan:cancel` (own), `document:upload`, `document:read` (own) | Self-service only; service layer enforces ownership |
| **Loan Officer** | `loan:read`, `loan:review`, `approval:submit`, `document:review`, `customer:read` | Reviews applications, documents, product verification results, and recommendations |
| **Approver** | `loan:read`, `approval:decide`, `document:read`, `audit:read` | Approves, rejects, or returns applications after Loan Officer review |
| **Accounting Officer** | `loan:contract:prepare`, `loan:contract:read`, `loan:disbursement:prepare`, `loan:disburse`, `loan:read` | Prepares contracts, confirms readiness/manual transfer evidence, and may reveal the immutable destination only before activation |
| **Back-Office Admin** | `loan:product:manage`, `partner:manage`, `admin:user:manage`, `admin:config`, `audit:read` | Manages products, partner data, internal users, and MVP configuration |

---

## Technology Stack

### Backend

| Technology | Purpose |
|---|---|
| **Java 25** | LTS runtime with virtual threads and pattern matching |
| **Spring Boot 4.1.x** | Application framework |
| **Spring Modulith** | Module boundary enforcement, event publication, transactional outbox (`spring-modulith-events-jdbc`) |
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
| **PostgreSQL** | Primary data store (ACID-compliant), job queue (`SKIP LOCKED`), full-text search |

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
- [ ] Repayment posting/tracking, overdue transitions, settlement, and closure
- [ ] Streamlined Unsecured Consumer Loan workflow
- [ ] Streamlined Collateral Loan workflow
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
- [ ] Redis for session controls, rate limiting, and appropriate idempotency caching
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
- Multi-level and configurable approval workflows
- Microservice extraction where justified, with Kafka-backed event streaming

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
├── loan/                    # Generic lending core (product policies + full hexagonal)
├── approval/                # Approval workflow
├── document/                # Document checklist, review, and correction workflows
├── audit/                   # Audit & compliance controls
└── notification/            # Optional later
```

Each module follows Practical Hexagonal Architecture internally:

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

Feature modules follow Meridian's practical hexagonal structure; detailed layer and dependency rules are documented in [MER-ARCH-003](docs/architecture/MER-ARCH-003-dependency-rules.md).

---

## Documentation

- [Business requirements and workflows](docs/business/MER-BIZ-001-business-requirements-and-workflows.md)
- [Architecture and dependency rules](docs/architecture/MER-ARCH-003-dependency-rules.md)
- [Data model and ERD](docs/database/MER-DB-001-data-model-and-erd.md) and [current physical schema snapshot](docs/database/MER-DB-CURRENT-SCHEMA.sql)
- [API endpoint and scenario guide](docs/api/MER-API-001-endpoints-and-postman-scenarios.md)
- [Follow-up register](docs/project/MER-TRACK-001-follow-up-register.md)

---

## License

*TBD*
