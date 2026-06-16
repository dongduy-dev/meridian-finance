# Dependency Rules & Architecture Enforcement

> The MVP remains one backend and one database. Modules are bounded contexts inside the modular monolith, not separate services by default.

## Layer Dependency Rules (Within Each Module)

```mermaid
graph TB
    WEB["Adapter: Web (Controllers)"] --> APP["Application (Use Cases)"]
    PERSIST["Adapter: Persistence (JPA)"] --> APP
    CLIENT["Adapter: Client (REST/gRPC)"] --> APP
    APP --> DOMAIN["Domain (Entities, Ports, Services)"]
    
    WEB -.->|"Allowed: domain/port/in/ only"| DOMAIN
    PERSIST -.->|FORBIDDEN| WEB
    
    style DOMAIN fill:#e74c3c,color:#fff
    style APP fill:#f39c12,color:#fff
    style WEB fill:#3498db,color:#fff
    style PERSIST fill:#2ecc71,color:#fff
```

> Controllers MAY depend on `domain/port/in/` interfaces (use case ports). Controllers MUST NOT depend on `domain/model/`, `domain/service/`, or `domain/port/out/`.

| Rule | From | To | Allowed? |
|---|---|---|---|
| 1 | Domain | Anything | NO - Domain has ZERO outward dependencies |
| 2 | Application | Domain | YES |
| 3 | Application | Infrastructure | NO |
| 4 | Infrastructure (adapters) | Application | YES - (implements ports) |
| 5 | Infrastructure (adapters) | Domain | YES - (reads domain models for mapping) |
| 6 | Controller → Use Case | Via Port interface | YES |
| 7 | Controller → Entity directly | — | NO - Controllers use DTOs only |
| 8 | Application services → `CurrentUserProvider` | Shared application abstraction | YES |
| 9 | Domain/Application → Spring Security or JWT classes | — | NO - use shared abstractions instead |

### The Rule
> **Dependencies point inward.** Domain knows nothing about the outside world. Application knows Domain. Infrastructure knows both but implements contracts defined by inner layers.

`AuthenticatedUser` is a shared representation of the current actor. `CurrentUserProvider` is a shared application-level abstraction used by modules that need the current user.

---

## Module Communication Rules

- Modules should not directly access each other's internals.
- Cross-module interaction should happen through application services, ports, published interfaces, or Spring Modulith events where appropriate.
- Shared concepts should live in `shared`; product-specific policies must not leak into the top-level package structure.
- `shared` must not depend on any feature module.
- `identity` may depend on `shared`; customer, partner, loan, approval, document, audit, and notification may depend on `shared`.
- `shared/application/security` contains abstractions only. `identity/infrastructure/security` contains concrete Spring Security/JWT implementation.
- `JwtAuthFilter`, `JwtTokenProvider`, and Spring Security adapters belong to identity infrastructure.
- OCR integration should be treated as an external or infrastructure-facing capability behind a document/OCR port.
- Audit should record events without controlling the core workflow.

### Allowed Patterns

```java
// PATTERN 1: Sync — Call public port interface
// Loan module calling Customer module through a port
// loan/domain/port/out/CustomerQueryPort.java
public interface CustomerQueryPort {
    Optional<CustomerSummaryDto> findById(CustomerId id);
}

// customer/infrastructure/.../CustomerModuleAdapter.java
@Component
public class CustomerModuleAdapter implements CustomerQueryPort {
    private final CustomerQueryService customerQueryService; // Customer's own service
    // ...
}
```

```java
// PATTERN 2: Async — Spring ApplicationEvents
// loan/application/service/ReviewLoanService.java
eventPublisher.publishEvent(new LoanSentForApprovalEvent(loanId, customerId, productCode, recommendation));

// approval/infrastructure/listener/LoanEventListener.java
@Component
public class LoanEventListener {
    // @ApplicationModuleListener ensures the listener runs after the publishing transaction commits,
    // preventing listeners from seeing uncommitted data.
    @ApplicationModuleListener
    public void onLoanSentForApproval(LoanSentForApprovalEvent event) {
        approvalService.createApprovalDecisionWorkItem(event.loanId(), event.recommendation());
    }
}
```

```java
// PATTERN 3: Sync — Product-supporting data through clear ports
// loan/domain/port/out/PartnerEligibilityPort.java
public interface PartnerEligibilityPort {
    SalaryAdvanceEligibilityData verifyEmployee(PartnerEmployeeVerificationQuery query);
}

// loan/domain/port/out/DocumentReadinessPort.java
public interface DocumentReadinessPort {
    DocumentReadinessResult checkReadiness(LoanApplicationId loanApplicationId);
}
```

> Product-specific behavior belongs under the `loan` module through product policies and strategies. Partner data remains in `partner`; document and OCR behavior remains in `document` or behind document/OCR ports.

```java
// PATTERN 4: Current actor access through shared abstraction
// loan/application/service/SubmitLoanService.java
public class SubmitLoanService {
    private final CurrentUserProvider currentUserProvider;

    public void submit(...) {
        AuthenticatedUser actor = currentUserProvider.currentUser();
        // use actor.id() and role/permission data without depending on Spring Security
    }
}
```

> `SpringSecurityCurrentUserProvider` is an identity infrastructure adapter that implements `CurrentUserProvider` using Spring Security. `JwtAuthFilter` and `JwtTokenProvider` belong to identity because identity owns authentication, JWT, users, roles, refresh tokens, and RBAC.

### Forbidden Anti-Patterns

```java
// ANTI-PATTERN 1: Direct entity import across modules
// loan/application/service/LoanService.java
import com.meridian.platform.customer.domain.model.Customer; // FORBIDDEN!

// ANTI-PATTERN 2: Direct JPA repo access across modules
// loan/application/service/LoanService.java
@Autowired CustomerJpaRepository customerRepo; // FORBIDDEN!

// ANTI-PATTERN 3: Shared JPA entities
// Loan entity with @ManyToOne to Customer entity // FORBIDDEN!

// ANTI-PATTERN 4: Controller calling repository directly
// loan/infrastructure/web/LoanController.java
@Autowired LoanRepository loanRepo; // FORBIDDEN! Must go through use case port

// ANTI-PATTERN 5: Domain depending on Spring
// loan/domain/model/LoanApplication.java
@Entity @Table // FORBIDDEN in domain layer — JPA annotations go on infra JPA entities

// ANTI-PATTERN 6: Circular module dependency
// Loan → Customer AND Customer → Loan // FORBIDDEN! Use events to break cycles

// ANTI-PATTERN 7: Transaction spanning modules
@Transactional
public void crossModuleOperation() {
    loanService.approve(...);
    customerService.updateStatus(...); // FORBIDDEN! Different bounded context
}

// ANTI-PATTERN 8: Top-level modules for individual loan products
// com.meridian.platform.salaryadvance        // FORBIDDEN!
// com.meridian.platform.unsecuredloan        // FORBIDDEN!
// com.meridian.platform.collateralloan       // FORBIDDEN!

// ANTI-PATTERN 9: Product-specific behavior leaking outside Loan Core
// partner/application/service/SalaryAdvanceApprovalService.java // FORBIDDEN!
// Product policies and strategies belong under loan/domain/product or loan/application policy orchestration.

// ANTI-PATTERN 10: OCR integration called directly from Loan
// loan/infrastructure/client/OcrRestClientAdapter.java // FORBIDDEN!
// OCR integration is an external/infrastructure-facing capability behind a document/OCR port.

// ANTI-PATTERN 11: Audit controlling business workflow
// audit/application/service/AuditEventService.java calls loan.approve(...) // FORBIDDEN!
// Audit records events and history; it does not own business decision logic.

// ANTI-PATTERN 12: Shared importing feature module classes
// shared/infrastructure/config/SecurityConfig.java imports com.meridian.platform.identity.infrastructure.security.JwtAuthFilter // FORBIDDEN!
// shared must not depend on identity or any other feature module.

// ANTI-PATTERN 13: Spring Security/JWT classes in domain or application services
// loan/application/service/SubmitLoanService.java imports org.springframework.security.core.Authentication // FORBIDDEN!
// loan/domain/service/LoanEligibilityService.java imports io.jsonwebtoken.Claims // FORBIDDEN!
// Application code should use CurrentUserProvider; domain code should stay pure.

// ANTI-PATTERN 14: JWT implementation in shared
// shared/infrastructure/security/JwtTokenProvider.java // FORBIDDEN!
// shared/application/security contains abstractions only.
```

---

## Enforcement Strategies

### 1. Spring Modulith Verification (Primary)

```java
@Test
void verifiesModularStructure() {
    ApplicationModules.of(MeridianPlatformApplication.class).verify();
    // Automatically detects: cycle dependencies, illegal cross-module access
}
```

### 2. ArchUnit Rules (Supplementary)

```java
@AnalyzeClasses(packages = "com.meridian.platform")
class ArchitectureRulesTest {

    // Rule: Domain layer must not depend on Spring
    @ArchTest
    static final ArchRule domainMustNotDependOnSpring =
        noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.springframework..");

    // Rule: Domain and application code must not depend on Spring Security or JWT implementation classes
    @ArchTest
    static final ArchRule domainAndApplicationMustNotDependOnSecurityImplementations =
        noClasses().that().resideInAnyPackage("..domain..", "..application..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "org.springframework.security..",
                "io.jsonwebtoken..",
                "com.auth0.jwt.."
            )
            .because("Use shared CurrentUserProvider/AuthenticatedUser abstractions outside identity infrastructure");

    // Rule: Domain models must not depend on JPA
    @ArchTest
    static final ArchRule domainModelMustNotUseJpa =
        noClasses().that().resideInAPackage("..domain.model..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("jakarta.persistence..")
            .because("Domain models must not depend on JPA — use JPA entities in infrastructure");

    // Rule: Domain services must not use Spring annotations
    @ArchTest
    static final ArchRule domainServicesMustBePureJava =
        noClasses().that().resideInAPackage("..domain.service..")
            .should().beAnnotatedWith("org.springframework.stereotype.Service")
            .orShould().beAnnotatedWith("org.springframework.transaction.annotation.Transactional")
            .because("Domain services must be pure Java — Spring annotations belong in application layer");

    // Rule: Domain must not depend on infrastructure
    @ArchTest
    static final ArchRule domainMustNotDependOnInfra =
        noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAPackage("..infrastructure..");

    // Rule: Application must not depend on infrastructure
    @ArchTest
    static final ArchRule applicationMustNotDependOnInfra =
        noClasses().that().resideInAPackage("..application..")
            .should().dependOnClassesThat()
            .resideInAPackage("..infrastructure..");

    // Rule: Shared module must not depend on feature modules
    @ArchTest
    static final ArchRule sharedMustNotDependOnFeatureModules =
        noClasses().that().resideInAPackage("com.meridian.platform.shared..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "com.meridian.platform.identity..",
                "com.meridian.platform.customer..",
                "com.meridian.platform.partner..",
                "com.meridian.platform.loan..",
                "com.meridian.platform.approval..",
                "com.meridian.platform.document..",
                "com.meridian.platform.audit..",
                "com.meridian.platform.notification.."
            );

    // Rule: Controllers must only access use case ports (+ security + OpenAPI annotations)
    @ArchTest
    static final ArchRule controllersMustUsePortsOnly =
        classes().that().resideInAPackage("..adapter.in.web..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage(
                "..application.dto..", "..domain.port.in..",
                "..application.mapper..",
                "..shared..", "java..", "jakarta..",
                "org.springframework.web..", "org.springframework.http..",
                "org.springframework.security.access.prepost..",  // @PreAuthorize
                "org.springframework.security.core..",             // Authentication
                "io.swagger.v3.oas.annotations.."                  // OpenAPI docs
            );

    // Rule: No circular dependencies between modules
    @ArchTest
    static final ArchRule noCyclicDependencies =
        slices().matching("com.meridian.platform.(*)..")
            .should().beFreeOfCycles();

    // Rule: Product-specific policies must stay inside the Loan module
    @ArchTest
    static final ArchRule noTopLevelProductModules =
        noClasses().should().resideInAnyPackage(
            "com.meridian.platform.salaryadvance..",
            "com.meridian.platform.unsecuredloan..",
            "com.meridian.platform.collateralloan.."
        );

    // Rule: Prevent SQL injection via string concatenation in persistence layer
    @ArchTest
    static final ArchRule noNativeQueryStringConcat =
        noClasses().that().resideInAPackage("..persistence..")
            .should().callMethod(String.class, "concat", String.class)
            .orShould().callMethod(StringBuilder.class, "append", String.class)
            .because("Repository classes must not build queries via string concatenation");

    // Rule: Enforce Spring Boot 4 @MockitoBean over deprecated @MockBean
    @ArchTest
    static final ArchRule enforceModernMockitoBean =
        noClasses().should().beAnnotatedWith("org.springframework.boot.test.mock.mockito.MockBean")
            .because("Use @MockitoBean from org.springframework.test.context.bean.override.mockito in Spring Boot 4.0");

    // Rule: Enforce JUnit 5 over JUnit 4
    @ArchTest
    static final ArchRule enforceJUnit5 =
        noMethods().should().beAnnotatedWith("org.junit.Test")
            .because("Use org.junit.jupiter.api.Test (JUnit 5) instead of JUnit 4");
}
```

### 3. CI Pipeline Enforcement

```yaml
# .github/workflows/ci.yml
- name: Architecture Tests
  run: mvn test -Dtest="ArchitectureRulesTest,ModulithStructureTest"
  # Fail the build if any architectural rule is violated
```

### 4. Package Visibility (Java Modules)

Use `package-info.java` with Spring Modulith `@ApplicationModule` to control what each module exposes:

```java
// shared/package-info.java — shared kernel and application abstractions only
@org.springframework.modulith.ApplicationModule(
    allowedDependencies = {}
)
package com.meridian.platform.shared;

// loan/package-info.java
@org.springframework.modulith.ApplicationModule(
    allowedDependencies = {"customer::public", "partner::public", "document::public", "shared"}
)
package com.meridian.platform.loan;

// approval/package-info.java — receives Loan workflow events and publishes decisions
@org.springframework.modulith.ApplicationModule(
    allowedDependencies = {"loan::events", "shared"}
)
package com.meridian.platform.approval;

// identity/package-info.java
@org.springframework.modulith.ApplicationModule(
    allowedDependencies = {"shared"}
)
package com.meridian.platform.identity;

// customer/package-info.java
@org.springframework.modulith.ApplicationModule(
    allowedDependencies = {"shared"}
)
package com.meridian.platform.customer;

// partner/package-info.java
@org.springframework.modulith.ApplicationModule(
    allowedDependencies = {"shared"}
)
package com.meridian.platform.partner;

// document/package-info.java
@org.springframework.modulith.ApplicationModule(
    allowedDependencies = {"shared"}
)
package com.meridian.platform.document;

// audit/package-info.java — receives events via @ApplicationModuleListener (no explicit dependency)
@org.springframework.modulith.ApplicationModule(
    allowedDependencies = {"shared"}
)
package com.meridian.platform.audit;

// notification/package-info.java — optional later
@org.springframework.modulith.ApplicationModule(
    allowedDependencies = {"shared"}
)
package com.meridian.platform.notification;
```

> The `audit` module consumes events from ALL modules via `@ApplicationModuleListener`. Spring Modulith routes events without requiring explicit `allowedDependencies` declarations for event sources.

---

### Logging Rule: No PII in Log Statements

```java
// FORBIDDEN — PII in logs
log.info("Customer registered: {}", customer.getNationalId());
log.info("Processing loan for {}", customer.getFullName());

// CORRECT — Use IDs only, never PII
log.info("Customer registered", kv("customerId", customer.getId()));
log.info("Processing loan", kv("loanId", loanId), kv("customerId", customerId));
```

---

## Summary Matrix

| Source Module | Can Call (Sync) | Can Listen (Async) | Cannot Access |
|---|---|---|---|
| **Shared** | — | — | All feature modules, including Identity |
| **Loan** | Customer, Partner, Document | — | Approval internals, IAM internals, top-level product modules |
| **Approval** | — (no sync calls) | LoanSentForApprovalEvent | Customer, Partner, Document, Loan internals |
| **Customer** | — | — | Loan internals, Partner internals |
| **Partner** | — | — | Loan internals, Customer internals |
| **Document** | — | — | Loan internals, Customer, Partner |
| **Audit** | — | Business/domain events | All module internals, business decision logic |
| **Notification** | — | Future notification events | All module internals; optional later |
| **IAM** | Shared | — | All business modules |


> **Approval receives all needed data from Loan workflow events** (loan amount, product, customer, Loan Officer recommendation). It never calls Loan synchronously, eliminating bidirectional coupling.

> **Audit receives business events and records immutable history.** It does not approve, reject, disburse, calculate eligibility, or otherwise control the core workflow.

> **Current user access flows through shared abstractions.** Application services may depend on `CurrentUserProvider`; concrete Spring Security and JWT implementation stays in `identity/infrastructure/security`.
