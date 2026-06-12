# Phase 1 — Java Package Structure

## Root Package Structure

```
com.lending.platform/
├── LendingPlatformApplication.java
│
├── shared/                          # Shared kernel — minimal, guarded
│   ├── domain/
│   │   ├── model/
│   │   │   ├── DomainModel.java          # Pure Java: UUID id, timestamps (optional base)
│   │   │   ├── Money.java               # Value object for monetary amounts
│   │   │   ├── NationalId.java          # Value object (CCCD/CMND)
│   │   │   ├── EmailAddress.java        # Value object
│   │   │   ├── PhoneNumber.java         # Value object
│   │   │   ├── UserId.java              # Value object
│   │   │   └── DomainEvent.java         # Marker interface
│   │   └── exception/
│   │       ├── DomainException.java
│   │       └── EntityNotFoundException.java
│   ├── application/
│   │   └── IdempotencyService.java      # Cross-cutting idempotency
│   └── infrastructure/
│       ├── config/
│       │   ├── SecurityConfig.java      # Auth + public/private split ONLY (no role checks)
│       │   ├── JacksonConfig.java
│       │   └── FlywayConfig.java
│       ├── security/
│       │   ├── JwtAuthFilter.java
│       │   ├── JwtTokenProvider.java
│       │   └── AuthenticatedUser.java
│       ├── persistence/
│       │   ├── BaseJpaEntity.java        # @MappedSuperclass: id, createdAt, updatedAt
│       │   └── IdempotencyRepository.java
│       └── web/
│           ├── GlobalExceptionHandler.java
│           └── ApiResponse.java
│
├── identity/                        # ── IAM Module ──
│   ├── domain/
│   │   ├── model/
│   │   │   ├── User.java
│   │   │   ├── Role.java
│   │   │   └── UserStatus.java
│   │   └── port/
│   │       ├── in/
│   │       │   ├── AuthenticationUseCase.java
│   │       │   └── UserManagementUseCase.java
│   │       └── out/
│   │           ├── UserRepository.java
│   │           └── RefreshTokenRepository.java
│   ├── application/
│   │   ├── service/
│   │   │   ├── AuthenticationService.java
│   │   │   └── UserManagementService.java
│   │   ├── dto/
│   │   │   ├── LoginRequest.java
│   │   │   ├── RegisterRequest.java
│   │   │   └── AuthResponse.java
│   │   └── mapper/
│   │       └── UserMapper.java
│   └── infrastructure/
│       ├── adapter/
│       │   ├── in/
│       │   │   └── web/
│       │   │       └── AuthController.java
│       │   └── out/
│       │       └── persistence/
│       │           ├── JpaUserRepository.java
│       │           ├── UserJpaEntity.java    # JPA entity (infra concern)
│       │           ├── JpaRefreshTokenRepository.java
│       │           └── RefreshTokenJpaEntity.java
│       └── config/
│           └── IdentityModuleConfig.java
│
├── customer/                        # ── Customer Module ──
│   ├── domain/ ...                  # (same hexagonal structure)
│   ├── application/ ...
│   └── infrastructure/ ...
│
├── loan/                            # ── Loan Module (FULL HEXAGONAL) ──
│   ├── domain/
│   │   ├── model/
│   │   │   ├── LoanApplication.java     # Aggregate root (rich model)
│   │   │   ├── LoanProduct.java
│   │   │   ├── LoanStatus.java
│   │   │   ├── RepaymentSchedule.java
│   │   │   └── StatusTransition.java
│   │   ├── port/
│   │   │   ├── in/
│   │   │   │   ├── SubmitLoanUseCase.java
│   │   │   │   ├── ReviewLoanUseCase.java
│   │   │   │   ├── QueryLoanUseCase.java
│   │   │   │   ├── ManageLoanProductUseCase.java  # CRUD for loan products
│   │   │   │   ├── QueryLoanProductUseCase.java   # Read-only loan product queries
│   │   │   │   └── command/
│   │   │   │       ├── SubmitLoanCommand.java      # Pure Java record (domain layer)
│   │   │   │       ├── ReviewLoanCommand.java
│   │   │   │       ├── CreateLoanProductCommand.java
│   │   │   │       └── UpdateLoanProductCommand.java
│   │   │   └── out/
│   │   │       ├── LoanRepository.java
│   │   │       ├── LoanProductRepository.java     # CRUD for loan products
│   │   │       ├── CustomerQueryPort.java         # To call Customer module
│   │   │       └── LoanEventPublisher.java
│   │   ├── service/
│   │   │   ├── LoanEligibilityService.java  # Domain service (PURE JAVA — no @Service)
│   │   │   └── EirCalculationService.java   # Domain service for SBV math (PURE JAVA — no @Service)
│   │   └── event/
│   │       ├── LoanSubmittedEvent.java       # Carries: loanId, customerId, productId, amount
│   │       ├── LoanReviewStartedEvent.java
│   │       ├── LoanSentForApprovalEvent.java
│   │       ├── LoanApprovedEvent.java
│   │       ├── LoanRejectedEvent.java
│   │       ├── LoanCancelledEvent.java
│   │       ├── LoanDisbursedEvent.java
│   │       └── LoanCompletedEvent.java
│   ├── application/
│   │   ├── service/
│   │   │   ├── SubmitLoanService.java       # Implements SubmitLoanUseCase
│   │   │   ├── ReviewLoanService.java
│   │   │   └── QueryLoanService.java
│   │   ├── dto/
│   │   │   ├── CreateLoanRequest.java        # Inbound REST request (raw primitives)
│   │   │   ├── LoanApplicationDto.java       # Outbound response DTO
│   │   │   └── LoanSummaryDto.java
│   │   └── mapper/
│   │       └── LoanMapper.java               # Domain ↔ DTO mapping (lives here, NOT in domain)
│   └── infrastructure/
│       ├── adapter/
│       │   ├── in/
│       │   │   └── web/
│       │   │       ├── LoanController.java
│       │   │       └── LoanProductController.java
│       │   └── out/
│       │       ├── persistence/
│       │       │   ├── JpaLoanRepository.java
│       │       │   ├── JpaLoanProductRepository.java
│       │       │   ├── LoanJpaEntity.java
│       │       │   └── LoanProductJpaEntity.java
│       │       ├── client/
│       │       │   └── CustomerModuleAdapter.java
│       │       └── event/
│       │           └── SpringLoanEventPublisher.java
│       └── config/
│           └── LoanModuleConfig.java
│
├── approval/                        # ── Approval Module (FULL HEXAGONAL) ──
│   ├── domain/
│   │   ├── model/
│   │   │   ├── ApprovalRequest.java      # Aggregate root
│   │   │   ├── ApprovalStep.java
│   │   │   ├── ApprovalRule.java
│   │   │   └── ApprovalStatus.java
│   │   ├── port/
│   │   │   ├── in/
│   │   │   │   ├── CreateApprovalUseCase.java
│   │   │   │   ├── SubmitDecisionUseCase.java
│   │   │   │   └── QueryApprovalUseCase.java
│   │   │   └── out/
│   │   │       ├── ApprovalRepository.java
│   │   │       └── ApprovalEventPublisher.java
│   │   └── event/
│   │       ├── ApprovalCompletedEvent.java
│   │       └── ApprovalPendingEvent.java
│   ├── application/
│   │   ├── service/
│   │   │   ├── CreateApprovalService.java
│   │   │   ├── SubmitDecisionService.java
│   │   │   └── QueryApprovalService.java
│   │   ├── dto/
│   │   │   ├── ApprovalRequestDto.java
│   │   │   └── SubmitDecisionRequest.java
│   │   └── mapper/
│   │       └── ApprovalMapper.java
│   └── infrastructure/
│       ├── adapter/
│       │   ├── in/
│       │   │   └── web/
│       │   │       └── ApprovalController.java
│       │   └── out/
│       │       ├── persistence/
│       │       │   ├── JpaApprovalRepository.java
│       │       │   └── ApprovalJpaEntity.java
│       │       └── event/
│       │           └── SpringApprovalEventPublisher.java
│       ├── listener/
│       │   └── LoanEventListener.java    # @ApplicationModuleListener for LoanSubmittedEvent
│       └── config/
│           └── ApprovalModuleConfig.java
│
├── document/                        # ── Document Module (MODERATE HEXAGONAL) ──
│   ├── domain/
│   │   ├── model/
│   │   │   ├── Document.java             # Aggregate root
│   │   │   ├── StorageReference.java     # Value object
│   │   │   └── DocumentType.java         # Enum
│   │   └── port/
│   │       ├── in/
│   │       │   ├── UploadDocumentUseCase.java
│   │       │   ├── QueryDocumentUseCase.java
│   │       │   └── DownloadDocumentUseCase.java
│   │       └── out/
│   │           ├── DocumentRepository.java
│   │           ├── FileStoragePort.java   # Abstraction for local/S3
│   │           └── OcrProcessingPort.java # Thin REST client to Python service
│   ├── application/
│   │   ├── service/
│   │   │   ├── UploadDocumentService.java
│   │   │   ├── QueryDocumentService.java
│   │   │   └── DownloadDocumentService.java
│   │   ├── dto/
│   │   │   ├── DocumentDto.java
│   │   │   └── UploadDocumentRequest.java
│   │   └── mapper/
│   │       └── DocumentMapper.java
│   └── infrastructure/
│       ├── adapter/
│       │   ├── in/
│       │   │   └── web/
│       │   │       └── DocumentController.java
│       │   └── out/
│       │       ├── persistence/
│       │       │   ├── JpaDocumentRepository.java
│       │       │   └── DocumentJpaEntity.java
│       │       ├── storage/
│       │       │   └── LocalFileStorageAdapter.java  # Implements FileStoragePort
│       │       └── client/
│       │           └── OcrRestClientAdapter.java     # Implements OcrProcessingPort
│       └── config/
│           └── DocumentModuleConfig.java
│
└── audit/                           # ── Audit Module (SIMPLIFIED) ──
    ├── domain/
    │   ├── model/
    │   │   └── AuditEvent.java
    │   └── port/
    │       └── in/
    │           └── QueryAuditUseCase.java   # Read-only audit log queries
    ├── application/
    │   └── service/
    │       └── AuditEventService.java
    └── infrastructure/
        ├── listener/
        │   └── DomainEventAuditListener.java  # Uses @ApplicationModuleListener (not @EventListener)
        ├── persistence/
        │   └── JpaAuditEventRepository.java
        └── web/
            └── AuditController.java          # Calls QueryAuditUseCase
```

---

## When to Apply Full vs. Simplified Hexagonal

| Module | Pattern | Why |
|---|---|---|
| **Loan Origination** | Full Hexagonal | Core domain. Complex state machine. Must be framework-independent and testable. |
| **Approval Workflow** | Full Hexagonal | Core domain. Independent state machine. Complex rules. |
| **Identity & Access** | Full Hexagonal | Security-critical. Will be first microservice extraction candidate. |
| **Customer** | Moderate | Supporting domain. Use ports for external-facing interfaces only. |
| **Document** | Moderate | Storage abstraction justifies ports (local → S3 migration). |
| **Audit** | Simplified | Cross-cutting concern. Simple append-only writes. No complex domain logic. |
| **Notification** | Simplified | Generic subdomain. Template-based, minimal logic. |

---

## Testing Pyramid Strategy

1. **Domain Unit Tests (70%)**: Pure Java, zero Spring dependencies. Fast. Tests core state machines, value objects, and domain services.
2. **Application Layer Tests (15%)**: Tests use cases and transaction boundaries using `@ExtendWith(SpringExtension.class)` and `@MockitoBean` to mock out ports.
3. **Data/Adapter Integration Tests (10%)**: Tests `JpaRepository` implementations and Flyway migrations against a real PostgreSQL instance using Testcontainers (`@DataJpaTest` + `@AutoConfigureTestDatabase(replace = Replace.NONE)`).
4. **Module & E2E Tests (5%)**: Tests cross-module interactions using Spring Modulith's `@ApplicationModuleTest` and full REST API testing using `MockMvc` or `TestRestTemplate`.
