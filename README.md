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
| **Accounting Officer** | `loan:read`, `loan:disburse`, `repayment:update`, `document:read` | Confirms manual disbursement and records repayment updates |
| **Back-Office Admin** | `loan:product:manage`, `partner:manage`, `admin:user:manage`, `admin:config`, `audit:read` | Manages products, partner data, internal users, and MVP configuration |

---

## Technology Stack

### Backend

| Technology | Purpose |
|---|---|
| **Java 25** | LTS runtime with virtual threads and pattern matching |
| **Spring Boot 4.0.x** | Application framework |
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

## Local Backend Configuration

Customer profile and bank-account encryption requires two Base64-encoded keys at startup:

- `MERIDIAN_CUSTOMER_ENCRYPTION_KEY` maps to `meridian.customer.encryption-key` and must decode to 32 bytes for AES-256-GCM.
- `MERIDIAN_CUSTOMER_FINGERPRINT_KEY` maps to `meridian.customer.fingerprint-key` and must decode to at least 32 bytes for HMAC-SHA-256.

Use non-production values for local and test runs only; do not commit real secrets.

---

## Current Salary Advance Implementation Boundary

Executable backend code currently covers authenticated Customer readiness, employee verification, whole-VND Salary Advance submission, limit reservation, versioned document checklists and manual review, customer/staff correction and resubmission, Loan Officer review, Approver decision, immutable approved-offer generation, and customer accept/decline/expiry. The furthest successful waiting state remains `CONTRACT_PENDING`.

Current workflow contracts:

- mathematically whole amounts such as `3000000`, `3000000.0`, and `3000000.00` are valid; non-zero fractional VND returns `422 INVALID_PRODUCT_AMOUNT`;
- same-Customer duplicate Salary Advance submissions, including cross-link concurrency, return `409 BLOCKING_APPLICATION_EXISTS` to the losing request;
- all review actions are executable: `RECOMMEND_APPROVAL`, `RECOMMEND_REJECTION`, `RETURN_TO_CUSTOMER_REVISION`, and `REQUEST_STAFF_CORRECTION`;
- all approval actions are executable: `APPROVE`, `REJECT`, `RETURN_TO_LOAN_OFFICER_REVIEW`, and `REQUEST_CUSTOMER_OR_STAFF_CORRECTION`;
- revision actions require the expected active review cycle, a controlled reason code, and an exact structured correction plan;
- Salary Advance requires no document at initial submission; `RECENT_PAYSLIP` is created only by an authorized correction plan;
- document versions and review decisions are immutable; upload completeness is distinct from accepted/waived processing readiness;
- correction resubmission revalidates Customer, Partner employee, product, blocking-application, document, and reservation invariants; requested amount and term remain immutable and the existing reservation is preserved;
- customer, Loan Officer, and Back-Office document/correction operations are permission- and ownership-checked, and document content responses use private no-store attachment delivery;
- local filesystem binary storage is the MVP adapter behind a storage port; PDF, JPEG, and PNG are limited to 10 MiB, while malware scanning, object storage, retention/deletion, and OCR remain deferred;
- accept or decline against an expired offer returns `409 OFFER_EXPIRED`, including when expiry was already persisted.

Contract readiness, manual disbursement, LoanAccount activation, final repayment schedules, repayment servicing, Customer registration, and frontends remain target-state work rather than current executable behavior.

---

## Roadmap

Current Salary Advance delivery order:

1. Critical-path stabilization. Complete in the current checkpoint.
2. Document checklist and correction/revision readiness. Complete through V24.
3. Contract readiness. Next checkpoint.
4. Manual disbursement and LoanAccount activation.
5. Repayment, settlement, and closure.

### Phase 1 — Core Lending MVP
- [ ] Common loan application lifecycle with state machine
- [ ] Loan product catalog and product policy framework
- [ ] Salary Advance workflow with Partner Company and Partner Employee eligibility support
- [ ] Streamlined Unsecured Consumer Loan and Collateral Loan workflows
- [ ] Controlled review and approval workflow
- [ ] Document upload, checklist handling, and metadata management
- [ ] Customer acceptance, manual disbursement confirmation, and repayment tracking
- [ ] JWT authentication + RBAC
- [ ] Idempotency framework
- [ ] Flyway database migrations
- [ ] Spring Modulith structure + verification tests
- [ ] Event Publication Registry (`event_publication` Flyway migration + startup replay scheduler)
- [ ] Docker Compose (PostgreSQL + application)
- [ ] Structured JSON logging
- [ ] GitHub Actions CI pipeline

### Phase 2 — OCR-Assisted Document Processing
- [ ] Python FastAPI OCR service (containerized)
- [ ] Vietnamese TrOCR model integration
- [ ] Async job queue (PostgreSQL-backed)
- [ ] OCR result persistence
- [ ] Manual review UI for OCR-assisted document results

### Phase 3 — Operational Maturity
- [ ] Redis (JWT blacklist, rate limiting, idempotency cache)
- [ ] Prometheus metrics + Grafana dashboards
- [ ] OpenTelemetry distributed tracing
- [ ] Performance profiling and optimization

### Phase 4 — Analytics & Risk
- [ ] Elasticsearch (loan search, audit log analytics)
- [ ] Reporting dashboards
- [ ] Rule-based risk assessment engine
- [ ] Loan eligibility scoring

### Future Considerations
- Notification service (email, SMS, in-app)
- Mobile application support
- Payroll provider, employer API, payment gateway, bank transfer, and credit bureau integrations
- Multi-level approval workflows
- Microservice extraction (documented path, deferred execution) + Kafka

#### Financial Ledger & Accounting

- Double-Entry Accounting Ledger
- Journal Entry Engine (Debit/Credit)
- Chart of Accounts Management
- Automated Repayment Posting
- Financial Reconciliation & Balance Validation
- Accounting Audit Reports

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
├── document/                # Document management + OCR-assisted processing
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

Controllers call application input ports and return DTOs. Application services implement input ports, call output ports, and map domain objects to DTOs. JPA entities stay inside infrastructure persistence adapters.

`shared/` keeps a simpler shape: `shared/domain/model`, `shared/domain/exception`, `shared/application`, and `shared/infrastructure/{config,persistence,web}`. Cross-cutting web infrastructure such as health checks, global exception handling, and API error responses belongs in `shared/infrastructure/web`.

---

## License

*TBD*
