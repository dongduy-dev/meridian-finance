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
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`
- `GET /api/v1/loan-products`
- `GET /api/v1/loan-products/{productCode}`
- `GET /v3/api-docs`
- `GET /swagger-ui.html` and `/swagger-ui/**`

All endpoints outside login, refresh, logout, and the other public operations require:

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
| POST | `/api/v1/auth/login` | Public | Authenticate, return Bearer-token actor facts, and create the refresh cookie. |
| POST | `/api/v1/auth/refresh` | Public at the Bearer layer; refresh cookie required | Rotate the refresh token and return a fresh access token. |
| POST | `/api/v1/auth/logout` | Public at the Bearer layer; credentials optional | Invalidate the presented current-session credentials and clear the refresh cookie. |
| GET | `/api/v1/loan-products` | Public | List active loan products. |
| GET | `/api/v1/loan-products/{productCode}` | Public | Return one active loan product by code. |
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
| POST | `/api/v1/loan-applications/collateral-loan` | Customer with `loan:submit` | Submit a Collateral Loan application with one structured asset and required ownership-evidence collection. |
| GET | `/api/v1/loan-applications/{loanApplicationId}` | Customer `loan:read:own` or Staff `loan:read` | Return a safe durable LoanApplication status projection. |
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
| POST | `/api/v1/loan-applications/{loanApplicationId}/approved-offer/decline` | `loan:offer:respond:own` | Decline a valid pending offer and release a Salary Advance reservation when applicable. |
| POST | `/api/v1/loan-applications/{loanApplicationId}/contracts` | `loan:contract:prepare` | Prepare version 1 or regenerate the current contract. |
| GET | `/api/v1/loan-applications/{loanApplicationId}/contracts/current` | `loan:read:own` or `loan:contract:read` | Return the safe masked current contract. |
| POST | `/api/v1/loan-applications/{loanApplicationId}/contracts/current/acknowledgment` | `loan:contract:acknowledge:own` | Acknowledge the exact current version. |
| GET | `/api/v1/loan-applications/{loanApplicationId}/contracts/current/readiness` | `loan:contract:read` | Calculate point-in-time readiness; optional `expectedContractVersion`. |
| POST | `/api/v1/loan-applications/{loanApplicationId}/contracts/current/readiness/confirm` | `loan:disbursement:prepare` | Recompute and confirm readiness. |
| POST | `/api/v1/loan-applications/{loanApplicationId}/contracts/current/disbursement-destination/reveal` | `loan:disburse` | Reveal the full immutable ready-contract destination. |
| POST | `/api/v1/loan-applications/{loanApplicationId}/disbursements` | `loan:disburse` | Confirm an external transfer and activate the LoanAccount. |
| GET | `/api/v1/loan-applications/{loanApplicationId}/loan-account` | `loan:read:own` or `loan:read` | Return originated terms, final schedule, and servicing state. |
| POST | `/api/v1/loan-applications/{loanApplicationId}/repayments` | `repayment:update` | Record or replay a manual Salary Advance, UCL, or Collateral Loan repayment. |
| GET | `/api/v1/loan-applications/{loanApplicationId}/repayments?page=0&size=20` | `loan:read:own` or `loan:read` | Return immutable paged repayment history. |
| POST | `/api/v1/loan-applications/{loanApplicationId}/settlements` | `loan:settlement:approve` plus Approver role | Approve and apply an Administrative Full-Balance Settlement. |
| POST | `/api/v1/loan-applications/{loanApplicationId}/loan-account/closure` | `loan:account:close` plus Accounting Officer role | Close an eligible settled LoanAccount administratively. |

---

## 3. Authentication, Customer, and Partner Requests

### 3.1 Login

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

### 3.2 Refresh access

```text
POST /api/v1/auth/refresh
Cookie: MERIDIAN_REFRESH_TOKEN=<opaque-refresh-token>
```

The endpoint accepts no request body and does not require a Bearer token. Meridian locks the refresh-token session, verifies that it is unconsumed, unrevoked, and unexpired, reloads the associated active User and current RBAC grants, consumes the presented token, and creates one replacement in the same family. The response uses the same JSON fields as login and replaces the refresh cookie. Each replacement receives the full configured inactivity lifetime from its rotation time.

Only a SHA-256 digest of the cryptographically random refresh token is persisted. The raw refresh token, digest, session identity, family identity, and replacement relationship are not returned in JSON or errors.

Missing, unknown, expired, revoked, or otherwise invalid refresh state returns `401 INVALID_REFRESH_TOKEN`. Reusing a consumed token also returns that safe error and revokes the active token family, requiring a new login. Competing refresh requests with one token cannot both succeed.

### 3.3 Current-session logout

```text
POST /api/v1/auth/logout
Cookie: MERIDIAN_REFRESH_TOKEN=<opaque-refresh-token>  (optional)
Authorization: Bearer <accessToken>                    (optional)
```

The endpoint accepts no request body. A known presented refresh token revokes its complete refresh-token family, and a presented valid Bearer token becomes unusable immediately. Logout clears `MERIDIAN_REFRESH_TOKEN` on `/api/v1/auth` with the same security attributes used for issuance and `Max-Age=0`.

Logout returns `204 No Content` whether credentials are valid, missing, unknown, expired, malformed, or already revoked. An invalid Bearer token does not prevent revocation through a valid refresh cookie. Repeated logout is idempotent and does not disclose credential state. Only the credentials presented by the current client are invalidated; other login families and access tokens for the same User remain usable.

### 3.4 Loan-product catalogue

```text
GET /api/v1/loan-products
GET /api/v1/loan-products/{productCode}
```

Both reads are public. The collection read returns active products; the item read normalizes the supplied code and returns only an active matching product. Each product contains `productCode`, `productType`, `name`, `description`, `active`, `minAmount`, and `maxAmount`.

An unknown code returns `404 PRODUCT_CODE_NOT_FOUND`. A recognized code with no active product returns `404 PRODUCT_NOT_FOUND`.

### 3.5 Customer profile

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

### 3.6 Customer bank accounts

```json
{
  "bankCode": "VCB",
  "bankNameSnapshot": "Vietcombank",
  "accountHolderName": "Customer Demo",
  "accountNumber": "1234567890"
}
```

The account number is normalized by removing spaces and hyphens and must contain at least six normalized characters. List, add, make-primary, and deactivate responses contain the account ID, bank code/name, account-holder name, masked account number, last four characters, status, primary flag, and lifecycle timestamps. They never return the full account number, ciphertext, fingerprint, or protection metadata.

### 3.7 Partner staff reads

The Partner Company, employee, and import-batch reads require `partner:read`. Partner Company responses include the configured Salary Advance policy limit. Partner Employee responses include employee code, identity reference, salary amount, Salary Advance limit, employment status, active state, company identity, and import-batch identity. These are restricted Staff contracts and must not be reused as Customer response shapes. Import-batch responses contain company identity, effective month, status, and valid/invalid row counts.

### 3.8 Employee verification

```json
{
  "employeeCode": "MER-EMP-001"
}
```

The body does not accept `customerId` or `identityReference`.

Safe response fields: `customerId`, `partnerCompanyId`, `partnerEmployeeId`, `customerPartnerEmployeeLinkId`, `outcome`, `linkStatus`, and `manualReviewRequired`.

Responses exclude salary, limit values, employee code, identity evidence, and raw matching evidence.

---

## 4. Salary Advance, UCL, and Collateral Loan Origination

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

The Customer-owned correction-abandonment command in Section 5.4 is the only v1 command that produces `CANCELLED`. The route does not accept cancellation from another state or a Staff or administrative cancellation.

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
  "internalNotes": "Optional staff-only note."
}
```

The Approver must differ from the Loan Officer who submitted the applicable recommendation. Mixed corrections use separate Customer and Staff tasks.

Success returns `201 Created` with decision, application, recommendation, and Approver identities; action, reason, reason code, restricted internal notes, and decision time. This Staff-only response is not exposed through Customer application, offer, contract, or LoanAccount reads.

For UCL, `APPROVE` atomically records the decision, generates one immutable exact-request offer with `FLAT_ORIGINAL_PRINCIPAL` pricing and `MONTHLY_INSTALLMENT` items, and finishes in `CUSTOMER_ACCEPTANCE_PENDING`. `REJECT`, `RETURN_TO_LOAN_OFFICER_REVIEW`, and structured mixed Customer/Staff correction remain available common decisions under the UCL document restrictions.

For Collateral Loan, the authoritative latest verification cycle must still be `VERIFIED` when any Approver action executes. Missing evidence returns `409 COLLATERAL_VERIFICATION_REQUIRED`; pending, failed, or more-information outcomes return the corresponding `422 PRODUCT_VERIFICATION_PENDING`, `PRODUCT_VERIFICATION_FAILED`, or `PRODUCT_VERIFICATION_REQUIRES_MORE_INFORMATION` code. Each failure leaves no durable decision or Loan-side effect. `APPROVE` atomically records the decision and generates one exact-request offer using 1.5% monthly flat original-principal interest, zero fee, seven-calendar-day validity, and one undated `MONTHLY_INSTALLMENT` item per approved month. Total interest is rounded once to whole VND using `HALF_UP`; principal and interest residuals are assigned only to the final item. The estimated Collateral value does not change principal or create a loan-to-value decision or counteroffer.

Collateral `REJECT`, `RETURN_TO_LOAN_OFFICER_REVIEW`, and `REQUEST_CUSTOMER_OR_STAFF_CORRECTION` use the common transitions and reason/plan rules. Correction remains limited to replacement or Staff review of the existing ownership-evidence item; no action changes submitted Collateral facts or requested terms. None creates Salary Advance reservation, limit, movement, or exposure effects. `MER-ARCH-006-api-request-flow-and-dependencies.md` defines the synchronous coordination and rollback boundary.

Important errors include `MAKER_CHECKER_VIOLATION`, `STALE_REVIEW_CYCLE`, `COLLATERAL_VERIFICATION_REQUIRED`, the product-verification outcome codes above, `PRODUCT_POLICY_INVALID`, `INVALID_PRODUCT_TERM`, and controlled reason/plan validation errors.

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

Salary Advance activation converts the exact reserved principal to used exposure. UCL and Collateral Loan activation create the same common account, disbursement, final-schedule, progress, history, transition, and audit evidence without creating or mutating any Salary Advance exposure artifact. Their final `MONTHLY_INSTALLMENT` schedules copy the contract amounts and item order exactly and apply the controlled first repayment date plus monthly anchor.

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

It authenticates role-specific demo actors, stores Bearer tokens, exercises refresh and current-session logout through the cookie jar, and covers the catalogue above, including advisory Salary Advance readiness, durable LoanApplication status recovery, returned-correction cancellation and exact replay, Customer, Staff, mixed-correction, document, offer, contract, disbursement, LoanAccount, repayment, Administrative Full-Balance Settlement, administrative closure, and negative-security flows. UCL scenarios include all three verification outcomes, correction and re-verification, cancellation, outstanding-debt rejection, and product-generic servicing through closure. The Collateral folder covers prepared ownership evidence, exact-cycle manual verification, Loan Officer recommendation, all four Approver actions, exact offer assertions, Customer acceptance/decline, protected contract preparation, acknowledgment, readiness, destination reveal, activation replay, final schedule reads, ownership concealment, partial repayment and history, Administrative Full-Balance Settlement, and closure.

Complex correction scenarios require prepared application, review-cycle, checklist, and version variables. The optional cancellation folder requires `returnedCancellationScenarioEnabled=true` and a separate Customer-owned `cancellationLoanApplicationId` in `RETURNED_FOR_REVISION`; it confirms the command, exact replay, and terminal application GET without exposing internal evidence IDs. Seed fixtures and scenario-specific IDs belong to the collection or its environment, not this API contract.

---
