# Meridian Backend Project Structure — Java Package Blueprint

## Purpose and Scope

This document defines the intended source and package structure of the Meridian Java backend modular monolith.

It is a durable structural blueprint. Representative types and contracts show important placement decisions; the Java source tree remains authoritative for exact implementation names.

This document covers `meridian-platform` only. Frontend and external OCR-service source structures are defined separately. Implementation status belongs in the project roadmap and follow-up register.

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

`MeridianPlatformApplication` is the Spring Boot entry point and root component-scan boundary.

---

## 2. Top-Level Java Packages

Each bounded context is represented by a top-level feature module under `com.meridian.platform`. The module's Java package follows the structure defined in this document. `shared` is a technical package, not a bounded context.

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
| `loan` | Products, applications, limits, review cycles, offers, contracts, activation, servicing, repayment, contractual payoff, Administrative Full-Balance Settlement, and administrative closure |
| `approval` | Loan Officer recommendations, Approver decisions, maker-checker controls, and decision records |
| `document` | Checklists, document versions, review decisions, storage, readiness, and the backend OCR boundary |
| `audit` | Append-only cross-cutting business audit records |
| `notification` | Controlled message templates, delivery-provider contracts, and transport adapters; broader request/status management remains incremental |

Salary Advance, Unsecured Consumer Loan, and Collateral Loan remain product behaviors inside `loan`. They do not become top-level product packages such as `salaryadvance`, `unsecuredloan`, or `collateralloan`.

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

A module contains only the packages it uses.

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

Domain code remains pure Java. Application code must not depend on infrastructure. Controllers and persistence adapters must not implement lending rules. `MER-ARCH-003-dependency-rules.md` defines the enforceable dependency rules.

---

## 4. Structural Profiles

`Full`, `Moderate`, and `Simplified` describe the amount of package structure a module needs, not different quality standards. Every profile preserves inward dependency direction.

| Module | Profile | Emphasis |
|---|---|---|
| Loan Core / Lending Lifecycle | Full | Complex lifecycle, product policies, many use cases, cross-context ports, persistence, events, and secured APIs |
| Approval Workflow | Full | Recommendation and decision records, maker-checker enforcement, and Loan coordination |
| Identity & Access | Full | Authentication, authorization, token/session boundaries, persistence, and security adapters |
| Customer | Moderate | Aggregate behavior, profile and bank-account services, sensitive-data boundaries, and narrow public contracts |
| Partner | Moderate | Company and employee data, imports, verification, reusable links, and Customer/Loan collaboration |
| Document | Moderate | Checklists, versions, review, storage, readiness, and the OCR integration boundary |
| Audit | Simplified | Event intake, append-only persistence, and authorized queries |
| Notification | Simplified | Public delivery contracts, controlled templates, provider ports, and transport adapters |

A simplified module may omit domain services, mappers, publishers, or other packages when unnecessary. It must not collapse web or event intake, business orchestration, and persistence into one layer.

---

## 5. Representative Architectural Elements

These examples show placement, not a required file inventory. The Java source tree remains authoritative for exact names.

| Module | Representative elements |
|---|---|
| Shared | `AuthenticatedUser`, `CurrentUserProvider`, `BusinessAuditEvent`, `BusinessOperationContext` |
| Identity | `User`, `EmailVerificationToken`, `RegisterCustomerUseCase`, `JwtAuthenticationFilter`, `JwtTokenService`, `SpringSecurityCurrentUserProvider`, `SecurityConfig` |
| Customer | `Customer`, `CustomerProfile`, `CustomerBankAccount`, `RegisterCustomerUseCase`, `ContractBankAccountUseCase` |
| Partner | `PartnerCompany`, `PartnerEmployee`, `PartnerEmployeeImportBatch`, `CustomerPartnerEmployeeLink`, `VerifyPartnerEmployeeService` |
| Loan | `LoanApplication`, `SalaryAdvanceLimit`, `SalaryAdvanceVerification`, `UnsecuredConsumerLoanVerification`, `Collateral`, `CollateralLoanVerification`, `ApprovedOffer`, `LoanContract`, `LoanAccount`, `ManualDisbursement`, `RepaymentSchedule` |
| Approval | `ReviewRecommendation`, `ApprovalDecision`, `SubmitApprovalDecisionService` |
| Document | `DocumentChecklist`, `DocumentChecklistItem`, logical document/version models, review decisions, `DocumentChecklistService` |
| Audit | `AuditEvent`, `RecordAuditEventsUseCase`, `RecordAuditEventsService`, `BusinessAuditEventListener` |
| Notification | `EmailVerificationMessage`, `SendEmailVerificationUseCase`, controlled template service, `EmailSenderPort`, SMTP adapter |

Representative Loan application services include `StartSalaryAdvanceApplicationService`, `StartUnsecuredConsumerLoanApplicationService`, `StartCollateralLoanApplicationService`, `ApplyApprovalDecisionService`, `LoanContractReadinessService`, `ConfirmManualDisbursementService`, and `RecordRepaymentService`.

---

## 6. Module-Specific Placement Rules

### Shared

`shared` contains only stable technical abstractions genuinely required by multiple modules. It must not own feature behavior or depend on a feature module.

### Identity

Spring Security, JWT, principal, and authenticated-user resolution code belongs under `identity/infrastructure/security`.

Other modules use `CurrentUserProvider` and `AuthenticatedUser`; they do not import Identity's security implementation.

### Customer and Partner

Customer owns source profile, identity, and bank-account data. Partner owns employment source data and reusable Customer–Partner Employee links.

Cross-context access uses narrow application contracts and infrastructure adapters. A module must not use another context's repositories or JPA entities.

### Loan

Loan owns origination, review cycles, corrections, offers, contracts, activation, LoanAccount servicing, repayment, overdue evaluation, contractual payoff, Administrative Full-Balance Settlement, and administrative closure.

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

Internal subpackages may group types for one lifecycle capability or policy family as Loan grows.

### Approval

Approval owns recommendation and decision records. Loan owns `LoanApplication` transitions. Approval publishes structured outcomes; Loan consumes them through an inbound event adapter or another explicit application contract.

### Document

Document storage implementations belong under `document.infrastructure.adapter.out.storage`.

Backend OCR clients belong under an OCR-specific output-adapter package and implement a Document-owned output port. The external OCR service's source tree remains outside this document.

### Audit and Notification

Business-audit and notification event listeners belong under `infrastructure.adapter.in.event` when a durable event is the selected collaboration. Persistence and delivery-provider implementations belong under the corresponding output-adapter packages.

Secret-bearing notification commands must not be serialized as durable events. The Customer email-verification flow therefore enters Notification through its public application contract only after Identity registration or token replacement commits. Identity owns token validity and passes the raw token transiently through an Identity infrastructure boundary adapter; Notification owns controlled rendering and SMTP transport. Delivery failure cannot reverse committed Identity or Customer state.

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
- contractual-payoff, Administrative Full-Balance Settlement, and administrative-closure effects.

Activation, repayment including contractual payoff, Administrative Full-Balance Settlement, and administrative closure remain Loan lifecycle capabilities within `loan`.

---

## 8. Cross-Context Collaboration Placement

### 8.1 Synchronous Application Contracts

The consuming module owns an output port that describes the facts or action it needs. A boundary adapter implements that port by calling the providing context's public application contract.

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

This pattern applies when:

- Loan consumes Partner eligibility;
- Loan consumes Document readiness;
- Partner consumes Customer identity evidence;
- Approval queries Loan-owned review-cycle facts.

A boundary adapter may translate identifiers and immutable contract records. It must not expose another context's repositories, JPA entities, internal services, or aggregate object graphs.

### 8.2 Published Events and Inbound Event Adapters

A module that consumes a published business event handles it through `infrastructure.adapter.in.event` and enters its own application layer through an explicit contract.

Event intake is structurally different from a synchronous output-port call. Delivery may participate in the publisher's transaction or use durable asynchronous processing; `MER-ARCH-003-dependency-rules.md` owns those reliability choices and `MER-ARCH-006-api-request-flow-and-dependencies.md` documents representative runtime sequences.

Audit consumes business events through inbound event adapters. Notification may do the same for non-secret business events, while secret-bearing post-commit delivery uses the explicit synchronous output-port/public-contract pattern described above so durable `event_publication` state never contains the secret.

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

- Released Flyway migrations are append-only.
- Schema changes use ordered migration files.
- Secrets and real credentials must not be committed.
- Schema snapshots document the resulting structure; Flyway remains the executable database history.

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

Tests follow production ownership. Shared reusable fixtures and test support remain under `com.meridian.platform.support`.

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
6. JPA entities remain under infrastructure persistence and distinct from domain models.
7. REST DTOs and application commands must not become domain models.
8. Add a package only when it groups types under one cohesive responsibility or owner.
9. Update this document when a placement rule or module boundary changes. The Java source tree remains authoritative for routine file additions, removals, and renames.
