# MER-BIZ-001 - Business Requirements and Workflow Specification

## 1. Document Information

| Field | Value |
| ----- | ----- |
| Project | Meridian |
| Product | Meridian Lending Platform |
| Document Type | Business Requirements and Workflow Specification |
| Version | 1.0 |
| Status | Draft |
| Author | Dong Duy |
| Scope | MVP multi-product digital lending platform centered on Salary Advance, with streamlined support for Unsecured Consumer Loan and Collateral Loan workflows |

---

## 2. Purpose

This document is the single business requirements and workflow specification for the Meridian Lending Platform MVP. It defines product scope, application scope, roles, product behavior, common lifecycle rules, product-specific workflows, status transitions, document review logic, permissions, business rules, MVP boundaries, and future enhancements.

This document focuses on what the system must do from a business and functional perspective. Detailed database design, API design, deployment design, and implementation-level technical design are outside this document unless needed to clarify business behavior.

---

## 3. Product and Application Scope

### 3.1 Supported Loan Products

Meridian uses one generic lending core with product-specific policy behavior.

| Product Code | Product Name | Product Type | MVP Depth | Verification Model |
| ------------ | ------------ | ------------ | --------- | ------------------ |
| `SALARY_ADVANCE` | Salary Advance | `SALARY_BASED` | Primary | Reusable employee verification and limit-based model |
| `UNSECURED_CONSUMER_LOAN` | Unsecured Consumer Loan | `UNSECURED` | Streamlined | Document-based income and employment review model |
| `COLLATERAL_LOAN` | Collateral Loan | `SECURED` | Streamlined | Collateral information and document review model |

`productCode` identifies the exact lending product. `productType` is a broader category used for grouping, reporting, and policy selection.

### 3.2 Application Scope

| Component | MVP Scope |
| --------- | --------- |
| Backend | One Spring Boot backend responsible for business logic, security, persistence, workflow execution, audit trail, and product policy selection. |
| Back-Office Web Portal | Internal application for Back-Office Admins, Loan Officers, Approvers, and Accounting Officers. |
| Customer Web Portal | Customer-facing application for registration, profile completion, product selection, application submission, document upload, offer acceptance, and status tracking. |
| Mobile App | Future enhancement only; not part of the MVP. |

The MVP uses one backend and one database. Customer-facing and back-office applications communicate with the same backend.

### 3.3 Architecture Scope

The product architecture remains intentionally high level:

* Java and Spring Boot backend;
* PostgreSQL database;
* modular monolith;
* DDD-style bounded contexts;
* Hexagonal / Ports and Adapters where useful;
* one backend, one database, multiple frontends.

Backend top-level modules:

```text
com.meridian.platform/
|-- shared/
|-- identity/
|-- customer/
|-- partner/
|-- loan/
|-- approval/
|-- document/
|-- audit/
`-- notification/   # optional later
```

`loan/` is the generic lending core. Loan products must not become separate top-level backend modules. Product-specific rules belong inside `loan/domain/product/...` and are selected through a Strategy/Policy pattern. `LoanApplication` is the common workflow entity, and `LoanProductPolicy` handles product-specific validation and behavior.

### 3.4 MVP In Scope

* Customer registration, authentication, profile completion, and controlled profile updates.
* Customer bank account capture and controlled post-submission bank account changes.
* Back-office authentication, role-based access control, and internal user administration.
* Loan product catalog, product activation/deactivation, and product-specific policy configuration.
* Common loan application lifecycle, submission validation, and transition control.
* Salary Advance partner company management, monthly Partner Employee import, reusable customer employee verification, import freshness handling, limit tracking, and available limit calculation.
* Streamlined Unsecured Consumer Loan and Collateral Loan application workflows.
* Document checklist completeness validation, document upload, manual review, rejection, replacement, waiver, and readiness checks.
* Loan Officer review, Approver decision, maker-checker controls, customer acceptance, offer expiry, contract/document readiness, manual disbursement confirmation, LoanAccount activation, repayment tracking, settlement, closure, and audit trail.

### 3.5 MVP Out of Scope

The MVP excludes real financial integrations, production banking operations, full mobile delivery, and non-lending product families. The authoritative boundary list is maintained in Section 13.4.

---

## 4. User Roles and Permission Rules

### 4.1 Roles

| Role | Responsibilities |
| ---- | ---------------- |
| Customer | Completes own profile, selects products, creates drafts, submits applications, uploads required customer documents, accepts or declines approved offers, and tracks application, loan, repayment, settlement, and closure status. |
| Loan Officer | Reviews customer profile, product verification result, documents, requested amount and term, repayment capacity or collateral notes, and recommends approval, rejection, or revision. |
| Approver | Reviews Loan Officer recommendation and approves, rejects, returns to Loan Officer review, or requests customer/staff correction. |
| Accounting Officer | Confirms document readiness and bank account information, manually marks disbursement as completed, records disbursement details, and records or confirms repayment updates. |
| Back-Office Admin | Manages product catalog, product activation/deactivation, Partner Companies, Partner Employee imports, internal users, role assignments, and MVP system configuration. |
| System | Validates product rules, maintains workflow status, calculates Salary Advance limits, tracks checklist status, generates provisional and final repayment schedules, applies offer expiry rules, and records audit events. |

### 4.2 Role / Action Permission Matrix

| Action | Customer | Loan Officer | Approver | Accounting Officer | Back-Office Admin | System |
| ------ | -------- | ------------ | -------- | ------------------ | ----------------- | ------ |
| Register/login to Customer Web Portal | Yes | No | No | No | No | No |
| Authenticate to Back-Office Web Portal | No | Yes | Yes | Yes | Yes | No |
| Maintain customer profile | Own profile | View/review | View | View bank info for disbursement | Support/admin as configured | Validate |
| Manage products | No | No | No | No | Yes | Enforce active status |
| Manage Partner Companies/imports | No | No | No | No | Yes | Validate/store |
| Create/submit application | Yes | No | No | No | No | Validate |
| Upload customer-required documents | Yes | Assist if authorized | No | No | Assist if authorized | Store |
| Review documents | No | Yes | View | Confirm readiness | Admin correction as configured | Track status |
| Recommend approval/rejection | No | Yes | No | No | No | Audit |
| Approve/reject/return approval | No | No | Yes | No | No | Audit |
| Confirm manual disbursement | No | No | No | Yes | No | Audit/create account |
| Record repayment update | No | No | No | Yes | No | Update statuses |
| View audit events | No | Yes as authorized | Yes as authorized | Yes as authorized | Yes | Record |

Internal users must authenticate before using the Back-Office Web Portal. Permissions are enforced by role and action. All internal business actions record actor identity.

---

## 5. Core Business Concepts

### 5.1 Loan Product and Product Catalog

A `LoanProduct` defines display, validation, and workflow behavior for a supported lending product.

Each product must include:

* product code, name, type, active/inactive status, and description;
* minimum and maximum loan amount;
* allowed loan terms;
* interest rate and repayment method;
* required document types;
* product-specific policy reference;
* offer validity period;
* product-specific eligibility notes.

Product activation and deactivation are part of MVP scope because product display, eligibility, and submission depend on active product status.

### 5.2 Loan Application

A `LoanApplication` represents a customer request for one selected loan product. It may exist as a draft before submission and becomes submitted after all required pre-submission validation passes.

Common application information includes customer reference, selected product, requested amount, requested term, product-specific details, product verification result, document checklist status, document review status summary, review and approval history, customer acceptance status, contract/document readiness, disbursement status, and audit history.

### 5.3 Loan Account

A `LoanAccount` represents the active loan record created only after an approved and accepted application is manually confirmed as disbursed.

A controlled post-disbursement transaction must:

1. move the LoanApplication to `DISBURSED`;
2. create the LoanAccount;
3. generate the final repayment schedule;
4. set the LoanAccount status to `ACTIVE`;
5. record audit trail entries.

If account creation or schedule generation fails, the system must not leave the application, account, or repayment schedule in an inconsistent state.

### 5.4 Partner Company and Partner Employee

A Partner Company is an employer configured by Back-Office Admins for Salary Advance eligibility.

A Partner Employee record represents monthly employee information imported for a partner company. It is source data for reusable customer employee verification and Salary Advance limit refresh.

Partner Employee data includes partner company reference, employee code, employee full name, identity reference, salary amount, employment status, salary advance limit, effective month, import batch reference, and active/inactive status.

### 5.5 Customer Employee Link and Salary Advance Limit

A Customer Partner Employee Link represents the reusable relationship between a customer and a verified Partner Employee record. It is created after successful employee verification or authorized manual review and can be reused for future Salary Advance applications.

The customer employee link answers: "Is this customer verified as an employee of this partner company?" It is not a loan application and it is not the current limit account.

A Salary Advance Limit represents the customer's current limit state for Salary Advance. It tracks total limit, used amount, reserved amount, available amount, status, and last refresh information. It is recalculated when Partner Employee data changes and updated when applications reserve or release amount, disbursements convert reserved amount to used amount, and repayments release used amount.

The Salary Advance limit answers: "How much Salary Advance limit does this customer currently have available?"

Each Salary Advance application still records a Salary Advance verification snapshot. The snapshot answers: "What employee verification and limit values were used for this specific application?" It preserves the employee status and limit values used at application time even if the customer's reusable employee link or current limit changes later.

### 5.6 Collateral

Collateral represents asset information submitted by a customer for a Collateral Loan. MVP collateral information includes collateral type, description, estimated value, ownership status, ownership document reference, collateral condition note, and manual review note.

MVP collateral types:

```text
MOTORBIKE
CAR
ELECTRONICS
PROPERTY_DOCUMENT
OTHER
```

Estimated value is informational for manual review in the MVP. The MVP does not enforce an automated loan-to-value rule.

### 5.7 Verification, Checklist, and Review Boundaries

Meridian separates three concepts that must not be collapsed into one status:

| Concept | Purpose | Typical Owner | Typical Output |
| ------- | ------- | ------------- | -------------- |
| Product-specific verification or review | Determines whether product-specific information supports workflow progression. | System or Loan Officer | `ProductVerificationResult` |
| Document checklist completeness | Determines whether each required document is uploaded, accepted, not required, or waived. | System | Checklist completion result |
| Manual document review | Determines whether uploaded documents are acceptable. | Loan Officer or authorized back-office user | `DocumentReviewStatus` |

Submission may require checklist completeness at the `UPLOADED` or `NOT_REQUIRED` level. Disbursement readiness requires required documents to be `ACCEPTED`, `NOT_REQUIRED`, or `WAIVED`.

---

## 6. Common Loan Lifecycle and Workflow Controls

All loan products share the same core lifecycle, with product-specific pre-submission checks where required:

1. Customer profile completion.
2. Product selection.
3. Product eligibility pre-check.
4. Product-specific pre-submission information capture, if required.
5. Loan application submission.
6. Post-submission product verification or review.
7. Document checklist completeness validation.
8. Document review, where required.
9. Loan Officer review.
10. Approval decision.
11. Customer acceptance.
12. Contract/document preparation.
13. Manual disbursement confirmation.
14. LoanAccount creation and activation.
15. Repayment tracking.
16. Settlement or closure.

### 6.1 Profile, Product Selection, and Pre-Check

Before submission, the customer must complete a basic profile including identity reference, contact details, residential address, employment information, bank account information for disbursement, and consent confirmations as configured.

Current Customer foundation rules:

* Customer profile completeness and bank-account readiness are separate facts.
* Normal Salary Advance submission requires an active Customer, a complete Customer profile, and one primary active bank account.
* Customer `VerificationStatus.VERIFIED` is not required for Salary Advance submission in the current MVP because real Customer KYC/provider verification is deferred.
* The Customer identity reference is captured in the Customer profile, encrypted at rest, and becomes immutable after the profile first becomes complete.
* Partner employee verification derives identity evidence from the authenticated Customer profile; customer-facing requests must not provide unrestricted `identityReference`.
* Bank-account numbers are encrypted at rest and exposed only as masked values.
* Bank-account identity is not edited in place. Replacements are modeled by adding a new account and deactivating the old account.
* Contact details, residential address, employment summary, and configured consent confirmations may be updated by the customer and must be audited without storing PII in generic Audit payloads.
* Loan-status-sensitive Customer profile and bank-account mutation restrictions are deferred until immutable application/disbursement snapshot behavior exists, so Customer does not depend on Loan.

The Customer Web Portal displays active products with name, description, amount range, available terms, interest rate, repayment method, required documents, eligibility notes, and product-specific pre-submission requirements.

Common eligibility pre-checks:

* customer profile is complete;
* product is active;
* requested amount is within product limits;
* requested term is allowed;
* no active blocking application exists for the same product;
* required product-specific information is provided;
* required pre-submission verification passed or is approved for manual override.

Concurrency rule for MVP:

* A customer may keep multiple drafts.
* A customer may not submit a new application for the same product while another application for that product is in `SUBMITTED`, `VERIFICATION_PENDING`, `DOCUMENTS_PENDING`, `UNDER_REVIEW`, `RETURNED_TO_REVIEW`, `APPROVAL_PENDING`, `APPROVED`, `CUSTOMER_ACCEPTANCE_PENDING`, `CONTRACT_PENDING`, or `DISBURSEMENT_PENDING`.
* Salary Advance also requires a verified active customer employee link, an active available limit, and overdue/used/reserved exposure checks.

### 6.2 Draft and Submission

After passing required pre-submission validation, the customer submits the application. If the application was saved as a draft, the system updates the existing `LoanApplication` from `DRAFT` to `SUBMITTED`. If draft saving is not used, the system creates a new `LoanApplication` directly in `SUBMITTED`.

The system records unique application number, customer reference, product reference, product code, product type, requested amount, requested term, application status, submission timestamp, product-specific details, and audit trail entry.

### 6.3 Product Verification or Review

After submission, Meridian records the formal product verification result for the `LoanApplication`.

| Product | Verification / Review Behavior |
| ------- | ------------------------------ |
| `SALARY_ADVANCE` | Records the application-level snapshot of the verified employee link and current limit values used for the submitted application. |
| `UNSECURED_CONSUMER_LOAN` | Reviews income/employment documents for consistency and basic repayment capacity. |
| `COLLATERAL_LOAN` | Reviews collateral information, ownership/supporting documents, and manual collateral assessment notes. |

`REQUIRES_MORE_INFORMATION` is a product verification result, not an approval outcome. When it requires customer or staff action, the LoanApplication moves to `RETURNED_FOR_REVISION`.

### 6.4 Document Checklist and Review Rules

Document checklist completeness requires each required item to be one of:

```text
UPLOADED
ACCEPTED
NOT_REQUIRED
WAIVED
```

Manual document review outcomes:

```text
ACCEPT_DOCUMENT
REJECT_DOCUMENT
WAIVE_DOCUMENT
REQUEST_REPLACEMENT
```

If required documents are missing, rejected, expired, or need replacement, the application moves to:

| Next Status | Usage |
| ----------- | ----- |
| `DOCUMENTS_PENDING` | Customer must upload or replace documents. |
| `RETURNED_FOR_REVISION` | Customer or staff must correct information, replace documents, or provide clarification. |

Required documents must be `ACCEPTED`, `NOT_REQUIRED`, or `WAIVED` before the application can move to `DISBURSEMENT_PENDING`.

### 6.5 Loan Officer Review

The Loan Officer reviews the customer profile, product verification result, document checklist and document review results, requested amount and term, product-specific details, and internal comments.

Loan Officer actions:

| Action | Next Status |
| ------ | ----------- |
| `RECOMMEND_APPROVAL` | `APPROVAL_PENDING` |
| `RECOMMEND_REJECTION` | `APPROVAL_PENDING` |
| `RETURN_TO_CUSTOMER_REVISION` | `RETURNED_FOR_REVISION` |
| `REQUEST_STAFF_CORRECTION` | `RETURNED_FOR_REVISION` |

Current implementation note: `RECOMMEND_APPROVAL` and `RECOMMEND_REJECTION` are executable. `RETURN_TO_CUSTOMER_REVISION` and `REQUEST_STAFF_CORRECTION` remain recognized target-state actions, but currently return `409 REVISION_WORKFLOW_NOT_AVAILABLE` before recommendation persistence, audit/event publication, history creation, or Loan status change. The complete correction and resubmission lifecycle remains tracked by `MER-FU-031`.

### 6.6 Approval Decision

The Approver reviews the application and Loan Officer recommendation. Approval must be separate from Loan Officer review, and the same back-office user cannot record both the Loan Officer recommendation and final Approver decision for the same application.

For the current MVP, approval is exact-request approval:

* approved amount equals the submitted requested amount;
* approved term equals the submitted requested term;
* the Approver does not create a counteroffer and does not modify amount or term during approval.

If the requested amount or term must change, the application must be returned through an existing review or correction path. Counteroffers and approver-adjusted financial terms are outside the current MVP.

Approver actions:

| Action | Next Status |
| ------ | ----------- |
| `APPROVE` | `APPROVED`, then `CUSTOMER_ACCEPTANCE_PENDING` after approved terms are generated |
| `REJECT` | `REJECTED` |
| `RETURN_TO_LOAN_OFFICER_REVIEW` | `RETURNED_TO_REVIEW` |
| `REQUEST_CUSTOMER_INFORMATION` | `RETURNED_FOR_REVISION` |
| `REQUEST_STAFF_CORRECTION` | `RETURNED_FOR_REVISION` |

Current implementation note: `APPROVE`, `REJECT`, and `RETURN_TO_LOAN_OFFICER_REVIEW` are executable. The current API represents target-state customer/staff correction as `REQUEST_CUSTOMER_OR_STAFF_CORRECTION`; it returns `409 REVISION_WORKFLOW_NOT_AVAILABLE` before decision persistence, audit/event publication, history creation, or Loan status change. The target correction requirements above remain valid for the future `MER-FU-031` workflow.

`APPROVED` is a decision status. It should not remain the customer-facing waiting status once approved terms are generated; the customer-facing status becomes `CUSTOMER_ACCEPTANCE_PENDING`.

When approved-offer generation is part of the workflow, approval and offer generation must complete as one controlled business operation. If the approved offer cannot be generated, the approval must not leave the application permanently in `APPROVED` without customer-facing approved terms.

### 6.7 Offer, Contract, Disbursement, and Activation

An approved offer is the customer-facing historical snapshot of the terms presented after approval and before contract preparation.

For the current MVP, each Loan Application may have one approved offer. Once the offer is presented, the approved financial terms snapshot is immutable: approved principal, approved term, interest and pricing terms, total interest, fees, total repayment amount, repayment method, provisional repayment items, generated time, and expiry time must not change. Later product or policy changes must not silently change terms already presented. Offer lifecycle state and response metadata may change only through defined transitions, including offer status, accepted time, declined time, and expired time.

For Salary Advance, the approved offer uses exact-request terms and approved product pricing:

* approved principal amount is the submitted requested amount;
* approved term is the submitted requested term, limited to 1, 2, or 3 months;
* interest is 1.2% flat interest per month, applied to the original approved principal for each approved term month;
* fee amount is 0 VND;
* total repayment amount is approved principal plus total interest;
* repayment method is `ON_SALARY_DATE`, used as a repayment timing category only;
* provisional repayment items are generated using the Salary Advance rules in Section 11.2;
* generated time and expiry time are captured when the offer is generated;
* offer status tracks whether the offer is pending, accepted, declined, or expired.

The customer-visible Salary Advance offer shows product, approved principal amount, approved term, flat monthly interest rate, total interest amount, fee amount, total repayment amount, repayment method, provisional repayment items, generated time, expiry time, and offer lifecycle state. Provisional repayment items show item number, principal due, interest due, fee due, total due, and salary-cycle timing. Exact calendar due dates are not shown in the approved offer and are determined later by the final repayment schedule process.

The customer must accept approved terms before contract preparation and disbursement. Viewing an offer is read-only and must not change application status, expire the offer, or release a Salary Advance reservation. Responding to an offer requires authenticated customer ownership of the Loan Application. Viewing the customer's own offer may use `loan:read:own`; accepting or declining the offer requires `loan:offer:respond:own`.

If the customer accepts a valid unexpired offer, the application moves from `CUSTOMER_ACCEPTANCE_PENDING` to `CONTRACT_PENDING`. If the customer declines a pending Salary Advance offer, the application moves to `CUSTOMER_DECLINED` and the reserved Salary Advance amount is released exactly once in the same controlled business operation. If the offer expires before acceptance, the application moves to `EXPIRED` and the reserved Salary Advance amount is released exactly once in the same controlled business operation.

Same-action retries are idempotent for customer offer responses: accepting an already accepted offer returns the current accepted result, and declining an already declined offer returns the current declined result without releasing reservation again. Contradictory accepted/declined actions return `OFFER_ACTION_CONFLICT`. Accept or decline against an expired offer consistently returns `OFFER_EXPIRED`: a customer action that first discovers expiry commits expiry and exact-once release, while an already persisted expiry returns the same conflict without additional history, audit, movement, or release effects.

After customer acceptance, the system prepares or records required contract and disbursement documents. MVP document handling may include generated loan agreement record, uploaded signed agreement, uploaded supporting documents, internal approval memo, disbursement instruction record, or manual staff confirmation.

The Accounting Officer confirms approved amount, customer bank account information, required document readiness, approval validity, customer acceptance, and `DISBURSEMENT_PENDING` status before marking disbursement as completed. Meridian does not execute real bank transfers in the MVP.

### 6.8 Repayment, Settlement, and Closure

Repayment tracking is manual in the MVP and may include scheduled due date, principal due, interest due, amount paid, payment date, payment method note, manual payment confirmation, and outstanding balance.

LoanAccount roll-up rules:

* if any unpaid repayment is past due, the LoanAccount becomes `OVERDUE`;
* if all scheduled repayments are paid, or an approved settlement is recorded, the LoanAccount becomes `SETTLED`;
* if a settled loan is administratively closed, the LoanAccount becomes `CLOSED`;
* `SETTLED` and `CLOSED` are terminal for normal repayment operations.

---

## 7. Product Workflows

### 7.1 Salary Advance

Salary Advance is the flagship MVP product and should be treated as a limit-based lending product. Customers verify employee status before creating a loan application, see their current Salary Advance limit, and create applications using available limit.

Product model:

```text
Partner Company + Partner Employee data
-> reusable customer employee link
-> current Salary Advance limit
-> customer Salary Advance dashboard
-> draft application created without limit reservation
-> application submission reserves limit and records verification snapshot
-> review and approval
-> manual disbursement confirmation
-> LoanAccount activation
-> repayment tracking and limit release
```

Back-office setup and import rules:

* Back-Office Admin creates and activates/deactivates Partner Companies.
* Back-Office Admin imports Partner Employee data monthly.
* The system records each import batch, validates rows, stores valid records, and prevents invalid rows from being used for verification.
* The latest active record for the effective month is used for normal eligibility.
* Stale employee data cannot be used if it falls outside the configured freshness window.
* Duplicate employee records in the same partner/effective month require correction before use.

Customer product page and dashboard behavior:

* The Salary Advance product page shows the customer's employee verification status before application creation.
* If the customer has no verified employee link, the page prompts the customer to verify employee status before starting a Salary Advance application.
* If the customer has a verified active employee link, the page shows Salary Advance limit status, total limit, used amount, reserved amount, available amount, and last refresh time.
* If the limit is active and available amount is positive, the customer can start a new draft Salary Advance application for an amount up to available limit.
* If the limit is suspended, disabled, stale, or unavailable, the page shows the status and blocks normal application creation until the issue is resolved.
* Customers should not need to manually verify employee status for every future application while the reusable employee link remains verified and active.

Employee verification outcomes:

```text
MATCHED_ACTIVE
MATCHED_INACTIVE
NOT_FOUND
MULTIPLE_MATCHES
PENDING_MANUAL_REVIEW
MANUAL_REVIEW_APPROVED
MANUAL_REVIEW_REJECTED
```

Mapping to generic product verification:

| Employee Verification Outcome | ProductVerificationResult | Business Handling |
| ----------------------------- | ------------------------- | ----------------- |
| `MATCHED_ACTIVE` | `VERIFIED` | Customer may continue to limit calculation. |
| `MATCHED_INACTIVE` | `FAILED` | Hard stop for normal eligibility; data may be corrected through admin maintenance but not manually overridden for normal eligibility. |
| `NOT_FOUND` | `PENDING_MANUAL_REVIEW` | Manual review may approve only if sufficient evidence links the customer to an active Partner Employee record. |
| `MULTIPLE_MATCHES` | `PENDING_MANUAL_REVIEW` | Manual review must resolve the correct active employee record before proceeding. |
| `PENDING_MANUAL_REVIEW` | `PENDING_MANUAL_REVIEW` | Case waits for Loan Officer or authorized back-office review. |
| `MANUAL_REVIEW_APPROVED` | `VERIFIED` | Customer may continue; reason and reviewer are audited. |
| `MANUAL_REVIEW_REJECTED` | `FAILED` | Customer cannot proceed unless corrected information is submitted. |

Manual review must include a reason and identify supporting evidence. Inactive Partner Companies and inactive Partner Employee records are hard stops for normal eligibility. Manual approval cannot override an inactive Partner Company.

Salary Advance limit calculation must consider product maximum amount, Partner Company policy limit, employee-level salary advance limit, salary-based percentage cap, employee status, freshness of the latest employee import, existing used exposure, and reserved exposure from submitted non-terminal applications.

Example logic:

```text
totalSalaryAdvanceLimit = min(
  productMaximumAmount,
  partnerCompanyLimit,
  employeeConfiguredLimit,
  salaryBasedLimit
)

availableSalaryAdvanceLimit = totalSalaryAdvanceLimit
  - usedSalaryAdvanceAmount
  - reservedSalaryAdvanceAmount
```

Limit state behavior:

* `totalLimit` is the customer's current maximum Salary Advance limit after product, partner, employee, and salary cap rules.
* `usedAmount` reflects active disbursed Salary Advance exposure that has not been repaid or released.
* `reservedAmount` reflects submitted, approved, or accepted Salary Advance applications that are not yet disbursed or terminal.
* `availableAmount` is `totalLimit - usedAmount - reservedAmount`.
* Draft Salary Advance application creation does not reserve limit.
* A submitted Salary Advance application reserves the requested amount only if submission validation passes.
* Rejected, cancelled, customer-declined, expired, or otherwise released applications free the reserved amount.
* Customer decline and offer expiry release the reserved amount exactly once, in the same controlled business operation as the terminal application transition.
* Manual disbursement converts the reserved amount into used amount when the LoanAccount is created.
* Repayment, settlement, or approved correction releases used amount according to the configured Salary Advance policy.
* Blocking overdue Salary Advance exposure prevents new Salary Advance submission even when a calculated available amount remains.

Limit refresh, suspension, and disablement:

* When a new valid Partner Employee import changes salary, employee-level limit, active status, or effective-month data, the system refreshes the reusable employee link and recalculates the Salary Advance limit.
* If employee data is temporarily stale, unresolved, or requires manual review, the limit may be marked `SUSPENDED` or `STALE` and normal application creation is blocked.
* If the Partner Company or Partner Employee becomes inactive, or the relationship is no longer eligible, the limit is marked `DISABLED` for normal application creation.
* Suspension and disablement must not erase existing application or loan history. Existing active loans continue through repayment tracking.
* Every Salary Advance application records its own verification snapshot with employee status, employee/link references, total limit, used amount, reserved amount, available amount, and verification result used at submission time.

End-to-end workflow:

1. Back-Office Admin creates and configures a Partner Company.
2. Back-Office Admin imports monthly Partner Employee data.
3. System records, validates, and stores Partner Employee import data.
4. Customer completes profile information.
5. Customer opens the Salary Advance product page.
6. System checks whether the customer already has a verified active customer employee link.
7. If no verified link exists, customer submits employee verification information before creating a loan application.
8. System matches the customer against Partner Employee records or routes unresolved cases to authorized manual review.
9. System creates or refreshes the reusable customer employee link after successful verification or approved manual review.
10. System calculates or refreshes the customer's Salary Advance limit.
11. Customer views employee verification status plus total, used, reserved, and available Salary Advance limit.
12. Customer starts a draft Salary Advance application. Draft creation does not reserve limit.
13. Customer enters requested amount, term, and required application information.
14. Customer uploads required Salary Advance documents, or the system marks non-required checklist items `NOT_REQUIRED`.
15. Customer submits the Salary Advance request.
16. System validates the active employee link, active limit, product rules, requested amount, term, stale data, overdue exposure, blocking application rules, available amount, and document checklist completeness.
17. If validation passes, the system reserves the requested amount and records the application-level Salary Advance verification snapshot.
18. Loan Officer reviews verification snapshot, current warnings if any, documents, requested amount, requested term, and application details.
19. Loan Officer recommends approval, recommends rejection, or returns for revision.
20. Approver approves, rejects, returns to Loan Officer review, or requests customer/staff correction.
21. If the Approver approves, the system applies exact-request approval, generates one immutable approved financial-terms snapshot, and moves the application to `CUSTOMER_ACCEPTANCE_PENDING` only if offer generation succeeds.
22. Customer views the approved offer without causing financial state changes.
23. Customer accepts a valid unexpired offer, declines the offer, or the system expires the offer after its validity period.
24. Accepted offers move to `CONTRACT_PENDING`; declined or expired offers are terminal pre-disbursement outcomes and release the reserved Salary Advance amount exactly once.
25. Contract and disbursement documents are prepared or uploaded after customer acceptance.
26. Accounting Officer marks disbursement as completed.
27. System marks the application `DISBURSED`, creates the LoanAccount, generates the final repayment schedule, activates the LoanAccount, and converts reserved limit to used limit.
28. Repayment, settlement, and closure status are tracked; repayment releases used limit according to policy.


Salary Advance MVP does not include real payroll integration, real employer API integration, automatic payroll deduction, real bank transfer, employer-facing production portal, counteroffers, approver-modified amount or term, or real-time HR system sync.

### 7.2 Unsecured Consumer Loan

Unsecured Consumer Loan is a streamlined product based on document-based income and employment review.

Product model:

```text
Customer profile + income and employment documents
-> document checklist completeness
-> application submission
-> income/employment document review
-> repayment capacity review
-> approval
-> manual disbursement confirmation
-> LoanAccount activation
-> repayment tracking
```

Required MVP documents are defined in Section 11.1. A loan purpose declaration may be added as an optional policy-configured document.

This workflow describes intended Unsecured Consumer Loan product behavior. Product-specific pricing and approved-offer details require Unsecured Consumer Loan rules.

End-to-end workflow:

1. Customer completes profile information.
2. Customer selects Unsecured Consumer Loan.
3. Customer enters requested amount and term.
4. Customer uploads required income and employment documents.
5. System validates product rules, requested amount, requested term, required fields, and pre-submission checklist completeness.
6. Customer submits the application.
7. System records formal product verification or marks it `PENDING_MANUAL_REVIEW`.
8. System validates document checklist completeness.
9. Loan Officer reviews customer profile, documents, and basic repayment capacity.
10. Loan Officer recommends approval, recommends rejection, or returns for revision.
11. Approver approves, rejects, returns to Loan Officer review, or requests customer/staff correction.
12. System generates approved terms and provisional repayment schedule.
13. Customer accepts approved terms.
14. Contract and disbursement documents are prepared or uploaded.
15. Accounting Officer marks disbursement as completed.
16. System marks the application `DISBURSED`, creates the LoanAccount, generates the final repayment schedule, and activates the LoanAccount.
17. Repayment, settlement, and closure status are tracked.

Unsecured Consumer Loan MVP does not include real credit bureau integration, real income verification API, real bank statement parsing, automated credit scoring, or fully automated approval.

### 7.3 Collateral Loan

Collateral Loan is a lightweight secured-loan workflow based on collateral information and document review, with manual assessment handled in the review workflow.

Product model:

```text
Customer profile + collateral information + collateral documents
-> document checklist completeness
-> application submission
-> collateral review
-> approval
-> manual disbursement confirmation
-> LoanAccount activation
-> repayment tracking
```

Collateral information includes collateral type, collateral description, estimated value, ownership status, ownership document reference, collateral condition note, and manual review note.

Required MVP documents are defined in Section 11.1.

This workflow describes intended Collateral Loan product behavior. Product-specific pricing and approved-offer details require Collateral Loan rules.

End-to-end workflow:

1. Customer completes profile information.
2. Customer selects Collateral Loan.
3. Customer enters requested amount and term.
4. Customer submits collateral information.
5. Customer uploads collateral ownership or supporting documents.
6. System validates product rules, requested amount, requested term, required collateral information, and pre-submission checklist completeness.
7. Customer submits the application.
8. System records formal product verification or marks it `PENDING_MANUAL_REVIEW`.
9. System validates document checklist completeness.
10. Loan Officer performs manual collateral review and records assessment notes.
11. Loan Officer recommends approval, recommends rejection, or returns for revision.
12. Approver approves, rejects, returns to Loan Officer review, or requests customer/staff correction.
13. System generates approved terms and provisional repayment schedule.
14. Customer accepts approved terms.
15. Contract and disbursement documents are prepared or uploaded.
16. Accounting Officer marks disbursement as completed.
17. System marks the application `DISBURSED`, creates the LoanAccount, generates the final repayment schedule, and activates the LoanAccount.
18. Repayment, settlement, and closure status are tracked.

Collateral Loan MVP does not include full collateral valuation engine, legal enforcement workflow, notarization workflow, collateral auction/liquidation, external asset registry integration, or insurance integration.

---

## 8. Status Model and Transition Rules

Status names are namespace-scoped. For example, `LoanApplicationStatus.UNDER_REVIEW` and `DocumentReviewStatus.UNDER_REVIEW` are separate enum values even if their display labels are similar.

### 8.1 LoanApplication Statuses

| Status | Description |
| ------ | ----------- |
| `DRAFT` | Application has been started but not submitted. |
| `SUBMITTED` | Customer has submitted the application. |
| `VERIFICATION_PENDING` | Formal product-specific verification or review is pending. |
| `VERIFICATION_FAILED` | Formal product-specific verification failed. |
| `DOCUMENTS_PENDING` | Required documents are missing, incomplete, rejected, expired, or pending upload/replacement. |
| `UNDER_REVIEW` | Loan Officer is reviewing the application. |
| `RETURNED_FOR_REVISION` | Application has been returned for customer information, customer revision, or staff correction. |
| `RETURNED_TO_REVIEW` | Approver has returned the application to Loan Officer review. |
| `APPROVAL_PENDING` | Application is waiting for Approver decision. |
| `APPROVED` | Approver has approved the application; approved terms still need to be generated. |
| `REJECTED` | Application has been rejected. |
| `CUSTOMER_ACCEPTANCE_PENDING` | Approved terms have been generated and are waiting for customer acceptance. |
| `CUSTOMER_DECLINED` | Customer declined approved terms. |
| `CONTRACT_PENDING` | Customer accepted the offer and contract/disbursement documents are being prepared. |
| `DISBURSEMENT_PENDING` | Contract and required documents are ready for manual disbursement confirmation. |
| `DISBURSED` | Disbursement has been manually confirmed and the LoanAccount has been activated. |
| `CANCELLED` | Application was cancelled before disbursement. |
| `EXPIRED` | Approved offer expired before customer acceptance. |

Terminal application statuses for normal processing:

```text
REJECTED
CUSTOMER_DECLINED
DISBURSED
CANCELLED
EXPIRED
```

### 8.2 Other Status Values

| Status Group | Values |
| ------------ | ------ |
| `LoanAccountStatus` | `ACTIVE`, `OVERDUE`, `SETTLED`, `CLOSED` |
| `ProductVerificationResult` | `VERIFIED`, `FAILED`, `PENDING_MANUAL_REVIEW`, `REQUIRES_MORE_INFORMATION` |
| `DocumentReviewStatus` | `NOT_REQUIRED`, `PENDING_UPLOAD`, `UPLOADED`, `UNDER_REVIEW`, `ACCEPTED`, `REJECTED`, `EXPIRED`, `WAIVED` |
| `RepaymentStatus` | `NOT_DUE`, `DUE`, `PARTIALLY_PAID`, `PAID`, `OVERDUE`, `SETTLED` |

### 8.3 Core LoanApplication Transition Matrix

| Current Status | Trigger / Action | Actor | Guard Condition | Next Status | Reason Required |
| -------------- | ---------------- | ----- | --------------- | ----------- | --------------- |
| `DRAFT` | Submit application | Customer | Profile complete, product active, pre-checks pass | `SUBMITTED` | No |
| `SUBMITTED` | Record product verification or application snapshot | System | Application submitted | `VERIFICATION_PENDING` | No |
| `VERIFICATION_PENDING` | Verification passed | System or Loan Officer | Product verification result is `VERIFIED` | `DOCUMENTS_PENDING` or `UNDER_REVIEW` | No |
| `VERIFICATION_PENDING` | Verification failed | System or Loan Officer | Product verification result is `FAILED` | `VERIFICATION_FAILED` | Yes |
| `VERIFICATION_PENDING` | More information needed | System or Loan Officer | Correctable issue exists | `RETURNED_FOR_REVISION` | Yes |
| `VERIFICATION_FAILED` | Corrected information submitted | Customer or staff | Correctable failure | `VERIFICATION_PENDING` | Yes |
| `DOCUMENTS_PENDING` | Required documents uploaded | Customer or staff | Checklist complete at upload level | `UNDER_REVIEW` | No |
| `UNDER_REVIEW` | Recommend approval or rejection | Loan Officer | Review complete | `APPROVAL_PENDING` | Yes for rejection |
| `UNDER_REVIEW` | Return for customer/staff revision | Loan Officer | Correctable issue exists | `RETURNED_FOR_REVISION` | Yes |
| `RETURNED_FOR_REVISION` | Corrections submitted | Customer or staff | Required corrections complete | `VERIFICATION_PENDING`, `DOCUMENTS_PENDING`, or `UNDER_REVIEW` | No |
| `APPROVAL_PENDING` | Approve | Approver | Maker-checker rule satisfied | `APPROVED` | No |
| `APPROVAL_PENDING` | Reject | Approver | Decision complete | `REJECTED` | Yes |
| `APPROVAL_PENDING` | Return to Loan Officer review | Approver | Further review needed | `RETURNED_TO_REVIEW` | Yes |
| `APPROVAL_PENDING` | Request customer/staff correction | Approver | Correctable issue exists | `RETURNED_FOR_REVISION` | Yes |
| `RETURNED_TO_REVIEW` | Review resumed | Loan Officer | Application returned by Approver | `UNDER_REVIEW` | No |
| `APPROVED` | Generate approved offer | System | Approved offer generation succeeds | `CUSTOMER_ACCEPTANCE_PENDING` | No |
| `CUSTOMER_ACCEPTANCE_PENDING` | Accept offer | Customer | Authenticated owner; offer pending; current time is before expiry | `CONTRACT_PENDING` | No |
| `CUSTOMER_ACCEPTANCE_PENDING` | Decline offer | Customer | Authenticated owner; offer pending | `CUSTOMER_DECLINED` | No |
| `CUSTOMER_ACCEPTANCE_PENDING` | Offer expires | System | Offer pending; current time is at or after expiry | `EXPIRED` | No |
| `CONTRACT_PENDING` | Required contract/disbursement documents ready | Staff or system | Documents accepted/not required/waived | `DISBURSEMENT_PENDING` | No |
| `DISBURSEMENT_PENDING` | Confirm manual disbursement | Accounting Officer | Approved, accepted, document-ready, bank account confirmed | `DISBURSED` | No |
| Any pre-`DISBURSED` non-terminal status | Cancel application | Customer or authorized back-office user | Cancellation allowed by actor/status rule | `CANCELLED` | Yes for staff cancellation |

---

## 9. Functional Requirements

| ID | Requirement |
| -- | ----------- |
| FR-CUST-001 | The system shall allow customers to register, log in, maintain their own profile, and provide identity, contact, residential, employment, bank account, and consent information required for loan submission. |
| FR-CUST-002 | The system shall restrict post-submission customer profile and bank account changes by application status and audit every approved change. |
| FR-IAM-001 | The system shall authenticate customer and back-office users before allowing portal access. |
| FR-IAM-002 | The system shall enforce role-based permissions using the role/action matrix and prevent users from performing actions outside assigned roles. |
| FR-PROD-001 | The system shall store and display active loan products from the product catalog with product limits, terms, rates, repayment method, required documents, and eligibility notes. |
| FR-PROD-002 | The system shall allow Back-Office Admins to manage product configuration, activation, and deactivation. |
| FR-APP-001 | The system shall support draft creation, submission, cancellation, status tracking, and transition control for all supported loan products through one common `LoanApplication` workflow. |
| FR-APP-002 | The system shall validate profile completeness, active product status, amount, term, product-specific information, checklist requirements, and concurrency rules before submission. |
| FR-APP-003 | The system shall prevent new submitted applications for the same product while another application for that product is active and non-terminal. |
| FR-SA-001 | The system shall allow Back-Office Admins to manage Partner Companies and monthly Partner Employee imports for Salary Advance eligibility. |
| FR-SA-002 | The system shall validate Partner Employee import rows, track import batches, enforce freshness rules, and prevent invalid, stale, inactive, or duplicate unresolved employee data from normal eligibility use. |
| FR-SA-003 | The system shall allow customers to verify Salary Advance employee status before creating a loan application and shall maintain a reusable customer employee link after successful verification or approved manual review. |
| FR-SA-004 | The system shall show Salary Advance employee verification status and current total, used, reserved, and available limit on the customer Salary Advance product page or dashboard. |
| FR-SA-005 | The system shall calculate and maintain Salary Advance limit state using product, partner, employee, salary cap, used exposure, reserved exposure, overdue exposure, employee status, and import freshness rules. |
| FR-SA-006 | The system shall block Salary Advance application creation or submission when the customer is not employee-verified, the limit is unavailable, stale, suspended, disabled, insufficient, or blocked by overdue exposure. |
| FR-SA-007 | The system shall reserve Salary Advance limit for submitted non-terminal applications, release reserved limit exactly once when applications terminate before disbursement including rejection, cancellation, customer decline, and offer expiry, convert reserved limit to used limit at disbursement, and release used limit through repayment or settlement policy. |
| FR-SA-008 | The system shall refresh customer employee links and Salary Advance limits when valid Partner Employee data changes. |
| FR-SA-009 | The system shall record a Salary Advance verification snapshot for each Salary Advance application, including the employee verification result and limit values used for that application. |
| FR-UCL-001 | The system shall support Unsecured Consumer Loan submission using requested amount, requested term, income/employment documents, document review, repayment capacity review, approval, acceptance, disbursement, activation, and repayment tracking. |
| FR-CL-001 | The system shall support Collateral Loan submission using requested amount, requested term, collateral information, collateral documents, manual collateral assessment notes, approval, acceptance, disbursement, activation, and repayment tracking. |
| FR-DOC-001 | The system shall allow customers and authorized back-office users to upload required documents and associate them with customer, application, collateral, contract, or disbursement records. |
| FR-DOC-002 | The system shall validate document checklist completeness separately from manual document review. |
| FR-DOC-003 | The system shall support document acceptance, rejection, waiver, replacement request, rejection reason capture, and readiness checks before disbursement. |
| FR-REV-001 | The system shall allow Loan Officers to review applications and recommend approval, recommend rejection, return to customer revision, or request staff correction. |
| FR-APR-001 | The system shall allow Approvers to approve, reject, return to Loan Officer review, request customer information, or request staff correction. |
| FR-APR-002 | The system shall enforce maker-checker separation between Loan Officer recommendation and Approver decision. |
| FR-OFFER-001 | The system shall generate one immutable approved financial-terms snapshot after approval, present confirmed customer-visible terms to the authenticated customer owner, support customer acceptance/decline, and expire pending offers after the configured validity period. Salary Advance approved offers include approved principal, approved term, flat monthly interest rate, total interest, 0 VND fees, total repayment amount, `ON_SALARY_DATE` repayment timing category, and provisional repayment items defined by Salary Advance rules, without exact calendar due dates or the final repayment schedule at offer time. |
| FR-CON-001 | The system shall support contract and disbursement document preparation after customer acceptance, including uploaded signed documents or manual confirmation for MVP handling. |
| FR-DIS-001 | The system shall allow only Accounting Officers to confirm manual disbursement after approval, customer acceptance, document readiness, and bank account confirmation. |
| FR-DIS-002 | The system shall move the application to `DISBURSED`, create the LoanAccount, generate the final repayment schedule, activate the LoanAccount, and audit all actions in one controlled post-disbursement transaction. |
| FR-REP-001 | The system shall generate and track repayment schedules, due amounts, paid amounts, outstanding balance, repayment status, overdue status, settlement, and administrative closure. |
| FR-PORTAL-001 | The Customer Web Portal shall support registration, login, profile completion, active product browsing, application submission, document upload, viewing the customer's own approved offer, customer-owned offer acceptance/decline, and status tracking. |
| FR-PORTAL-002 | The Back-Office Web Portal shall support product, partner, import, user, queue, review, approval, disbursement, repayment, and audit operations according to role permissions. |
| FR-AUD-001 | The system shall record audit trail entries for important business actions and status transitions, including actor, action, timestamp, affected entity, previous status, new status, and reason where applicable. |
| FR-AUD-002 | Audit records shall not be modified by normal users and must support maker-checker traceability. |

---

## 10. Business Rules

| ID | Rule |
| -- | ---- |
| BR-001 | A customer can submit applications only for active loan products. |
| BR-002 | A customer profile must be complete before loan application submission. |
| BR-003 | A loan application must pass required pre-submission validation before submission. |
| BR-004 | A customer may keep multiple drafts but cannot submit a new application for the same product while another application for that product is active and non-terminal. |
| BR-005 | Customers may cancel their own applications before approval. |
| BR-006 | Authorized back-office users may cancel applications before disbursement with a reason. |
| BR-007 | Disbursed applications cannot be cancelled. |
| BR-008 | Salary Advance requires a verified active customer employee link before normal application creation. |
| BR-009 | Salary Advance requested amount must be less than or equal to the active available Salary Advance limit. |
| BR-010 | Inactive Partner Companies cannot be manually overridden for normal Salary Advance eligibility. |
| BR-011 | Inactive Partner Employee records cannot be used for normal Salary Advance eligibility. |
| BR-012 | Blocking overdue Salary Advance exposure prevents new Salary Advance submission. |
| BR-013 | Existing active Salary Advance loans reduce used limit, and submitted non-terminal Salary Advance applications reduce reserved limit. |
| BR-014 | Salary Advance limit calculation and refresh must use the latest valid Partner Employee record within the configured freshness window. |
| BR-015 | Suspended, disabled, stale, unavailable, or insufficient Salary Advance limits block normal application creation and submission. |
| BR-016 | Each Salary Advance application must record a verification snapshot even when the customer's reusable employee link was verified earlier. |
| BR-017 | Salary Advance reserved limit must be released exactly once when an application is rejected, cancelled, customer-declined, expired, or otherwise released before disbursement, and the release must be part of the same controlled business operation as the terminal transition. |
| BR-018 | Salary Advance reserved limit must become used limit when manual disbursement creates the LoanAccount. |
| BR-019 | Salary Advance used limit must be released through repayment, settlement, or approved correction according to product policy. |
| BR-020 | Unsecured Consumer Loan requires income and employment document review but does not require collateral information. |
| BR-021 | Collateral Loan requires collateral information and collateral ownership or supporting documents. |
| BR-022 | Collateral estimated value is informational in MVP and does not trigger automated loan-to-value blocking. |
| BR-023 | Document checklist completeness and manual document review are separate controls. |
| BR-024 | Application submission may require uploaded or not-required checklist items depending on product policy. |
| BR-025 | Disbursement readiness requires required documents to be `ACCEPTED`, `NOT_REQUIRED`, or `WAIVED`. |
| BR-026 | Missing, rejected, expired, or replacement-required documents must route to the correct owner queue. |
| BR-027 | Loan Officer review and Approver decision must be separate responsibilities. |
| BR-028 | The same back-office user cannot record both the Loan Officer recommendation and final Approver decision for the same application. |
| BR-029 | Rejection, return, staff cancellation, request-more-information, staff correction, and manual override actions must include a reason. |
| BR-030 | Approved offer terms require authenticated customer acceptance before contract preparation and disbursement. |
| BR-031 | Approved offers expire when the current time is at or after `generatedAt + offerValidityDays` calendar days, defaulting to 7 calendar days. |
| BR-032 | Approval and disbursement must be separate responsibilities. |
| BR-033 | Disbursement can be marked completed only after approval, customer acceptance, document readiness, and bank account confirmation. |
| BR-034 | A LoanAccount is created only after manual disbursement confirmation. |
| BR-035 | LoanApplication `DISBURSED`, LoanAccount creation, final repayment schedule generation, and LoanAccount `ACTIVE` status are completed as one controlled post-disbursement transaction. |
| BR-036 | Post-submission customer bank account changes are restricted by application status and must be audited. |
| BR-036A | Until immutable application/disbursement snapshots exist, Customer profile and bank-account changes remain Customer-owned, audited, and not Loan-status-aware to avoid a Customer-to-Loan dependency cycle. |
| BR-037 | Repayment updates are manually entered or confirmed in the MVP. |
| BR-038 | Any unpaid repayment past due sets the LoanAccount to `OVERDUE`. |
| BR-039 | Full repayment or approved settlement sets the LoanAccount to `SETTLED`. |
| BR-040 | Administrative closure may move a settled LoanAccount to `CLOSED`. |
| BR-041 | Every important status transition must create an audit trail record. |
| BR-042 | For the current MVP, Approver approval approves the exact submitted amount and exact submitted term; any change to amount or term must return through review or correction rather than becoming a counteroffer. |
| BR-043 | Each Loan Application may have one approved offer for the current MVP; once presented, its approved financial terms snapshot is immutable, while offer lifecycle state and response metadata may change only through defined transitions. |
| BR-044 | Customer offer viewing and response actions must derive customer identity from authentication and verify ownership through the Loan Application. |
| BR-045 | Viewing an offer is read-only; offer expiry state changes are performed by system expiry processing or by guarded state-changing customer actions. |
| BR-046 | Same-action offer response retries are idempotent, while contradictory terminal offer actions must be rejected as conflicts. |
| BR-047 | Salary Advance approved principal equals the submitted requested amount, and approved term equals the submitted requested term, limited to 1, 2, or 3 months. |
| BR-048 | Salary Advance total interest is 1.2% flat interest per approved term month applied to the original approved principal: `unroundedTotalInterest = approvedPrincipal x 0.012 x approvedTermMonths`; `totalInterest` is rounded to whole VND using `HALF_UP`. |
| BR-049 | Salary Advance fees are 0 VND, and total repayment amount equals approved principal plus total interest. |
| BR-050 | Salary Advance provisional repayment items include one item per approved term month, use `ON_SALARY_DATE` as a timing category, and describe first, second, and third salary cycle after disbursement timing as applicable without exact calendar due dates. |
| BR-051 | Salary Advance provisional item principal and interest are allocated in whole VND, remainders are assigned to the final item, fee due is 0 VND for every item, each item total equals principal plus interest plus fee, and item sums must reconcile to approved principal, total interest, 0 VND fees, and total repayment amount with no unreconciled 1-VND difference. |
| BR-052 | A Salary Advance requested amount must be mathematically whole VND. Scale-only trailing zeros are valid; a non-zero fractional VND amount must be rejected before workflow or financial persistence. |

---

## 11. MVP Product Seed Configuration

This section defines initial product configuration values for the Meridian MVP.

These values are used for portfolio demonstration, validation, UI display, workflow testing, and seed data. They do not represent real lending products or real financial advice.

### 11.1 Product Catalog Seed Values

| Product Code              | Product Name            | Product Type   | Active | Minimum Amount | Maximum Amount  | Allowed Terms        | Interest Rate  | Repayment Method                            | Offer Validity  |
| ------------------------- | ----------------------- | -------------- | ------ | -------------- | --------------- | -------------------- | -------------- | ------------------------------------------- | --------------- |
| `SALARY_ADVANCE`          | Salary Advance          | `SALARY_BASED` | Yes    | 500,000 VND    | 20,000,000 VND  | 1, 2, 3 months       | 1.2% per month | Manual repayment tracking                   | 7 calendar days |
| `UNSECURED_CONSUMER_LOAN` | Unsecured Consumer Loan | `UNSECURED`    | Yes    | 2,000,000 VND  | 50,000,000 VND  | 3, 6, 9, 12 months   | 1.8% per month | Equal monthly installment, manually tracked | 7 calendar days |
| `COLLATERAL_LOAN`         | Collateral Loan         | `SECURED`      | Yes    | 5,000,000 VND  | 100,000,000 VND | 6, 12, 18, 24 months | 1.5% per month | Equal monthly installment, manually tracked | 7 calendar days |

For Salary Advance, allowed terms of 1, 2, and 3 months mean the requested and approved term length in months and the number of provisional repayment items generated for the approved offer.

The Salary Advance interest-rate column is the approved flat monthly interest rate for approved-offer calculation. It is applied to the original approved principal for each approved term month and rounded according to the Salary Advance policy rules below.

### 11.2 Salary Advance Policy Seed Values

| Policy Item                    | Seed Value                                                                                                            |
| ------------------------------ | --------------------------------------------------------------------------------------------------------------------- |
| Partner Company policy limit   | 20,000,000 VND                                                                                                        |
| Salary-based percentage cap    | 40% of monthly salary                                                                                                 |
| Employee configured limit      | Imported from monthly Partner Employee data                                                                           |
| Used limit rule                | Active disbursed Salary Advance exposure reduces used limit until repayment, settlement, or approved release          |
| Reserved limit rule            | Submitted non-terminal Salary Advance applications reserve limit until disbursement or release; drafts do not reserve limit |
| Blocking exposure rule         | Blocking overdue Salary Advance exposure prevents new Salary Advance application creation or submission               |
| Import freshness rule          | Latest valid active Partner Employee record for the configured effective month must be used                           |
| Limit status rule              | Suspended, disabled, stale, unavailable, or insufficient limit blocks normal application creation                     |
| Manual override rule           | `NOT_FOUND` and `MULTIPLE_MATCHES` may be reviewed manually; inactive Partner Companies cannot be manually overridden |

The Salary Advance policy seed uses the repayment method value `ON_SALARY_DATE`. This identifies the repayment timing category only. Approved-offer provisional repayment items describe the first, second, and third salary cycle after disbursement as applicable. Exact calendar due dates are not part of the approved offer and belong to the later final repayment schedule process.

Salary Advance approved-offer pricing:

```text
approvedPrincipal = submitted requested amount
approvedTermMonths = submitted requested term
unroundedTotalInterest = approvedPrincipal x 0.012 x approvedTermMonths
totalInterest = round unroundedTotalInterest to whole VND using HALF_UP
feeAmount = 0 VND
totalRepaymentAmount = approvedPrincipal + totalInterest
```

Salary Advance uses flat interest, not declining-balance interest. No fee is deducted from disbursement, added separately, charged as a percentage, or charged as a fixed processing fee.

Salary Advance provisional repayment items:

* one item is generated for each approved term month: 1 month creates 1 item, 2 months creates 2 items, and 3 months creates 3 items;
* each item includes installment number, principal due, interest due, fee due, total due, and repayment timing;
* repayment timing is expressed as first salary cycle after disbursement, second salary cycle after disbursement, and third salary cycle after disbursement as applicable;
* principal due is split evenly across items using whole VND, with any remainder assigned to the final item;
* interest due is allocated from total interest, split evenly across items using whole VND, with any remainder assigned to the final item;
* fee due is 0 VND for every item;
* item total due equals principal due plus interest due plus fee due.

Reconciliation invariants:

```text
sum(item.principalDue) = approvedPrincipal
sum(item.interestDue) = totalInterest
sum(item.feeDue) = 0 VND
sum(item.totalDue) = totalRepaymentAmount
```

All customer-visible Salary Advance monetary values are whole VND. Percentage interest is rounded to whole VND using `HALF_UP`; provisional item allocation uses whole-VND integer amounts, assigns remainders to the final item, and must not leave an unreconciled 1-VND difference.

Example calculation:

```text
totalSalaryAdvanceLimit = min(
  productMaximumAmount,
  partnerCompanyLimit,
  employeeConfiguredLimit,
  salaryBasedLimit
)

availableSalaryAdvanceLimit = totalSalaryAdvanceLimit
  - usedSalaryAdvanceAmount
  - reservedSalaryAdvanceAmount
```

### 11.3 Required Documents by Product

| Product Code              | Required MVP Documents                                                                                                                                      |
| ------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `SALARY_ADVANCE`          | Identity document if not already on profile; bank account proof if required by product policy; otherwise customer-uploaded documents may be `NOT_REQUIRED`. |
| `UNSECURED_CONSUMER_LOAN` | Identity document, proof of income, payslip or salary statement, bank statement, labor contract or employment confirmation.                                 |
| `COLLATERAL_LOAN`         | Identity document, collateral ownership document, collateral supporting photos or description attachment where applicable, bank account proof if required.  |

### 11.4 Collateral Loan Policy Seed Values

| Policy Item                        | Seed Value                                                      |
| ---------------------------------- | --------------------------------------------------------------- |
| Supported collateral types         | `MOTORBIKE`, `CAR`, `ELECTRONICS`, `PROPERTY_DOCUMENT`, `OTHER` |
| Estimated collateral value         | Informational for manual review only                            |
| Automated loan-to-value validation | Not enforced in MVP                                             |
| Collateral decision model          | Manual Loan Officer assessment                                  |
| Required collateral review note    | Yes                                                             |

### 11.5 Offer Validity

MVP default offer validity is 7 calendar days from approved-offer generation.

Offer validity should be configurable by product.

The expiry timestamp is calculated as:

```text
expiresAt = generatedAt + offerValidityDays calendar days
```

A pending offer is expired when the current time is at or after `expiresAt`.

Offer expiry must not depend only on the customer returning to the application. The system must include expiry processing that eventually finds overdue pending offers and expires them. Customer actions such as accept must also enforce expiry before completing the action, so an already-expired offer cannot be accepted between expiry processing runs.

Viewing an offer is read-only. It must not change application status, mark the offer expired, release Salary Advance reservation, or create financial movements.

If the customer does not accept a Salary Advance approved offer before it expires, the system moves the application to `EXPIRED`, marks the offer expired, releases the reserved Salary Advance amount exactly once, and records an audit event.


---

## 12. Data Entities and Non-Functional Requirements

### 12.1 Conceptual Data Entities

Initial conceptual entities identified from the functional requirements:

* User;
* Customer;
* BackOfficeUser;
* RoleAssignment;
* LoanProduct;
* LoanProductPolicy;
* LoanApplication;
* LoanAccount;
* PartnerCompany;
* PartnerEmployee;
* PartnerEmployeeImportBatch;
* CustomerPartnerEmployeeLink;
* SalaryAdvanceLimit;
* SalaryAdvanceLimitMovement;
* SalaryAdvanceVerification;
* ProductVerificationResult;
* Document;
* DocumentChecklist;
* DocumentChecklistItem;
* Collateral;
* ReviewRecommendation;
* ApprovalDecision;
* CustomerAcceptance;
* OfferTerms;
* ContractDocumentRecord;
* DisbursementRecord;
* RepaymentSchedule;
* RepaymentRecord;
* AuditEvent.

### 12.2 Non-Functional Requirements

| Category | Requirement |
| -------- | ----------- |
| Security | The system must enforce role-based access control for customer and back-office actions. |
| Auditability | Important business actions and status transitions must be traceable. |
| Data Integrity | Loan status transitions must be controlled by business rules. |
| Reliability | Failed operations should not create inconsistent loan application, document, disbursement, schedule, or repayment states. |
| Maintainability | Business modules should remain organized by bounded context within a modular monolith. |
| Privacy | Personal, employment, financial, and collateral data must not be exposed unnecessarily. |
| Extensibility | New loan products should be supported through product-specific policy components inside the generic lending core. |

---

## 13. MVP Priority and Boundaries

### 13.1 Must Have

One backend and one database; Customer Web Portal; Back-Office Web Portal; customer and back-office authentication; role-based access control; customer profile completion; loan product catalog; product activation/deactivation; common loan application workflow; transition matrix enforcement; Salary Advance workflow; Partner Company management; monthly Partner Employee import; import validation and freshness handling; reusable employee verification; Salary Advance limit dashboard, calculation, reservation, refresh, suspension, disablement, and release; Unsecured Consumer Loan workflow; Collateral Loan workflow; document checklist configuration; checklist completeness validation; manual document review; Loan Officer review; Approver decision; maker-checker same-user prevention; customer acceptance; provisional repayment schedule; offer expiry; manual disbursement confirmation; LoanAccount creation and activation; final repayment schedule; repayment tracking; settlement and closure tracking; audit trail.

### 13.2 Should Have

Customer application history, back-office application queue, collateral assessment notes, import batch tracking, repayment status update screens, and simple dashboard views.

### 13.3 Could Have

Notification service, OCR-assisted document extraction, simple analytics dashboards, and a lightweight mobile app after the Customer Web Portal is stable.

### 13.4 Won't Have in MVP

Real bank transfer, real payroll integration, real employer API integration, real payment gateway, real SMS OTP, biometric login, real credit bureau integration, full collateral valuation/legal enforcement, double-entry ledger, production compliance workflow, microservices, full mobile app, automated credit scoring, full e-signature integration, external asset registry integration, savings deposit products, entrusted loans, or corporate loans.

---

## 14. Open Questions and Future Enhancements

No MVP-blocking open questions remain in this draft.

Future releases may revisit:

* multi-level approval;
* automated repayment simulation;
* automated credit scoring;
* simple collateral loan-to-value validation;
* full e-signature integration;
* mobile app support;
* notification service;
* external integrations for credit bureau, payment gateway, employer systems, payroll, bank transfer, and asset registry.

---

## 15. Design Principles

1. Use one common lending core for all products.
2. Handle product-specific rules through product policies.
3. Keep Salary Advance deeper than the other MVP products.
4. Keep Unsecured Consumer Loan and Collateral Loan streamlined.
5. Accept manual review and manual operational confirmations for MVP scope.
6. Keep real financial integrations out of scope.
7. Audit every important status change.
8. Separate approval and disbursement responsibilities.
9. Keep customer-facing steps clear and simple.
10. Make the workflow realistic without becoming a production banking core.
