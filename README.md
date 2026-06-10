# Project Meridian

## Meridian Lending Platform

A digital salary advance lending platform engineered for loan origination, multi-level approval workflows, and OCR-assisted document processing. Built as a **Modular Monolith** with **Hexagonal Architecture**, designed to evolve without rewrites.

---

## Mission

To simplify access to short-term financing while improving operational efficiency through automation, transparency, and intelligent document processing.

---

## Architecture

| Principle | Implementation |
|---|---|
| **Architecture Style** | Modular Monolith (Spring Modulith) |
| **Internal Design** | Hexagonal Architecture (Ports & Adapters) |
| **Domain Modeling** | Domain-Driven Design (Bounded Contexts) |
| **Dependency Direction** | Inward-only — Domain → Application → Infrastructure |
| **Boundary Enforcement** | Spring Modulith + ArchUnit fitness functions |
| **Module Communication** | Sync via port interfaces, async via Spring ApplicationEvents |
| **Migration Path** | Each module is a future microservice candidate — zero-rewrite extraction via adapter swap |

### Bounded Contexts

```
┌──────────────────────────────────────────────────────────────┐
│                    MERIDIAN LENDING PLATFORM                  │
│                                                              │
│  ┌────────────┐  ┌──────────────┐  ┌──────────────────────┐ │
│  │ Identity & │  │   Customer   │  │   Loan Origination   │ │
│  │   Access   │  │  Management  │  │    (Core Domain)     │ │
│  └────────────┘  └──────────────┘  └──────────────────────┘ │
│  ┌────────────┐  ┌──────────────┐  ┌──────────────────────┐ │
│  │  Approval  │  │   Document   │  │  Audit & Compliance  │ │
│  │  Workflow  │  │  Management  │  │                      │ │
│  └────────────┘  └──────────────┘  └──────────────────────┘ │
│                                                              │
│  ═══════════ Spring ApplicationEventPublisher ════════════   │
└──────────────────────────────────────────────────────────────┘
         │                                    │
         ▼                                    ▼
   ┌───────────┐                    ┌──────────────────┐
   │ PostgreSQL│                    │ Python OCR Service│
   └───────────┘                    │ (Phase 2)        │
                                    └──────────────────┘
```

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

| Role | Capabilities |
|---|---|
| **Customer** | Apply for loans, upload documents, track application status |
| **Loan Officer** | Review applications, request documents, submit approval decisions |
| **Manager** | Approve/reject loans, override decisions, access reports |
| **Administrator** | User management, system configuration, full audit access |

---

## Technology Stack

### Backend

| Technology | Purpose |
|---|---|
| **Java 25** | LTS runtime with virtual threads and pattern matching |
| **Spring Boot 4.0.x** | Application framework |
| **Spring Modulith** | Module boundary enforcement and event publication |
| **Spring Security** | Authentication & authorization |
| **Spring Data JPA / Hibernate** | Data persistence |
| **Flyway** | Versioned database migrations |
| **ArchUnit** | Architectural fitness function testing |
| **JWT (RS256)** | Stateless authentication with asymmetric signing |

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
- [x] Loan origination with state machine
- [x] Multi-level approval workflow
- [x] Document upload & metadata management
- [x] JWT authentication + RBAC (4 roles)
- [x] Idempotency framework
- [x] Flyway database migrations
- [x] Spring Modulith structure + verification tests
- [x] Docker Compose (PostgreSQL + application)
- [x] Structured JSON logging
- [x] GitHub Actions CI pipeline

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
- Microservice extraction (documented path, deferred execution)

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
