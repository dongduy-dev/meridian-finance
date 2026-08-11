# MER-API-001 — Meridian HTTP API v1 Contract

## Purpose and Authority

This document defines Meridian’s client-facing HTTP contract under `/api/v1`: methods, paths, authentication, permissions, request parameters, externally visible responses, pagination, idempotency, ownership concealment, sensitive-data disclosure, and operation-specific errors.

It does not define bounded-context ownership, Java controller structure, persistence rows, transaction lock order, migration history, or delivery progress. A capability not listed in the endpoint catalogue is not part of the v1 HTTP surface.

---

## 1. Common Conventions

### 1.1 Transport

- Standard media type: `application/json`
- Document upload: `multipart/form-data`
- Document content: streamed attachment
- UUIDs use canonical UUID text.
- Dates use `YYYY-MM-DD`; timestamps use ISO-8601 UTC representations.
- Salary Advance monetary inputs must represent whole VND. `3000000`, `3000000.0`, and `3000000.00` are valid; fractional VND is rejected.

### 1.2 Authentication and authorization

Public endpoints:

- `GET /api/v1/health`
- `POST /api/v1/auth/login`
- `GET /api/v1/loan-products`

All other endpoints require:

```text
Authorization: Bearer <accessToken>
```

Meridian uses stateless Bearer authentication. HTTP Basic, form login, server sessions, refresh-token endpoints, and logout endpoints are not part of v1.

| Failure | Response |
|---|---|
| Missing or invalid authentication | `401 AUTHENTICATION_REQUIRED` |
| Authenticated actor lacks permission | `403 ACCESS_DENIED` |

Customer-facing operations derive `customerId` from the authenticated principal. Staff actor IDs are likewise token-derived and are not accepted in recommendation, decision, review, disbursement, or repayment request bodies.

### 1.3 Error envelope

```json
{
  "timestamp": "2026-08-04T12:00:00Z",
  "status": 409,
  "errorCode": "IDEMPOTENCY_KEY_REUSED",
  "message": "Safe client-facing message.",
  "path": "/api/v1/..."
}
```

| Status | Meaning |
|---|---|
| `400` | Malformed input or validation failure |
| `401` | Authentication failure |
| `403` | Permission, ownership, or maker-checker denial |
| `404` | Missing or intentionally concealed resource |
| `409` | State, duplicate, concurrency, or idempotency conflict |
| `422` | Business-rule violation |
| `503` | Required protected capability unavailable |

`MER-ARCH-004-api-error-catalog.md` owns the complete error-code catalogue and safe messages.

### 1.4 Ownership concealment

Customer-owned read APIs may return the same generic `404` for a nonexistent resource, another Customer’s resource, or a resource unavailable in the requested lifecycle state. Authorized Staff reads may retain more accurate operational distinctions.

### 1.5 Idempotency

| Operation | Request UUID field | Durable replay semantics |
|---|---|---|
| Customer or Staff document upload/replacement | multipart `uploadRequestId` | Returns the same immutable document version; conflicting logical content is rejected. |
| Document review/waiver/replacement decision | `reviewRequestId` | Returns the recorded immutable review outcome; conflicting logical content is rejected. |
| Correction task completion | `completionRequestId` | Returns the completed task without a second completion effect. |
| Correction resubmission | `resubmissionRequestId` | Returns the consumed resubmission result without another verification or transition. |
| Customer correction cancellation | `requestId` | Returns the durable cancellation result without another terminal transition, reservation release, movement, history entry, or audit event. |
| Contract preparation/regeneration | `preparationRequestId` | Returns the same prepared contract version. |
| Contract acknowledgment | `acknowledgmentRequestId` | Returns the same acknowledgment result for the exact version. |
| Readiness confirmation | `confirmationRequestId` | Returns the same confirmed-readiness result without another transition. |
| Disbursement confirmation | `requestId` | Returns the durable activation result without another transfer, account, schedule, or exposure effect. |
| Repayment posting | `requestId` | Returns the immutable operation outcome captured at first execution. |
| Administrative Full-Balance Settlement | `requestId` | Returns the immutable settlement outcome without another payment, allocation, exposure, history, or audit effect. |
| Administrative LoanAccount closure | `requestId` | Returns the immutable closure result without another status, closure-evidence, history, or audit effect. |

An identical logical replay returns the original result without another business effect. Reuse with different logical content returns `409 IDEMPOTENCY_KEY_REUSED` without identifying the protected field that differed.

### 1.6 Sensitive-data boundary

Ordinary JSON responses must not expose passwords, tokens, unrestricted identity evidence, full bank-account numbers, cryptographic envelope fields, document storage metadata, restricted Staff notes, external transfer/payment references, or internal audit/history identifiers.

The contractual destination-reveal operation is the sole v1 JSON endpoint permitted to return the full immutable disbursement account number.

---

## 2. Endpoint Catalogue

### 2.1 Public, Customer, and Partner

| Method | Path | Authorization | Summary |
|---|---|---|---|
| GET | `/api/v1/health` | Public | Return versioned health status. |
| POST | `/api/v1/auth/login` | Public | Authenticate and return Bearer-token actor facts. |
| GET | `/api/v1/loan-products` | Public | List active loan products. |
| GET | `/api/v1/customers/me` | `customer:profile:read:own` | Return the authenticated Customer’s safe profile-readiness view. |
| PUT | `/api/v1/customers/me/profile` | `customer:profile:write:own` | Create or update the authenticated Customer profile. |
| GET | `/api/v1/customers/me/bank-accounts` | `customer:bank-account:read:own` | List masked owned bank accounts. |
| POST | `/api/v1/customers/me/bank-accounts` | `customer:bank-account:write:own` | Add a bank account; the first active account becomes primary. |
| POST | `/api/v1/customers/me/bank-accounts/{customerBankAccountId}/make-primary` | `customer:bank-account:write:own` | Make an active owned account primary. |
| POST | `/api/v1/customers/me/bank-accounts/{customerBankAccountId}/deactivate` | `customer:bank-account:write:own` | Deactivate an owned account subject to primary-account rules. |
| GET | `/api/v1/partner-companies` | `partner:read` | List Partner Companies. |
| GET | `/api/v1/partner-companies/{partnerCompanyId}` | `partner:read` | Return one Partner Company. |
| GET | `/api/v1/partner-companies/{partnerCompanyId}/employees?activeOnly=false` | `partner:read` | List Partner Employees; `activeOnly` defaults to `false`. |
| GET | `/api/v1/partner-companies/{partnerCompanyId}/employee-import-batches` | `partner:read` | List employee import batches. |
| POST | `/api/v1/partner-companies/{partnerCompanyId}/employee-verifications` | `partner:employee:verify:own` | Verify the authenticated Customer and create/reuse an eligible employee link. |

### 2.2 Origination, review, approval, corrections, and documents

| Method | Path | Authorization | Summary |
|---|---|---|---|
| GET | `/api/v1/loan-products/salary-advance/readiness` | Customer with `loan:submit` | Return the authenticated Customer's advisory Salary Advance eligibility and safe limit view. |
| POST | `/api/v1/loan-applications/salary-advance` | `loan:submit` | Submit a Salary Advance application and reserve eligible limit. |
| POST | `/api/v1/loan-applications/unsecured-consumer-loan` | Customer with `loan:submit` | Submit an Unsecured Consumer Loan application for manual verification and required evidence collection. |
| GET | `/api/v1/loan-applications/{loanApplicationId}` | Customer `loan:read:own` or Staff `loan:read` | Return a safe durable LoanApplication status projection. |
| POST | `/api/v1/loan-applications/{loanApplicationId}/cancel` | Customer with `loan:cancel:own` | Cancel an owned Salary Advance only from `RETURNED_FOR_REVISION` and release its reservation exactly once. |
| POST | `/api/v1/loan-applications/{loanApplicationId}/unsecured-consumer-loan-verification/start` | Staff with `loan:review` | Start positive-path manual UCL verification after document processing readiness. |
| POST | `/api/v1/loan-applications/{loanApplicationId}/unsecured-consumer-loan-verification/complete` | Staff with `loan:review` | Complete positive-path manual UCL verification as `VERIFIED`. |
| POST | `/api/v1/loan-applications/{loanApplicationId}/review/start` | `loan:review` | Start Loan Officer review. |
| POST | `/api/v1/loan-applications/{loanApplicationId}/review-recommendations` | `approval:recommend` | Record a Loan Officer recommendation. |
| POST | `/api/v1/loan-applications/{loanApplicationId}/approval-decisions` | `approval:decide` | Record an Approver decision. |
| GET | `/api/v1/loan-applications/{loanApplicationId}/corrections/tasks` | `loan:correction:own` | List owned Customer correction tasks. |
| POST | `/api/v1/loan-applications/{loanApplicationId}/corrections/tasks/{taskId}/complete` | `loan:correction:own` | Complete an owned task after evidence exists. |
| POST | `/api/v1/loan-applications/{loanApplicationId}/corrections/resubmit` | `loan:correction:own` | Resubmit an eligible Customer-only correction. |
| POST | `/api/v1/loan-applications/{loanApplicationId}/documents/{checklistItemId}/versions` | `document:upload:own` | Upload or replace an owned document version. |
| GET | `/api/v1/loan-applications/{loanApplicationId}/documents/{checklistItemId}/versions/{documentVersionId}/content` | `document:read:own` | Stream an owned immutable version. |
| GET | `/api/v1/document-review-items?status=AWAITING_REVIEW` | `document:review` | List current versions awaiting review. |
| POST | `/api/v1/loan-applications/{loanApplicationId}/document-review-items/{checklistItemId}/reviews` | `document:review` | Review the exact current version. |
| GET | `/api/v1/staff-corrections/tasks?status=OPEN&page=0&size=20` | `loan:correction:staff` | List Staff-owned correction tasks. |
| POST | `/api/v1/staff-corrections/tasks/{taskId}/complete` | `loan:correction:staff` | Complete a Staff task with proof and maker-checker enforcement. |
| POST | `/api/v1/staff-corrections/loan-applications/{loanApplicationId}/resubmit` | `loan:correction:staff` | Resubmit an eligible Staff-only or mixed correction. |
| POST | `/api/v1/staff/loan-applications/{loanApplicationId}/documents/{checklistItemId}/versions` | `document:upload:staff` | Upload for an open Staff upload task. |
| GET | `/api/v1/staff/loan-applications/{loanApplicationId}/documents/{checklistItemId}/versions/{documentVersionId}/content` | `document:review` | Stream a review-authorized immutable version. |

### 2.3 Offers, contracts, disbursement, account, and servicing

| Method | Path | Authorization | Summary |
|---|---|---|---|
| GET | `/api/v1/loan-applications/{loanApplicationId}/approved-offer` | `loan:read:own` | Return the Customer’s approved offer without mutating state. |
| POST | `/api/v1/loan-applications/{loanApplicationId}/approved-offer/accept` | `loan:offer:respond:own` | Accept a valid pending offer. |
| POST | `/api/v1/loan-applications/{loanApplicationId}/approved-offer/decline` | `loan:offer:respond:own` | Decline a valid pending offer and release reservation once. |
| POST | `/api/v1/loan-applications/{loanApplicationId}/contracts` | `loan:contract:prepare` | Prepare version 1 or regenerate the current contract. |
| GET | `/api/v1/loan-applications/{loanApplicationId}/contracts/current` | `loan:read:own` or `loan:contract:read` | Return the safe masked current contract. |
| POST | `/api/v1/loan-applications/{loanApplicationId}/contracts/current/acknowledgment` | `loan:contract:acknowledge:own` | Acknowledge the exact current version. |
| GET | `/api/v1/loan-applications/{loanApplicationId}/contracts/current/readiness` | `loan:contract:read` | Calculate point-in-time readiness; optional `expectedContractVersion`. |
| POST | `/api/v1/loan-applications/{loanApplicationId}/contracts/current/readiness/confirm` | `loan:disbursement:prepare` | Recompute and confirm readiness. |
| POST | `/api/v1/loan-applications/{loanApplicationId}/contracts/current/disbursement-destination/reveal` | `loan:disburse` | Reveal the full immutable ready-contract destination. |
| POST | `/api/v1/loan-applications/{loanApplicationId}/disbursements` | `loan:disburse` | Confirm an external transfer and activate the LoanAccount. |
| GET | `/api/v1/loan-applications/{loanApplicationId}/loan-account` | `loan:read:own` or `loan:read` | Return originated terms, final schedule, and servicing state. |
| POST | `/api/v1/loan-applications/{loanApplicationId}/repayments` | `repayment:update` | Record or replay a manual Salary Advance repayment. |
| GET | `/api/v1/loan-applications/{loanApplicationId}/repayments?page=0&size=20` | `loan:read:own` or `loan:read` | Return immutable paged repayment history. |
| POST | `/api/v1/loan-applications/{loanApplicationId}/settlements` | `loan:settlement:approve` plus Approver role | Approve and apply an Administrative Full-Balance Settlement. |
| POST | `/api/v1/loan-applications/{loanApplicationId}/loan-account/closure` | `loan:account:close` plus Accounting Officer role | Close an eligible settled LoanAccount administratively. |

---

## 3. Authentication, Customer, and Partner Requests

### 3.1 Login

```json
{
  "email": "customer.demo@meridian.local",
  "password": "<password>"
}
```

Successful response fields:

- `tokenType`
- `accessToken`
- `expiresAt`
- `userId`
- `email`
- `userType`
- `customerId`
- `roles`
- `permissions`

`customerId` may be `null` for Staff principals.

### 3.2 Customer profile

```json
{
  "fullName": "Customer Demo",
  "identityReference": "IDREF-MER-001",
  "phoneNumber": "0901234567",
  "residentialAddress": "1 Meridian Street, District 1, Ho Chi Minh City",
  "employmentStatus": "SALARIED",
  "employerName": "Meridian Partner Co.",
  "termsConsentAccepted": true,
  "dataProcessingConsentAccepted": true
}
```

`identityReference` is accepted only on profile update and is never returned. Duplicate normalized evidence owned by another Customer returns `409 IDENTITY_REFERENCE_ALREADY_IN_USE`.

### 3.3 Add bank account

```json
{
  "bankCode": "VCB",
  "bankNameSnapshot": "Vietcombank",
  "accountHolderName": "Customer Demo",
  "accountNumber": "1234567890"
}
```

The account number is normalized by removing spaces and hyphens, must contain at least six normalized characters, and is returned only as a masked value plus last-four representation.

### 3.4 Employee verification

```json
{
  "employeeCode": "MER-EMP-001"
}
```

The body does not accept `customerId` or `identityReference`.

Safe response fields: `customerId`, `partnerCompanyId`, `partnerEmployeeId`, `customerPartnerEmployeeLinkId`, `outcome`, `linkStatus`, and `manualReviewRequired`.

Responses exclude salary, limit values, employee code, identity evidence, and raw matching evidence.

---

## 4. Salary Advance and UCL Origination

### 4.1 Salary Advance readiness

```text
GET /api/v1/loan-products/salary-advance/readiness
```

This Customer-only read requires `loan:submit` and derives Customer identity from the Bearer token. It is advisory: it uses non-locking reads, creates no application or limit, reserves no exposure, writes no movement, verification, history, or audit evidence, and does not promise that a later command will succeed after concurrent state changes.

The response contains `productCode`, the reusable `customerPartnerEmployeeLinkId` when currently eligible, `employeeVerificationStatus`, `partnerEligibilityStatus`, `limitStatus`, `totalAmount`, `usedAmount`, `reservedAmount`, `availableAmount`, `lastRefreshAt`, `applicationAllowed`, and ordered `blockerCodes`. It excludes Customer identity, Partner Employee and import-batch identity, Partner salary/evidence, Salary Advance limit and verification identity, workflow recommendations, and internal audit/history evidence.

Important blockers include Customer/profile/bank readiness, `EMPLOYEE_NOT_VERIFIED`, `SALARY_ADVANCE_ELIGIBILITY_DATA_STALE`, `SALARY_ADVANCE_LIMIT_UNAVAILABLE`, `INSUFFICIENT_AVAILABLE_LIMIT`, `BLOCKING_APPLICATION_EXISTS`, `OUTSTANDING_LOAN_ACCOUNT_EXISTS`, `PRODUCT_NOT_AVAILABLE`, and safe `SYSTEM_STATE_CONFLICT`. Current eligibility requires the authoritative latest valid completed Partner import batch for the current UTC effective month; stale evidence remains blocked until re-verification refreshes the reusable link.

### 4.2 LoanApplication status read

```text
GET /api/v1/loan-applications/{loanApplicationId}
```

An authenticated Customer with `loan:read:own` may read only their own application. Missing and foreign-owned IDs both return `404 LOAN_APPLICATION_NOT_FOUND`. Authorized Staff require `loan:read`; `loan:submit`, `repayment:update`, `approval:decide`, and document permissions do not imply this read.

The response contains only `loanApplicationId`, `applicationNumber`, `productCode`, `productType`, `requestedAmount`, `requestedTermMonths`, `status`, and `submittedAt`. It excludes Customer, employee-link, limit, verification, review-cycle, actor, audit/history, payment, and banking evidence. This is a durable status projection for reconnect/resume flows, not a next-action engine, command recommendation, history API, or Staff work queue.

`CANCELLED` is executable in v0.1.0 only through the Customer-owned correction-abandonment command described in Section 5.4. Broader Customer cancellation from other states and every Staff or administrative cancellation policy remain deferred.

### 4.3 Submit application

```json
{
  "customerPartnerEmployeeLinkId": "UUID",
  "requestedAmount": 3000000.00,
  "requestedTermMonths": 1
}
```

Success returns `201 Created`.

The authenticated Customer must satisfy Customer readiness, Partner eligibility, product, amount, term, document, blocking-application, outstanding-debt, and available-limit requirements.

Important errors:

| Status | Code |
|---|---|
| `409` | `BLOCKING_APPLICATION_EXISTS` |
| `409` | `OUTSTANDING_LOAN_ACCOUNT_EXISTS` |
| `422` | `EMPLOYEE_NOT_VERIFIED` |
| `422` | `SALARY_ADVANCE_ELIGIBILITY_DATA_STALE` |
| `422` | `INVALID_PRODUCT_AMOUNT` |
| `422` | Applicable Salary Advance eligibility or limit code from the error catalogue |

### 4.4 Submit Unsecured Consumer Loan application

```text
POST /api/v1/loan-applications/unsecured-consumer-loan
```

The authenticated Customer supplies only the requested amount and term:

```json
{
  "requestedAmount": 5000000,
  "requestedTermMonths": 6
}
```

The API derives Customer identity from the Bearer token and does not accept `customerId`. The Customer must be active, have a complete required profile and a primary active bank account, and hold `loan:submit`. The active catalog product must be `UNSECURED_CONSUMER_LOAN` / `UNSECURED`. Amounts must be whole VND from 2,000,000 through 50,000,000 inclusive. Supported terms are 3, 6, 9, and 12 months.

Success returns `201 Created` with `loanApplicationId`, `applicationNumber`, `productCode`, `productType`, `status`, `requestedAmount`, `requestedTermMonths`, `productVerificationResult`, and `submittedAt`. Loan records the application in `DOCUMENTS_PENDING`, stores `PENDING_MANUAL_REVIEW` as its product-verification result, and creates required `INCOME_PROOF`, `BANK_STATEMENT`, and `EMPLOYMENT_PROOF` checklist items.

The endpoint stops at origination and evidence setup. Separate Staff commands perform positive manual verification and review entry. UCL negative-verification outcomes, correction, final approval, pricing, offer, contract, activation, cancellation, and servicing are not executable.

Important errors:

| Status | Code |
|---|---|
| `404` | `CUSTOMER_NOT_FOUND` or `PRODUCT_NOT_FOUND` |
| `409` | `CUSTOMER_NOT_ACTIVE` or `BLOCKING_APPLICATION_EXISTS` |
| `422` | `PROFILE_INCOMPLETE`, `PRIMARY_BANK_ACCOUNT_REQUIRED`, `PRODUCT_INACTIVE`, `PRODUCT_POLICY_INVALID`, `INVALID_PRODUCT_AMOUNT`, or `INVALID_PRODUCT_TERM` |

### 4.5 Start UCL manual verification

```text
POST /api/v1/loan-applications/{loanApplicationId}/unsecured-consumer-loan-verification/start
```

No body is required. The authenticated Staff actor must hold `loan:review`. The application must be `UNSECURED_CONSUMER_LOAN` / `UNSECURED`, be in `SUBMITTED`, have an existing `PENDING_MANUAL_REVIEW` verification record, and have a processing-ready submission checklist. Success atomically moves the application to `VERIFICATION_PENDING` and records status history and a PII-safe business audit event. It does not create a review cycle or modify the verification result.

Representative response:

```json
{
  "loanApplicationId": "UUID",
  "status": "VERIFICATION_PENDING",
  "productVerificationResult": "PENDING_MANUAL_REVIEW",
  "reviewedAt": null
}
```

Important errors include `UCL_VERIFICATION_NOT_APPLICABLE`, `UCL_VERIFICATION_REQUIRED`, `UCL_VERIFICATION_DOCUMENTS_NOT_READY`, `PRODUCT_VERIFICATION_NOT_PENDING`, and `PRODUCT_VERIFICATION_START_NOT_ALLOWED`.

### 4.6 Complete UCL manual verification

```text
POST /api/v1/loan-applications/{loanApplicationId}/unsecured-consumer-loan-verification/complete
```

The request contains only a required restricted assessment note:

```json
{
  "assessmentNote": "Income and employment evidence are consistent for Loan Officer review."
}
```

Unknown properties and blank or over-2,000-character notes return `400 VALIDATION_FAILED`. The application must be in `VERIFICATION_PENDING`, the existing result must remain `PENDING_MANUAL_REVIEW`, and documents must still be processing-ready. Success stores `VERIFIED` with the authenticated Staff actor, one sampled UTC completion time, and the restricted note; moves the application back to `SUBMITTED`; and records history and audit atomically. The response does not expose the Staff actor or assessment note:

```json
{
  "loanApplicationId": "UUID",
  "status": "SUBMITTED",
  "productVerificationResult": "VERIFIED",
  "reviewedAt": "2026-08-11T09:30:00"
}
```

This command implements only the positive `VERIFIED` outcome. `FAILED` and `REQUIRES_MORE_INFORMATION` remain non-executable.

Important errors include `UCL_VERIFICATION_NOT_APPLICABLE`, `UCL_VERIFICATION_REQUIRED`, `UCL_VERIFICATION_DOCUMENTS_NOT_READY`, `PRODUCT_VERIFICATION_NOT_PENDING`, `PRODUCT_VERIFICATION_COMPLETION_NOT_ALLOWED`, and `UCL_VERIFICATION_ASSESSMENT_REQUIRED`.

### 4.7 Start review

```text
POST /api/v1/loan-applications/{loanApplicationId}/review/start
```

No body is required. The reviewer is derived from the Bearer token.

For UCL, review start additionally requires the authoritative verification result to be `VERIFIED`. Missing, pending, `FAILED`, and `REQUIRES_MORE_INFORMATION` records fail closed. The same Loan Officer may complete UCL manual verification and start review; the existing maker-checker rule still requires a different Approver for the final decision.

---

## 5. Recommendation, Approval, Corrections, and Documents

### 5.1 Review recommendation

Supported actions:

- `RECOMMEND_APPROVAL`
- `RECOMMEND_REJECTION`
- `RETURN_TO_CUSTOMER_REVISION`
- `REQUEST_STAFF_CORRECTION`

Normal recommendation:

```json
{
  "action": "RECOMMEND_APPROVAL",
  "reason": "Application and verification snapshot reviewed.",
  "internalNotes": "Optional staff-only note."
}
```

Rejection requires a nonblank `reason`. Revision actions require `expectedReviewCycleId`, a controlled `reasonCode`, and one to ten tasks.

For UCL, only `RECOMMEND_APPROVAL` and `RECOMMEND_REJECTION` are executable. UCL Customer or Staff correction recommendations return `409 UCL_CORRECTION_NOT_READY` before any correction request or task is created.

Representative Customer task:

```json
{
  "action": "RETURN_TO_CUSTOMER_REVISION",
  "reason": null,
  "internalNotes": "Optional restricted note.",
  "expectedReviewCycleId": "UUID",
  "reasonCode": "RECENT_PAYSLIP_REQUIRED",
  "correctionPlan": {
    "tasks": [
      {
        "scope": "SUPPORTING_DOCUMENT_UPLOAD",
        "responsibleParty": "CUSTOMER",
        "documentType": "RECENT_PAYSLIP",
        "createChecklistItem": true,
        "checklistItemId": null,
        "baselineDocumentVersionId": null,
        "customerInstruction": "Upload a recent payslip for clarification.",
        "staffInstruction": null
      }
    ]
  }
}
```

Staff tasks use `responsibleParty = STAFF` with `SUPPORTING_DOCUMENT_UPLOAD` or `DOCUMENT_REVIEW`.

### 5.2 Approval decision

Supported actions:

- `APPROVE`
- `REJECT`
- `RETURN_TO_LOAN_OFFICER_REVIEW`
- `REQUEST_CUSTOMER_OR_STAFF_CORRECTION`

```json
{
  "action": "APPROVE",
  "reason": "Optional for approval; required for reject or return.",
  "internalNotes": "Optional staff-only note."
}
```

The Approver must differ from the Loan Officer who submitted the applicable recommendation. Mixed corrections use separate Customer and Staff tasks.

For UCL, `REJECT` and `RETURN_TO_LOAN_OFFICER_REVIEW` remain available common decisions. `APPROVE` returns `409 UCL_OFFER_EXECUTION_NOT_READY`, and structured correction returns `409 UCL_CORRECTION_NOT_READY`. Both fail before durable decision, transition, correction, or offer effects. UCL cannot reach `APPROVED` until its pricing and approved-offer execution are implemented.

Important errors include `MAKER_CHECKER_VIOLATION`, `STALE_REVIEW_CYCLE`, and controlled reason/plan validation errors.

### 5.3 Task completion and resubmission

Completion:

```json
{ "completionRequestId": "UUID" }
```

Resubmission:

```json
{ "resubmissionRequestId": "UUID" }
```

Client-visible rules:

- a task cannot complete before its required upload or review proof exists;
- the Staff member who created the correction request cannot complete its Staff tasks;
- a mixed request cannot be resubmitted after only Customer work is complete;
- identical requests replay safely;
- a different request after completion or resubmission conflicts.

Important codes: `STAFF_CORRECTION_MAKER_CHECKER_VIOLATION`, `CORRECTION_TASK_PROOF_MISSING`, `CORRECTION_TASKS_INCOMPLETE`, `CORRECTION_RESUBMISSION_DENIED`, and `CORRECTION_ALREADY_RESUBMITTED`.

### 5.4 Customer cancellation from returned correction

```http
POST /api/v1/loan-applications/{loanApplicationId}/cancel
Authorization: Bearer <customer-token>
Content-Type: application/json
```

```json
{ "requestId": "UUID" }
```

This narrow command requires an authenticated Customer with `loan:cancel:own`, derives Customer identity from the token, and accepts no Customer ID or financial amount. Missing and foreign-owned applications both return `404 LOAN_APPLICATION_NOT_FOUND`.

The only allowed source status is `RETURNED_FOR_REVISION`. A new request against any other status, including an application already cancelled by another request, returns `409 LOAN_APPLICATION_CANCELLATION_NOT_ALLOWED`. An exact replay of the successful request UUID returns the original `CANCELLED` result with `idempotentReplay = true`.

Success atomically marks the active correction request `CANCELLED`, changes the LoanApplication to `CANCELLED`, releases the application’s repository-derived Salary Advance reservation back to available exposure, writes exactly one `RESERVATION_RELEASED` movement, and records immutable history and audit evidence. Cancellation does not run Partner freshness or re-verification; stale or otherwise unusable Partner evidence cannot prevent abandonment. Cancellation changes neither used exposure nor LoanAccount or repayment state.

### 5.5 Document upload and review

Upload is multipart with:

- `uploadRequestId`
- optional `expectedCurrentVersionId`
- `file`

Accepted types are PDF, JPEG, and PNG, up to 10 MiB, with signature-to-media-type matching. A stale replacement baseline returns `409 STALE_DOCUMENT_VERSION`.

Review targets the exact `documentVersionId` and supports:

- `ACCEPT_DOCUMENT`
- `WAIVE_DOCUMENT`
- `REQUEST_REPLACEMENT`

Waiver requires `document:waive` and an allowed waiver code. Replacement requires `DOCUMENT_REPLACEMENT_REQUIRED` plus a Customer-visible instruction. Reviewing a non-current version returns `409 STALE_DOCUMENT_VERSION`.

Content responses stream only the authorized immutable version and include attachment, `Cache-Control: no-store, private`, and `X-Content-Type-Options: nosniff`.

---

## 6. Approved Offer and Contract

### 6.1 Approved offer

```text
GET  /api/v1/loan-applications/{loanApplicationId}/approved-offer
POST /api/v1/loan-applications/{loanApplicationId}/approved-offer/accept
POST /api/v1/loan-applications/{loanApplicationId}/approved-offer/decline
```

Response actions require no body.

Safe offer data includes approved principal and term, pricing method, flat monthly rate, interest, fee, total repayment, repayment method, generated/expiry times, effective status, available actions, and provisional repayment items. Provisional items do not contain final calendar due dates.

`GET` is read-only. Acceptance moves the application to `CONTRACT_PENDING`. Decline or first discovery of pending expiry applies the terminal outcome and reservation release exactly once.

Important conflicts: `OFFER_EXPIRED` and `OFFER_ACTION_CONFLICT`.

### 6.2 Prepare or regenerate contract

Version 1:

```json
{
  "preparationRequestId": "UUID",
  "expectedCurrentContractVersion": 0,
  "supersessionReasonCode": null
}
```

Regeneration:

```json
{
  "preparationRequestId": "UUID",
  "expectedCurrentContractVersion": 1,
  "supersessionReasonCode": "DISBURSEMENT_ACCOUNT_REFRESH"
}
```

Regeneration preserves accepted terms and repayment items, supersedes the prior version, refreshes the eligible destination, and requires fresh Customer acknowledgment.

Important errors: `APPROVED_OFFER_NOT_FOUND`, `OFFER_NOT_ACCEPTED`, `CONTRACT_VERSION_STALE`, `CONTRACT_REGENERATION_NOT_ALLOWED`, and `IDEMPOTENCY_KEY_REUSED`.

### 6.3 Read and acknowledge contract

The current-contract response may expose identifiers, reference/version/status, accepted terms, repayment items, safe bank name/code, account-holder name, masked account number, timestamps, and available action. It never exposes the full destination or cryptographic evidence.

Acknowledgment:

```json
{
  "acknowledgmentRequestId": "UUID",
  "expectedContractVersion": 1
}
```

An identical acknowledgment of an earlier version remains replayable after a later version becomes current; it returns the original result and does not acknowledge the newer version.

### 6.4 Readiness

Advisory query:

```text
GET /api/v1/loan-applications/{loanApplicationId}/contracts/current/readiness
GET /api/v1/loan-applications/{loanApplicationId}/contracts/current/readiness?expectedContractVersion=1
```

Representative response:

```json
{
  "loanApplicationId": "UUID",
  "contractId": "UUID",
  "contractVersion": 1,
  "ready": false,
  "blockerCodes": ["ACKNOWLEDGMENT_MISSING"],
  "calculationSemantics": "POINT_IN_TIME_ADVISORY",
  "recomputedDuringConfirmation": true
}
```

Confirmation:

```json
{
  "confirmationRequestId": "UUID",
  "expectedContractVersion": 1
}
```

Success moves the contract to `READY_FOR_DISBURSEMENT` and the application to `DISBURSEMENT_PENDING`; it does not perform a transfer or activate a LoanAccount.

Stable blockers include `DOCUMENTS_NOT_PROCESSING_READY`, `ACTIVE_CORRECTION_REQUEST`, `CUSTOMER_INACTIVE`, `CAPTURED_ACCOUNT_MISSING`, `CAPTURED_ACCOUNT_INACTIVE`, `SALARY_ADVANCE_RESERVATION_INVALID`, `SALARY_ADVANCE_RESERVATION_RELEASED`, `READINESS_ALREADY_CONFIRMED`, and `CONTRACT_VERSION_STALE`.

---

## 7. Destination Reveal, Disbursement, and LoanAccount

### 7.1 Reveal destination

```json
{
  "expectedContractVersion": 1
}
```

Reveal requires a non-Customer actor with `loan:disburse`, an application in `DISBURSEMENT_PENDING`, and the exact current contract in `READY_FOR_DISBURSEMENT`.

The response contains only contract ID/version, bank code/name, account-holder name, and the full immutable contract account number. It never queries the Customer’s mutable bank account.

Successful headers:

```text
Cache-Control: no-store, private
Pragma: no-cache
X-Content-Type-Options: nosniff
```

Important conflicts: `CONTRACT_VERSION_STALE`, `DISBURSEMENT_DESTINATION_REVEAL_NOT_ALLOWED`, and `DISBURSEMENT_DESTINATION_UNAVAILABLE`.

### 7.2 Confirm manual disbursement

```json
{
  "requestId": "UUID",
  "expectedContractVersion": 1,
  "externalTransferReference": "BANK-REFERENCE",
  "disbursementValueDate": "2026-07-28",
  "firstRepaymentDate": "2026-08-28"
}
```

The body cannot supply Customer, product, destination, amount, pricing, term, limit, account, or schedule facts. Those values come from the ready contract.

The external reference is normalized, retained as protected evidence, and never returned. Success returns safe application/account/disbursement/schedule identifiers, amount and dates, activation time, final schedule, and `idempotentReplay`.

Important errors:

- `IDEMPOTENCY_KEY_REUSED`
- `DISBURSEMENT_ALREADY_COMPLETED`
- `DUPLICATE_TRANSFER_REFERENCE`
- `SYSTEM_STATE_CONFLICT`
- `DISBURSEMENT_VALUE_DATE_INVALID`
- `FIRST_REPAYMENT_DATE_INVALID`
- `PRODUCT_ACTIVATION_NOT_SUPPORTED`

### 7.3 Query LoanAccount

The response contains:

| Group | Fields |
|---|---|
| Account | application/account IDs, account number, status, activation time |
| Origination | principal, approved term, total interest, fee, total repayment |
| Servicing summary | paid/outstanding component totals, total paid/outstanding, evaluation date, last payment dates |
| Destination | bank code/name, account-holder name, fixed mask `********` |
| Final schedule | schedule ID/type/version, first/last due dates, immutable items |
| Installment servicing | paid/outstanding components, derived status, evaluation date, last payment dates |

The read does not decrypt the destination or perform allocation, overdue evaluation, mutation, audit, or history writes.

For Customers, missing, foreign-owned, and unavailable accounts all return `404 LOAN_ACCOUNT_NOT_FOUND`.

---

## 8. Repayment

Repayment APIs support serviceable Salary Advance LoanAccounts.

### 8.1 Record or replay repayment

```json
{
  "requestId": "8ca0b35e-e2e8-4b91-90d9-499ab9b0a879",
  "externalPaymentReference": " payroll-aug-000042 ",
  "amount": 100000,
  "paymentValueDate": "2026-08-01"
}
```

Rules visible to clients:

- partial and early payments are supported;
- allocation uses oldest installment first;
- component order is fee, interest, then principal;
- payment cannot exceed total outstanding;
- value date cannot precede disbursement or exceed the current business date;
- only principal allocation releases Salary Advance used exposure;
- exact contractual payoff produces `SETTLED`.

The response includes safe transaction/account IDs, amount/value date, recording time, ordered allocations, principal released, installment outcomes, resulting account status/balances, and `idempotentReplay`. It excludes the external reference, request UUID, actor, Customer, employee-link, limit, bank, audit, history, and internal operation evidence.

An identical replay returns the original result even if later servicing state differs.

Validation failures use `400`, business-rule failures `422`, and state/idempotency conflicts `409`. Important conflicts include idempotency reuse, duplicate normalized reference, overpayment, invalid value date, non-serviceable state, and `SYSTEM_STATE_CONFLICT`.

### 8.2 Repayment history

```text
GET /api/v1/loan-applications/{loanApplicationId}/repayments?page=0&size=20
```

- `page` defaults to `0`.
- `size` defaults to `20` and must be `1–100`.
- Ordering is `recordedAt DESC`, then repayment transaction ID descending.
- Historical outcomes are reconstructed from immutable transaction/allocation outcome evidence rather than recalculated from later account state.
- The response excludes replay flags and external payment references.
- Customers require ownership plus `loan:read:own`; Staff use `loan:read`. `repayment:update` alone does not grant read access.

### 8.3 Administrative Full-Balance Settlement

```http
POST /api/v1/loan-applications/{loanApplicationId}/settlements
Authorization: Bearer <approver-token>
Content-Type: application/json
```

```json
{
  "requestId": "5adc5851-af5a-4fb0-8745-bd66a0cf36c4",
  "expectedSettlementAmount": 1230000,
  "paymentValueDate": "2026-08-09",
  "externalPaymentReference": "BANK-REFERENCE"
}
```

The caller must be an Approver with `loan:settlement:approve`. The account must be `ACTIVE` or `OVERDUE`, and `expectedSettlementAmount` must equal locked current total outstanding. Meridian records an `APPROVED_SETTLEMENT` payment transaction, applies oldest-installment and fee-interest-principal allocation, releases only allocated Salary Advance principal, and returns a `SETTLED` result. Discounted, concessionary, waiver, forgiveness, and write-off outcomes are not accepted.

The response contains safe application/account/payment/schedule identifiers, amount and value date, approval time, principal allocated and released, resulting balances/status, and `idempotentReplay`. It excludes the request UUID, canonical external payment reference, actor and Customer identities, settlement evidence identity, limit/movement identities, audit/history identities, and internal reconciliation evidence.

An identical request replay returns the original durable settlement result, including after later administrative closure, without new payment, allocation, exposure, history, settlement, or audit evidence. Reusing the request UUID with different logical content returns `409 IDEMPOTENCY_KEY_REUSED`. Other relevant outcomes are `422 SETTLEMENT_AMOUNT_INVALID`, `422 SETTLEMENT_VALUE_DATE_INVALID`, `409 DUPLICATE_PAYMENT_REFERENCE`, `409 SETTLEMENT_NOT_ALLOWED`, and safe `409 SYSTEM_STATE_CONFLICT`; missing permission or business role returns `403`.

### 8.4 Administrative LoanAccount closure

```http
POST /api/v1/loan-applications/{loanApplicationId}/loan-account/closure
Authorization: Bearer <accounting-token>
Content-Type: application/json
```

```json
{
  "requestId": "c2cb8155-e3c9-40e9-8887-81de1b37474f"
}
```

The caller must be an Accounting Officer with `loan:account:close`. Closure requires a fully reconciled `SETTLED` LoanAccount produced by ordinary contractual payoff or Administrative Full-Balance Settlement. It records a separate `SETTLED -> CLOSED` administrative result and does not change payments, allocations, balances, the final schedule, installment progress, Salary Advance exposure, or LoanApplication state.

The response contains only application/account identity, `CLOSED`, closure time, and `idempotentReplay`. It excludes request, closure-evidence, actor, payment, limit/movement, audit/history, and internal reconciliation identities. An identical replay returns the original closure result. A different request after closure or an attempt before `SETTLED` returns `409 LOAN_ACCOUNT_CLOSURE_NOT_ALLOWED`; conflicting reuse of the same request UUID returns `409 IDEMPOTENCY_KEY_REUSED`; inconsistent evidence returns safe `409 SYSTEM_STATE_CONFLICT`; missing permission or business role returns `403`.

---

## 9. Postman Collection

Collection:

```text
docs/api/Meridian-Platform.postman_collection.json
```

It authenticates role-specific demo actors, stores Bearer tokens, and covers the catalogue above, including advisory Salary Advance readiness, durable LoanApplication status recovery, optional returned-correction cancellation and exact replay, Customer, Staff, mixed-correction, document, offer, contract, disbursement, LoanAccount, repayment, Administrative Full-Balance Settlement, administrative closure, and negative-security flows.

Complex correction scenarios require prepared application, review-cycle, checklist, and version variables. The optional cancellation folder requires `returnedCancellationScenarioEnabled=true` and a separate Customer-owned `cancellationLoanApplicationId` in `RETURNED_FOR_REVISION`; it confirms the command, exact replay, and terminal application GET without exposing internal evidence IDs. Seed fixtures and scenario-specific IDs belong to the collection or its environment, not this API contract.

---
