# Project Meridian

## Meridian Lending Platform

Meridian is a multi-product digital lending platform built around a common lending lifecycle and product-specific policies. Salary Advance is the flagship and deepest product workflow; Unsecured Consumer Loan (UCL) and Collateral Loan use streamlined variants of the same lifecycle.

The complete platform combines Customer, Staff, and administrative experiences, document management and advisory OCR-assisted processing, controlled correction and review, independent approval, immutable offers and operational contracts, disbursement activation, LoanAccount servicing, and audit evidence. Meridian prioritizes financial integrity, security, explicit state transitions, maker-checker controls, and clear operational ownership.

Meridian uses a Java and Spring modular monolith with Domain-Driven Design and Practical Hexagonal Architecture. React/Vite clients and Python/FastAPI OCR processing form the selected direction for upcoming platform components; the Roadmap distinguishes delivered capabilities from planned work.

---

## Key Features

### Lending Products

- **Salary Advance** — Partner-linked employment eligibility and pre-submission Partner evidence drive a product-specific limit, reservation, and used-exposure model. Positive contractual outstanding on a matching `ACTIVE` or `OVERDUE` Salary Advance `LoanAccount` also blocks new submission.
- **Unsecured Consumer Loan** — Required income, bank-statement, and employment evidence feed manual product verification, structured correction, and re-verification. UCL uses exact-request flat-rate pricing and a product-scoped outstanding-account restriction without creating Salary Advance exposure.
- **Collateral Loan** — One Customer-submitted structured Collateral fact is supported by Document-owned ownership evidence, numbered manual verification, and document-only correction and re-verification. Collateral uses exact-request pricing, creates no Salary Advance exposure, and adds no product-specific outstanding-LoanAccount origination restriction.

All three products participate in Meridian's common application, approval, contract, activation, servicing, and closure lifecycle.

### Platform Capabilities

- **Common Lending Lifecycle** — A shared LoanApplication lifecycle coordinates submission, verification, review, approval, Customer response, contract readiness, disbursement, and LoanAccount activation while product policies retain product-specific behavior.
- **Document and OCR-Assisted Processing** — Document owns checklists, uploads, immutable versions, manual review, replacement, waiver, and processing readiness. Planned OCR processing remains advisory; authorized Document review stays authoritative and OCR never approves credit or documents by itself.
- **Review, Correction, and Approval** — Loan owns review cycles, correction work, resubmission, and application transitions. Loan Officers record recommendations, while Approvers make independent decisions under maker-checker controls.
- **Offers, Contracts, and Disbursement** — Immutable approved offers, Customer response, versioned operational contracts, acknowledgment, readiness, and idempotent manual-disbursement activation preserve accepted terms and operational evidence. Customer owns the mutable source bank account; Loan owns only the protected contract-bound destination snapshot.
- **Common Loan Servicing Lifecycle** — Salary Advance, UCL, and Collateral Loan support repayment, overdue evaluation and cure, contractual payoff, payment-backed Administrative Full-Balance Settlement, and separate administrative closure.
- **Identity and Ownership Controls** — JWT access-token authentication, permission-based RBAC, token-derived Customer identity, ownership checks, and purpose-limited cross-context contracts protect Customer and Staff operations.
- **Transactional Command Safety** — Critical operations use command-specific request identities, semantic replay validation, atomic persistence, concurrency controls, and durable outcomes.
- **Audit and Data Protection** — Ordered lifecycle history, append-only business audit evidence, protected sensitive values, masked responses, and restricted payloads preserve traceability without broad PII exposure.

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
| **Boundary Enforcement** | Architecture documents define the intended module law. Current ArchUnit tests enforce core layer, security, and shared-kernel rules; broader public-surface, legal-dependency, and cycle enforcement remains planned hardening. |
| **Module Communication** | Narrow public application contracts support synchronous collaboration. Transaction-aware coordination preserves atomic outcomes, while durable asynchronous delivery requires explicit retry, recovery, and idempotency. |
| **Future Evolution** | Bounded contexts and published contracts preserve selective extraction options; services are extracted only when stable boundaries, scale, or operational ownership justify it |

### Complete Platform Architecture

The diagram shows Meridian's complete intended platform architecture. The Roadmap distinguishes delivered components from planned components.

```text
┌──────────────────────────────────────────────────────────────────────────────┐
│                                   CLIENTS                                    │
│         React/Vite Customer Portal  ·  Staff/Admin  ·  Mobile Experience     │
└──────────────────────────────────────┬───────────────────────────────────────┘
                                       │ HTTPS
                                       ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                         API EDGE / SECURITY LAYER                            │
│              Spring Security  ·  JWT (RS256)  ·  RBAC  ·  CORS              │
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
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                             │
│       Public contracts  ·  Transaction-aware coordination  ·  Events       │
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

---

## Technology Stack

The stack combines the delivered lending backend with technologies selected for upcoming Meridian platform components. The Roadmap shows delivery status.

### Backend

| Technology | Purpose |
|---|---|
| **Java 25** | LTS runtime with virtual threads and pattern matching |
| **Spring Boot 4.1.0** | Application framework |
| **Spring Modulith 2.1.0** | Module organization, observability support, and transactional event-publication persistence |
| **Spring Security** | Authentication & authorization |
| **Spring Data JPA / Hibernate** | Data persistence |
| **Flyway** | Versioned database migrations |
| **ArchUnit** | Executable fitness functions for core layer, security, and dependency rules |
| **JWT (RS256)** | Access-token authentication with asymmetric signing |

### Frontend

| Technology | Purpose |
|---|---|
| **React** | Customer, Staff, and administrative web experiences |
| **Vite** | Frontend build tooling |

### OCR / Document Intelligence

| Technology | Purpose |
|---|---|
| **Python + FastAPI** | Purpose-limited OCR service and asynchronous document-processing worker |
| **Vietnamese TrOCR** | Advisory OCR-assisted extraction for uploaded documents |

### Database

| Technology | Purpose |
|---|---|
| **PostgreSQL** | Primary relational store for ACID transactions, concurrency control, durable event evidence, and planned OCR job coordination |

### Infrastructure

| Technology | Purpose |
|---|---|
| **Docker Compose** | Local PostgreSQL environment; application containerization remains roadmap work |
| **GitHub Actions** | Java 25 and Maven verification against PostgreSQL 16 |
| **SLF4J + Logback** | Application logging foundation; structured JSON and correlation remain roadmap work |

### Development Tools

| Tool | Purpose |
|---|---|
| **Git / GitHub** | Version control |
| **Postman** | API testing |

---

## Roadmap

Phase 1 establishes the multi-product lending backend and is substantially complete. The next delivery focus is OCR-assisted document processing and Customer, Staff, and administrative experiences.

### Phase 1 — Core Lending MVP

- [x] Common loan application lifecycle with state machine, product catalog, and product-policy framework
- [x] Customer profile and bank-account readiness, Partner employment evidence, and Salary Advance eligibility and exposure foundations
- [x] Product-aware document checklists, immutable versions, manual review, correction, and guarded resubmission
- [x] Loan Officer review, independent approval, maker-checker controls, audit evidence, and ordered lifecycle history
- [x] Approved offers, Customer response, operational contracts, readiness, manual disbursement, LoanAccount activation, and final schedules
- [x] Salary Advance lifecycle through repayment, overdue cure, contractual payoff, Administrative Full-Balance Settlement, administrative closure, and exact product-exposure effects
- [x] UCL lifecycle through evidence, verification and correction, approval, exact-request pricing, activation, servicing, closure, product-scoped outstanding protection, and zero Salary Advance exposure
- [x] Collateral Loan lifecycle through structured facts, ownership evidence, numbered verification, document-only correction, exact-request pricing, activation, servicing, closure, and zero Salary Advance exposure
- [x] JWT/RBAC, command-specific idempotency, transactional and concurrency controls, Flyway/PostgreSQL persistence, Spring Modulith event publication, and GitHub Actions verification
- [ ] Startup replay and recovery for incomplete event publications
- [ ] Application containerization and a complete local Compose environment; PostgreSQL Compose support exists
- [ ] Structured JSON logging with request and business correlation

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
