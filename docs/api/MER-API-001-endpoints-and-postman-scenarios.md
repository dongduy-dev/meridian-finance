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
- Customer-supplied loan and servicing amounts must represent whole VND. This includes requested principal, Collateral estimated value, repayment amount, and expected settlement amount. `3000000`, `3000000.0`, and `3000000.00` are valid; fractional VND is rejected.

### 1.2 Authentication and authorization

Public endpoints:

- `GET /api/v1/health`
- `POST /api/v1/auth/register`
- `POST /api/v1/auth/email-verification/request`
- `POST /api/v1/auth/email-verification/confirm`
- `POST /api/v1/auth/password-reset/request`
- `POST /api/v1/auth/password-reset/confirm`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`
- `GET /api/v1/loan-products`
- `GET /api/v1/loan-products/{productCode}`
- `GET /v3/api-docs`
- `GET /swagger-ui.html` and `/swagger-ui/**`

All endpoints outside registration, email verification, password reset, login, refresh, logout, and the other public operations require:

```text
Authorization: Bearer <accessToken>
```

Meridian uses stateless Bearer access authentication. Login creates an opaque refresh token in an HttpOnly cookie, and refresh and current-session logout accept that cookie without requiring successful Bearer authentication. HTTP Basic, form login, server sessions, and Spring-managed logout are not part of v1.

| Failure | Response |
|---|---|
| Missing or invalid authentication | `401 AUTHENTICATION_REQUIRED` |
| Missing, invalid, expired, revoked, or reused refresh token | `401 INVALID_REFRESH_TOKEN` |
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
| `429` | Request-rate limit exceeded |
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
| Customer correction cancellation | `requestId` | Returns the durable cancellation result without another terminal transition, product-specific exposure effect, history entry, or audit event. |
| Contract preparation/regeneration | `preparationRequestId` | Returns the same prepared contract version. |
| Contract acknowledgment | `acknowledgmentRequestId` | Returns the same acknowledgment result for the exact version. |
| Readiness confirmation | `confirmationRequestId` | Returns the same confirmed-readiness result without another transition. |
| Disbursement confirmation | `requestId` | Returns the durable activation result without another transfer, account, schedule, or exposure effect. |
| Repayment posting | `requestId` | Returns the immutable operation outcome captured at first execution. |
| Administrative Full-Balance Settlement | `requestId` | Returns the immutable settlement outcome without another payment, allocation, exposure, history, or audit effect. |
| Administrative LoanAccount closure | `requestId` | Returns the immutable closure result without another status, closure-evidence, history, or audit effect. |
| Partner Employee effective-month import | `requestId` | Returns the original committed batch and row-validation summary without another batch, employee row, or audit effect. |

An identical logical replay returns the original result without another business effect. Reuse with different logical content returns `409 IDEMPOTENCY_KEY_REUSED` without identifying the protected field that differed.

### 1.6 Sensitive-data boundary

Outside the login and refresh responses, JSON responses do not expose passwords or access tokens. Refresh-token values never appear in JSON. Public and Customer-facing responses must not expose unrestricted identity evidence, Partner salary evidence, full bank-account numbers, cryptographic envelope fields, document storage metadata, restricted Staff notes, external transfer/payment references, or internal audit/history identifiers.

Authorized Staff endpoints return only the restricted operational fields defined for their contracts. Partner employee reads may return employment and salary evidence to callers with `partner:read`; recommendation and decision responses may return their own actor and internal-note fields to the authorized Staff caller. Product-verification completion responses never return the reviewer or restricted assessment note.

The contractual destination-reveal operation is the sole v1 JSON endpoint permitted to return the full immutable disbursement account number.

### 1.7 Request correlation

Every response includes `X-Request-ID` as transport correlation for the HTTP request. A caller may supply a canonical UUID in this header; Meridian returns that UUID. When the header is absent or malformed, Meridian generates and returns a new UUID without rejecting the business request.

`X-Request-ID` does not provide idempotency and does not replace operation-specific request UUIDs such as `requestId`, `uploadRequestId`, or `reviewRequestId`. Those fields retain their operation-specific replay and conflict semantics.

### 1.8 OpenAPI, Swagger UI, and browser origins

`GET /v3/api-docs` returns the generated OpenAPI definition. `GET /swagger-ui.html` redirects to the Swagger UI entry under `/swagger-ui/`. The definition identifies the API as `Meridian Lending Platform API` version `v1` and declares stateless JWT access through the HTTP Bearer scheme named `bearerAuth`. The public operations listed in section 1.2 do not require that scheme.

Meridian grants credentialed cross-origin browser access only to the explicit origins configured by `MERIDIAN_FRONTEND_ALLOWED_ORIGINS`; the local-development default is `http://localhost:5173`. The CORS policy allows credentials plus `GET`, `POST`, `PUT`, and preflight `OPTIONS` requests with `Accept`, `Authorization`, `Content-Type`, and `X-Request-ID`. Responses expose `X-Request-ID` so browser clients can read the transport correlation identifier. A disallowed origin receives no CORS grant, wildcard origins are rejected, and CORS does not change endpoint authentication or permission requirements.

---

## 2. Endpoint Catalogue

### 2.1 Public, Customer, and Partner

| Method | Path | Authorization | Summary |
|---|---|---|---|
| GET | `/api/v1/health` | Public | Return versioned health status. |
| POST | `/api/v1/auth/register` | Public | Create an unverified Customer account and request verification email delivery without issuing credentials. |
| POST | `/api/v1/auth/email-verification/request` | Public | Enumeration-safely replace and deliver a verification token for an eligible unverified Customer account. |
| POST | `/api/v1/auth/email-verification/confirm` | Public | Confirm an email by opaque verification token. |
| POST | `/api/v1/auth/password-reset/request` | Public | Enumeration-safely replace and deliver a password-reset token for an eligible User. |
| POST | `/api/v1/auth/password-reset/confirm` | Public | Replace the password using an opaque reset token and revoke the User's refresh sessions. |
| POST | `/api/v1/auth/login` | Public | Authenticate, return Bearer-token actor facts, and create the refresh cookie. |
| POST | `/api/v1/auth/refresh` | Public at the Bearer layer; refresh cookie required | Rotate the refresh token and return a fresh access token. |
| POST | `/api/v1/auth/logout` | Public at the Bearer layer; credentials optional | Invalidate the presented current-session credentials and clear the refresh cookie. |
| GET | `/api/v1/loan-products` | Public | List active loan products. |
| GET | `/api/v1/loan-products/{productCode}` | Public | Return one active loan product by code. |
| GET | `/api/v1/admin/loan-products` | `loan:product:manage` | List active and inactive Loan Products through the protected administration projection. |
| PUT | `/api/v1/admin/loan-products/{productCode}/limits` | `loan:product:manage` | Replace the minimum and maximum amount for future product validation. |
| PUT | `/api/v1/admin/loan-products/{productCode}/activation` | `loan:product:manage` | Replace the product activation state for future discovery and submission. |
| GET | `/api/v1/customers/me` | `customer:profile:read:own` | Return the authenticated Customer’s safe profile-readiness view. |
| PUT | `/api/v1/customers/me/profile` | `customer:profile:write:own` | Create or update the authenticated Customer profile. |
| GET | `/api/v1/customers/me/bank-accounts` | `customer:bank-account:read:own` | List masked owned bank accounts. |
| POST | `/api/v1/customers/me/bank-accounts` | `customer:bank-account:write:own` | Add a bank account; the first active account becomes primary. |
| POST | `/api/v1/customers/me/bank-accounts/{customerBankAccountId}/make-primary` | `customer:bank-account:write:own` | Make an active owned account primary. |
| POST | `/api/v1/customers/me/bank-accounts/{customerBankAccountId}/deactivate` | `customer:bank-account:write:own` | Deactivate an owned account subject to primary-account rules. |
| GET | `/api/v1/partner-companies` | `partner:read` | List Partner Companies. |
| GET | `/api/v1/partner-companies/{partnerCompanyId}` | `partner:read` | Return one Partner Company. |
| GET | `/api/v1/partner-companies/verification-options` | `partner:employee:verify:own` | List active Partner Companies as a Customer-safe employee-verification selector. |
| GET | `/api/v1/partner-companies/{partnerCompanyId}/employees?activeOnly=false` | `partner:read` | List Partner Employees; `activeOnly` defaults to `false`. |
| GET | `/api/v1/partner-companies/{partnerCompanyId}/employee-import-batches` | `partner:read` | List employee import batches. |
| POST | `/api/v1/partner-companies` | `partner:manage` | Create a Partner Company. |
| PUT | `/api/v1/partner-companies/{partnerCompanyId}` | `partner:manage` | Update the company name and Salary Advance policy limit. |
| POST | `/api/v1/partner-companies/{partnerCompanyId}/status` | `partner:manage` | Change Partner Company status. |
| POST | `/api/v1/partner-companies/{partnerCompanyId}/employee-import-batches` | `partner:manage` | Import Partner Employee source rows for an effective month. |
| GET | `/api/v1/partner-companies/{partnerCompanyId}/employee-verifications` | `partner:employee:verify:own` | Return the authenticated Customer's latest manual-review verification state for the selected Partner Company. |
| POST | `/api/v1/partner-companies/{partnerCompanyId}/employee-verifications` | `partner:employee:verify:own` | Verify the authenticated Customer and create/reuse an eligible employee link. |
| GET | `/api/v1/admin/partner-eligibility-reviews?status=PENDING&page=0&size=20` | `partner:read` | Return the bounded shared eligibility-review queue. |
| GET | `/api/v1/admin/partner-eligibility-reviews/{reviewId}` | `partner:read` | Return one review with current purpose-limited candidate evidence. |
| POST | `/api/v1/admin/partner-eligibility-reviews/{reviewId}/decision` | `partner:manage` | Approve one exact current Partner Employee or reject the review with a controlled reason. |
| POST | `/api/v1/staff/customers/search` | Staff with `customer:read` | Find one Customer by exact Customer number or protected identity match. |
| GET | `/api/v1/staff/customers/{customerId}` | Staff with `customer:read` | Return one purpose-limited Customer intake projection. |
| POST | `/api/v1/staff/customers` | Staff with `customer:intake:manage` | Atomically create a Customer and required identity-bearing profile. |
| PUT | `/api/v1/staff/customers/{customerId}/profile` | Staff with `customer:intake:manage` | Maintain the selected Customer profile. |
| GET | `/api/v1/staff/customers/{customerId}/bank-accounts` | Staff with `customer:intake:manage` | List the selected Customer's masked bank accounts. |
| POST | `/api/v1/staff/customers/{customerId}/bank-accounts` | Staff with `customer:intake:manage` | Add a protected bank account. |
| POST | `/api/v1/staff/customers/{customerId}/bank-accounts/{customerBankAccountId}/make-primary` | Staff with `customer:intake:manage` | Make an active selected-Customer account primary. |
| POST | `/api/v1/staff/customers/{customerId}/bank-accounts/{customerBankAccountId}/deactivate` | Staff with `customer:intake:manage` | Deactivate a selected-Customer account subject to aggregate rules. |

### 2.2 Origination, review, approval, corrections, and documents

| Method | Path | Authorization | Summary |
|---|---|---|---|
| GET | `/api/v1/loan-products/salary-advance/readiness` | Customer with `loan:submit` | Return the authenticated Customer's advisory Salary Advance eligibility and safe limit view. |
| GET | `/api/v1/staff/assisted-originations?status={status}` | Staff with `loan:originate:staff` | List assisted-origination cases, optionally by exact status. |
| POST | `/api/v1/staff/assisted-originations` | Staff with `loan:originate:staff` | Create an open UCL or Collateral Loan assisted-origination case. |
| GET | `/api/v1/staff/assisted-originations/{assistedOriginationCaseId}` | Staff with `loan:originate:staff` | Reopen one assisted-origination case or reconcile its completed LoanApplication result. |
| PUT | `/api/v1/staff/assisted-originations/{assistedOriginationCaseId}/customer` | Staff with `loan:originate:staff` | Associate or replace the selected active Customer while open. |
| POST | `/api/v1/staff/assisted-originations/{assistedOriginationCaseId}/abandon` | Staff with `loan:originate:staff` | Terminally abandon an open intake. |
| POST | `/api/v1/staff/assisted-originations/{assistedOriginationCaseId}/unsecured-consumer-loan/submit` | Staff with `loan:originate:staff` | Atomically convert an eligible open UCL intake into one Staff-assisted UCL application. |
| POST | `/api/v1/staff/assisted-originations/{assistedOriginationCaseId}/collateral-loan/submit` | Staff with `loan:originate:staff` | Atomically convert an eligible open Collateral intake into one Staff-assisted Collateral Loan application. |
| GET | `/api/v1/staff/assisted-originations/{assistedOriginationCaseId}/evidence` | Staff with `document:upload:intake` and `loan:originate:staff` | Return controlled intake-evidence version metadata without storage keys. |
| POST | `/api/v1/staff/assisted-originations/{assistedOriginationCaseId}/evidence/{evidenceType}/versions` | Staff with `document:upload:intake` and `loan:originate:staff` | Upload or replace a controlled intake-evidence version for an open case. |
| POST | `/api/v1/staff/assisted-originations/{assistedOriginationCaseId}/evidence/{evidenceType}/versions/{intakeDocumentVersionId}/ocr` | Staff with `document:upload:intake` and `loan:originate:staff` | Start asynchronous OCR for the exact current immutable intake-evidence version or return its existing job. |
| GET | `/api/v1/staff/assisted-originations/{assistedOriginationCaseId}/evidence/{evidenceType}/versions/{intakeDocumentVersionId}/ocr` | Staff with `document:upload:intake` and `loan:originate:staff` | Return safe OCR job status for an intake-evidence version. |
| GET | `/api/v1/staff/assisted-originations/{assistedOriginationCaseId}/evidence/{evidenceType}/versions/{intakeDocumentVersionId}/ocr/review` | Staff with `document:upload:intake` and `loan:originate:staff` | Return the purpose-limited structured suggestion and final-review projection for a completed OCR result. |
| POST | `/api/v1/staff/assisted-originations/{assistedOriginationCaseId}/evidence/{evidenceType}/versions/{intakeDocumentVersionId}/ocr/review` | Staff with `document:upload:intake` and `loan:originate:staff` | Finalize one immutable corrected field map for the current OCR result of an open intake. |
| POST | `/api/v1/loan-applications/salary-advance` | `loan:submit` | Submit a Salary Advance application and reserve eligible limit. |
| POST | `/api/v1/loan-applications/unsecured-consumer-loan` | Customer with `loan:submit` | Submit an Unsecured Consumer Loan application for manual verification and required evidence collection. |
| POST | `/api/v1/loan-applications/collateral-loan` | Customer with `loan:submit` | Submit a Collateral Loan application with one structured asset and required ownership-evidence collection. |
| GET | `/api/v1/loan-applications` | Customer with `loan:read:own` | List the authenticated Customer's applications with authoritative lifecycle/action summaries. |
| GET | `/api/v1/loan-applications/{loanApplicationId}` | Customer `loan:read:own` or Staff `loan:read` | Return a safe durable LoanApplication status projection. |
| GET | `/api/v1/staff/loan-applications?productCode={productCode}&status={status}&page=0&size=20` | Staff with `loan:read` | Discover applications across products through a safe, deterministic page. |
| GET | `/api/v1/staff/loan-applications/{loanApplicationId}` | Staff with `loan:read` | Return the purpose-limited Staff case header, Customer readiness, and ordered lifecycle evidence. |
| GET | `/api/v1/staff/loan-applications/{loanApplicationId}/documents` | Staff with `document:review` | Return the Staff-safe submission checklist, immutable version history, and safe review history. |
| GET | `/api/v1/staff/loan-applications/{loanApplicationId}/corrections` | Staff with `loan:correction:staff` | Return the latest correction request, mixed task composition, proof state, and current-actor maker-checker evidence. |
| GET | `/api/v1/staff/loan-applications/{loanApplicationId}/verification` | Staff with `loan:review` | Return purpose-limited product-verification evidence, readiness, backend-derived action availability, and authoritative current/history cycles. |
| GET | `/api/v1/staff/loan-applications/{loanApplicationId}/review` | Staff with `loan:review` | Return purpose-limited review-start readiness and the latest Loan-owned review cycle. |
| POST | `/api/v1/loan-applications/{loanApplicationId}/cancel` | Customer with `loan:cancel:own` | Cancel an owned Salary Advance or UCL from `RETURNED_FOR_REVISION`; Salary Advance releases its reservation exactly once, while UCL has no exposure effect. |
| POST | `/api/v1/loan-applications/{loanApplicationId}/unsecured-consumer-loan-verification/start` | Staff with `loan:review` | Start manual UCL verification after document processing readiness. |
| POST | `/api/v1/loan-applications/{loanApplicationId}/unsecured-consumer-loan-verification/complete` | Staff with `loan:review` | Complete manual UCL verification as `VERIFIED`, `FAILED`, or `REQUIRES_MORE_INFORMATION`. |
| POST | `/api/v1/loan-applications/{loanApplicationId}/collateral-loan-verification/start` | Staff with `loan:review` | Start manual Collateral verification and return the restricted assessment snapshot after document processing readiness. |
| POST | `/api/v1/loan-applications/{loanApplicationId}/collateral-loan-verification/complete` | Staff with `loan:review` | Complete the exact authoritative Collateral verification cycle as `VERIFIED`, `FAILED`, or `REQUIRES_MORE_INFORMATION`. |
| POST | `/api/v1/loan-applications/{loanApplicationId}/review/start` | `loan:review` | Start Loan Officer review. |
| POST | `/api/v1/loan-applications/{loanApplicationId}/review-recommendations` | `approval:recommend` | Record a Loan Officer recommendation. |
| POST | `/api/v1/loan-applications/{loanApplicationId}/approval-decisions` | `approval:decide` | Record an Approver decision. |
| GET | `/api/v1/loan-applications/{loanApplicationId}/corrections/tasks` | `loan:correction:own` | List owned Customer correction tasks. |
| POST | `/api/v1/loan-applications/{loanApplicationId}/corrections/tasks/{taskId}/complete` | `loan:correction:own` | Complete an owned task after evidence exists. |
| POST | `/api/v1/loan-applications/{loanApplicationId}/corrections/resubmit` | `loan:correction:own` | Resubmit an eligible Customer-only correction. |
| POST | `/api/v1/loan-applications/{loanApplicationId}/documents/{checklistItemId}/versions` | `document:upload:own` | Upload or replace an owned document version. |
| GET | `/api/v1/loan-applications/{loanApplicationId}/documents` | `document:read:own` | Return the owned submission checklist, current versions, and Customer-safe readiness. |
| GET | `/api/v1/loan-applications/{loanApplicationId}/documents/{checklistItemId}/versions/{documentVersionId}/content` | `document:read:own` | Stream an owned immutable version. |
| GET | `/api/v1/document-review-items?status=AWAITING_REVIEW` | `document:review` | List current versions awaiting review. |
| POST | `/api/v1/loan-applications/{loanApplicationId}/document-review-items/{checklistItemId}/reviews` | `document:review` | Review the exact current version. |
| GET | `/api/v1/staff-corrections/tasks?status=OPEN&page=0&size=20` | `loan:correction:staff` | List Staff-owned correction tasks. |
| POST | `/api/v1/staff-corrections/tasks/{taskId}/complete` | `loan:correction:staff` | Complete a Staff task with proof and maker-checker enforcement. |
| POST | `/api/v1/staff-corrections/loan-applications/{loanApplicationId}/customer-tasks/{taskId}/complete` | `loan:correction:staff` | Record completion of an active Customer-owned task for an eligible Staff-assisted UCL or Collateral application. |
| POST | `/api/v1/staff-corrections/loan-applications/{loanApplicationId}/resubmit` | `loan:correction:staff` | Resubmit an eligible Staff-only, mixed, or Staff-assisted Customer-only correction. |
| POST | `/api/v1/staff/loan-applications/{loanApplicationId}/documents/{checklistItemId}/versions` | `document:upload:assisted` for initial Staff-assisted `DOCUMENTS_PENDING` evidence; `document:upload:assisted-correction` for an eligible Staff-assisted Customer correction task; otherwise `document:upload:staff` for a Staff-owned correction task | Upload initial assisted application evidence or upload exact task-scoped correction proof. |
| POST | `/api/v1/staff/loan-applications/{loanApplicationId}/assisted-action-evidence/{evidenceType}/versions` | Staff with `document:upload:assisted-action` plus the action-specific Loan permission and business role | Upload or replace the current immutable signed `CUSTOMER_OFFER_RESPONSE`, `CUSTOMER_CONTRACT_ACKNOWLEDGMENT`, or `CUSTOMER_CANCELLATION_REQUEST` evidence version for its exact authorized target. |
| POST | `/api/v1/staff/loan-applications/{loanApplicationId}/cancellation` | Staff with `loan:cancel:staff` and the Loan Officer role | Record an evidenced Customer-requested cancellation for the exact active correction of a Staff-assisted UCL. |
| GET | `/api/v1/staff/loan-applications/{loanApplicationId}/documents/{checklistItemId}/versions/{documentVersionId}/content` | `document:review` | Stream a review-authorized immutable version. |

### 2.3 Offers, contracts, disbursement, account, and servicing

| Method | Path | Authorization | Summary |
|---|---|---|---|
| GET | `/api/v1/loan-applications/{loanApplicationId}/approved-offer` | `loan:read:own` | Return the Customer’s approved offer without mutating state. |
| POST | `/api/v1/loan-applications/{loanApplicationId}/approved-offer/accept` | `loan:offer:respond:own` | Accept a valid pending offer. |
| POST | `/api/v1/loan-applications/{loanApplicationId}/approved-offer/decline` | `loan:offer:respond:own` | Decline a valid pending offer and release a Salary Advance reservation when applicable. |
| GET | `/api/v1/staff/loan-applications/{loanApplicationId}/offer-response` | Staff `loan:offer:respond:staff` plus Loan Officer role | Return the safe exact pending-offer, action state, and current assisted-action evidence metadata for an eligible Staff-assisted UCL or Collateral application. |
| POST | `/api/v1/staff/loan-applications/{loanApplicationId}/offer-response` | Staff `loan:offer:respond:staff` plus Loan Officer role | Record the Customer's evidenced `ACCEPT` or `DECLINE` decision for the exact approved offer. |
| POST | `/api/v1/loan-applications/{loanApplicationId}/contracts` | `loan:contract:prepare` | Prepare version 1 or regenerate the current contract. |
| GET | `/api/v1/loan-applications/{loanApplicationId}/contracts/current` | `loan:read:own` or `loan:contract:read` | Return the safe masked current contract. |
| POST | `/api/v1/loan-applications/{loanApplicationId}/contracts/current/acknowledgment` | `loan:contract:acknowledge:own` | Acknowledge the exact current version. |
| GET | `/api/v1/loan-applications/{loanApplicationId}/contracts/current/readiness` | `loan:contract:read` | Calculate point-in-time readiness; optional `expectedContractVersion`. |
| GET | `/api/v1/staff/contract-work?productCode={productCode}&page=0&size=25` | Staff `loan:contract:read` plus Accounting Officer role | Return the authoritative `CONTRACT_PENDING` operational queue with safe current-contract and advisory-readiness evidence. |
| GET | `/api/v1/staff/loan-applications/{loanApplicationId}/contract` | Staff `loan:contract:read` plus Accounting Officer role | Return one contract workspace for `CONTRACT_PENDING` or `DISBURSEMENT_PENDING`. |
| POST | `/api/v1/staff/loan-applications/{loanApplicationId}/contract/acknowledgment` | Staff `loan:contract:acknowledge:staff` plus Accounting Officer role | Record the Customer's evidenced acknowledgment of the exact current `PREPARED` contract version. |
| POST | `/api/v1/loan-applications/{loanApplicationId}/contracts/current/readiness/confirm` | `loan:disbursement:prepare` | Recompute and confirm readiness. |
| GET | `/api/v1/staff/disbursement-work?productCode={productCode}&page=0&size=25` | Staff `loan:disburse` plus Accounting Officer role | Return the authoritative ready-disbursement queue. |
| GET | `/api/v1/staff/servicing-work?productCode={productCode}&accountStatus={accountStatus}&page=0&size=25` | Staff `loan:read` | Return the authoritative ordinary-repayment servicing queue. |
| GET | `/api/v1/staff/settlement-work?productCode={productCode}&page=0&size=25` | Staff `loan:settlement:approve` plus Approver role | Return authoritative full-balance settlement candidates. |
| GET | `/api/v1/staff/closure-work?productCode={productCode}&page=0&size=25` | Staff `loan:account:close` plus Accounting Officer role | Return authoritative administrative-closure candidates. |
| GET | `/api/v1/staff/loan-applications/{loanApplicationId}/disbursement` | Staff `loan:disburse` plus Accounting Officer role | Return one coherent pending or completed disbursement case. |
| POST | `/api/v1/loan-applications/{loanApplicationId}/contracts/current/disbursement-destination/reveal` | `loan:disburse` | Reveal the full immutable ready-contract destination. |
| POST | `/api/v1/loan-applications/{loanApplicationId}/disbursements` | `loan:disburse` | Confirm an external transfer and activate the LoanAccount. |
| GET | `/api/v1/loan-accounts` | Customer with `loan:read:own` | List the authenticated Customer's compact LoanAccount summaries. |
| GET | `/api/v1/loan-applications/{loanApplicationId}/loan-account` | `loan:read:own` or `loan:read` | Return originated terms, final schedule, and servicing state. |
| POST | `/api/v1/loan-applications/{loanApplicationId}/repayments` | `repayment:update` | Record or replay a manual Salary Advance, UCL, or Collateral Loan repayment. |
| GET | `/api/v1/loan-applications/{loanApplicationId}/repayments?page=0&size=20` | `loan:read:own` or `loan:read` | Return immutable paged repayment history. |
| POST | `/api/v1/loan-applications/{loanApplicationId}/settlements` | `loan:settlement:approve` plus Approver role | Approve and apply an Administrative Full-Balance Settlement. |
| GET | `/api/v1/loan-applications/{loanApplicationId}/settlements/approved` | `loan:settlement:approve` plus Approver role | Return PII-minimized immutable settlement facts for exact reload recovery. |
| POST | `/api/v1/loan-applications/{loanApplicationId}/loan-account/closure` | `loan:account:close` plus Accounting Officer role | Close an eligible settled LoanAccount administratively. |

---

## 3. Authentication, Customer, and Partner Requests

### 3.1 Customer registration

```text
POST /api/v1/auth/register
```

```json
{
  "email": "customer@example.com",
  "password": "<12-to-72-character-password>",
  "displayName": "Customer Name"
}
```

Email and display name are trimmed, and email is normalized to lowercase under the same convention used by login. Email must be syntactically valid and no longer than 255 characters; display name must be nonblank and no longer than 150 characters. The password must contain 12 through 72 characters. No additional character-composition rule applies.

Success returns `201 Created`:

```json
{
  "emailVerificationRequired": true
}
```

Registration creates one Customer in `ACTIVE` / `UNVERIFIED` / `INCOMPLETE` business state with no profile or bank account, then creates its active Customer User, `CUSTOMER` role assignment, and digest-only email-verification state in one transaction. It does not issue an access token, refresh cookie, or raw verification token. A duplicate normalized email returns `409 EMAIL_ALREADY_REGISTERED` without exposing account identity or state. The database uniqueness rule remains authoritative for competing registrations, and a losing attempt leaves no orphan Customer.

Verification email delivery occurs after that transaction commits. Delivery failure does not reverse registration and produces the same success response; the request endpoint below is the recovery path. Registration uses an independent per-instance, effective-remote-address limit of 5 requests per 10 minutes by default. A rejected request returns `429 RATE_LIMIT_EXCEEDED` with `Retry-After` before creating Customer, User, role, or token state. Caller-controlled forwarding headers do not alter the limiter identity.

### 3.2 Request or resend email verification

```text
POST /api/v1/auth/email-verification/request
```

```json
{
  "email": "customer@example.com"
}
```

The endpoint always returns `202 Accepted` with no response body for an unknown email, an already verified User, a Staff User, or an eligible unverified Customer User. Only the eligible case locks current User state, revokes the prior active verification token, creates a replacement digest, commits, and then attempts email delivery. Delivery failure does not reverse replacement state and does not alter the public response.

The endpoint has its own per-instance, effective-remote-address limit of 5 requests per 10 minutes by default. This capacity is independent from registration, login, and refresh. A rejected request returns `429 RATE_LIMIT_EXCEEDED` with `Retry-After` before token rotation or email delivery.

### 3.3 Confirm email verification

```text
POST /api/v1/auth/email-verification/confirm
```

```json
{
  "token": "<opaque-token-copied-from-the-captured-email>"
}
```

The opaque token identifies the User; the request does not accept email or User identity. Meridian digests and locks authoritative token state, verifies that it is known, unexpired, and unrevoked, marks Identity `users.email_verified_at`, consumes the token, and returns `204 No Content`. Repeating confirmation with that consumed token after successful verification is idempotent. Competing confirmations serialize on the token and cannot create conflicting state.

Unknown, expired, and revoked tokens all return `401 INVALID_EMAIL_VERIFICATION_TOKEN` with `Email verification token is invalid or expired.`. Confirmation changes only Identity account-security state; Customer `verificationStatus` remains `UNVERIFIED` until its separate business-verification workflow changes it. Confirmation is not request-rate limited because the token has at least 256 bits of entropy.

The controlled email contains a frontend link in the form `http://localhost:5173/verify-email#token=<opaque-token>` by default. The future frontend reads the fragment and sends the JSON request above; the raw token is not sent in a backend query string. Local manual testing obtains it from Mailpit at `http://localhost:8025`.

### 3.4 Request password reset

```text
POST /api/v1/auth/password-reset/request
```

```json
{
  "email": "customer@example.com"
}
```

The endpoint always returns `202 Accepted` with no response body. Unknown, email-unverified, `SUSPENDED`, and `DISABLED` Users receive the same response as eligible Users, and the response exposes no User, Customer, role, account-state, verification, or delivery fact. Email lookup trims surrounding whitespace and normalizes case under the registration and login convention.

An `ACTIVE`, email-verified Customer or Staff User is eligible. Identity locks the authoritative User, revokes any prior active password-reset token, persists one fresh SHA-256 token digest with a 30-minute default lifetime, commits, and then asks Notification to send the raw token. SMTP failure does not reverse token state or alter the response; another request replaces that token. Competing requests for one User serialize so the database retains at most one unconsumed, unrevoked token.

The endpoint has an independent per-instance, effective-remote-address limit of 5 requests per 10 minutes by default. Caller-controlled forwarding headers do not alter the key. A rejected request returns `429 RATE_LIMIT_EXCEEDED` with `Retry-After` before token mutation or email delivery and does not consume login, refresh, registration, or email-verification capacity.

### 3.5 Confirm password reset

```text
POST /api/v1/auth/password-reset/confirm
```

```json
{
  "token": "<opaque-token-copied-from-the-captured-email>",
  "newPassword": "<12-to-72-character-password>"
}
```

The new password uses the registration length policy: 12 through 72 characters with no additional composition rule. Meridian does not trim or normalize the password.

Identity digests and locks the token, locks its authoritative User, verifies that the token is known, active, unexpired, and owned by an active email-verified User, BCrypt-hashes the new password, replaces the password, clears failed-login and temporary-lock state, consumes the token, and revokes every refresh-token family for that User in one transaction. Competing confirmations cannot both change password state. Success returns `204 No Content`, clears the browser refresh cookie, and returns no access or refresh credential. The User must log in normally with the new password.

Unknown, expired, revoked, consumed, or otherwise ineligible token state returns `401 INVALID_PASSWORD_RESET_TOKEN` with `Password reset token is invalid or expired.`. Consumed-token replay is not idempotently successful, including when the replay supplies a different password. Administrative User status, email-verification time, Customer association, roles, permissions, and Customer business-verification state do not change.

Existing refresh tokens cannot mint new credentials after success, including tokens from independent login families. Refresh sessions owned by another User remain usable. Existing access JWTs are self-contained and are not enumerated or revoked by password reset; they remain usable until their existing one-hour expiry or a separate token-specific revocation.

The controlled email uses the configured frontend base URL and a fragment link such as `http://localhost:5173/reset-password#token=<opaque-token>`. The frontend reads the fragment and submits the JSON body above. The raw token does not enter a backend query string, API response, durable event, or database row. Confirmation is not request-rate limited because the opaque token has at least 256 bits of entropy.

### 3.6 Login

```text
POST /api/v1/auth/login
```

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

Successful login also sets `MERIDIAN_REFRESH_TOKEN` as an HttpOnly cookie. The cookie is not part of the JSON response. Its path is `/api/v1/auth`, `SameSite` is `Strict`, its maximum age matches the configured refresh-token lifetime, and `Secure` is controlled by `MERIDIAN_REFRESH_TOKEN_COOKIE_SECURE`. Deployed HTTPS environments must set that flag to `true`; the local HTTP default is `false`.

For a known User, Meridian records consecutive failed password attempts. The default policy temporarily blocks new password logins for 15 minutes after 5 consecutive failures; both values are configurable. Competing password attempts for one known User observe one authoritative sequence. An expired lock starts a fresh attempt sequence, and a successful active-User login clears previous failure and lock state.

An unknown email, a wrong password, and any password submitted during an active temporary lock all return `401 INVALID_CREDENTIALS` with `Invalid credentials.`. The response does not expose whether the User exists, the failed-attempt count, or the lock expiry. Temporary login lockout does not change User status or revoke existing access tokens and refresh-token families. Administrative `SUSPENDED` and `DISABLED` behavior remains separate.

A newly registered Customer User with a correct password but no `emailVerifiedAt` receives `401 EMAIL_VERIFICATION_REQUIRED` with `Email verification required.`. That attempt creates no access token or refresh session and does not count as a failed password attempt; any stale failed-attempt state is cleared as it is for a successful password check. Verified Customer and backfilled demo Users continue through the established login flow. Refresh also rejects and revokes inconsistent session state for an unverified User as `401 INVALID_REFRESH_TOKEN`.

Login applies request-boundary throttling per effective servlet remote address and application instance before password verification. The default permits 10 requests per one-minute window. A request over the configured limit returns `429 RATE_LIMIT_EXCEEDED` with `Too many requests.` and a positive `Retry-After` header containing the whole-second wait until the current window resets. A rejected request does not change User login-protection state or create authentication credentials.

### 3.7 Refresh access

```text
POST /api/v1/auth/refresh
Cookie: MERIDIAN_REFRESH_TOKEN=<opaque-refresh-token>
```

The endpoint accepts no request body and does not require a Bearer token. Meridian locks the refresh-token session, verifies that it is unconsumed, unrevoked, and unexpired, reloads the associated active User and current RBAC grants, consumes the presented token, and creates one replacement in the same family. The response uses the same JSON fields as login and replaces the refresh cookie. Each replacement receives the full configured inactivity lifetime from its rotation time.

Only a SHA-256 digest of the cryptographically random refresh token is persisted. The raw refresh token, digest, session identity, family identity, and replacement relationship are not returned in JSON or errors.

Missing, unknown, expired, revoked, or otherwise invalid refresh state returns `401 INVALID_REFRESH_TOKEN`. Reusing a consumed token also returns that safe error and revokes the active token family, requiring a new login. Competing refresh requests with one token cannot both succeed.

Refresh uses an independent request-boundary policy per effective servlet remote address and application instance. The default permits 30 requests per one-minute window, and login traffic does not consume refresh capacity. A request over the configured limit returns `429 RATE_LIMIT_EXCEEDED` with `Too many requests.` and `Retry-After`; the request does not inspect, consume, rotate, or revoke refresh state.

### 3.8 Current-session logout

```text
POST /api/v1/auth/logout
Cookie: MERIDIAN_REFRESH_TOKEN=<opaque-refresh-token>  (optional)
Authorization: Bearer <accessToken>                    (optional)
```

The endpoint accepts no request body. A known presented refresh token revokes its complete refresh-token family, and a presented valid Bearer token becomes unusable immediately. Logout clears `MERIDIAN_REFRESH_TOKEN` on `/api/v1/auth` with the same security attributes used for issuance and `Max-Age=0`.

Logout returns `204 No Content` whether credentials are valid, missing, unknown, expired, malformed, or already revoked. An invalid Bearer token does not prevent revocation through a valid refresh cookie. Repeated logout is idempotent and does not disclose credential state. Only the credentials presented by the current client are invalidated; other login families and access tokens for the same User remain usable.

Authentication request throttling does not apply to logout, so a client may always attempt to revoke its current session.

### 3.9 Loan-product catalogue

```text
GET /api/v1/loan-products
GET /api/v1/loan-products/{productCode}
```

Both reads are public. The collection read returns active products; the item read normalizes the supplied code and returns only an active matching product. Each product contains `productCode`, `productType`, `name`, `description`, `active`, `minAmount`, `maxAmount`, and a typed `policy` presentation:

| Policy group | Fields |
|---|---|
| Terms | Ordered `allowedTermsMonths` |
| Pricing | `flatMonthlyInterestRate`, `feeAmount`, `interestCalculationMethod` |
| Repayment and validity | `repaymentMethod`, `offerValidityDays` |
| Submission evidence | Ordered `documentType` and `requirementStatus` pairs |
| Eligibility presentation | Ordered, verified `eligibilityNotes` that explain product prerequisites without calculating Customer eligibility |

Pricing, repayment, validity, and returned terms come from the active executable product policy. Submission evidence comes from the same Document-owned product checklist resolver used to create an application checklist: Salary Advance has no initial item, UCL has required `BANK_STATEMENT`, `EMPLOYMENT_PROOF`, and `INCOME_PROOF`, and Collateral Loan has required `COLLATERAL_OWNERSHIP_EVIDENCE`. Missing or malformed active executable policy returns `422 PRODUCT_POLICY_INVALID`; the API does not return a partial policy.

An unknown code returns `404 PRODUCT_CODE_NOT_FOUND`. A recognized code with no active product returns `404 PRODUCT_NOT_FOUND`.

### 3.10 Protected Loan Product administration

```text
GET /api/v1/admin/loan-products
PUT /api/v1/admin/loan-products/{productCode}/limits
PUT /api/v1/admin/loan-products/{productCode}/activation
```

All three operations require exact `loan:product:manage`. They use the protected `/api/v1/admin/loan-products` namespace because `/api/v1/loan-products` and its descendants remain public catalogue routes.

The list returns every persisted Loan Product, including inactive products, in deterministic product-code order. The purpose-specific response contains only `productCode`, `productType`, `name`, `description`, `active`, `minAmount`, and `maxAmount`. It does not depend on an active pricing or document policy and does not expose policy persistence identity.

The limits request replaces only the two supported amount facts:

```json
{
  "minAmount": 1000000.00,
  "maxAmount": 50000000.00
}
```

Both values are required, nonnegative, and constrained to the `NUMERIC(19,2)` representation. `maxAmount` must be greater than or equal to `minAmount`. Transport shape, precision, and scale violations return `400 VALIDATION_FAILED`; a backend-owned invalid range returns `422 INVALID_PRODUCT_LIMITS`.

The activation request contains an explicit non-null target state:

```json
{
  "active": false
}
```

Deactivation removes the product from the public catalogue and preserves the existing inactive-product submission and Salary Advance readiness behavior. Neither command changes existing applications, offers, contracts, LoanAccounts, schedules, repayments, or Salary Advance exposure.

An unknown code returns `404 PRODUCT_CODE_NOT_FOUND`. A supported code whose product row is missing returns `404 PRODUCT_NOT_FOUND`. Each command locks the product row before applying the target state. Repeating identical limits or activation returns the current projection without another save, timestamp change, or audit event. The commands do not use a request UUID. A client must not automatically retry after an unknown transport result; it refreshes the protected list and may explicitly submit the same target state again.

### 3.11 Protected Internal User administration

```text
GET /api/v1/admin/internal-users
GET /api/v1/admin/internal-users/assignable-roles
PUT /api/v1/admin/internal-users/{userId}/status
PUT /api/v1/admin/internal-users/{userId}/roles/{roleCode}
```

All four operations require exact `identity:user:manage`. A role name, permission prefix, `admin:config`, or generic Staff authentication does not authorize them.

The User list contains only internal Users whose `userType` is `STAFF` and whose Customer association is absent, ordered by normalized email and stable User ID. Each purpose-limited response contains `userId`, `email`, `displayName`, User `status`, and ordered `assignedRoleCodes`. It excludes password and recovery state, failed-login or temporary-lock state, refresh/access-token metadata, authorization version, and Customer identity. Missing IDs and non-Staff targets share `404 INTERNAL_USER_NOT_FOUND` so the contract does not reveal whether an arbitrary Customer User exists.

Assignable-role discovery returns `code` and `name` in role-code order for the predefined backend-owned internal roles. `CUSTOMER` is excluded. An unknown role or any nonassignable role code returns `404 INTERNAL_ROLE_NOT_FOUND`; the API does not expose role, permission, or role-to-permission editing.

Status uses an explicit existing Identity target state:

```json
{
  "status": "SUSPENDED"
}
```

Role assignment changes one role only:

```json
{
  "assigned": true
}
```

Both commands use target-state `PUT` semantics and lock the target User before validation and mutation. A target equal to current state returns the current safe projection without changing `updatedAt`, authorization freshness, refresh sessions, or audit evidence. A real status or role-assignment change updates the User timestamp, increments authorization freshness once, and records one actor-bound PII-safe audit outcome. Suspension and disablement also revoke every refresh-token session for the target User in the same transaction; reactivation never restores those sessions. A real role change for an active User preserves its refresh sessions.

Access JWTs carry a private `authzVersion` claim. For a protected request, Meridian verifies signature and expiry, checks exact `jti` revocation, compares that claim with the current lightweight User authorization version, and only then installs the embedded roles and permissions. A mismatch returns `401 INVALID_TOKEN`. Internal Web may perform its established single refresh: an active User receives a replacement token with current roles and permissions, while a suspended or disabled User cannot refresh. The authorization version is never returned in an administration response.

The commands do not use a request UUID and clients must not retry automatically after an unknown transport result. The client preserves its last confirmed view, refreshes protected User state, and may explicitly send the same target state again after review.

### 3.12 Customer profile

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

The safe Customer response contains `customerId`, `customerNumber`, Customer `status`, `verificationStatus`, `profileCompletionStatus`, `primaryActiveBankAccountPresent`, and the profile fields shown above except `identityReference`. Duplicate normalized identity evidence owned by another Customer returns `409 IDENTITY_REFERENCE_ALREADY_IN_USE` without echoing the submitted value.

### 3.13 Customer bank accounts

```json
{
  "bankCode": "VCB",
  "bankNameSnapshot": "Vietcombank",
  "accountHolderName": "Customer Demo",
  "accountNumber": "1234567890"
}
```

The account number is normalized by removing spaces and hyphens and must contain at least six normalized characters. List, add, make-primary, and deactivate responses contain the account ID, bank code/name, account-holder name, masked account number, last four characters, status, primary flag, and lifecycle timestamps. They never return the full account number, ciphertext, fingerprint, or protection metadata.

### 3.14 Customer Partner verification options

```text
GET /api/v1/partner-companies/verification-options
```

This authenticated Customer read requires `partner:employee:verify:own`. It returns active Partner Companies in deterministic company-code order with only `partnerCompanyId`, `companyCode`, and `name`. It excludes the Salary Advance policy limit, Partner Employees, salary and eligibility evidence, and import-batch facts. The selector does not itself verify employment or state that the Customer is eligible.

### 3.15 Partner staff reads

The Partner Company, employee, and import-batch reads require `partner:read`. Partner Company responses include the configured Salary Advance policy limit. Partner Employee responses include employee code, identity reference, salary amount, Salary Advance limit, employment status, active state, company identity, and import-batch identity. These are restricted Staff contracts and must not be reused as Customer response shapes. Import-batch responses contain company identity, effective month, status, and valid/invalid row counts.

### 3.16 Partner administration commands

Partner administration commands require exact `partner:manage`. Company creation accepts `companyCode`, `name`, one of `ACTIVE`, `INACTIVE`, or `SUSPENDED`, and a nonnegative `salaryAdvancePolicyLimit`. Company code is a stable unique identifier. The update contract accepts only `name` and `salaryAdvancePolicyLimit`; status changes use the distinct `{ "status": "..." }` command. A same-status command returns the unchanged company without another audit effect.

The employee import body uses a structured JSON batch:

```json
{
  "requestId": "f3d0a51f-4e56-4a19-9a6f-f738113bc7db",
  "effectiveMonth": "2026-09",
  "rows": [
    {
      "employeeCode": "MER-EMP-101",
      "identityReference": "MER-ID-101",
      "salaryAmount": 12000000,
      "salaryAdvanceLimit": 4000000,
      "employmentStatus": "ACTIVE",
      "active": true
    }
  ]
}
```

`effectiveMonth` is a valid `YYYY-MM` year-month. Each row is validated independently. Valid rows become Partner Employee records in one `COMPLETED` batch; invalid rows are excluded and returned as `rowIndex`, `errorCode`, and safe `reason` values. Duplicate employee codes within the batch invalidate every conflicting row. The response returns `importBatchId`, `partnerCompanyId`, effective month, batch status, valid/invalid counts, and the safe rejection list. It does not echo employee codes, identity references, salaries, or employee limits in rejection details.

The `requestId` is the durable import operation identity. Exact replay returns the original response without repeating link reconciliation or audit effects. Reuse with different semantic content returns `409 IDEMPOTENCY_KEY_REUSED`. Multiple completed batches for one company and effective month remain legal; Partner selects the latest completed batch through its existing authority rules.

When the new batch is the authoritative latest `COMPLETED` batch for the current UTC month, the same transaction refreshes an existing `VERIFIED` Customer–Partner Employee link only if its stored verified identity reference and employee code resolve to exactly one active employee in that batch and no Partner eligibility review is pending. The link keeps its identifier, records the new employee and source batch, preserves the last verification time, and advances its refresh time. Missing, ambiguous, inactive, or review-controlled evidence leaves the link unchanged so existing eligibility reads report stale evidence. The endpoint response shape is unchanged. The import does not create reviews or mutate LoanApplications, Salary Advance limits, movements, or exposure.

### 3.17 Employee verification

```json
{
  "employeeCode": "MER-EMP-001"
}
```

The body does not accept `customerId` or `identityReference`.

Safe response fields: `customerId`, `partnerCompanyId`, `partnerEmployeeId`, `customerPartnerEmployeeLinkId`, `outcome`, `linkStatus`, and `manualReviewRequired`.

Responses exclude salary, limit values, employee code, identity evidence, and raw matching evidence.

When verification requires authorized review, Partner persists or reuses one pending review for the Customer and Partner Company. `MATCHED_INACTIVE` remains a hard stop and does not create a review. A later automatic terminal match (`MATCHED_ACTIVE` or `MATCHED_INACTIVE`) supersedes an unresolved review before it can authorize conflicting evidence.

The Customer reads a later manual-review result through:

```text
GET /api/v1/partner-companies/{partnerCompanyId}/employee-verifications
```

The read requires `partner:employee:verify:own` and derives `customerId` from the authenticated Customer. It does not accept a Customer identifier in the path, query, or body. Partner selects the latest review for that Customer and the exact `partnerCompanyId`, so reviews for different Customers or Partner Companies cannot be conflated.

```json
{
  "partnerCompanyId": "22222222-2222-2222-2222-222222222222",
  "outcome": "PENDING_MANUAL_REVIEW",
  "manualReviewRequired": true
}
```

A pending review returns `PENDING_MANUAL_REVIEW` with `manualReviewRequired: true`. Approval returns `MANUAL_REVIEW_APPROVED` and rejection returns `MANUAL_REVIEW_REJECTED`, both with `manualReviewRequired: false`. A missing or superseded latest review returns `404 PARTNER_EMPLOYEE_VERIFICATION_NOT_FOUND`.

The response exposes only `partnerCompanyId`, `outcome`, and `manualReviewRequired`. It excludes Customer identity, review identity, reviewer identity, decision reasons, selected employees, candidate lists, employee codes, identity evidence, salary and limit evidence, import-batch facts, timestamps, and internal notes.

### 3.18 Partner eligibility manual review

The default shared queue is:

```text
GET /api/v1/admin/partner-eligibility-reviews?status=PENDING&page=0&size=20
```

`status` accepts `PENDING`, `APPROVED`, `REJECTED`, or `SUPERSEDED`; omission selects `PENDING`. `page` starts at zero and `size` is between 1 and 100. The response contains `page`, `size`, `totalElements`, `totalPages`, and ordered `items`. Each item contains `reviewId`, opaque `customerId`, Partner Company identity/code/name, `effectiveMonth`, trigger outcome, requested employee code, status, creation time, `reviewable`, and a controlled `nonReviewableReason` when applicable.

The detail endpoint returns the same review identity plus `sourceImportBatchId`, decision outcome/reason, selected employee, reviewer user ID, review time, creation/update times, action availability, and current candidates. Candidate rows contain only `partnerEmployeeId`, `importBatchId`, employee code, employment status, and active state. They exclude Customer identity evidence, salary, Salary Advance limit, and unrestricted notes.

Approval body:

```json
{
  "outcome": "APPROVE",
  "partnerEmployeeId": "50000000-0000-4000-8000-000000000005",
  "reasonCode": "CURRENT_EMPLOYEE_CONFIRMED"
}
```

Rejection body:

```json
{
  "outcome": "REJECT",
  "partnerEmployeeId": null,
  "reasonCode": "NO_ELIGIBLE_CURRENT_EMPLOYEE"
}
```

Rejection also accepts `IDENTITY_EVIDENCE_MISMATCH` and `INSUFFICIENT_SOURCE_EVIDENCE`. Approval requires one exact Partner Employee and `CURRENT_EMPLOYEE_CONFIRMED`; rejection must not select an employee.

Before approval, Partner revalidates the active Partner Company, current UTC effective month, authoritative latest `COMPLETED` batch, current usable Customer identity evidence, selected employee ownership and batch, active row and employment state, and identity match. Success atomically creates or refreshes the reusable `VERIFIED` link with `MANUAL_REVIEW_APPROVED`, resolves the review, and records PII-safe audit evidence. Rejection records `MANUAL_REVIEW_REJECTED` and does not create or refresh a link. Neither outcome creates a LoanApplication, Salary Advance limit, reservation, or Loan-owned verification snapshot.

An exact terminal replay returns the recorded projection without another link or audit effect. A different decision after terminal resolution returns `409 PARTNER_ELIGIBILITY_REVIEW_ALREADY_RESOLVED`. Competing decisions serialize so one terminal outcome wins. The client reconciles an unknown command result through the detail GET and does not retry the decision optimistically.

Important errors:

| Status | Code | Condition |
|---|---|---|
| `400` | `VALIDATION_FAILED` | Invalid status/page input, unknown fields, missing outcome/reason, or an invalid outcome/reason/employee combination. |
| `403` | `FORBIDDEN` | The caller lacks exact `partner:read` for reads or `partner:manage` for decision. |
| `404` | `PARTNER_ELIGIBILITY_REVIEW_NOT_FOUND`, `PARTNER_COMPANY_NOT_FOUND`, or `CUSTOMER_NOT_FOUND` | Required authoritative state does not exist. |
| `409` | `PARTNER_ELIGIBILITY_REVIEW_ALREADY_RESOLVED`, `PARTNER_ELIGIBILITY_REVIEW_STALE`, or `CUSTOMER_NOT_ACTIVE` | The review is terminal, prior-month/replaced-batch stale, or the Customer is inactive. |
| `422` | `PARTNER_COMPANY_INACTIVE`, `PARTNER_EMPLOYEE_INACTIVE`, `PARTNER_ELIGIBILITY_EMPLOYEE_INVALID`, or `PROFILE_INCOMPLETE` | Current evidence cannot authorize the requested outcome. |

### 3.19 Staff-assisted Customer and pre-application intake

Every endpoint in this section authenticates a Staff User with no Customer context. Customer is the selected business subject; Staff never calls a `/customers/me` capability or authenticates as that Customer.

Exact Customer discovery accepts one and only one JSON field:

```json
{ "customerNumber": "CUS-000000042" }
```

or:

```json
{ "identityReference": "012345678901" }
```

Identity reference is accepted only in the `POST /api/v1/staff/customers/search` body. Customer protects it and performs an exact fingerprint lookup. It does not appear in a URL, response, error, or audit payload. A successful Customer response contains `customerId`, Customer number, aggregate status, `UNVERIFIED`/other verification status, profile-completion status, primary-bank-account readiness, and the maintainable profile fields. It excludes raw/protected identity material and bank-account numbers.

`POST /api/v1/staff/customers` accepts required `fullName`, `identityReference`, `phoneNumber`, `residentialAddress`, `employmentStatus`, nullable `employerName`, and both consent booleans. Customer creation and the identity-bearing profile commit in one transaction. The created Customer is `ACTIVE` and `UNVERIFIED`; the command creates no Identity User. Duplicate protected identity returns `409 IDENTITY_REFERENCE_ALREADY_IN_USE` and leaves no Customer shell. Profile update uses the same fields, permits omission of an unchanged identity reference, and preserves the identity-immutability rule after completion.

Staff bank-account requests and masked responses match Section 3.13. The explicit `customerId` identifies the authorized Staff-selected subject; aggregate duplicate, primary, inactive, and deactivation rules are unchanged.

Assisted-origination creation accepts:

```json
{
  "productCode": "UNSECURED_CONSUMER_LOAN",
  "customerId": null
}
```

`productCode` is `UNSECURED_CONSUMER_LOAN` or `COLLATERAL_LOAN`. `SALARY_ADVANCE` returns `422 ASSISTED_ORIGINATION_PRODUCT_NOT_ALLOWED`. `customerId` is optional at creation but, when present, must resolve to an active Customer. The response contains `assistedOriginationCaseId`, `productCode`, nullable `customerId`, `status`, nullable `loanApplicationId`, creator Staff User ID, and lifecycle timestamps. Creation always returns `OPEN`; a successful UCL or Collateral conversion returns `COMPLETED` with the durable resulting `loanApplicationId`.

Customer association uses `{ "customerId": "..." }`. Association and abandonment lock the intake row and require `OPEN`. Abandonment transitions to `ABANDONED`, records `terminalAt`, and blocks later Customer association or evidence mutation with `409 ASSISTED_ORIGINATION_CASE_NOT_OPEN`. None of these commands creates a LoanApplication, application number, checklist, product verification, review cycle, Approval evidence, offer, contract, or exposure.

The controlled intake evidence types are `CUSTOMER_IDENTITY`, `UCL_PAPER_APPLICATION`, and `COLLATERAL_PAPER_APPLICATION`. Identity evidence is allowed for either supported product. The UCL and Collateral paper application forms are accepted only for the matching intake product; mismatch returns `422 INTAKE_EVIDENCE_PRODUCT_MISMATCH`.

Upload is `multipart/form-data` with:

- path `evidenceType`;
- required UUID `uploadRequestId`;
- optional UUID `expectedCurrentVersionId`;
- required `file` with `application/pdf`, `image/jpeg`, or `image/png`, maximum 10 MiB.

The first upload omits `expectedCurrentVersionId`. Replacement supplies the current version returned by the metadata read. Exact `uploadRequestId` replay returns the existing version. Reuse for different content returns `409 IDEMPOTENCY_KEY_REUSED`; a changed current pointer returns `409 STALE_DOCUMENT_VERSION`. The response contains version ID, sequence, safe original filename, detected media type, byte size, and upload time. Evidence metadata contains logical intake-document ID, case ID, evidence type, current-version ID, and ordered immutable versions. It never exposes storage keys. Intake upload does not require or create an application checklist or correction task and is not authorized by `document:upload:staff`.

OCR is requested explicitly after upload:

```text
POST /api/v1/staff/assisted-originations/{assistedOriginationCaseId}/evidence/{evidenceType}/versions/{intakeDocumentVersionId}/ocr
GET  /api/v1/staff/assisted-originations/{assistedOriginationCaseId}/evidence/{evidenceType}/versions/{intakeDocumentVersionId}/ocr
```

`POST` requires an open assisted-origination case, the matching controlled evidence type, and the exact current immutable version. The operation needs no arbitrary idempotency key: the immutable version identifier is unique in the OCR queue. Repeating the request returns the same `ocrJobId` and does not create another job or audit action. A stale replaced version returns `409 OCR_REQUIRES_CURRENT_INTAKE_VERSION`; a version outside the selected case/evidence relationship returns `404 INTAKE_EVIDENCE_VERSION_NOT_FOUND`; a product/evidence mismatch returns `422 INTAKE_EVIDENCE_PRODUCT_MISMATCH`.

`GET` verifies the same case/evidence/version relationship and returns the existing job for that immutable version, including a historical version after replacement. A version without a job returns `404 OCR_JOB_NOT_FOUND`. Both operations return only `ocrJobId`, `intakeDocumentVersionId`, `state`, nullable result `disposition`, `attemptCount`, controlled nullable `failureCategory`, and lifecycle timestamps. They never return extracted text, normalized layout, structured suggestions, ciphertext, storage keys, provider resource names, exception messages, or credentials. `document:upload:staff` does not authorize either operation.

OCR review uses the nested contract:

```text
GET  /api/v1/staff/assisted-originations/{assistedOriginationCaseId}/evidence/{evidenceType}/versions/{intakeDocumentVersionId}/ocr/review
POST /api/v1/staff/assisted-originations/{assistedOriginationCaseId}/evidence/{evidenceType}/versions/{intakeDocumentVersionId}/ocr/review
```

`GET` requires a completed job/result and returns `ocrResultId`, evidence type, disposition, allowlisted `suggestions` with nullable confidence, an empty or finalized `reviewedFields` map, and nullable `reviewedAt`. It may read a historical replaced version after validating the case/evidence/version relationship. It never returns raw OCR text, normalized layout, ciphertext, storage keys, provider response content, processor resource names, or credentials.

`POST` accepts `expectedOcrResultId` and a `reviewedFields` map. Keys must belong to the canonical vocabulary for the evidence type; consent, verification, document-decision, readiness, and lending-decision fields are prohibited. Values use generic size bounds rather than duplicated Customer or Loan business validation, and the map may be empty. The case must remain `OPEN`, and the version must remain current. A stale version returns `409 OCR_REVIEW_REQUIRES_CURRENT_INTAKE_VERSION`; a different expected result returns `409 OCR_RESULT_CHANGED`; an unknown field returns `422 OCR_REVIEW_FIELD_NOT_ALLOWED`. The first successful command inserts one encrypted review and changes the result disposition to `REVIEWED` transactionally. Exact replay returns the existing review; a different replay returns `409 OCR_RESULT_ALREADY_REVIEWED`.

OCR state and reviewed values are advisory. `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`, or `REVIEWED` does not create or modify Customer or Loan state, establish consent or verification, accept or waive evidence, or block manual intake. Staff Web may explicitly copy finalized `reviewedFields` into its existing route-local intake forms. No additional HTTP endpoint applies those values: persistence occurs only when Staff invokes the existing authorized Customer or Loan command.

UCL conversion is:

```text
POST /api/v1/staff/assisted-originations/{assistedOriginationCaseId}/unsecured-consumer-loan/submit
```

```json
{
  "requestedAmount": 10000000,
  "requestedTermMonths": 12
}
```

The command locks the case and requires an `OPEN` UCL intake, a selected Customer, current authoritative Customer readiness, a current signed `UCL_PAPER_APPLICATION`, an active UCL product, valid product amount and term, no blocking UCL application, and no outstanding UCL LoanAccount. Success returns `201 Created` and atomically creates one `STAFF_ASSISTED` UCL application in `DOCUMENTS_PENDING`, the normal `INCOME_PROOF`, `BANK_STATEMENT`, and `EMPLOYMENT_PROOF` checklist, the initial `PENDING_MANUAL_REVIEW` verification, lifecycle/audit evidence, and the completed-case application link. The intake paper evidence remains Document-owned and is not copied into the application checklist.

Collateral conversion is:

```text
POST /api/v1/staff/assisted-originations/{assistedOriginationCaseId}/collateral-loan/submit
```

```json
{
  "requestedAmount": 25000000,
  "requestedTermMonths": 12,
  "collateral": {
    "type": "MOTORBIKE",
    "description": "2024 motorbike",
    "estimatedValue": 35000000,
    "ownershipStatus": "Owned by Customer",
    "conditionNote": "Normal used condition"
  }
}
```

The authenticated actor must be Staff without Customer context and must hold `loan:originate:staff`. The command locks and requires an `OPEN` Collateral case with a selected Customer, current authoritative Customer readiness, a current signed `COLLATERAL_PAPER_APPLICATION`, an active Collateral product, valid amount and term, one valid structured Collateral fact, and no blocking Collateral application. Existing Collateral LoanAccount state adds no product-specific origination restriction.

Success returns `201 Created` and atomically creates one `STAFF_ASSISTED` Collateral application in `DOCUMENTS_PENDING`, exactly one structured Collateral row, the normal required `COLLATERAL_OWNERSHIP_EVIDENCE` checklist item, the initial `PENDING_MANUAL_REVIEW` Collateral verification, lifecycle/audit evidence, and the completed-case application link. Estimated value remains advisory; the command performs no automated valuation or loan-to-value decision. `COLLATERAL_PAPER_APPLICATION` and `CUSTOMER_IDENTITY` remain pre-application intake evidence and are not copied into the application checklist.

The case GET is the authoritative uncertain-result reconciliation read. `COMPLETED` plus a non-null `loanApplicationId` proves success. A client must not automatically repeat the conversion POST after network loss; an authoritative `OPEN` result requires explicit operator confirmation before a new attempt.

---

## 4. Salary Advance, UCL, and Collateral Loan Origination

### 4.1 Salary Advance readiness

```text
GET /api/v1/loan-products/salary-advance/readiness
```

This Customer-only read requires `loan:submit` and derives Customer identity from the Bearer token. It is advisory: it uses non-locking reads, creates no application or limit, reserves no exposure, writes no movement, verification, history, or audit evidence, and does not promise that a later command will succeed after concurrent state changes.

The response contains `productCode`, the reusable `customerPartnerEmployeeLinkId` when currently eligible, `employeeVerificationStatus`, `partnerEligibilityStatus`, `limitStatus`, `totalAmount`, `usedAmount`, `reservedAmount`, `availableAmount`, `lastRefreshAt`, `applicationAllowed`, and ordered `blockerCodes`. It excludes Customer identity, Partner Employee and import-batch identity, Partner salary/evidence, Salary Advance limit and verification identity, workflow recommendations, and internal audit/history evidence.

Important blockers include Customer/profile/bank readiness, `EMPLOYEE_NOT_VERIFIED`, `SALARY_ADVANCE_ELIGIBILITY_DATA_STALE`, `SALARY_ADVANCE_LIMIT_UNAVAILABLE`, `INSUFFICIENT_AVAILABLE_LIMIT`, `BLOCKING_APPLICATION_EXISTS`, `OUTSTANDING_LOAN_ACCOUNT_EXISTS`, `PRODUCT_NOT_AVAILABLE`, and safe `SYSTEM_STATE_CONFLICT`. Current eligibility requires the authoritative latest valid completed Partner import batch for the current UTC effective month; stale evidence remains blocked until re-verification refreshes the reusable link.

### 4.2 LoanApplication read projections

#### 4.2.1 Customer index and shared minimal status read

```text
GET /api/v1/loan-applications
```

The index requires `loan:read:own`, derives Customer identity from the Bearer token, accepts no `customerId`, and returns only that Customer's applications newest first. Each item contains `loanApplicationId`, `applicationNumber`, `productCode`, `productType`, `originationChannel`, `requestedAmount`, `requestedTermMonths`, `status`, `submittedAt`, `lifecycleActive`, and `requiredAction`. All Customer submissions are `CUSTOMER_DIGITAL`; converted UCL and Collateral applications are `STAFF_ASSISTED`.

`lifecycleActive` is false for `VERIFICATION_FAILED`, `REJECTED`, `CUSTOMER_DECLINED`, `DISBURSED`, `CANCELLED`, and `EXPIRED`; it is true for other lifecycle states. `requiredAction` is a server-owned finite value:

| Value | Authoritative evidence |
|---|---|
| `UPLOAD_DOCUMENTS` | Application status is `DOCUMENTS_PENDING`. |
| `COMPLETE_CORRECTIONS` | Application is `RETURNED_FOR_REVISION` and its active correction has an open Customer task or is ready for Customer resubmission. |
| `REVIEW_APPROVED_OFFER` | Application is `CUSTOMER_ACCEPTANCE_PENDING` and its effective current offer remains `PENDING`. |
| `ACKNOWLEDGE_CONTRACT` | Application is `CONTRACT_PENDING` and its current contract is `PREPARED`. |
| `NONE` | No supported Customer action is proven by the owning state. |

The values describe action meaning, not frontend routes. Staff-owned, waiting, and terminal states return `NONE`.

```text
GET /api/v1/loan-applications/{loanApplicationId}
```

An authenticated Customer with `loan:read:own` may read only their own application. Missing and foreign-owned IDs both return `404 LOAN_APPLICATION_NOT_FOUND`. Authorized Staff require `loan:read`; `loan:submit`, `repayment:update`, `approval:decide`, and document permissions do not imply this read.

The response contains only `loanApplicationId`, `applicationNumber`, `productCode`, `productType`, `requestedAmount`, `requestedTermMonths`, `status`, and `submittedAt`. It excludes Customer, employee-link, limit, verification, review-cycle, actor, audit/history, payment, and banking evidence. This is a durable status projection for reconnect/resume flows, not a next-action engine, command recommendation, history API, or Staff work queue.

The Customer-owned correction-abandonment command in Section 5.4 is the only v1 command that produces `CANCELLED`. The route does not accept cancellation from another state or a Staff or administrative cancellation.

#### 4.2.2 Staff application discovery and case read

```text
GET /api/v1/staff/loan-applications?productCode={productCode}&status={status}&page=0&size=20
GET /api/v1/staff/loan-applications/{loanApplicationId}
```

Both routes require an authenticated Staff actor with the exact `loan:read` permission. `loan:read:own`, another lending or document permission, a role name, or a permission prefix does not authorize these routes. Normal authentication and authorization failures return `401` and `403`; a missing case returns `404 LOAN_APPLICATION_NOT_FOUND`.

The Staff index accepts optional exact `productCode` and `status` filters. `page` defaults to `0`, `size` defaults to `20`, and `size` is limited to `1` through `100`; invalid enum, page, or size input returns `400 VALIDATION_FAILED`. Results are ordered by `submittedAt DESC`, then LoanApplication ID `DESC`, and use this page envelope:

```json
{
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "items": [
    {
      "loanApplicationId": "UUID",
      "applicationNumber": "UCL-20260902-000001",
      "productCode": "UNSECURED_CONSUMER_LOAN",
      "productType": "UNSECURED",
      "requestedAmount": 12000000.00,
      "requestedTermMonths": 6,
      "status": "UNDER_REVIEW",
      "submittedAt": "2026-09-02T08:00:00"
    }
  ]
}
```

Each index item is limited to those eight durable application facts. The endpoint does not return Customer identity or contact data, bank data, Partner salary facts, restricted notes, document content, external references, operation or audit identifiers, actor identifiers, or inferred next actions.

The consolidated Staff case response contains the same eight application facts plus `customerReadiness` and `lifecycleHistory`. `customerReadiness` contains only `active`, `profileComplete`, `hasPrimaryActiveBankAccount`, and `verificationStatus`. Loan obtains that purpose-limited snapshot through its Customer readiness port; it does not read Customer persistence. This is not a general Staff Customer-profile contract and requires no `customer:read` expansion.

#### 4.2.3 Staff product-verification and review reads

```text
GET /api/v1/staff/loan-applications/{loanApplicationId}/verification
GET /api/v1/staff/loan-applications/{loanApplicationId}/review
Authorization: Bearer <staff-token with exact loan:review>
```

Both reads are independent of the broader `loan:read` case projection. They require a Staff-shaped principal and the exact `loan:review` authority at both controller and application-service boundaries. A role name, a permission prefix, `approval:recommend`, or `loan:read` alone does not grant either read.

The verification response contains the safe common application facts, upload and processing readiness, backend-derived `startAvailable` and `completeAvailable` flags, and product-specific evidence:

- Salary Advance returns the immutable application-owned verification sequence, outcomes, limit snapshots, and verification time. It never exposes a manual verification action or live Partner values.
- UCL returns the authoritative current cycle and all immutable cycles ordered by verification sequence.
- Collateral returns the same ordered cycle evidence plus the single application-owned Collateral assessment snapshot. No LTV or browser-derived eligibility value is returned.
- UCL and Collateral may return current document-version correction targets limited to the product's supported evidence vocabulary. Each target carries its checklist item, document type, requirement status, and current immutable version ID so the client does not accept free-form correction identifiers.

The review response contains the same safe common facts, document readiness, the latest product-verification result, backend-derived product readiness, `reviewStartAvailable`, and the latest Loan-owned review cycle when one exists. It deliberately excludes recommendation and Approver-decision evidence; those remain a later contract.

Neither response exposes Customer PII, raw document contents, reviewer identities, assessment notes, internal operation IDs, or audit payloads. Reads return `LOAN_APPLICATION_NOT_FOUND` for an absent application, product-specific verification-required conflicts for missing authoritative verification evidence, and `SYSTEM_STATE_CONFLICT` for inconsistent application-owned evidence. Clients use these GETs to reconcile the no-business-UUID verification and review-start commands and must not automatically replay a POST after an unknown network result.

`lifecycleHistory` preserves Loan's authoritative transition sequence. Each item contains nullable `fromStatus`, `toStatus`, `action`, `actorType`, and `occurredAt`. It omits transition and persistence IDs, sequence internals, operation IDs, actor User IDs, audit IDs, reasons, restricted assessment or internal notes, and external references. The read does not synthesize history, infer action eligibility, or mutate application, history, audit, or Customer state.

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

The response contains the application identity and number, authenticated `customerId`, product code/type, status, requested terms, reusable employee-link ID, product-verification result, total/used/reserved/available limit snapshots, and submission time. It excludes employee code, identity evidence, salary, Partner import-batch identity, bank-account data, and internal history/audit evidence.

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

The endpoint stops at origination and evidence setup. It rejects a Customer who already has another UCL LoanAccount with positive contractual outstanding in `ACTIVE` or `OVERDUE` using `409 OUTSTANDING_LOAN_ACCOUNT_EXISTS`; zero-outstanding `SETTLED` or `CLOSED` UCL accounts do not block, unrelated Salary Advance accounts are ignored, and inconsistent account state returns safe `409 SYSTEM_STATE_CONFLICT`. Separate Staff commands perform manual verification and review entry. The full UCL lifecycle then supports structured correction and re-verification, approval and immutable exact-request offer handling, correction cancellation, operational contracts, activation, and servicing through settlement and closure.

Important errors:

| Status | Code |
|---|---|
| `404` | `CUSTOMER_NOT_FOUND` or `PRODUCT_NOT_FOUND` |
| `409` | `CUSTOMER_NOT_ACTIVE`, `BLOCKING_APPLICATION_EXISTS`, `OUTSTANDING_LOAN_ACCOUNT_EXISTS`, or `SYSTEM_STATE_CONFLICT` |
| `422` | `PROFILE_INCOMPLETE`, `PRIMARY_BANK_ACCOUNT_REQUIRED`, `PRODUCT_INACTIVE`, `PRODUCT_POLICY_INVALID`, `INVALID_PRODUCT_AMOUNT`, or `INVALID_PRODUCT_TERM` |

### 4.5 Submit Collateral Loan application

```text
POST /api/v1/loan-applications/collateral-loan
```

The authenticated Customer supplies the requested terms and exactly one structured Collateral asset:

```json
{
  "requestedAmount": 25000000,
  "requestedTermMonths": 12,
  "collateral": {
    "type": "MOTORBIKE",
    "description": "2024 Honda motorbike",
    "estimatedValue": 35000000,
    "ownershipStatus": "Customer-provided ownership statement",
    "conditionNote": "Normal used condition"
  }
}
```

The API derives Customer identity from the Bearer token and rejects unknown top-level or Collateral fields. The Customer must be active, have a complete required profile and a primary active bank account, and hold `loan:submit`. The active catalog product must be `COLLATERAL_LOAN` / `SECURED`. The requested amount must be whole VND and within the current active product minimum and maximum. Supported terms are exactly 6, 12, 18, and 24 months. `estimatedValue` must be positive whole VND, but the endpoint performs no loan-to-value calculation or comparison between requested and estimated values. `description`, `ownershipStatus`, and `conditionNote` are required nonblank Customer-submitted text, normalized by trimming before storage, and limited to 500, 200, and 500 characters respectively.

Success returns `201 Created` with `loanApplicationId`, `applicationNumber`, `productCode`, `productType`, `status`, `requestedAmount`, `requestedTermMonths`, `collateralType`, `productVerificationResult`, `evidenceRequirements`, and `submittedAt`. Each safe evidence requirement contains `checklistItemId`, `documentType`, and `requirementStatus`. The response returns one required `COLLATERAL_OWNERSHIP_EVIDENCE` item, allowing the Customer to call the existing document-version upload endpoint without exposing document contents or internal review evidence.

The application starts in `DOCUMENTS_PENDING` with application-owned sequence-1 `PENDING_MANUAL_REVIEW` Collateral verification. Uploading the required evidence through the existing Document workflow can complete generic upload readiness and advance the application to `SUBMITTED`; it does not complete Collateral verification or permit review. The Staff verification commands in Sections 4.8-4.9 make the terminal decision. Only the authoritative latest `VERIFIED` cycle permits review, recommendation, and an Approver action.

Collateral submission serializes by Customer and product and rejects an existing blocking Collateral application. It deliberately does not impose an outstanding Collateral LoanAccount rule, compare requested amount to estimated value, or create Salary Advance reservation, Partner, or exposure effects. Structured Collateral facts and requested terms are immutable after submission. Approval, pricing, offer response, operational contract preparation, manual-disbursement activation, LoanAccount creation, final schedule construction, repayment, overdue evaluation and cure, settlement, and closure are executable. Collateral servicing uses the common financial controls with zero Salary Advance exposure effect.

Important errors:

| Status | Code |
|---|---|
| `400` | `VALIDATION_FAILED` for malformed input, unknown fields, unsupported Collateral type, or Bean Validation failure |
| `404` | `CUSTOMER_NOT_FOUND` or `PRODUCT_NOT_FOUND` |
| `409` | `CUSTOMER_NOT_ACTIVE`, `BLOCKING_APPLICATION_EXISTS`, `COLLATERAL_VERIFICATION_REQUIRED`, or `SYSTEM_STATE_CONFLICT` |
| `422` | `PROFILE_INCOMPLETE`, `PRIMARY_BANK_ACCOUNT_REQUIRED`, `PRODUCT_INACTIVE`, `PRODUCT_POLICY_INVALID`, `INVALID_PRODUCT_AMOUNT`, `INVALID_PRODUCT_TERM`, `INVALID_COLLATERAL_DETAILS`, or `PRODUCT_VERIFICATION_PENDING` |

### 4.6 Start UCL manual verification

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

### 4.7 Complete UCL manual verification

```text
POST /api/v1/loan-applications/{loanApplicationId}/unsecured-consumer-loan-verification/complete
```

The request contains a required outcome and restricted assessment note. `reasonCode` and `correctionPlan` are absent for `VERIFIED` and `FAILED`:

```json
{
  "outcome": "VERIFIED",
  "assessmentNote": "Income and employment evidence are consistent for Loan Officer review."
}
```

`REQUIRES_MORE_INFORMATION` requires a controlled reason and a valid UCL correction plan. For example:

```json
{
  "outcome": "REQUIRES_MORE_INFORMATION",
  "assessmentNote": "The current bank statement is unreadable and cannot support assessment.",
  "reasonCode": "DOCUMENT_REPLACEMENT_REQUIRED",
  "correctionPlan": {
    "tasks": [
      {
        "scope": "DOCUMENT_REPLACEMENT",
        "responsibleParty": "CUSTOMER",
        "documentType": "BANK_STATEMENT",
        "createChecklistItem": false,
        "checklistItemId": "UUID",
        "baselineDocumentVersionId": "UUID",
        "customerInstruction": "Upload a readable replacement bank statement.",
        "staffInstruction": null
      }
    ]
  }
}
```

Unknown properties, a missing outcome, and blank or over-2,000-character notes return `400 VALIDATION_FAILED`. The application must be in `VERIFICATION_PENDING`, the latest result must remain `PENDING_MANUAL_REVIEW`, and documents must still be processing-ready. Completion stores the outcome with the authenticated Staff actor, one sampled UTC completion time, and the restricted note. `VERIFIED` moves the application to `SUBMITTED`; `FAILED` moves it to terminal `VERIFICATION_FAILED`; `REQUIRES_MORE_INFORMATION` atomically moves it to `RETURNED_FOR_REVISION` and creates the correction request and tasks. The response does not expose the Staff actor or assessment note:

```json
{
  "loanApplicationId": "UUID",
  "status": "SUBMITTED",
  "productVerificationResult": "VERIFIED",
  "reviewedAt": "2026-08-11T09:30:00"
}
```

For a `FAILED` request, use `"outcome": "FAILED"` with no reason or plan. Supplying correction fields for `VERIFIED` or `FAILED`, omitting them for `REQUIRES_MORE_INFORMATION`, using `RECENT_PAYSLIP`, or targeting a stale or foreign UCL document yields the applicable validation or state-conflict error without partial effects.

Important errors include `UCL_VERIFICATION_NOT_APPLICABLE`, `UCL_VERIFICATION_REQUIRED`, `UCL_VERIFICATION_DOCUMENTS_NOT_READY`, `PRODUCT_VERIFICATION_NOT_PENDING`, `PRODUCT_VERIFICATION_COMPLETION_NOT_ALLOWED`, and `UCL_VERIFICATION_ASSESSMENT_REQUIRED`.

### 4.8 Start Collateral Loan manual verification

```text
POST /api/v1/loan-applications/{loanApplicationId}/collateral-loan-verification/start
```

No body is required. The authenticated Staff actor must hold `loan:review`. The application must be `COLLATERAL_LOAN` / `SECURED`, be in `SUBMITTED`, have an authoritative latest `PENDING_MANUAL_REVIEW` cycle, have processing-ready required ownership evidence, and have exactly one submitted Collateral fact row for the current public-API invariant. Success is not replay-safe: it atomically moves the application to `VERIFICATION_PENDING` and records one transition and PII-safe audit event. A repeated or concurrent losing start returns the normal lifecycle conflict.

The general LoanApplication status projection intentionally excludes product evidence. The start response is therefore the smallest authorized Staff assessment surface and returns the exact verification ID plus the submitted facts needed for manual assessment:

```json
{
  "verificationId": "UUID",
  "loanApplicationId": "UUID",
  "status": "VERIFICATION_PENDING",
  "productVerificationResult": "PENDING_MANUAL_REVIEW",
  "collateral": {
    "collateralType": "MOTORBIKE",
    "description": "2024 Honda motorbike",
    "estimatedValue": 35000000,
    "ownershipStatus": "Customer-provided ownership statement",
    "conditionNote": "Normal used condition"
  }
}
```

Important errors include `COLLATERAL_VERIFICATION_NOT_APPLICABLE`, `COLLATERAL_VERIFICATION_REQUIRED`, `COLLATERAL_VERIFICATION_DOCUMENTS_NOT_READY`, `PRODUCT_VERIFICATION_NOT_PENDING`, `PRODUCT_VERIFICATION_START_NOT_ALLOWED`, and `SYSTEM_STATE_CONFLICT`.

### 4.9 Complete Collateral Loan manual verification

```text
POST /api/v1/loan-applications/{loanApplicationId}/collateral-loan-verification/complete
```

The body identifies the exact cycle that Staff assessed. For `VERIFIED` or `FAILED`, correction fields must be absent:

```json
{
  "expectedVerificationId": "UUID",
  "outcome": "VERIFIED",
  "assessmentNote": "Ownership evidence and submitted Collateral facts are sufficient for Loan Officer review."
}
```

`REQUIRES_MORE_INFORMATION` requires a controlled reason and a document-only correction plan over the existing ownership-evidence item:

```json
{
  "expectedVerificationId": "UUID",
  "outcome": "REQUIRES_MORE_INFORMATION",
  "assessmentNote": "The ownership evidence is unreadable and must be replaced.",
  "reasonCode": "DOCUMENT_REPLACEMENT_REQUIRED",
  "correctionPlan": {
    "tasks": [
      {
        "scope": "DOCUMENT_REPLACEMENT",
        "responsibleParty": "CUSTOMER",
        "documentType": "COLLATERAL_OWNERSHIP_EVIDENCE",
        "createChecklistItem": false,
        "checklistItemId": "UUID",
        "baselineDocumentVersionId": "UUID",
        "customerInstruction": "Upload a readable replacement ownership document.",
        "staffInstruction": null
      }
    ]
  }
}
```

The note is trimmed, required, restricted, and limited to 2,000 characters. The API serializes completion against the authoritative latest cycle, rechecks document readiness, and completes a pending cycle exactly once. A mismatched `expectedVerificationId` returns `409 STALE_COLLATERAL_VERIFICATION`. `VERIFIED` returns the application to `SUBMITTED`; `FAILED` moves it to `VERIFICATION_FAILED` without a correction; `REQUIRES_MORE_INFORMATION` atomically moves it to `RETURNED_FOR_REVISION` and creates the correction request/tasks.

The safe response contains verification/application identity, application status, product-verification result, and completion time. It excludes reviewer identity, assessment note, correction internals, audit identity, and submitted Collateral facts. Unknown properties or an invalid body return `400 VALIDATION_FAILED`.

Important errors include `COLLATERAL_VERIFICATION_NOT_APPLICABLE`, `COLLATERAL_VERIFICATION_REQUIRED`, `COLLATERAL_VERIFICATION_DOCUMENTS_NOT_READY`, `COLLATERAL_VERIFICATION_ASSESSMENT_REQUIRED`, `STALE_COLLATERAL_VERIFICATION`, `PRODUCT_VERIFICATION_NOT_PENDING`, `PRODUCT_VERIFICATION_COMPLETION_NOT_ALLOWED`, and `INVALID_CORRECTION_PLAN`.

### 4.10 Start review

```text
POST /api/v1/loan-applications/{loanApplicationId}/review/start
```

No body is required. The reviewer is derived from the Bearer token.

For UCL, review start additionally requires the authoritative verification result to be `VERIFIED`. Missing, pending, `FAILED`, and `REQUIRES_MORE_INFORMATION` records fail closed. The same Loan Officer may complete UCL manual verification and start review; the existing maker-checker rule still requires a different Approver for the final decision.

For Collateral Loan, review start requires the authoritative latest numbered verification cycle to be `VERIFIED`. Missing, pending, `FAILED`, and `REQUIRES_MORE_INFORMATION` evidence fails closed. The same Loan Officer may complete manual verification and later start review or recommend; maker-checker still requires a different Approver from the recommending Loan Officer.

---

## 5. Recommendation, Approval, Corrections, and Documents

### 5.0 Staff recommendation and decision reads

```text
GET /api/v1/staff/loan-applications/{loanApplicationId}/recommendation
GET /api/v1/staff/loan-applications/{loanApplicationId}/decision
GET /api/v1/staff/approval-work?productCode={productCode}&page={page}&size={size}
```

The recommendation read requires exact `approval:recommend`. It returns the current Loan-owned review cycle, product-verification and document readiness, authoritative correction options, a durable recommendation for that cycle when present, and backend-derived recommendation availability. The decision read and Approver queue require exact `approval:decide`; neither requires `loan:read`. Application services also require a Staff-shaped principal with no Customer context.

The decision read returns the exact latest recommendation and review-cycle provenance, current-actor maker-checker eligibility, current Loan state and readiness, the durable decision tied to that recommendation when present, and ordered safe decision history. It does not expose recommending or deciding User IDs. The Approver queue uses server-side exact `APPROVAL_PENDING` membership, optional exact product filtering, zero-based paging of 1 to 100 items, and deterministic `submittedAt DESC, loanApplicationId DESC` ordering.

These reads exclude restricted internal notes, Customer PII, document content, audit and operation identifiers, external references, and Customer-only offer data. They execute as repeatable-read observations across the Approval-owned evidence and Loan-owned boundary projection. A missing application returns `404 LOAN_APPLICATION_NOT_FOUND`. An `APPROVAL_PENDING` case without an applicable recommendation for the active review cycle, or with a decision already recorded for that recommendation, fails closed with `409 SYSTEM_STATE_CONFLICT`; historical decisions remain readable after later valid Loan transitions.

Recommendation and decision POSTs have no business UUID and are never automatically retried. After an uncertain result, the client reads the corresponding projection and resolves only when the exact review-cycle/recommendation provenance and action prove the durable result. A failed reconciliation read leaves contradictory commands locked until an explicit successful refresh.

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
  "internalNotes": "Optional staff-only note.",
  "expectedReviewCycleId": "UUID"
}
```

Every recommendation requires `expectedReviewCycleId` from the Staff recommendation projection. The identifier is expected-state evidence, not a business operation UUID, and must match the active Loan-owned review cycle. Rejection also requires a nonblank `reason`. Revision actions additionally require a controlled `reasonCode` and one to ten tasks.

For UCL, positive and rejection recommendations and the structured Customer or Staff correction actions are executable. UCL correction tasks may replace or review only application-owned current `INCOME_PROOF`, `BANK_STATEMENT`, or `EMPLOYMENT_PROOF` evidence. They cannot create a Salary-specific `RECENT_PAYSLIP` task or change requested amount or term.

For Collateral Loan, a latest `VERIFIED` cycle permits normal approval/rejection recommendation. Revision recommendations may create only Customer `DOCUMENT_REPLACEMENT` or Staff `DOCUMENT_REVIEW` tasks for the existing current `COLLATERAL_OWNERSHIP_EVIDENCE` item. They cannot add checklist items, upload supporting documents, or change requested terms or submitted Collateral facts. Resubmission must return through manual verification before another review.

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

Success returns `201 Created` with recommendation, application, review-cycle, and Loan Officer identities; action, reason, reason code, restricted internal notes, and submission time. This Staff-only response is not part of any Customer read contract.

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
  "internalNotes": "Optional staff-only note.",
  "expectedReviewRecommendationId": "UUID",
  "expectedReviewCycleId": "UUID"
}
```

Every decision requires the recommendation and review-cycle identifiers returned by the Staff decision projection. These expected-state identifiers must still identify the latest applicable recommendation and active review cycle; they are concurrency evidence, not a business operation UUID. The Approver must differ from the Loan Officer who submitted the applicable recommendation. Mixed corrections use separate Customer and Staff tasks.

Success returns `201 Created` with decision, application, recommendation, and Approver identities; action, reason, reason code, restricted internal notes, and decision time. This Staff-only response is not exposed through Customer application, offer, contract, or LoanAccount reads.

For UCL, `APPROVE` atomically records the decision, generates one immutable exact-request offer with `FLAT_ORIGINAL_PRINCIPAL` pricing and `MONTHLY_INSTALLMENT` items, and finishes in `CUSTOMER_ACCEPTANCE_PENDING`. `REJECT`, `RETURN_TO_LOAN_OFFICER_REVIEW`, and structured mixed Customer/Staff correction remain available common decisions under the UCL document restrictions.

For Collateral Loan, the authoritative latest verification cycle must still be `VERIFIED` when any Approver action executes. Missing evidence returns `409 COLLATERAL_VERIFICATION_REQUIRED`; pending, failed, or more-information outcomes return the corresponding `422 PRODUCT_VERIFICATION_PENDING`, `PRODUCT_VERIFICATION_FAILED`, or `PRODUCT_VERIFICATION_REQUIRES_MORE_INFORMATION` code. Each failure leaves no durable decision or Loan-side effect. `APPROVE` atomically records the decision and generates one exact-request offer using 1.5% monthly flat original-principal interest, zero fee, seven-calendar-day validity, and one undated `MONTHLY_INSTALLMENT` item per approved month. Total interest is rounded once to whole VND using `HALF_UP`; principal and interest residuals are assigned only to the final item. The estimated Collateral value does not change principal or create a loan-to-value decision or counteroffer.

Collateral `REJECT`, `RETURN_TO_LOAN_OFFICER_REVIEW`, and `REQUEST_CUSTOMER_OR_STAFF_CORRECTION` use the common transitions and reason/plan rules. Correction remains limited to replacement or Staff review of the existing ownership-evidence item; no action changes submitted Collateral facts or requested terms. None creates Salary Advance reservation, limit, movement, or exposure effects. `MER-ARCH-006-api-request-flow-and-dependencies.md` defines the synchronous coordination and rollback boundary.

Important errors include `MAKER_CHECKER_VIOLATION`, `STALE_REVIEW_RECOMMENDATION`, `STALE_REVIEW_CYCLE`, `COLLATERAL_VERIFICATION_REQUIRED`, the product-verification outcome codes above, `PRODUCT_POLICY_INVALID`, `INVALID_PRODUCT_TERM`, and controlled reason/plan validation errors.

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
- the Staff member who created the correction request cannot complete its Staff-owned tasks; this maker-checker rule does not apply when Staff records completion of a Customer-owned task;
- the purpose-specific Customer-task completion route accepts only an authenticated Staff actor with no Customer identity and exact `loan:correction:staff` authority, an active Customer-owned task on the active request of a `RETURNED_FOR_REVISION` Staff-assisted UCL or Collateral application, and the same proof required by Customer completion;
- assisted Customer-task completion records the authenticated Staff user as completer and audit actor while retaining the application Customer as business subject; exact `completionRequestId` replay returns the completed task and different content conflicts;
- a mixed request cannot be resubmitted after only Customer work is complete;
- a Customer-only request may be resubmitted by Staff only for an eligible Staff-assisted UCL or Collateral application after every task is complete; Customer-digital Customer-only requests remain outside Staff authority;
- UCL resubmission preserves completed verification evidence, returns the application to `SUBMITTED`, and creates the next linked `PENDING_MANUAL_REVIEW` verification cycle; an untouched pending pre-review cycle is reused;
- review cannot restart until the authoritative latest UCL verification cycle is `VERIFIED`;
- UCL resubmission rechecks product-scoped outstanding debt and does not invoke Salary Advance eligibility, limit, reservation, movement, or revalidation behavior;
- Collateral resubmission preserves completed cycles, preserves submitted Collateral facts, returns to `SUBMITTED`, and creates exactly one next pending cycle linked to the resubmitted correction; it has no outstanding-LoanAccount guard or Salary Advance effect;
- Collateral replacement/review is limited to the existing ownership-evidence checklist item, and review cannot restart until the new authoritative cycle is `VERIFIED`;
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

Success atomically marks the active correction request `CANCELLED`, changes the LoanApplication to `CANCELLED`, and records immutable history and audit evidence. For Salary Advance it also releases the repository-derived reservation exactly once and writes one `RESERVATION_RELEASED` movement. For UCL it stores no release reference and creates no Salary Advance limit, movement, reservation, conversion, or release effect. Cancellation does not run Partner freshness or re-verification and changes neither LoanAccount nor repayment state.

#### 5.4.1 Evidenced Staff-assisted UCL cancellation

The Staff command is purpose-specific to `STAFF_ASSISTED` UCL in `RETURNED_FOR_REVISION`. It requires an authenticated Staff principal with no Customer identity, exact `loan:cancel:staff`, and the `LOAN_OFFICER` role. Collateral Loan, Salary Advance, Customer-digital applications, and every other application status fail with `409 LOAN_APPLICATION_CANCELLATION_NOT_ALLOWED`. Customer-digital cancellation remains on the Customer-owned route and cannot be authorized with the Staff permission.

Before recording cancellation, the Loan Officer uploads `CUSTOMER_CANCELLATION_REQUEST` evidence through the assisted-action evidence endpoint. The multipart target contains `correctionRequestId`, `uploadRequestId`, optional `expectedCurrentVersionId`, and `file`. Document authorizes only the exact active correction, stores immutable versions, permits exact-baseline replacement before cancellation, and rejects replacement after Loan records cancellation.

The command body is:

```json
{
  "requestId": "10000000-0000-4000-8000-000000000006",
  "expectedCorrectionRequestId": "20000000-0000-4000-8000-000000000006",
  "evidenceDocumentVersionId": "30000000-0000-4000-8000-000000000006"
}
```

Loan derives the Customer subject from the application and records the authenticated Staff user as actor. It consumes the exact current evidence version, terminalizes the correction and application through the same cancellation execution path as Customer-digital cancellation, stores the Staff actor and Document version reference in `loan_application_cancellations`, and records PII-safe Customer, application, correction, evidence, and status audit facts. UCL continues to create no Salary Advance exposure effect.

Exact replay requires the same request, application, correction, Staff actor, and evidence version and returns `idempotentReplay = true` without duplicate terminal effects. Reusing the request identity with different logical content returns `409 IDEMPOTENCY_KEY_REUSED`. A new request against the cancelled application returns `409 LOAN_APPLICATION_CANCELLATION_NOT_ALLOWED`. Missing, mismatched, foreign-target, or stale evidence uses the established assisted-action evidence errors.

### 5.5 Document upload and review

The Customer checklist projection is:

```text
GET /api/v1/loan-applications/{loanApplicationId}/documents
```

It requires `document:read:own`, derives Customer identity from authentication, and verifies application ownership through the Document-to-Loan boundary. Missing applications/checklists and foreign-owned applications return the same concealed `404 DOCUMENT_CHECKLIST_NOT_FOUND`.

The response contains checklist ID, application ID, `stage`, aggregate `uploadComplete` and `processingReady`, and ordered items. Each item contains checklist-item ID, document type, requirement status, Customer status, item upload/processing readiness, and nullable `currentVersion`. Customer status is one of `NOT_UPLOADED`, `AWAITING_REVIEW`, `ACCEPTED`, `REPLACEMENT_REQUESTED`, or `WAIVED`. A current version contains only document-version ID, checklist-item ID, version number, original filename, detected MIME type, byte size, and upload time.

The projection excludes review-decision IDs, reviewer identity, restricted notes and waiver rationale, storage keys/paths, hashes, audit IDs, historical versions, and document content. An empty Salary Advance submission checklist is both upload-complete and processing-ready.

The Staff document projection is:

```text
GET /api/v1/staff/loan-applications/{loanApplicationId}/documents
```

It requires exact `document:review` authority and does not require `loan:read`. The response contains the application ID and safe status, checklist stage and readiness, and ordered checklist items. Each item contains its requirement and evidence status, readiness, nullable current version, deterministic version history, and deterministic safe review history. Version metadata contains document-version ID, version number, original filename, detected MIME type, byte size, and upload time. Review history contains only the reviewed version ID, outcome, optional controlled waiver reason, and decision time.

The Staff projection excludes restricted Staff notes, reviewer and uploader User IDs, storage references, hashes, request/operation IDs, audit IDs, document content, and Customer identity or contact data. A missing checklist returns `404 DOCUMENT_CHECKLIST_NOT_FOUND`. Inconsistent current-version or review associations fail with `409 SYSTEM_STATE_CONFLICT` rather than returning partial evidence.

Upload is multipart with:

- `uploadRequestId`
- optional `expectedCurrentVersionId`
- `file`

Accepted types are PDF, JPEG, and PNG, up to 10 MiB, with signature-to-media-type matching. A stale replacement baseline returns `409 STALE_DOCUMENT_VERSION`.

For a `STAFF_ASSISTED` application in `DOCUMENTS_PENDING`, exact `document:upload:assisted` authority permits initial checklist upload without a correction task. It does not authorize Customer-digital application upload, later correction upload, or another workflow state. Exact `document:upload:assisted-correction` authority permits only an upload for the exact active open Customer-owned correction task of an eligible Staff-assisted UCL or Collateral application in `RETURNED_FOR_REVISION`; replacement must name that task's exact baseline version. `document:upload:staff` remains limited to an open Staff-owned correction task, and `document:upload:intake` remains limited to pre-application intake evidence. Exact `uploadRequestId` replay returns the existing logical upload, different logical content returns `409 IDEMPOTENCY_KEY_REUSED`, and replacement must retain the authoritative `expectedCurrentVersionId`. The final missing required initial upload publishes the established completion event and advances the application from `DOCUMENTS_PENDING` to `SUBMITTED` through the existing Loan workflow.

Customer-owned document upload, correction task/query and resubmission, cancellation, offer response, and contract acknowledgment require `originationChannel = CUSTOMER_DIGITAL`. A Customer attempting those mutations against a `STAFF_ASSISTED` application receives `403 CUSTOMER_DIRECT_ACTION_NOT_ALLOWED`; Customer projections do not advertise those direct actions. Purpose-specific Staff-mediated correction completion/resubmission, offer response, contract acknowledgment, and UCL Customer-requested cancellation use separate Staff contracts without manufacturing a Customer principal. No generic Staff checklist-mutation contract is required.

#### 5.5.1 Evidenced Staff-assisted downstream Customer decisions

These contracts apply only to the product/channel combinations named by each action. Offer response and contract acknowledgment support `STAFF_ASSISTED` UCL and Collateral Loan. Customer-requested cancellation supports only `STAFF_ASSISTED` UCL. The Customer is the decision subject, the authenticated Staff user is the recording actor, Document owns the signed immutable evidence, and Loan owns the resulting workflow action. Salary Advance remains Customer-digital only. The Staff endpoints never manufacture a Customer principal and do not broaden the Customer-owned endpoints.

Offer-response evidence uses `CUSTOMER_OFFER_RESPONSE` and binds `loanApplicationId`, exact `approvedOfferId`, and declared `ACCEPT` or `DECLINE`. Upload is multipart with `uploadRequestId`, optional `expectedCurrentVersionId`, `approvedOfferId`, `declaredOfferDecision`, and `file`. The Loan command body is:

```json
{
  "requestId": "10000000-0000-4000-8000-000000000001",
  "expectedApprovedOfferId": "20000000-0000-4000-8000-000000000001",
  "action": "ACCEPT",
  "evidenceDocumentVersionId": "30000000-0000-4000-8000-000000000001"
}
```

Exact `loan:offer:respond:staff` authority and the `LOAN_OFFICER` role are both required. Loan locks the workflow and current offer, derives the Customer subject from the application, verifies the exact current evidence version and declared action, and preserves ordinary offer expiry and transition history. Success moves `CUSTOMER_ACCEPTANCE_PENDING` to `CONTRACT_PENDING` for `ACCEPT`, or to `CUSTOMER_DECLINED` for `DECLINE`, and stores one immutable Staff-assisted response per offer.

Contract evidence uses `CUSTOMER_CONTRACT_ACKNOWLEDGMENT` and binds `loanApplicationId`, exact `loanContractId`, and exact `contractVersion`. Upload is multipart with `uploadRequestId`, optional `expectedCurrentVersionId`, `loanContractId`, `contractVersion`, and `file`. The Loan command body is:

```json
{
  "acknowledgmentRequestId": "10000000-0000-4000-8000-000000000002",
  "contractId": "20000000-0000-4000-8000-000000000002",
  "expectedContractVersion": 1,
  "evidenceDocumentVersionId": "30000000-0000-4000-8000-000000000002"
}
```

Exact `loan:contract:acknowledge:staff` authority and the `ACCOUNTING_OFFICER` role are both required. Loan uses the established acknowledgment-request and application lock order, requires the exact current `PREPARED` contract, validates evidence for that version, records the Staff actor and Customer subject separately, and changes only the contract to `ACKNOWLEDGED`; the application remains `CONTRACT_PENDING`. Existing readiness confirmation then operates unchanged. A regenerated version requires fresh evidence and a fresh acknowledgment.

For both actions, exact request-identity replay returns the recorded outcome without another transition, audit, or action row. Reuse with different semantic content returns `409 IDEMPOTENCY_KEY_REUSED`. Evidence replacement before the action requires the current `expectedCurrentVersionId`; stale replacement returns `409 STALE_DOCUMENT_VERSION`. Missing, mismatched, or non-current evidence returns `422 ASSISTED_ACTION_EVIDENCE_REQUIRED`, `409 ASSISTED_ACTION_EVIDENCE_INVALID`, or `409 STALE_DOCUMENT_VERSION`. Wrong channel, product, target, or state returns the applicable `ASSISTED_ACTION_NOT_ALLOWED`, offer, or contract conflict. After Loan records the action, target authorization rejects further evidence replacement.

Review targets the exact `documentVersionId` and supports:

- `ACCEPT_DOCUMENT`
- `WAIVE_DOCUMENT`
- `REQUEST_REPLACEMENT`

Waiver requires `document:waive` and an allowed waiver code. Replacement requires `DOCUMENT_REPLACEMENT_REQUIRED` plus a Customer-visible instruction. Only an unreviewed authoritative current version is actionable. Reviewing a non-current version returns `409 STALE_DOCUMENT_VERSION`; a new logical review against an already-reviewed current version returns `409 DOCUMENT_ALREADY_REVIEWED`. An exact replay with the original `reviewRequestId` and logical payload still returns the existing immutable decision, while reuse of that request ID with different content remains `409 IDEMPOTENCY_KEY_REUSED`.

Content responses stream only the authorized immutable version and include attachment, `Cache-Control: no-store, private`, and `X-Content-Type-Options: nosniff`.

### 5.6 Staff correction case and proof

```text
GET /api/v1/staff/loan-applications/{loanApplicationId}/corrections
```

The endpoint requires exact `loan:correction:staff` authority and does not require `loan:read`. It returns safe Loan-owned application number, product, origination channel, and status facts plus a nullable latest correction request. No correction is an ordinary `200` response with `correctionRequest: null`.

A correction request contains its ID, status, controlled reason, creation time, `makerCheckerBlockedForCurrentActor`, `allTasksComplete`, `staffResubmissionReady`, and tasks in deterministic request sequence. Each task contains its ID, responsibility, status, scope, optional document/item/baseline identities, controlled reason, timestamps, backend-derived `SATISFIED`, `MISSING`, or `NOT_APPLICABLE` proof state, and backend-derived upload/completion availability. Staff-owned work exposes only its Staff instruction. A Customer-owned task exposes its Customer instruction and `customerSourceViaStaff = true` only for an eligible Staff-assisted UCL or Collateral application; the same task remains non-actionable and instruction-free in the Customer-digital Staff projection.

The response also contains `assistedCancellation`. Loan reports `available = true` only for a Staff-assisted UCL in `RETURNED_FOR_REVISION` with the exact active correction and no recorded cancellation. The object carries that correction ID, nullable current `CUSTOMER_CANCELLATION_REQUEST` version metadata, and backend-derived upload and command availability for the current actor. Customer-digital, Collateral, Salary Advance, and non-returned cases report the action unavailable.

Loan derives document proof through `LoanDocumentChecklistPort`; it does not access Document persistence. The response excludes the correction creator and completer User IDs, operation IDs, audit IDs, restricted assessment notes, and unrelated Customer data. Customer instruction is purpose-limited to the eligible Staff-assisted correction workflow and is not returned for Customer-digital tasks. `404 LOAN_APPLICATION_NOT_FOUND` represents a missing application. The command endpoints remain authoritative when state changes after the read.

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

`GET` is read-only. A missing application or offer returns its stable not-found code; a foreign-owned application returns `403 ACCESS_DENIED`. Acceptance moves an eligible Salary Advance, UCL, or Collateral Loan application to `CONTRACT_PENDING`. Decline or first discovery of pending expiry applies the terminal outcome exactly once. Salary Advance reservation release runs only for Salary Advance; UCL and Collateral Loan create no Salary Advance limit, movement, reservation, conversion, or release effect. An identical Customer response is replay-safe, while a contradictory terminal action returns a conflict.

For Collateral Loan, the safe response preserves the submitted requested principal and term and reports `FLAT_ORIGINAL_PRINCIPAL`, `flatMonthlyInterestRate = 0.015000`, zero fee, `MONTHLY_INSTALLMENT`, and the reconciled provisional items. It contains no final repayment dates. Acceptance moves the application to `CONTRACT_PENDING`, where the common operational-contract flow in Section 6.2 begins.

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

Regeneration preserves accepted terms and repayment items, supersedes the prior version, refreshes the eligible destination, and requires fresh Customer acknowledgment. Contract preparation is executable for Salary Advance, UCL, and Collateral Loan. UCL and Collateral contracts copy the accepted offer's exact financial terms and monthly repayment items, require the authoritative latest application-owned product verification to be `VERIFIED`, capture the eligible destination through the common protected mechanism, and never read or mutate Salary Advance verification, limit, or movement state. Product-verification validation occurs before protected bank-account capture.

Important errors: `APPROVED_OFFER_NOT_FOUND`, `OFFER_NOT_ACCEPTED`, `UCL_VERIFICATION_INVALID`, `COLLATERAL_VERIFICATION_INVALID`, `CONTRACT_VERSION_STALE`, `CONTRACT_REGENERATION_NOT_ALLOWED`, and `IDEMPOTENCY_KEY_REUSED`.

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

Stable blockers include `DOCUMENTS_NOT_PROCESSING_READY`, `ACTIVE_CORRECTION_REQUEST`, `CUSTOMER_INACTIVE`, `CAPTURED_ACCOUNT_MISSING`, `CAPTURED_ACCOUNT_INACTIVE`, `SALARY_ADVANCE_RESERVATION_INVALID`, `SALARY_ADVANCE_RESERVATION_RELEASED`, `UCL_VERIFICATION_INVALID`, `COLLATERAL_VERIFICATION_INVALID`, `READINESS_ALREADY_CONFIRMED`, and `CONTRACT_VERSION_STALE`.

Product readiness is explicit: Salary Advance requires its exact unreleased reservation. UCL and Collateral Loan require authoritative latest application-owned `VERIFIED` evidence and have no Salary Advance reservation or exposure effect.

### 6.5 Staff contract work reads

```text
GET /api/v1/staff/contract-work?productCode=UNSECURED_CONSUMER_LOAN&page=0&size=25
GET /api/v1/staff/loan-applications/{loanApplicationId}/contract
```

Both reads require a Staff actor with exact `loan:contract:read` authority and the Accounting Officer role. The queue applies `CONTRACT_PENDING` membership, optional exact product filtering, paging with `size` from 1 through 100, and the established deterministic application ordering on the server. The case read accepts only `CONTRACT_PENDING` and `DISBURSEMENT_PENDING`; unrelated lifecycle states return `INVALID_APPLICATION_STATE`.

Each safe row or case combines the application header, current masked `LoanContractDto` when present, canonical point-in-time `ContractReadinessDto`, and one backend-derived `workStage`: `NEEDS_PREPARATION`, `CUSTOMER_ACKNOWLEDGMENT_REQUIRED`, `READINESS_BLOCKED`, `READY_TO_CONFIRM`, or `READINESS_CONFIRMED`. Contradictory application, contract, readiness-identity, or stage evidence fails with `SYSTEM_STATE_CONFLICT`; the browser must not repair or reinterpret it. The response contains no full account number, cryptographic protection material, internal actor/operation identifier, document content, assessment note, or Customer-owned acknowledgment command.

These Staff reads are operational discovery and composition only. Contract preparation/regeneration and readiness confirmation continue to use the commands in Sections 6.2 and 6.4 with exact displayed versions and stable request UUIDs. Customer acknowledgment remains exclusively Customer-owned. `READINESS_CONFIRMED` means the application is `DISBURSEMENT_PENDING`; it does not mean a transfer occurred or a LoanAccount was activated.

---

## 7. Destination Reveal, Disbursement, and LoanAccount

### 7.1 Staff disbursement work reads

```text
GET /api/v1/staff/disbursement-work?productCode=UNSECURED_CONSUMER_LOAN&page=0&size=25
GET /api/v1/staff/loan-applications/{loanApplicationId}/disbursement
```

Both reads require an authenticated Staff actor with no Customer identity, exact `loan:disburse` authority, and the Accounting Officer role. The queue selects exactly `DISBURSEMENT_PENDING` applications. It supports an optional exact `productCode`, `page >= 0`, and `size` from 1 through 100, using the deterministic application ordering owned by Loan.

Every queue item contains the application identity and requested terms, a safe current-contract summary with approved terms and masked destination, and the projection-only `READY_TO_DISBURSE` work stage. The case read accepts only `DISBURSEMENT_PENDING` and `DISBURSED`. A pending case returns the complete safe current contract with provisional repayment items and no activation. A completed case returns `DISBURSED`, the activated LoanAccount summary, value dates, and the persisted final schedule.

Loan composes each result under a repeatable read. A pending case requires the current confirmed, non-superseded `READY_FOR_DISBURSEMENT` contract, matching application and destination identity, and no activation evidence. A completed case additionally requires coherent `ManualDisbursement`, LoanAccount, and final `RepaymentSchedule` identities, contract version, financial terms, dates, and activation timestamps. Missing or contradictory evidence returns `SYSTEM_STATE_CONFLICT`; the read does not repair state.

These responses exclude Customer ID, the full destination, protection ciphertext, nonce, key ID and AAD, external transfer reference, request UUID, Staff actor ID, and audit identifiers. The completed case proves durable activation state but does not prove which disbursement request UUID caused it.

### 7.2 Reveal destination

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

### 7.3 Confirm manual disbursement

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

Salary Advance activation converts the exact reserved principal to used exposure. UCL and Collateral Loan activation create the same common account, disbursement, final-schedule, progress, history, transition, and audit evidence without creating or mutating any Salary Advance exposure artifact. Their final `MONTHLY_INSTALLMENT` schedules copy the contract amounts and item order exactly and apply the controlled first repayment date plus monthly anchor.

Important errors:

- `IDEMPOTENCY_KEY_REUSED`
- `DISBURSEMENT_ALREADY_COMPLETED`
- `DUPLICATE_TRANSFER_REFERENCE`
- `SYSTEM_STATE_CONFLICT`
- `DISBURSEMENT_VALUE_DATE_INVALID`
- `FIRST_REPAYMENT_DATE_INVALID`
- `PRODUCT_ACTIVATION_NOT_SUPPORTED`

### 7.4 Staff servicing work

```text
GET /api/v1/staff/servicing-work?productCode=UNSECURED_CONSUMER_LOAN&accountStatus=OVERDUE&page=0&size=25
```

The read requires an authenticated `STAFF` actor with no Customer identity and exact `loan:read` authority. It does not require `repayment:update` or an Accounting Officer role. The optional `productCode` filter accepts an executable product code. The optional `accountStatus` filter accepts only `ACTIVE` or `OVERDUE`; when omitted, both serviceable states are returned. `page` must be non-negative, `size` must be from 1 through 100, and ordering is `activatedAt DESC` followed by LoanAccount ID descending.

Loan owns queue membership. The persistence query filters and pages LoanAccounts directly, joins the linked LoanApplication only for application number and product facts, and never constructs membership by paging applications or calling known-ID account reads. Each returned row requires a positive outstanding balance, a linked `DISBURSED` LoanApplication, matching application/account identity, and an `ACTIVE` or `OVERDUE` account state. Contradictory evidence returns `SYSTEM_STATE_CONFLICT`; the read never repairs state or evaluates overdue status.

Each row contains application/account IDs and numbers, product code/type, account status, activation time, originated principal, total paid/outstanding, servicing evaluation date, and optional last-payment value/recorded dates. The response excludes Customer identity, destination details, external payment reference, request UUID, Staff actor identity, product-exposure movement identifiers, encryption evidence, and audit identifiers. `SETTLED` and `CLOSED` accounts do not belong to ordinary repayment work; the application-scoped LoanAccount read may still return them when queried directly.

### 7.5 Staff settlement work

```text
GET /api/v1/staff/settlement-work?productCode=UNSECURED_CONSUMER_LOAN&page=0&size=25
```

The read requires an authenticated `STAFF` actor with no Customer identity, exact `loan:settlement:approve`, and the `APPROVER` role. It accepts an optional executable `productCode`; `page` must be non-negative and `size` must be from 1 through 100. Ordering is `activatedAt DESC` followed by LoanAccount ID descending.

Loan owns membership. A row represents a `DISBURSED` application with a coherent `ACTIVE` or `OVERDUE` LoanAccount, positive authoritative total outstanding, and matching final-schedule and installment-progress evidence. The query does not validate future payment date or reference input and performs no overdue evaluation or mutation. Contradictory candidate evidence returns `409 SYSTEM_STATE_CONFLICT`.

Rows contain only application/account IDs and references, product code/type, account state, activation and servicing timing, and current total paid/outstanding. They exclude Customer identity, destination data, payment references, request UUIDs, settlement and exposure evidence IDs, actor IDs, audit IDs, and encryption details.

### 7.6 Staff closure work

```text
GET /api/v1/staff/closure-work?productCode=SALARY_ADVANCE&page=0&size=25
```

The read requires an authenticated `STAFF` actor with no Customer identity, exact `loan:account:close`, and the `ACCOUNTING_OFFICER` role. Filtering, paging, and deterministic ordering follow the settlement-work contract.

Loan owns membership. A row represents a `DISBURSED` application whose LoanAccount is exactly `SETTLED`, has zero contractual outstanding, fully paid final-schedule progress, a coherent terminal repayment outcome and status history, reconciled product-specific exposure evidence, and no administrative closure. `payoffProvenance` is `CONTRACTUAL_PAYOFF` or `APPROVED_SETTLEMENT`. `ACTIVE`, `OVERDUE`, and `CLOSED` accounts are excluded. Contradictory evidence returns `409 SYSTEM_STATE_CONFLICT`; the read performs no payment, balance, exposure, state, history, or audit mutation.

The response uses the same restricted-field boundary as settlement work and does not expose payment, settlement, closure, request, actor, exposure, or audit identifiers.

### 7.7 Query LoanAccount

The Customer LoanAccount index is:

```text
GET /api/v1/loan-accounts
```

It requires `loan:read:own`, derives Customer identity from authentication, accepts no `customerId`, and returns the Customer's accounts newest activation first. Each compact item contains application/account IDs, account number, application number, product code/type, account status, activation time, originated principal, authoritative total paid/outstanding from the LoanAccount repayment balance, and `servicingActive`. `servicingActive` is true only for `ACTIVE` and `OVERDUE`. An eligible Customer with no accounts receives an empty array.

The index excludes the disbursement destination, schedule items, payment/transfer references, contract internals, settlement/closure evidence, Staff identities, and audit/history evidence. Inconsistent account-to-application ownership evidence fails with `409 SYSTEM_STATE_CONFLICT`.

The detailed application-scoped read remains:

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

An activated Collateral LoanAccount uses this same safe read contract before, during, and after servicing.

For Customers, missing, foreign-owned, and unavailable accounts all return `404 LOAN_ACCOUNT_NOT_FOUND`.

---

## 8. Repayment

Repayment APIs support serviceable Salary Advance, UCL, and Collateral LoanAccounts.

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
- `principalAllocated` reports contractual principal satisfied by the payment;
- `principalReleased` reports product exposure released: it equals allocated principal for Salary Advance and is zero for UCL and Collateral Loan;
- UCL and Collateral repayment never create or mutate Salary Advance limit or movement evidence;
- exact contractual payoff produces `SETTLED`.

The response includes safe transaction/account IDs, amount/value date, recording time, ordered allocations, `principalAllocated`, `principalReleased`, installment outcomes, resulting account status/balances, and `idempotentReplay`. It excludes the external reference, request UUID, actor, Customer, employee-link, limit, bank, audit, history, and internal operation evidence.

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

The caller must be an Approver with `loan:settlement:approve`. The Salary Advance, UCL, or Collateral account must be `ACTIVE` or `OVERDUE`, and `expectedSettlementAmount` must equal locked current total outstanding. Meridian records an `APPROVED_SETTLEMENT` payment transaction, applies oldest-installment and fee-interest-principal allocation, applies the product-specific exposure result, and returns a `SETTLED` result. Salary Advance releases allocated principal exactly; UCL and Collateral report zero principal released and create no Salary Advance movement. Discounted, concessionary, waiver, forgiveness, and write-off outcomes are not accepted.

The response contains safe application/account/payment/schedule identifiers, amount and value date, approval time, principal allocated and released, resulting balances/status, and `idempotentReplay`. It excludes the request UUID, canonical external payment reference, actor and Customer identities, settlement evidence identity, limit/movement identities, audit/history identities, and internal reconciliation evidence.

An identical request replay returns the original durable settlement result, including after later administrative closure, without new payment, allocation, exposure, history, settlement, or audit evidence. Reusing the request UUID with different logical content returns `409 IDEMPOTENCY_KEY_REUSED`. Other relevant outcomes are `422 SETTLEMENT_AMOUNT_INVALID`, `422 SETTLEMENT_VALUE_DATE_INVALID`, `409 DUPLICATE_PAYMENT_REFERENCE`, `409 SETTLEMENT_NOT_ALLOWED`, and safe `409 SYSTEM_STATE_CONFLICT`; missing permission or business role returns `403`.

The purpose-limited reload-recovery read is:

```text
GET /api/v1/loan-applications/{loanApplicationId}/settlements/approved
```

It requires an authenticated `STAFF` Approver with exact `loan:settlement:approve`. The service verifies the application/account relationship, immutable approved settlement, matching payment amount and recording time, and a current `SETTLED` or `CLOSED` account. It returns application/account IDs, settlement amount, payment value date, and approval time only. The response excludes the request UUID, external payment reference, actor identity, settlement/payment evidence IDs, audit/history IDs, and product-exposure evidence. `404 APPROVED_LOAN_SETTLEMENT_NOT_FOUND` conceals absent evidence; inconsistent evidence returns `409 SYSTEM_STATE_CONFLICT`. This read reconstructs safe candidate fields but does not prove an unresolved request identity; only exact replay does.

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

The caller must be an Accounting Officer with `loan:account:close`. Closure supports a fully reconciled Salary Advance, UCL, or Collateral `SETTLED` LoanAccount produced by ordinary contractual payoff or Administrative Full-Balance Settlement. It records a separate `SETTLED -> CLOSED` administrative result and does not change payments, allocations, balances, the final schedule, installment progress, product exposure, or LoanApplication state.

The response contains only application/account identity, `CLOSED`, closure time, and `idempotentReplay`. It excludes request, closure-evidence, actor, payment, limit/movement, audit/history, and internal reconciliation identities. An identical replay returns the original closure result. A different request after closure or an attempt before `SETTLED` returns `409 LOAN_ACCOUNT_CLOSURE_NOT_ALLOWED`; conflicting reuse of the same request UUID returns `409 IDEMPOTENCY_KEY_REUSED`; inconsistent evidence returns safe `409 SYSTEM_STATE_CONFLICT`; missing permission or business role returns `403`.

---

## 9. Postman Collection

Collection:

```text
docs/api/Meridian-Platform.postman_collection.json
```

It authenticates role-specific demo actors, stores Bearer tokens, exercises refresh and current-session logout through the cookie jar, and covers the catalogue above, including protected Loan Product and Internal User administration, advisory Salary Advance readiness, durable LoanApplication status recovery, returned-correction cancellation and exact replay, Customer, Staff, mixed-correction, document, intake OCR start/status/replay/stale-version and purpose-limited review behavior, offer, contract, disbursement, LoanAccount, repayment, Administrative Full-Balance Settlement, administrative closure, and negative-security flows. Internal User scenarios cover discovery, predefined roles, reversible status and role targets, safe no-ops, stale-token rejection, missing/nonassignable targets, exact permission denial, and restoration of seeded status, role, access-token, and cookie state. UCL scenarios include all three verification outcomes, correction and re-verification, cancellation, outstanding-debt rejection, and product-generic servicing through closure. The Collateral folder covers prepared ownership evidence, exact-cycle manual verification, Loan Officer recommendation, all four Approver actions, exact offer assertions, Customer acceptance/decline, protected contract preparation, acknowledgment, readiness, destination reveal, activation replay, final schedule reads, ownership concealment, partial repayment and history, Administrative Full-Balance Settlement, and closure.

Complex correction scenarios require prepared application, review-cycle, checklist, and version variables. The optional cancellation folder requires `returnedCancellationScenarioEnabled=true` and a separate Customer-owned `cancellationLoanApplicationId` in `RETURNED_FOR_REVISION`; it confirms the command, exact replay, and terminal application GET without exposing internal evidence IDs. Seed fixtures and scenario-specific IDs belong to the collection or its environment, not this API contract.

---
