# Project Meridian

## Meridian Lending Platform

Meridian is a digital lending platform focused on salary advance and short-term credit products, designed to simplify access to financing while improving operational efficiency for lending teams. The platform manages the complete lending lifecycle, from application submission and document verification to approval workflows, disbursement, and audit tracking.

To reduce operational overhead and improve processing speed, Meridian incorporates OCR-assisted document processing alongside configurable multi-level approval workflows. The platform is built around core financial software concerns including auditability, security, data integrity, and regulatory compliance.

Built with Java, Spring Boot, PostgreSQL, and React, Meridian adopts Domain-Driven Design and a Modular Monolith architecture with clearly defined bounded contexts. This approach enables rapid delivery today while preserving a clear evolutionary path toward distributed services as business requirements grow.

---

## Architecture

| Principle | Implementation |
|---|---|
| **Architecture Style** | Modular Monolith (Spring Modulith) |
| **Internal Design** | Hexagonal Architecture (Ports & Adapters) |
| **Domain Modeling** | Domain-Driven Design (Bounded Contexts) |
| **Dependency Direction** | Inward-only — Infrastructure → Application → Domain |
| **Boundary Enforcement** | Spring Modulith + ArchUnit fitness functions |
| **Module Communication** | Sync via port interfaces, async via Spring Modulith `ApplicationEvents` + Transactional Outbox |
| **Migration Path** | Each module is designed to be independently extractable into a microservice with minimal impact on core business logic |

### Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                             CLIENTS                                  │
│              React SPA (Vite)  ·  Admin Panel  ·  Mobile (Future)   │
└───────────────────────────────┬─────────────────────────────────────┘
                                │ HTTPS + JWT (RS256)
                                ▼
┌─────────────────────────────────────────────────────────────────────┐
│                      API GATEWAY LAYER                               │
│            (Spring Security Filter Chain — embedded)                 │
│                                                                      │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌────────┐  │
│  │  JWT Auth    │  │  Caffeine    │  │ Idempotency  │  │  CORS  │  │
│  │  Filter      │  │  Rate Limiter│  │  Filter      │  │ Filter │  │
│  │  (RS256)     │  │  (in-memory) │  │  (DB-backed) │  │        │  │
│  └──────────────┘  └──────────────┘  └──────────────┘  └────────┘  │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │  Springdoc OpenAPI (auto-generated, /swagger-ui)             │   │
│  └──────────────────────────────────────────────────────────────┘   │
└───────────────────────────────┬─────────────────────────────────────┘
                                │
┌───────────────────────────────▼─────────────────────────────────────┐
│               MODULAR MONOLITH  (Spring Boot 4 + Spring Modulith)    │
│                                                                      │
│  ┌─────────────────┐  ┌─────────────────┐  ┌──────────────────────┐ │
│  │  Identity &     │  │   Customer       │  │   Loan Origination   │ │
│  │  Access (IAM)   │  │   Management     │  │   (Core Domain)      │ │
│  │                 │  │                  │  │                      │ │
│  │ • User/Role     │  │ • Profiles       │  │ • Applications       │ │
│  │ • JWT issuance  │  │ • KYC status     │  │ • Products           │ │
│  │ • RBAC (4 roles)│  │ • Employers      │  │ • State machine      │ │
│  │ • Refresh token │  │ • AES-256-GCM    │  │ • Disbursement       │ │
│  │   rotation      │  │   PII encryption │  │ • Repayment schedule │ │
│  └─────────────────┘  └─────────────────┘  └──────────────────────┘ │
│                                                                      │
│  ┌─────────────────┐  ┌─────────────────┐  ┌──────────────────────┐ │
│  │  Approval        │  │   Document       │  │  Audit & Compliance  │ │
│  │  Workflow        │  │   Management     │  │                      │ │
│  │                 │  │                  │  │ • Immutable event log │ │
│  │ • Configurable  │  │ • Upload         │  │ • JSONB snapshots    │ │
│  │   approval chain│  │ • Type classify  │  │ • SBV audit reports  │ │
│  │ • Delegation    │  │ • OCR job trigger│  │ • PDPA erasure log   │ │
│  │   limits & SLA  │  │ • Verification   │  │                      │ │
│  └─────────────────┘  └─────────────────┘  └──────────────────────┘ │
│                                                                      │
│  ══════════════ Spring Modulith ApplicationEvents ═════════════════  │
│  ══════════════ Transactional Outbox (spring-modulith-events-jdbc) ══│
│  ══════════════ Cross-cutting: MDC Logging · Metrics · ArchUnit ════ │
└──────────────────────────────┬──────────────────────────────────────┘
                               │
              ┌────────────────┼──────────────────┐
              ▼                ▼                  ▼
   ┌────────────────┐  ┌────────────┐  ┌──────────────────────┐
   │  PostgreSQL    │  │   File     │  │  Python OCR Service  │
   │                │  │  Storage   │  │  (Phase 2)           │
   │ • All module   │  │            │  │                      │
   │   schemas      │  │ • Document │  │ • FastAPI            │
   │ • event_publi- │  │   uploads  │  │ • Vietnamese TrOCR   │
   │   cation table │  │ • OCR input│  │ • Async job workers  │
   │   (outbox)     │  │   artifacts│  │ • Stale lease sweep  │
   │ • Audit log    │  │            │  │ • Shared secret auth │
   │ • Idempotency  │  │            │  │                      │
   │ • Job queue    │  │            │  │                      │
   └────────────────┘  └────────────┘  └──────────────────────┘
```

### Bounded Contexts

| Context | Role | Key Entities |
|---|---|---|
| **Identity & Access** | Authentication, authorization, RBAC | `User`, `Role`, `RefreshToken` |
| **Customer Management** | KYC, customer profile, employer linkage | `Customer`, `Employer`, `KycStatus` |
| **Loan Origination** | Core domain — state machine, products, disbursement | `LoanApplication`, `LoanProduct`, `RepaymentSchedule` |
| **Approval Workflow** | Multi-level approval chain, delegation, SLA | `ApprovalRequest`, `DelegationRule` |
| **Document Management** | Upload, classification, OCR job dispatch | `Document`, `OcrJob` |
| **Audit & Compliance** | Immutable event log, regulatory reporting, PDPA erasure | `AuditEvent` |

---

## Key Features

### Core Platform
- **Loan Application Lifecycle** — State machine–driven origination with rich domain model
- **Salary Advance Workflow** — Employer-linked salary advance with eligibility verification
- **Multi-Level Approval Workflow** — Configurable approval chains with role-based thresholds and SLA tracking
- **Document Upload & Management** — Type classification, metadata, storage abstraction (local → S3)
- **JWT Authentication & RBAC** — RS256 tokens, refresh rotation, 4-role permission model
- **Idempotent Financial Operations** — `Idempotency-Key` header processing for all mutation endpoints
- **Immutable Audit Trail** — Append-only event logging with JSONB state snapshots
- **Structured Logging** — JSON-formatted logs with request correlation (userId, loanId, traceId)
- **Data Encryption** — AES-256-GCM encryption at rest for PII fields (national ID, financial data)

### User Roles

| Role | Key Permissions | Notes |
|---|---|---|
| **Customer** | `loan:submit`, `loan:read` (own), `loan:cancel` (own), `document:upload`, `document:read` (own) | Self-service only; service layer enforces ownership |
| **Loan Officer** | `loan:read`, `loan:review`, `loan:disburse`, `approval:submit`, `document:verify`, `customer:read` | Can act on any customer's application |
| **Manager** | All Loan Officer permissions + `loan:product:manage`, `approval:override`, `customer:update`, `audit:read` | Can override decisions and manage products |
| **Administrator** | All permissions + `admin:user:manage`, `admin:config`, `admin:data:read-all` | Full platform access including break-glass data read |

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

### Phase 1 — Core MVP *(Weeks 1–6)*
- [ ] Loan origination with state machine
- [ ] Multi-level approval workflow
- [ ] Document upload & metadata management
- [ ] JWT authentication + RBAC (4 roles)
- [ ] Idempotency framework
- [ ] Flyway database migrations
- [ ] Spring Modulith structure + verification tests
- [ ] Event Publication Registry (`event_publication` Flyway migration + startup replay scheduler)
- [ ] Docker Compose (PostgreSQL + application)
- [ ] Structured JSON logging
- [ ] GitHub Actions CI pipeline

### Phase 2 — Document Intelligence *(Weeks 7–10)*
- [ ] Python FastAPI OCR service (containerized)
- [ ] Vietnamese TrOCR model integration
- [ ] Async job queue (PostgreSQL-backed)
- [ ] Document validation pipeline
- [ ] OCR result persistence and manual review UI

### Phase 3 — Operational Maturity *(Weeks 11–14)*
- [ ] Redis (JWT blacklist, rate limiting, idempotency cache)
- [ ] Prometheus metrics + Grafana dashboards
- [ ] OpenTelemetry distributed tracing
- [ ] Performance profiling and optimization

### Phase 4 — Analytics & Risk *(Weeks 15–18, if needed)*
- [ ] Elasticsearch (loan search, audit log analytics)
- [ ] Reporting dashboards
- [ ] Rule-based risk assessment engine
- [ ] Loan eligibility scoring

### Future Considerations *(Not Scheduled)*
- Notification service (email, SMS, in-app)
- Mobile application support
- Partner company / payroll provider integration
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

```
com.lending.platform/
├── shared/                  # Shared kernel (minimal)
│   ├── domain/              # Base entities, Money VO, domain events
│   ├── application/         # Cross-cutting (idempotency)
│   └── infrastructure/      # Security config, JWT, exception handling
│
├── identity/                # IAM bounded context
│   ├── domain/              # User, Role, ports
│   ├── application/         # Auth & user management use cases
│   └── infrastructure/      # Controllers, JPA adapters
│
├── customer/                # Customer bounded context
├── loan/                    # Loan Origination (core domain — full hexagonal)
├── approval/                # Approval Workflow
├── document/                # Document Management
└── audit/                   # Audit & Compliance (simplified)
```

Each module follows the Hexagonal Architecture internally:

```
module/
├── domain/
│   ├── model/               # Entities, value objects, enums
│   ├── port/
│   │   ├── in/              # Use case interfaces (driving ports)
│   │   └── out/             # Repository & external service ports (driven ports)
│   ├── service/             # Domain services
│   └── event/               # Domain events
├── application/
│   ├── service/             # Use case implementations
│   ├── dto/                 # Request/response DTOs
│   └── mapper/              # Domain ↔ DTO mapping
└── infrastructure/
    ├── adapter/
    │   ├── in/web/           # REST controllers
    │   └── out/persistence/  # JPA repositories & entities
    └── config/               # Module-specific configuration
```

---

## License

*TBD*
