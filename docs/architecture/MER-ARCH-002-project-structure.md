# Meridian Backend Project Structure — Java Package Blueprint

## Purpose and Scope

This document defines the intended source and package structure of the Meridian Java backend modular monolith.

It is a durable structural blueprint. Representative types illustrate important placement decisions; the Java source tree remains authoritative for exact implementation names.

This document covers `meridian-platform` only. Frontend and external OCR-service source structures are defined separately. Implementation status belongs in the project roadmap and follow-up register, not here.

---

## 1. Backend Project Root

```text
meridian-platform/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/com/meridian/platform/
│   │   │   └── MeridianPlatformApplication.java
│   │   └── resources/
│   │       ├── application.properties
│   │       └── db/migration/
│   └── test/
│       ├── java/com/meridian/platform/
│       └── resources/
│           └── application.properties
└── target/                         # Generated build output
```

`MeridianPlatformApplication` is the Spring Boot entry point and root component-scan boundary. Flyway migrations remain under `src/main/resources/db/migration`.

---

## 2. Top-Level Java Packages

```text
com.meridian.platform/
├── MeridianPlatformApplication.java
├── shared/
├── identity/
├── customer/
├── partner/
├── loan/
├── approval/
├── document/
├── audit/
└── notification/
```

| Package | Responsibility |
|---|---|
| `shared` | Minimal technical shared kernel; not a bounded context |
| `identity` | Users, authentication, authorization, roles, permissions, sessions, and security implementation |
| `customer` | Customer aggregate, profile, verification state, bank accounts, and sensitive Customer data |
| `partner` | Partner Companies, Partner Employees, imports, employment verification, and reusable employee links |
| `loan` | Products, applications, limits, review cycles, offers, contracts, activation, servicing, repayment, settlement, and closure |
| `approval` | Loan Officer recommendations, Approver decisions, maker-checker controls, and decision records |
| `document` | Checklists, document versions, review decisions, storage, readiness, and backend OCR integration |
| `audit` | Append-only cross-cutting business audit records |
| `notification` | Templates, delivery requests, channels, attempts, and delivery status |

Salary Advance, Unsecured Consumer Loan, and Collateral Loan remain product behaviors inside `loan`. Do not create top-level product packages such as `salaryadvance`, `unsecuredloan`, or `collateralloan`.

---

## 3. Canonical Feature-Module Shape

```text
<context>/
├── domain/
│   ├── model/
│   ├── service/
│   ├── event/
│   └── exception/
├── application/
│   ├── port/
│   │   ├── in/
│   │   └── out/
│   ├── service/
│   ├── dto/
│   └── mapper/
└── infrastructure/
    ├── adapter/
    │   ├── in/
    │   │   ├── web/
    │   │   └── event/
    │   └── out/
    │       ├── persistence/
    │       ├── event/
    │       ├── storage/
    │       └── <collaborating-context-or-provider>/
    ├── security/
    └── config/
```

A module uses only the packages it needs; empty placeholders are unnecessary.

| Package | Placement rule |
|---|---|
| `domain.model` | Aggregates, entities, value objects, enums, and state transitions |
| `domain.service` | Pure business policies or calculations not owned by one aggregate |
| `domain.event` | Context-owned domain event vocabulary |
| `application.port.in` | Public command and query use-case contracts |
| `application.port.out` | Persistence, publication, storage, provider, and cross-context dependency contracts |
| `application.service` | Transactional use-case orchestration and policy selection |
| `application.dto` | Application and API boundary shapes |
| `application.mapper` | Domain-to-boundary mapping |
| `infrastructure.adapter.in.web` | REST controllers and HTTP-specific concerns |
| `infrastructure.adapter.in.event` | Event listeners entering through application contracts |
| `infrastructure.adapter.out.persistence` | JPA/JDBC entities, repositories, adapters, and persistence mapping |
| `infrastructure.adapter.out.<boundary>` | Event, storage, provider, or collaborating-context adapters |
| `infrastructure.security` | Concrete Spring Security and JWT implementation owned by Identity |
| `infrastructure.config` | Module-specific technical wiring |

Domain code remains pure Java. Application code must not depend on infrastructure. Controllers and persistence adapters must not implement lending rules. Exact dependency enforcement is defined in `MER-ARCH-003-dependency-rules.md`.

---

## 4. Structural Profiles

“Full,” “Moderate,” and “Simplified” describe package ceremony, not different quality standards. Every module preserves inward dependency direction.

| Module | Profile | Emphasis |
|---|---|---|
| Loan Core / Lending Lifecycle | Full | Complex lifecycle, product policies, many use cases, cross-context ports, persistence, events, and secured APIs |
| Approval Workflow | Full | Recommendation and decision records, maker-checker enforcement, and Loan coordination |
| Identity & Access | Full | Authentication, authorization, token/session boundaries, persistence, and security adapters |
| Customer | Moderate | Aggregate behavior, profile and bank-account services, sensitive-data boundaries, and narrow public contracts |
| Partner | Moderate | Company and employee data, imports, verification, reusable links, and Customer/Loan collaboration |
| Document | Moderate | Checklists, versions, review, storage, readiness, and OCR integration |
| Audit | Simplified | Event intake, append-only persistence, and authorized queries |
| Notification | Simplified | Event intake, templates, provider ports, delivery attempts, and status |

A simplified module may omit domain services, mappers, publishers, or other packages when unnecessary. It must not collapse web, persistence, and business orchestration into one layer.

---

## 5. Representative Architectural Types

The following names are selective placement examples, not a complete file inventory.

| Module | Representative types |
|---|---|
| Shared | `AuthenticatedUser`, `CurrentUserProvider`, `BusinessAuditEvent`, `BusinessOperationContext` |
| Identity | `User`, `JwtAuthenticationFilter`, `JwtTokenService`, `SpringSecurityCurrentUserProvider`, `SecurityConfig` |
| Customer | `Customer`, `CustomerProfile`, `CustomerBankAccount`, `ContractBankAccountUseCase` |
| Partner | `PartnerCompany`, `PartnerEmployee`, `PartnerEmployeeImportBatch`, `CustomerPartnerEmployeeLink`, `VerifyPartnerEmployeeService` |
| Loan | `LoanApplication`, `SalaryAdvanceLimit`, `SalaryAdvanceVerification`, `ApprovedOffer`, `LoanContract`, `LoanAccount`, `ManualDisbursement`, `RepaymentSchedule` |
| Approval | `ReviewRecommendation`, `ApprovalDecision`, `SubmitApprovalDecisionService` |
| Document | `DocumentChecklist`, `DocumentChecklistItem`, logical document/version models, review decisions, `DocumentChecklistService` |
| Audit | `AuditEvent`, `RecordAuditEventsUseCase`, `RecordAuditEventsService`, `BusinessAuditEventListener` |
| Notification | `Notification`, `NotificationTemplate`, delivery request/status types, sender ports |

Important representative application services in Loan include `StartSalaryAdvanceApplicationService`, `ApplyApprovalDecisionService`, `LoanContractReadinessService`, `ConfirmManualDisbursementService`, and `RecordRepaymentService`.

---

## 6. Module-Specific Placement Rules

### Shared

`shared` contains only stable technical abstractions genuinely required by multiple modules. It must not own feature behavior or depend on a feature module.

### Identity

Concrete Spring Security, JWT, principal, and authenticated-user resolution code belongs under:

```text
identity/infrastructure/security/
```

Other modules use `CurrentUserProvider` and `AuthenticatedUser`; they do not import Identity’s security implementation.

### Customer and Partner

Customer owns source profile, identity, and bank-account data. Partner owns employment source data and reusable Customer–Partner Employee links.

Cross-context access occurs through narrow application contracts and infrastructure adapters, never through foreign repositories or JPA entities.

### Loan

Loan origination, review cycles, corrections, offers, contracts, activation, LoanAccount servicing, repayment, overdue evaluation, settlement, and closure remain inside `loan`.

Loan infrastructure may use boundary-specific adapter packages:

```text
loan/infrastructure/adapter/
├── in/
│   ├── web/
│   └── event/
└── out/
    ├── persistence/
    ├── event/
    ├── customer/
    ├── partner/
    └── document/
```

Internal subpackages may be introduced for cohesion as Loan grows, but lifecycle capabilities and individual products must not become separate top-level modules.

### Approval

Approval owns recommendation and decision records. Loan owns LoanApplication transitions. Approval publishes structured outcomes; Loan consumes them through an inbound event adapter or another explicit application contract.

### Document

Document storage implementations belong under `document.infrastructure.adapter.out.storage`.

Backend OCR clients belong under an OCR-specific output-adapter package and implement a Document-owned output port. The external OCR service’s own source tree remains outside this document.

### Audit and Notification

Business-audit and notification event listeners belong under `infrastructure.adapter.in.event`. Persistence and delivery-provider implementations belong under the corresponding output-adapter packages.

Neither Audit nor Notification may control the workflow that produced an event.

---

## 7. Loan Product and Lifecycle Placement

Product-specific behavior stays inside Loan-owned models, services, policies, and use cases.

| Concern | Package area |
|---|---|
| Product invariant or calculation | `loan.domain.model` or `loan.domain.service` |
| Policy selection and transactional orchestration | `loan.application.service` |
| Command or query contract | `loan.application.port.in` |
| Repository or external dependency contract | `loan.application.port.out` |
| REST exposure | `loan.infrastructure.adapter.in.web` |
| Event intake | `loan.infrastructure.adapter.in.event` |
| Persistence implementation | `loan.infrastructure.adapter.out.persistence` |

Product policies may specialize:

- eligibility and evidence;
- amount and term validation;
- pricing and repayment construction;
- exposure reservation and release;
- activation;
- collateral controls;
- repayment effects;
- settlement and closure.

Activation and repayment are durable Loan lifecycle capabilities, not tracking sections or separate architectural slices.

---

## 8. Cross-Context Adapter Placement

The consuming module owns the output port describing what it needs. Infrastructure implements that port by calling the publishing context’s public application contract.

```text
loan/
├── application/port/out/
│   └── ContractBankAccountPort.java
└── infrastructure/adapter/out/customer/
    └── CustomerContractBankAccountAdapter.java

customer/
└── application/port/in/
    └── ContractBankAccountUseCase.java
```

The same pattern applies when:

- Loan consumes Partner eligibility;
- Loan consumes Document readiness;
- Partner consumes Customer identity evidence;
- Approval queries Loan-owned review-cycle facts;
- Notification consumes published business events.

Adapters may translate identifiers and immutable contract records. They must not expose another context’s repositories, JPA entities, internal services, or aggregate object graphs.

---

## 9. Resources and Database Migrations

```text
src/main/resources/
├── application.properties
└── db/migration/
    ├── V1__...sql
    ├── V2__...sql
    └── ...
```

- Flyway migrations are append-only after release.
- Schema changes use ordered migration files.
- Secrets and real credentials are never committed.
- The schema snapshot is documentation; Flyway remains the executable database history.

---

## 10. Test Source Structure

```text
src/test/java/com/meridian/platform/
├── ArchitectureRulesTest.java
├── shared/
├── identity/
├── customer/
├── partner/
├── loan/
├── approval/
├── document/
├── audit/
├── notification/
└── support/
```

Tests mirror production ownership where practical:

| Test type | Preferred location |
|---|---|
| Domain unit test | Corresponding context and domain package |
| Application-service test | Corresponding `application.service` test package |
| Persistence/adapter integration test | Corresponding infrastructure adapter test package |
| Controller/security test | Web-adapter or Identity security test package |
| Migration test | Context-owning persistence test package |
| Cross-context workflow test | Orchestrating or consuming context |
| Shared reusable fixture/support | `com.meridian.platform.support` |
| Architecture enforcement | Root `com.meridian.platform` package |

Domain tests remain framework-free. Infrastructure tests verify persistence, migrations, security, storage, events, and external boundaries. Testing percentages and checkpoint-specific suite counts do not belong in this blueprint.

---

## 11. Naming and Evolution Rules

1. Package names use lowercase context or capability names.
2. Use-case interfaces normally end with `UseCase`.
3. Application implementations normally end with `Service`.
4. Persistence output ports use domain-oriented `Repository` names.
5. Infrastructure adapters identify their technology or collaborating boundary.
6. JPA entities remain under infrastructure persistence and remain distinct from domain models.
7. REST DTOs and application commands must not become domain models.
8. Product-specific classes remain under `loan`.
9. Types enter `shared` only when stable, minimal, and truly cross-cutting.
10. Add packages for cohesion and ownership, not because a checkpoint added several files.
11. Do not create empty placeholder packages.
12. Exact Java files may evolve without mirroring every change here, provided these placement rules remain intact.
