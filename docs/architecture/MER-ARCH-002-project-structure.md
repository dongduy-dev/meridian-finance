# Meridian Backend Project Structure

## Purpose and Scope

This document defines how the Meridian Java backend is organized under `meridian-platform`. It records the stable package boundaries and placement rules for production and test code.

Representative names show where important responsibilities belong. The Java source tree is the authority for exact file and type names.

This document covers the backend only. Frontend and external OCR-service structures are defined separately. Implementation status belongs in the project roadmap and follow-up register.

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

`MeridianPlatformApplication` starts the Spring Boot application and defines the root component-scan boundary. Flyway migrations live under `src/main/resources/db/migration`.

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
| `shared` | Technical abstractions required by multiple modules and owned by no bounded context |
| `identity` | Users, authentication, authorization, roles, permissions, sessions, tokens, and security implementation |
| `customer` | Customer profile, verification state, bank accounts, and sensitive Customer data |
| `partner` | Partner Companies, Partner Employees, imports, employment verification, and reusable employee links |
| `loan` | Products, applications, limits, review cycles, offers, contracts, activation, servicing, repayment, settlement, and closure |
| `approval` | Loan Officer recommendations, Approver decisions, maker-checker controls, and decision records |
| `document` | Checklists, document versions, review decisions, storage, readiness, and backend OCR integration |
| `audit` | Append-only cross-cutting business audit records |
| `notification` | Templates, delivery requests, channels, attempts, and delivery status |

Salary Advance, Unsecured Consumer Loan, and Collateral Loan remain product behaviors inside `loan`. Do not create top-level product packages such as `salaryadvance`, `unsecuredloan`, or `collateralloan`.

---

## 3. Standard Module Shape

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

A module creates only the packages it uses. Empty package placeholders add no value.

| Package | Placement rule |
|---|---|
| `domain.model` | Aggregates, entities, value objects, enums, and state transitions |
| `domain.service` | Business policies or calculations that do not belong to one aggregate |
| `domain.event` | Domain events owned by the context |
| `application.port.in` | Public command and query use-case contracts |
| `application.port.out` | Persistence, event publication, storage, provider, and cross-context dependency contracts |
| `application.service` | Transactional use-case orchestration and policy selection |
| `application.dto` | Application and API boundary shapes |
| `application.mapper` | Mapping between domain objects and boundary shapes |
| `infrastructure.adapter.in.web` | REST controllers and HTTP translation |
| `infrastructure.adapter.in.event` | Event listeners that enter through application contracts |
| `infrastructure.adapter.out.persistence` | JPA/JDBC entities, repositories, adapters, and persistence mapping |
| `infrastructure.adapter.out.<boundary>` | Event, storage, provider, or cross-context adapters |
| `infrastructure.security` | Spring Security and JWT implementation owned by Identity |
| `infrastructure.config` | Module-specific technical wiring |

Domain code remains pure Java. Application code depends on the domain and ports, not infrastructure. Controllers translate HTTP requests into application calls, and persistence adapters translate between the domain and storage. Neither boundary owns lending rules. `MER-ARCH-003-dependency-rules.md` defines the enforceable dependency rules.

---

## 4. Applying the Module Shape

The same dependency direction applies to every module, but each module needs a different amount of package structure.

| Module group | Expected structure |
|---|---|
| Loan, Approval, and Identity | Separate domain, application, and infrastructure concerns because they contain complex rules, orchestration, persistence, security, or cross-context collaboration |
| Customer, Partner, and Document | Preserve the same dependency direction but omit packages that have no responsibility |
| Audit and Notification | Keep event intake, application orchestration, persistence, and provider adapters separate even when the module is small |

A small module must not collapse web or event intake, business orchestration, and persistence into one layer.

---

## 5. Representative Architectural Types

These examples show placement, not a required file inventory.

| Module | Representative types |
|---|---|
| Shared | `AuthenticatedUser`, `CurrentUserProvider`, `BusinessAuditEvent` |
| Identity | `User`, `JwtAuthenticationFilter`, `JwtTokenService`, `SecurityConfig` |
| Customer | `Customer`, `CustomerBankAccount`, `ContractBankAccountUseCase` |
| Partner | `PartnerCompany`, `PartnerEmployee`, `CustomerPartnerEmployeeLink` |
| Loan | `LoanApplication`, `ApprovedOffer`, `LoanContract`, `LoanAccount`, `RepaymentSchedule` |
| Approval | `ReviewRecommendation`, `ApprovalDecision`, `SubmitApprovalDecisionService` |
| Document | `DocumentChecklist`, logical document/version models, `DocumentChecklistService` |
| Audit | `AuditEvent`, `RecordAuditEventsUseCase`, `BusinessAuditEventListener` |
| Notification | `Notification`, `NotificationTemplate`, sender ports |

---

## 6. Module Placement Rules

### Shared

A type belongs in `shared` only when multiple modules need the same technical abstraction and no bounded context owns it. `shared` must not contain feature behavior or depend on a feature module.

### Identity

Place Spring Security, JWT, principal, and authenticated-user resolution code under `identity/infrastructure/security`.

Other modules depend on `CurrentUserProvider` and `AuthenticatedUser`. They must not import Identity's security implementation.

### Customer and Partner

Customer owns customer profile, verification state, and bank-account data. Partner owns Partner Company data, employment records, employee verification, and reusable Customer–Partner Employee links.

Cross-context consumers use public application contracts through boundary adapters. They must not import another context's repositories, JPA entities, or internal services.

### Loan

Loan owns origination, review cycles, corrections, offers, contracts, activation, LoanAccount servicing, repayment, overdue evaluation, settlement, and closure.

Place Loan's inbound and outbound adapters by boundary:

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

Create an internal Loan subpackage when it groups types for one lifecycle capability or policy family. Do not turn a lifecycle capability or product variant into a separate top-level module.

### Approval

Approval owns recommendation and decision records. Loan owns `LoanApplication` transitions. Approval exposes a structured decision outcome, and Loan applies that outcome through an event adapter or another explicit application contract.

### Document

Place document storage implementations under `document.infrastructure.adapter.out.storage`.

An OCR client implements a Document-owned output port and lives in an OCR-specific output-adapter package. The external OCR service has its own source structure outside `meridian-platform`.

### Audit and Notification

Place business-audit and notification event listeners under `infrastructure.adapter.in.event`. Place persistence and delivery-provider implementations under the corresponding output-adapter packages.

Audit and Notification must not control the workflow that produced an event.

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

Product-specific policies can define:

- eligibility and evidence
- amount and term validation
- pricing and repayment construction
- exposure reservation and release
- activation
- collateral controls
- repayment effects
- settlement and closure

Activation, repayment, settlement, and closure remain Loan lifecycle capabilities. They do not form separate top-level modules.

---

## 8. Cross-Context Adapter Placement

The consuming module defines an output port for the facts or action it needs. A boundary adapter implements that port by calling the providing context's public input contract.

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

Use the same placement when:

- Loan consumes Partner eligibility
- Loan consumes Document readiness
- Partner consumes Customer identity evidence
- Approval queries Loan-owned review-cycle facts
- Notification consumes published business events

An adapter may translate identifiers and immutable contract records. It must not expose another context's repositories, JPA entities, internal services, or aggregate object graphs.

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
- Each schema change uses a new ordered migration file.
- Source control must not contain secrets or real credentials.
- Schema snapshots explain the resulting structure; Flyway migrations are the executable database history.

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

Tests follow the same ownership as production code. Shared framework fixtures and reusable test support live under `com.meridian.platform.support`.

| Test type | Location |
|---|---|
| Domain unit test | Corresponding context and domain package |
| Application-service test | Corresponding `application.service` test package |
| Persistence or adapter integration test | Corresponding infrastructure adapter test package |
| Controller or security test | Web-adapter or Identity security test package |
| Migration test | Persistence test package of the context that owns the schema |
| Cross-context workflow test | Orchestrating or consuming context |
| Shared fixture or reusable test support | `com.meridian.platform.support` |
| Architecture enforcement | Root `com.meridian.platform` package |

Domain tests do not load Spring or infrastructure. Infrastructure tests verify persistence, migrations, security, storage, events, and external boundaries. Testing percentages and release-specific suite counts belong in release or verification records, not this document.

---

## 11. Naming and Evolution Rules

1. Package names use lowercase context or capability names.
2. Use-case interfaces end with `UseCase`.
3. Application implementations end with `Service`.
4. Persistence output ports use domain-oriented `Repository` names.
5. Infrastructure adapter names identify their technology or collaborating boundary.
6. JPA entities stay under infrastructure persistence and remain distinct from domain models.
7. REST DTOs and application commands must not become domain models.
8. Product-specific classes stay under `loan`.
9. A type enters `shared` only when multiple modules require the same technical abstraction and no bounded context owns it.
10. Create a package when it groups types with one owner or responsibility. File count alone is not a reason to create a package.
11. Do not create empty placeholder packages.
12. Update this document when a placement rule or module boundary changes. Routine file additions, removals, and renames stay in the Java source tree.
