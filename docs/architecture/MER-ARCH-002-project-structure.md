# Java Package Structure

## Root Package Structure

```
com.meridian.platform/
├── MeridianPlatformApplication.java
│
├── shared/                          # Shared kernel — common abstractions, base types, cross-cutting support
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
│   │   ├── IdempotencyService.java      # Cross-cutting idempotency
│   │   └── security/
│   │       ├── AuthenticatedUser.java    # Shared current actor representation
│   │       └── CurrentUserProvider.java  # Application-level abstraction
│   └── infrastructure/
│       ├── config/
│       │   ├── JacksonConfig.java
│       │   └── FlywayConfig.java
│       ├── persistence/
│       │   ├── BaseJpaEntity.java        # @MappedSuperclass: id, createdAt, updatedAt
│       │   └── IdempotencyRepository.java
│       └── web/
│           ├── HealthController.java
│           ├── GlobalExceptionHandler.java
│           └── ApiErrorResponse.java
│
├── identity/                        # ── IAM Module ──
│   ├── domain/
│   │   ├── model/
│   │   │   ├── User.java
│   │   │   ├── Role.java
│   │   │   ├── Permission.java
│   │   │   ├── RefreshToken.java
│   │   │   └── UserStatus.java
│   │   ├── service/
│   │   │   └── RolePermissionPolicy.java
│   │   ├── event/
│   │   │   ├── UserRegisteredEvent.java
│   │   │   └── UserSuspendedEvent.java
│   │   └── exception/
│   │       └── AuthenticationException.java
│   ├── application/
│   │   ├── port/
│   │   │   ├── in/
│   │   │   │   ├── AuthenticationUseCase.java
│   │   │   │   └── UserManagementUseCase.java
│   │   │   └── out/
│   │   │       ├── UserRepository.java
│   │   │       ├── RefreshTokenRepository.java
│   │   │       └── TokenIssuerPort.java
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
│       ├── security/
│       │   ├── JwtAuthenticationFilter.java
│       │   ├── JwtTokenService.java
│       │   ├── SpringSecurityCurrentUserProvider.java  # Implements shared CurrentUserProvider
│       │   ├── RolePermissionRegistry.java
│       │   └── SecurityConfig.java       # Wires Spring Security, JWT, and identity auth
│       └── config/
│           └── IdentityModuleConfig.java
│
├── customer/                        # ── Customer Module ──
│   ├── domain/
│   │   ├── model/
│   │   │   ├── Customer.java
│   │   │   ├── CustomerProfile.java
│   │   │   ├── CustomerBankAccount.java
│   │   │   ├── CustomerStatus.java
│   │   │   ├── ProfileCompletionStatus.java
│   │   │   └── VerificationStatus.java
│   │   ├── service/
│   │   │   └── CustomerVerificationPolicy.java
│   │   ├── event/
│   │   │   ├── CustomerVerifiedEvent.java
│   │   │   └── CustomerProfileUpdatedEvent.java
│   │   └── exception/
│   │       └── CustomerDomainException.java
│   ├── application/
│   │   ├── port/
│   │   │   ├── in/
│   │   │   │   ├── ManageCustomerProfileUseCase.java
│   │   │   │   └── QueryCustomerUseCase.java
│   │   │   └── out/
│   │   │       └── CustomerRepository.java
│   │   ├── service/ ...             # Profile completeness, bank-account lifecycle, sensitive data handling
│   │   ├── dto/
│   │   └── mapper/
│   └── infrastructure/ ...
│
├── partner/                         # ── Partner Module ──
│   ├── domain/
│   │   ├── model/
│   │   │   ├── PartnerCompany.java
│   │   │   ├── PartnerEmployee.java
│   │   │   ├── PartnerEmployeeImportBatch.java
│   │   │   ├── CustomerPartnerEmployeeLink.java
│   │   │   ├── CustomerPartnerEmployeeLinkStatus.java
│   │   │   └── EmployeeEligibilityData.java
│   │   ├── service/
│   │   │   └── EmployeeEligibilityPolicy.java
│   │   ├── event/
│   │   │   ├── PartnerCompanyActivatedEvent.java
│   │   │   ├── PartnerEmployeeImportCompletedEvent.java
│   │   │   └── CustomerPartnerEmployeeLinkedEvent.java
│   │   └── exception/
│   │       └── PartnerDomainException.java
│   ├── application/
│   │   ├── port/
│   │   │   ├── in/
│   │   │   │   ├── ManagePartnerCompanyUseCase.java
│   │   │   │   ├── ImportPartnerEmployeesUseCase.java
│   │   │   │   ├── ManageCustomerEmployeeLinkUseCase.java
│   │   │   │   └── VerifyPartnerEmployeeUseCase.java
│   │   │   └── out/
│   │   │       ├── PartnerCompanyRepository.java
│   │   │       ├── PartnerEmployeeRepository.java
│   │   │       └── CustomerPartnerEmployeeLinkRepository.java
│   │   ├── service/ ...             # Partner Companies, Partner Employees, import batches, reusable Salary Advance employee links
│   │   ├── dto/
│   │   └── mapper/
│   └── infrastructure/ ...
│
├── loan/                            # ── Loan Module (FULL HEXAGONAL) ──
│   ├── domain/
│   │   ├── model/
│   │   │   ├── LoanApplication.java     # Aggregate root (common workflow)
│   │   │   ├── LoanProduct.java
│   │   │   ├── LoanProductPolicy.java
│   │   │   ├── SalaryAdvanceLimit.java
│   │   │   ├── SalaryAdvanceLimitMovement.java
│   │   │   ├── SalaryAdvanceVerification.java
│   │   │   ├── LoanApplicationStatusTransition.java
│   │   │   ├── LoanAccount.java
│   │   │   ├── LoanStatus.java
│   │   │   ├── OfferTerms.java
│   │   │   ├── DisbursementRecord.java
│   │   │   └── RepaymentSchedule.java
│   │   ├── product/                    # Product policies/strategies; no top-level product modules
│   │   │   ├── LoanProductStrategy.java
│   │   │   ├── SalaryAdvancePolicy.java
│   │   │   ├── SalaryAdvanceLimitPolicy.java
│   │   │   ├── UnsecuredConsumerLoanPolicy.java
│   │   │   └── CollateralLoanPolicy.java
│   │   ├── service/
│   │   │   ├── LoanEligibilityService.java       # Domain service (PURE JAVA — no @Service)
│   │   │   ├── LoanProductPolicyService.java     # Selects product policy/strategy
│   │   │   ├── SalaryAdvanceLimitService.java    # Reserves, releases, refreshes, suspends, disables limits
│   │   │   └── RepaymentScheduleService.java     # Domain service (PURE JAVA — no @Service)
│   │   ├── event/
│   │   │   ├── LoanSubmittedEvent.java       # Carries: loanId, customerId, productId, amount
│   │   │   ├── LoanReviewStartedEvent.java
│   │   │   ├── LoanSentForApprovalEvent.java
│   │   │   ├── LoanApprovedEvent.java
│   │   │   ├── LoanRejectedEvent.java
│   │   │   ├── LoanCancelledEvent.java
│   │   │   ├── LoanDisbursedEvent.java
│   │   │   └── LoanCompletedEvent.java
│   │   └── exception/
│   │       └── LoanDomainException.java
│   ├── application/
│   │   ├── port/
│   │   │   ├── in/
│   │   │   │   ├── SubmitLoanUseCase.java
│   │   │   │   ├── ReviewLoanUseCase.java
│   │   │   │   ├── AcceptOfferUseCase.java
│   │   │   │   ├── ConfirmDisbursementUseCase.java
│   │   │   │   ├── QueryLoanUseCase.java
│   │   │   │   ├── QuerySalaryAdvanceLimitUseCase.java
│   │   │   │   ├── StartSalaryAdvanceApplicationUseCase.java
│   │   │   │   ├── ManageLoanProductUseCase.java  # CRUD for loan products
│   │   │   │   ├── QueryLoanProductUseCase.java   # Read-only loan product queries
│   │   │   │   └── command/
│   │   │   │       ├── SubmitLoanCommand.java      # Use-case command record
│   │   │   │       ├── ReviewLoanCommand.java
│   │   │   │       ├── CreateLoanProductCommand.java
│   │   │   │       └── UpdateLoanProductCommand.java
│   │   │   └── out/
│   │   │       ├── LoanRepository.java             # Returns domain objects, not DTOs
│   │   │       ├── SalaryAdvanceLimitRepository.java
│   │   │       ├── LoanProductRepository.java      # CRUD for loan products
│   │   │       ├── CustomerQueryPort.java          # To call Customer module
│   │   │       ├── PartnerEligibilityPort.java     # To call Partner module for employee links and eligibility checks
│   │   │       ├── DocumentReadinessPort.java      # To call Document module for checklist/readiness
│   │   │       └── LoanEventPublisher.java
│   │   ├── service/
│   │   │   ├── SubmitLoanService.java       # Implements SubmitLoanUseCase
│   │   │   ├── ReviewLoanService.java
│   │   │   ├── AcceptOfferService.java
│   │   │   ├── ConfirmDisbursementService.java
│   │   │   ├── QuerySalaryAdvanceLimitService.java
│   │   │   ├── StartSalaryAdvanceApplicationService.java
│   │   │   └── QueryLoanService.java
│   │   ├── dto/
│   │   │   ├── CreateLoanRequest.java        # Inbound REST request (raw primitives)
│   │   │   ├── LoanApplicationDto.java       # Outbound response DTO
│   │   │   ├── SalaryAdvanceDashboardDto.java
│   │   │   └── LoanSummaryDto.java
│   │   └── mapper/
│   │       └── LoanMapper.java               # Domain ↔ DTO mapping (lives here, NOT in domain)
│   └── infrastructure/
│       ├── adapter/
│       │   ├── in/
│       │   │   └── web/
│       │   │       ├── LoanController.java
│       │   │       ├── SalaryAdvanceLimitController.java
│       │   │       └── LoanProductController.java
│       │   └── out/
│       │       ├── persistence/
│       │       │   ├── JpaLoanRepository.java
│       │       │   ├── JpaLoanProductRepository.java
│       │       │   ├── JpaSalaryAdvanceLimitRepository.java
│       │       │   ├── LoanJpaEntity.java
│       │       │   ├── LoanProductJpaEntity.java
│       │       │   ├── SalaryAdvanceLimitJpaEntity.java
│       │       │   ├── SalaryAdvanceLimitMovementJpaEntity.java
│       │       │   └── SalaryAdvanceVerificationJpaEntity.java
│       │       ├── client/
│       │       │   ├── CustomerModuleAdapter.java
│       │       │   ├── PartnerModuleAdapter.java
│       │       │   └── DocumentModuleAdapter.java
│       │       └── event/
│       │           └── SpringLoanEventPublisher.java
│       └── config/
│           └── LoanModuleConfig.java
│
├── approval/                        # ── Approval Module (FULL HEXAGONAL) ──
│   ├── domain/
│   │   ├── model/
│   │   │   ├── ReviewRecommendation.java
│   │   │   ├── ApprovalDecision.java
│   │   │   ├── MakerCheckerControl.java
│   │   │   └── ApprovalStatus.java
│   │   ├── service/
│   │   │   └── MakerCheckerPolicyService.java
│   │   ├── event/
│   │   │   ├── LoanReviewRecommendedEvent.java
│   │   │   └── ApprovalDecisionRecordedEvent.java
│   │   └── exception/
│   │       └── ApprovalDomainException.java
│   ├── application/
│   │   ├── port/
│   │   │   ├── in/
│   │   │   │   ├── CreateApprovalUseCase.java
│   │   │   │   ├── SubmitReviewRecommendationUseCase.java
│   │   │   │   ├── SubmitDecisionUseCase.java
│   │   │   │   └── QueryApprovalUseCase.java
│   │   │   └── out/
│   │   │       ├── ApprovalRepository.java
│   │   │       └── ApprovalEventPublisher.java
│   │   ├── service/
│   │   │   ├── CreateApprovalService.java
│   │   │   ├── SubmitReviewRecommendationService.java
│   │   │   ├── SubmitDecisionService.java
│   │   │   └── QueryApprovalService.java
│   │   ├── dto/
│   │   │   ├── ReviewRecommendationDto.java
│   │   │   ├── ApprovalDecisionDto.java
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
│       │   └── LoanEventListener.java    # Inbound event adapter; synchronous @EventListener when rollback semantics are required
│       └── config/
│           └── ApprovalModuleConfig.java
│
├── document/                        # ── Document Module (MODERATE HEXAGONAL) ──
│   ├── domain/
│   │   ├── model/
│   │   │   ├── Document.java             # Aggregate root
│   │   │   ├── DocumentChecklist.java
│   │   │   ├── DocumentChecklistItem.java
│   │   │   ├── DocumentReview.java
│   │   │   ├── DocumentReplacementRequest.java
│   │   │   ├── DocumentWaiver.java
│   │   │   ├── DocumentReadiness.java
│   │   │   ├── OcrJob.java
│   │   │   ├── OcrResult.java
│   │   │   ├── StorageReference.java     # Value object
│   │   │   └── DocumentType.java         # Enum
│   │   ├── service/
│   │   │   └── DocumentReadinessPolicy.java
│   │   ├── event/
│   │   │   ├── DocumentUploadedEvent.java
│   │   │   ├── DocumentReviewedEvent.java
│   │   │   └── DocumentChecklistReadyEvent.java
│   │   └── exception/
│   │       └── DocumentDomainException.java
│   ├── application/
│   │   ├── port/
│   │   │   ├── in/
│   │   │   │   ├── UploadDocumentUseCase.java
│   │   │   │   ├── ReviewDocumentUseCase.java
│   │   │   │   ├── ManageDocumentChecklistUseCase.java
│   │   │   │   ├── QueryDocumentUseCase.java
│   │   │   │   └── DownloadDocumentUseCase.java
│   │   │   └── out/
│   │   │       ├── DocumentRepository.java
│   │   │       ├── FileStoragePort.java   # Abstraction for local/S3
│   │   │       └── OcrProcessingPort.java # Thin REST client to Python service
│   │   ├── service/
│   │   │   ├── UploadDocumentService.java
│   │   │   ├── ReviewDocumentService.java
│   │   │   ├── ManageDocumentChecklistService.java
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
├── audit/                           # ── Audit Module (SIMPLIFIED) ──
│   ├── domain/
│   │   ├── model/
│   │   │   └── AuditEvent.java
│   │   └── exception/
│   │       └── AuditDomainException.java
│   ├── application/
│   │   ├── port/
│   │   │   ├── in/
│   │   │   │   └── RecordAuditEventsUseCase.java # Synchronous append-only recording
│   │   │   └── out/
│   │   │       └── AuditEventRepository.java
│   │   ├── service/
│   │   │   └── RecordAuditEventsService.java # Records events; does not control workflow decisions
│   │   ├── dto/
│   │   └── mapper/
│   └── infrastructure/
│       └── adapter/
│           ├── in/
│           │   ├── event/
│           │   │   └── BusinessAuditEventListener.java # Terminal synchronous @EventListener consumer
│           │   └── web/
│           └── out/
│               └── persistence/
│                   └── JpaAuditEventRepository.java
│
└── notification/                    # ── Notification Module (OPTIONAL LATER) ──
    ├── domain/
    │   ├── model/
    │   │   ├── Notification.java
    │   │   └── NotificationTemplate.java
    │   ├── event/
    │   └── exception/
    ├── application/
    │   ├── port/
    │   │   ├── in/
    │   │   │   └── QueryNotificationUseCase.java
    │   │   └── out/
    │   │       └── NotificationSenderPort.java
    │   ├── service/
    │   ├── dto/
    │   └── mapper/
    └── infrastructure/
        └── adapter/
            ├── in/
            │   └── event/
            └── out/
                └── client/
```

Salary Advance remains inside the generic lending architecture. `partner/` owns Partner Companies, Partner Employees, import batches, and reusable customer employee links. `loan/` owns Salary Advance limit state, limit movements, application reservation/release behavior, and application-level Salary Advance verification snapshots. Do not create top-level modules named `salaryadvance`, `unsecuredloan`, or `collateralloan`.

---

## When to Apply Full vs. Simplified Hexagonal

| Module | Pattern | Why |
|---|---|---|
| **Loan Core / Origination** | Full Hexagonal | Core domain. Generic lending core, product policies/strategies, and complex state machine. |
| **Approval Workflow** | Full Hexagonal | Core domain. Loan Officer review, Approver decision, maker-checker controls. |
| **Identity & Access** | Full Hexagonal | Security-critical. Owns users, roles, JWT, refresh tokens, and RBAC. |
| **Customer** | Moderate | Supporting domain. Owns profile, verification status, bank-account information, and sensitive data handling. Identity still owns the users.customer_id login mapping. |
| **Partner** | Moderate | Supporting domain. Owns Partner Companies, Partner Employees, import batches, and reusable Salary Advance employee links. |
| **Document** | Moderate | Checklist, manual review, replacement, waiver, readiness, storage, and OCR-assisted processing justify ports. |
| **Audit** | Simplified | Cross-cutting concern. Append-only writes for approved workflow actions. No read API or workflow-control logic in the current checkpoint. |
| **Notification** | Simplified | Optional later. Template-based, minimal logic. |

---

## Testing Pyramid Strategy

1. **Domain Unit Tests (70%)**: Pure Java, zero Spring dependencies. Fast. Tests core state machines, value objects, and domain services.
2. **Application Layer Tests (15%)**: Tests use cases and transaction boundaries using `@ExtendWith(SpringExtension.class)` and `@MockitoBean` to mock application output ports.
3. **Data/Adapter Integration Tests (10%)**: Tests `JpaRepository` implementations and Flyway migrations against a real PostgreSQL instance using Testcontainers (`@DataJpaTest` + `@AutoConfigureTestDatabase(replace = Replace.NONE)`).
4. **Module & E2E Tests (5%)**: Tests cross-module interactions using Spring Modulith's `@ApplicationModuleTest` and full REST API testing using `MockMvc` or `TestRestTemplate`.

## Current Loan Activation Slice

The executable Loan module now includes:

- domain aggregates `LoanAccount`, `ManualDisbursement`, `RepaymentSchedule`, and final schedule items;
- application input ports for confirmation, destination reveal, and LoanAccount query;
- one transactional confirmation service with a product activation policy resolver and the Salary Advance policy;
- output ports and JPA adapters for account, disbursement, and schedule persistence; and
- a secured Loan-owned controller with safe DTO mapping, owner-aware account queries, and narrowly scoped destination reveal.
