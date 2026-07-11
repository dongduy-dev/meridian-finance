# Bounded Context Design â€” DDD Context Map

## Context Map Overview

> Public Interface entries refer to application/public ports exposed by a module. They are not domain-owned ports.

```mermaid
graph TB
    subgraph Core["Core Domain"]
        LOAN["Loan Core / Origination"]
        APPROVAL["Approval Workflow"]
    end
    subgraph Supporting["Supporting Domains"]
        IAM["Identity & Access"]
        CUSTOMER["Customer Management"]
        PARTNER["Partner Management"]
        DOC["Document Management"]
        AUDIT["Audit & Compliance Controls"]
    end
    subgraph Generic["Generic Subdomains"]
        NOTIF["Notification"]
    end
    IAM -->|AuthContext| LOAN
    CUSTOMER -->|CustomerProfile/BankAccountInfo| LOAN
    PARTNER -->|Employee link and eligibility data| LOAN
    LOAN -->|LoanEvents| APPROVAL
    APPROVAL -->|Decision| LOAN
    DOC -->|DocumentChecklist/DocumentRef/OcrResult Phase 2| LOAN
    LOAN -->|DomainEvents| AUDIT
    LOAN -->|DomainEvents for future notifications| NOTIF
```

---

## 1. Identity & Access (IAM) â€” Supporting Domain

| Aspect | Detail |
|---|---|
| **Responsibilities** | User registration, authentication (JWT), authorization (RBAC), users, roles, refresh token/session management |
| **Entities** | `User`, `Role` (enum: `CUSTOMER`, `LOAN_OFFICER`, `APPROVER`, `ACCOUNTING_OFFICER`, `BACK_OFFICE_ADMIN`), `Permission` (constants class), `RolePermissionRegistry` (roleâ†’permission mapping), `RefreshToken`, `UserId` (VO), `EmailAddress` (VO) |
| **Public Interface** | `AuthenticationPort.authenticate(token)`, `UserQueryPort.findById(id)` |
| **Events Published** | `UserRegisteredEvent`, `UserSuspendedEvent` |
| **Microservice Candidacy** | First to extract. Minimal domain coupling, well-defined API. |

---

## 2. Customer Management â€” Supporting Domain

| Aspect | Detail |
|---|---|
| **Responsibilities** | Customer profile, verification status, bank account information, sensitive customer data protection |
| **Entities** | `Customer` (aggregate root), `CustomerProfile`, `PersonalInfo` (VO), `EmploymentInfo` (VO), `BankAccountInfo` (VO), `NationalId` (VO), `PhoneNumber` (VO), `EmailAddress` (VO), `VerificationStatus` |
| **Public Interface** | `CustomerQueryPort.findByUserId(id)`, `CustomerQueryPort.getBankAccountInfo(id)`, `CustomerProfilePort.updateVerificationStatus(id)` |
| **Events Published** | `CustomerVerifiedEvent`, `CustomerProfileUpdatedEvent` |
| **Microservice Candidacy** | Future extraction candidate. Keep customer data protection and ownership boundaries explicit. |

---

## 3. Partner Management â€” Supporting Domain

| Aspect | Detail |
|---|---|
| **Responsibilities** | Partner Companies, Partner Employees, monthly employee imports, import batches, reusable customer employee links for Salary Advance eligibility |
| **Entities** | `PartnerCompany` (aggregate root), `PartnerEmployee`, `PartnerEmployeeImportBatch`, `CustomerPartnerEmployeeLink`, `EmployeeEligibilityData` |
| **Public Interface** | `PartnerQueryPort.findCompany(id)`, `PartnerEmployeePort.verifyEmployee(...)`, `CustomerPartnerEmployeeLinkPort.getActiveLink(customerId, partnerCompanyId)`, `PartnerImportPort.importMonthlyEmployees(...)` |
| **Events Published** | `PartnerCompanyActivatedEvent`, `PartnerEmployeeImportCompletedEvent`, `CustomerPartnerEmployeeLinkedEvent`, `CustomerPartnerEmployeeLinkSuspendedEvent` |
| **Microservice Candidacy** | Future extraction candidate. In MVP it supports the Loan Core for Salary Advance policy checks. |

Partner Management owns Partner Company and Partner Employee source data. It also owns the reusable customer-to-partner-employee eligibility link because that link answers whether a customer is verified as an employee of a partner company. Loan Core may reference the link by ID and consume eligibility data through application/public ports, but it must not own Partner Employee records.

---

## 4. Loan Core / Origination â€” CORE DOMAIN

> Loan Core / Origination is the generic lending core of the platform and is responsible for enforcing lending business rules. To maintain domain integrity, loan lifecycle transitions, eligibility policies, repayment calculations, and interest computations are owned by the Loan domain and must not be implemented in controllers, persistence adapters, or external services.

> Salary Advance, Unsecured Consumer Loan, and Collateral Loan are product behaviors inside this context, not separate top-level bounded contexts. Product-specific behavior is handled by loan product policies and strategies. Salary Advance uses Partner Management data for employee eligibility, owns the Salary Advance limit state and usage workflow, and records an application-level verification snapshot. Unsecured Consumer Loan and Collateral Loan use the same shared loan lifecycle with streamlined product-specific review rules.

| Aspect | Detail |
|---|---|
| **Responsibilities** | Generic loan application lifecycle, product definition, `LoanProductPolicy` selection, product-specific policies/strategies, eligibility, Salary Advance limit state and usage, offer terms, manual disbursement confirmation state, repayment schedule, state machine |
| **Entities** | `LoanApplication` (aggregate root), `LoanProduct`, `LoanProductPolicy`, `SalaryAdvanceLimit`, `SalaryAdvanceLimitMovement`, `SalaryAdvanceVerification`, `LoanAccount`, `OfferTerms`, `DisbursementRecord`, `RepaymentSchedule`, `ProductVerificationResult`, `Money` (VO), `LoanTerm` (VO), `InterestRate` (VO), `RejectionReason` (VO) |
| **State Machine** | `DRAFT â†’ SUBMITTED â†’ VERIFICATION_PENDING/DOCUMENTS_PENDING â†’ UNDER_REVIEW â†’ APPROVAL_PENDING â†’ APPROVED â†’ CUSTOMER_ACCEPTANCE_PENDING â†’ CONTRACT_PENDING â†’ DISBURSEMENT_PENDING â†’ DISBURSED â†’ SETTLED/CLOSED` (also `â†’ RETURNED_FOR_REVISION`, `â†’ RETURNED_TO_REVIEW`, `â†’ REJECTED`, `â†’ CANCELLED`, `â†’ EXPIRED`) |
| **Public Interface** | `LoanApplicationPort.submit()`, `.getApplication()`, `.listApplications()`, `SalaryAdvanceLimitPort.getCurrentLimit()`, `.startApplicationUsingLimit()` |
| **Events Published** | `LoanSubmittedEvent` (carries: loanId, customerId, productId, requestedAmount, submittedAt), `SalaryAdvanceLimitReservedEvent`, `SalaryAdvanceLimitReleasedEvent`, `LoanReviewStartedEvent`, `LoanSentForApprovalEvent`, `LoanApprovedEvent`, `LoanRejectedEvent`, `LoanCancelledEvent`, `LoanDisbursedEvent`, `LoanCompletedEvent` |
| **Microservice Candidacy** | LAST to extract |

Loan Core owns the current Salary Advance limit because it is lending state: total, used, reserved, available, status, reservation, disbursement usage, repayment release, suspension, and disablement. The application-level `SalaryAdvanceVerification` snapshot belongs to the Salary Advance loan application workflow. It stores the employee link and limit values used for one application, but it is not the reusable employee relationship and not the current limit account.

---

## 5. Approval Workflow â€” Core Domain

| Aspect | Detail |
|---|---|
| **Responsibilities** | Loan Officer review, Approver decision, controlled review and approval, maker-checker controls, approval decision trail |
| **Entities** | `ReviewRecommendation`, `ApprovalDecision`, `ApprovalRequest` (aggregate root), `ApprovalStep`, `UserId` (VO), `RejectionReason` (VO) |
| **Public Interface** | `ApprovalPort.createReview()`, `.submitRecommendation()`, `.submitDecision()`, `.getDecisionTrail()` |
| **Listens To** | `LoanSentForApprovalEvent` â†’ creates approval decision work item after Loan Officer review |
| **Events Published** | `LoanReviewRecommendedEvent`, `ApprovalDecisionRecordedEvent` â†’ Loan module updates status |
| **Microservice Candidacy** | Future extraction candidate. Keep with the modular monolith for MVP controlled review and approval. |

---

## 6. Document Management â€” Supporting Domain

| Aspect | Detail |
|---|---|
| **Responsibilities** | Document upload, storage, metadata, checklist management, manual document review, replacement, waiver, readiness, and planned OCR-assisted processing |
| **Entities** | `Document` (aggregate root), `DocumentChecklist`, `DocumentChecklistItem`, `OcrJob`, `OcrResult`, `StorageReference` (VO), `DocumentType` enum |
| **Public Interface** | `DocumentPort.upload()`, `.getMetadata()`, `.download()`, `.findByLoan()` |
| **Events Published** | `DocumentUploadedEvent`, `DocumentReviewedEvent`, `DocumentChecklistReadyEvent` |
| **Microservice Candidacy** | Future extraction candidate. Keep checklist, review, and readiness controls in the modular monolith for MVP. |

---

### OCR-Assisted Processing Boundary

> OCR-assisted document processing is a planned Phase 2 capability within Document Management, not a separate top-level bounded context. The core MVP remains manual-review based, and manual document review remains authoritative for checklist readiness, replacement, waiver, and acceptance decisions.

| Aspect | Detail |
|---|---|
| **Responsibilities** | OCR-assisted document processing, Vietnamese TrOCR inference, document text extraction, field parsing |
| **Application/Output Port** | `OcrProcessingPort.submitForProcessing(docId)`, `.getResult(jobId)` |
| **Python Service** | FastAPI + TrOCR model and async workers when the OCR service is enabled |
| **Core MVP Boundary** | Manual upload, checklist handling, manual review, replacement, waiver, and readiness checks work without OCR. |
| **Phase 2 Boundary** | OCR is assistive only; checklist readiness, replacement, waiver, and acceptance remain controlled by Document Management and manual review. |

---

## 7. Audit & Compliance Controls â€” Supporting (Cross-Cutting)

| Aspect | Detail |
|---|---|
| **Responsibilities** | Immutable audit events, compliance-oriented traceability, and observational audit logging. Loan owns Loan Application lifecycle history. |
| **Entities** | Current implementation: `AuditEvent` append-only rows with safe JSONB payloads. Loan owns `LoanApplicationStatusTransition`; Approval source records remain `ReviewRecommendation` and `ApprovalDecision`. |
| **Integration** | Current implementation consumes shared audit-record requests synchronously via ordinary Spring `@EventListener` in the same transaction. Audit is terminal and never publishes. Future async/replay delivery would need idempotency, retry, and failure tracking before replacing current same-transaction writes. |
| **Microservice Candidacy** | Remain within the monolith by default. Extraction is possible for large-scale compliance, archival, or regulatory workloads but is not expected within the current platform scope. |

---

## 8. Notification â€” Generic Subdomain (Optional Later)

| Aspect | Detail |
|---|---|
| **Responsibilities** | Future email, SMS, in-app notifications, template management |
| **Entities** | `Notification`, `NotificationTemplate` |
| **Consumes** | `LoanApprovedEvent`, `LoanDisbursedEvent`, `ApprovalPendingEvent` |
| **MVP Boundary** | Optional later; not required for the MVP core workflow. |
| **Microservice Candidacy** | Future extraction candidate if notification volume or channel complexity requires it. |

---

## Communication Rules Summary

| Type | Allowed | Forbidden |
|---|---|---|
| **Sync** | IAMâ†’Any (auth), Loanâ†’Customer (profile/bank account checks), Loanâ†’Partner (Salary Advance eligibility data), Loan/Approvalâ†’Document (checklist readiness) | Direct entity imports across modules |
| **Events** | Spring `ApplicationEventPublisher`; current mandatory workflows use synchronous same-transaction listeners | Direct JPA repo access across modules |
| **Data** | Each module owns its tables exclusively | Shared tables, cross-module JOINs |
| **Reliability** | Current implemented workflows use same-transaction synchronous listeners for mandatory rollback semantics. Spring Modulith Event Publication Registry remains a future hardening option, not the current audit implementation. | Rolling your own outbox table; silently making mandatory audit/history writes async |

> **Approval â†” Loan coordination is event-driven for state changes.** Loan sends sufficient application context and Loan Officer recommendation when an approval decision is needed. Approval publishes the recorded decision so Loan can update the application lifecycle. No direct entity imports are allowed between these modules.

> **Salary Advance eligibility and limit coordination uses clear ownership.** Partner owns Partner Company, Partner Employee, and the reusable customer employee link. Loan owns Salary Advance limit state, limit movements, and application verification snapshots. Cross-context references use IDs and application/public ports, not shared JPA entity ownership.

> **Current implemented event delivery is synchronous for mandatory workflow writes.** Approval-to-Loan propagation and Audit recording currently use ordinary Spring `@EventListener` so failures roll back the originating transaction. Spring Modulith event-publication/replay remains a future architecture option only after idempotency, retry, and failure tracking are added.
