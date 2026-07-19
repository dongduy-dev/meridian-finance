# MER-API-001 - Endpoint Inventory and Postman Scenario

## Current Endpoint Inventory

Current security posture comes from `SecurityConfig`: health, login, and loan product catalog endpoints are public; all other implemented business endpoints require JWT Bearer authentication plus role/permission checks. HTTP Basic is no longer the intended development gate.

| Method | Path | Auth | Controller | Purpose |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/health` | Public | `HealthController` | Versioned health check. |
| POST | `/api/v1/auth/login` | Public | `AuthController` | Authenticate a seeded demo user and return a Bearer access token. |
| GET | `/api/v1/loan-products` | Public | `LoanProductController` | List active loan products. |
| GET | `/api/v1/partner-companies` | Bearer + `partner:read` | `PartnerCompanyController` | List Partner Companies. |
| GET | `/api/v1/partner-companies/{partnerCompanyId}` | Bearer + `partner:read` | `PartnerCompanyController` | Get one Partner Company. |
| GET | `/api/v1/partner-companies/{partnerCompanyId}/employees?activeOnly=false` | Bearer + `partner:read` | `PartnerEmployeeController` | List Partner Employees for a company. `activeOnly` is optional and defaults to `false`. |
| GET | `/api/v1/partner-companies/{partnerCompanyId}/employee-import-batches` | Bearer + `partner:read` | `PartnerEmployeeImportBatchController` | List Partner Employee import batches. |
| GET | `/api/v1/customers/me` | Bearer + `customer:profile:read:own` | `CustomerProfileController` | Read the authenticated Customer profile readiness snapshot without exposing identity evidence. |
| PUT | `/api/v1/customers/me/profile` | Bearer + `customer:profile:write:own` | `CustomerProfileController` | Create or update the authenticated Customer profile. |
| GET | `/api/v1/customers/me/bank-accounts` | Bearer + `customer:bank-account:read:own` | `CustomerBankAccountController` | List masked bank accounts owned by the authenticated Customer. |
| POST | `/api/v1/customers/me/bank-accounts` | Bearer + `customer:bank-account:write:own` | `CustomerBankAccountController` | Add an encrypted bank account; the first active account becomes primary. |
| POST | `/api/v1/customers/me/bank-accounts/{customerBankAccountId}/make-primary` | Bearer + `customer:bank-account:write:own` | `CustomerBankAccountController` | Make one active bank account primary. |
| POST | `/api/v1/customers/me/bank-accounts/{customerBankAccountId}/deactivate` | Bearer + `customer:bank-account:write:own` | `CustomerBankAccountController` | Deactivate a bank account; a primary account cannot be deactivated while another active account exists. |
| POST | `/api/v1/partner-companies/{partnerCompanyId}/employee-verifications` | Bearer + `partner:employee:verify:own` | `PartnerEmployeeVerificationController` | Verify the authenticated customer's Partner Employee evidence and create/reuse a verified link. |
| POST | `/api/v1/loan-applications/salary-advance` | Bearer + `loan:submit` | `SalaryAdvanceLoanApplicationController` | Create a submitted Salary Advance application for the authenticated customer and reserve limit. |
| POST | `/api/v1/loan-applications/{loanApplicationId}/review/start` | Bearer + `loan:review` | `LoanApplicationReviewController` | Start Loan Officer review and transition a submitted application to `UNDER_REVIEW`. |
| POST | `/api/v1/loan-applications/{loanApplicationId}/review-recommendations` | Bearer + `approval:recommend` | `ReviewRecommendationController` | Record the authenticated Loan Officer recommendation and trigger Loan-owned status transition. |
| POST | `/api/v1/loan-applications/{loanApplicationId}/approval-decisions` | Bearer + `approval:decide` | `ApprovalDecisionController` | Record the authenticated Approver decision and trigger Loan-owned final/return status transition. |
| GET | `/api/v1/loan-applications/{loanApplicationId}/approved-offer` | Bearer + `loan:read:own` | `ApprovedOfferController` | View the authenticated customer's approved offer without mutating expiry, status, or financial movements. |
| POST | `/api/v1/loan-applications/{loanApplicationId}/approved-offer/accept` | Bearer + `loan:offer:respond:own` | `ApprovedOfferController` | Accept the authenticated customer's pending approved offer and move the application to `CONTRACT_PENDING`; any expired offer returns `409 OFFER_EXPIRED`, with effects only when the action first discovers expiry. |
| POST | `/api/v1/loan-applications/{loanApplicationId}/approved-offer/decline` | Bearer + `loan:offer:respond:own` | `ApprovedOfferController` | Decline the authenticated customer's pending approved offer and release reservation exactly once; any expired offer returns `409 OFFER_EXPIRED`, with no duplicate effect for persisted expiry. |

## Authentication

### Login

```json
{
  "email": "customer.demo@meridian.local",
  "password": "<local-demo-password>"
}
```

Use the returned `accessToken` as:

```text
Authorization: Bearer <accessToken>
```

Seeded demo user emails:

| Role | Email |
| --- | --- |
| Customer | `customer.demo@meridian.local` |
| Loan Officer | `loan.officer@meridian.local` |
| Approver | `approver@meridian.local` |
| Accounting Officer | `accounting.officer@meridian.local` |
| Back-Office Admin | `backoffice.admin@meridian.local` |

## Request Payloads
### Customer Profile

`customerId` is derived from the authenticated customer token. `identityReference` is accepted only on profile update, stored encrypted, and is not returned by Customer profile responses. Duplicate normalized identity references owned by another Customer return `409 IDENTITY_REFERENCE_ALREADY_IN_USE` without returning the sensitive value.

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

### Customer Bank Accounts

Bank-account numbers are accepted only on add, normalized by removing spaces and hyphens, must contain at least 6 normalized characters, encrypted at rest, and returned only as masked values plus last four digits.

```json
{
  "bankCode": "VCB",
  "bankNameSnapshot": "Vietcombank",
  "accountHolderName": "Customer Demo",
  "accountNumber": "1234567890"
}
```
### Employee Verification

`customerId` is derived from the authenticated customer token and is no longer accepted in the request body. `identityReference` is also not accepted; Partner verification uses identity evidence from the authenticated Customer profile.

```json
{
  "employeeCode": "MER-EMP-001"
}
```

Safe response fields: `customerId`, `partnerCompanyId`, `partnerEmployeeId`, `customerPartnerEmployeeLinkId`, `outcome`, `linkStatus`, `manualReviewRequired`.

The response intentionally does not expose salary, salary advance limit, identity reference, employee code, or raw matching evidence.

### Salary Advance Application

`customerId` is derived from the authenticated customer token and is no longer accepted in the request body. Salary Advance submission requires an active Customer, complete Customer profile, and one primary active bank account.

```json
{
  "customerPartnerEmployeeLinkId": "<capture from employee verification response>",
  "requestedAmount": 3000000.00,
  "requestedTermMonths": 1
}
```
### Start Loan Officer Review

The reviewer actor is derived from the Bearer token. No request body is required.

```text
POST /api/v1/loan-applications/{loanApplicationId}/review/start
```

### Review Recommendation

`loanOfficerUserId` is derived from the authenticated Loan Officer token and is not accepted in the request body.

```json
{
  "action": "RECOMMEND_APPROVAL",
  "reason": "Application and verification snapshot reviewed.",
  "internalNotes": "Optional staff-only note."
}
```

Current executable actions are `RECOMMEND_APPROVAL`, `RECOMMEND_REJECTION`, and `RETURN_TO_CUSTOMER_REVISION`; rejection requires a nonblank `reason`.
`REQUEST_STAFF_CORRECTION` remains gated with HTTP `409 REVISION_WORKFLOW_NOT_AVAILABLE`
until the staff queue and resubmitter continuation are available.

Customer revision uses controlled fields instead of free-text `reason`:

```json
{
  "action": "RETURN_TO_CUSTOMER_REVISION",
  "reason": null,
  "internalNotes": "Optional restricted Approval note.",
  "expectedReviewCycleId": "{{reviewCycleId}}",
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


### Approval Decision

`approverUserId` is derived from the authenticated Approver token and is not accepted in the request body. The Approver must be different from the Loan Officer who submitted the latest recommendation.

```json
{
  "action": "APPROVE",
  "reason": "Optional for approval; required for executable reject/return decisions.",
  "internalNotes": "Optional staff-only note."
}
```

Current executable actions are `APPROVE`, `REJECT`, and `RETURN_TO_LOAN_OFFICER_REVIEW`; a nonblank `reason` is required except for `APPROVE`. For Salary Advance, `APPROVE` atomically generates the customer approved offer and moves the Loan Application to `CUSTOMER_ACCEPTANCE_PENDING`; `REJECT` transitions the Loan Application to `REJECTED` and releases the reserved Salary Advance limit.
`REQUEST_CUSTOMER_OR_STAFF_CORRECTION` remains a target-state enum value but is temporarily rejected with HTTP `409 REVISION_WORKFLOW_NOT_AVAILABLE` before any decision, audit, event, history, or Loan status effect. `RETURN_TO_LOAN_OFFICER_REVIEW` remains operational. See `MER-FU-031`.

### Customer Correction and Document Replacement

Customer ownership is derived from the authenticated token. These endpoints require
`loan:correction:own` or `document:upload:own` as shown:

```text
GET  /api/v1/loan-applications/{loanApplicationId}/corrections/tasks
POST /api/v1/loan-applications/{loanApplicationId}/corrections/tasks/{taskId}/complete
POST /api/v1/loan-applications/{loanApplicationId}/corrections/resubmit
POST /api/v1/loan-applications/{loanApplicationId}/documents/{checklistItemId}/versions
```

Task completion and resubmission bodies carry `completionRequestId` and
`resubmissionRequestId` respectively. Document upload is multipart with
`uploadRequestId`, optional `expectedCurrentVersionId`, and `file`. Only PDF, JPEG,
and PNG are accepted, with a 10 MiB limit and signature-to-declared-MIME matching.

Customer responses expose safe IDs, status, `RECENT_PAYSLIP`, controlled reason code,
customer instruction, and timestamps. They never expose storage keys, hashes, paths,
restricted notes, salary, employee codes, identity evidence, or bank information.

Resubmission requires every customer task to be completed and revalidates Customer
readiness, product policy, whole-VND amount, allowed term, blocking applications,
Partner eligibility, the verified employee link, current effective limit, the
existing unchanged reservation, and Document readiness. It inserts a new immutable
Salary Advance verification snapshot and creates no limit movement. The target is
`SUBMITTED` while document review remains pending, or `UNDER_REVIEW` with a new
active review cycle when prior review exists and the checklist is processing-ready.

### Approved Offer

`customerId` is derived from the authenticated customer token and is not accepted in the path or request body. Offer response actions do not require a request body.

```text
GET /api/v1/loan-applications/{loanApplicationId}/approved-offer
POST /api/v1/loan-applications/{loanApplicationId}/approved-offer/accept
POST /api/v1/loan-applications/{loanApplicationId}/approved-offer/decline
```

Safe response fields include approved principal, approved term, interest calculation method, flat monthly interest rate, total interest, fee amount, total repayment amount, repayment method, generated and expiry timestamps, effective customer-facing status, available actions, and provisional repayment items. Provisional items expose installment number, principal due, interest due, fee due, total due, and the controlled `ON_SALARY_DATE` repayment timing code. Exact calendar due dates are not part of the approved offer.

GET is read-only. If a persisted pending offer is already expired, GET returns `status = EXPIRED` and `availableActions = []` without changing the application, offer, reservation, or financial movements.

## Seed Data Useful For API Verification

| Purpose | Value |
| --- | --- |
| Active Partner Company | `11111111-1111-1111-1111-111111111111` |
| Active employee code | `MER-EMP-001` |
| Active identity reference | `IDREF-MER-001` |
| Inactive employee code | `MER-EMP-003` |
| Inactive identity reference | `IDREF-MER-003` |
| Suggested Salary Advance amount | `3000000.00` |
| Suggested term | `1` |

## Postman Collection

Import this file into Postman:

`docs/api/Meridian-Platform.postman_collection.json`

Collection note: the Postman collection now uses `POST /api/v1/auth/login`, role-specific Bearer token variables, and the full current endpoint inventory including Loan Officer review, recommendation, approval decision, and customer approved-offer endpoints.

Expected high-value checks:

| Scenario | Expected result |
| --- | --- |
| Public health | `200`, `status = UP`. |
| Public loan products | `200`, includes `SALARY_ADVANCE`. |
| Login with seeded demo user | `200`, `tokenType = Bearer`, returns `accessToken`. |
| Protected endpoint without token | `401`, `AUTHENTICATION_REQUIRED`. |
| Authenticated user without permission | `403`, `ACCESS_DENIED`. |
| Back-Office Admin reads Partner Employee list | `200`, detailed internal DTO visible only behind `partner:read`. |
| Customer active employee verification | `200`, `MATCHED_ACTIVE`, captures `customerPartnerEmployeeLinkId`, no PII fields in response. |
| Customer inactive employee verification | `200`, `MATCHED_INACTIVE`, no link created. |
| Customer missing employee evidence | `200`, `PENDING_MANUAL_REVIEW`. |
| Salary Advance with missing link | `422`, `EMPLOYEE_NOT_VERIFIED`. |
| Salary Advance below minimum amount | `422`, `INVALID_PRODUCT_AMOUNT`. |
| Salary Advance fractional VND amount | `422`, `INVALID_PRODUCT_AMOUNT`; mathematically whole values such as `3000000`, `3000000.0`, and `3000000.00` remain valid. |
| Salary Advance happy path | `201`, `SUBMITTED`, limit reserved. |
| Start Loan Officer review | `200`, `UNDER_REVIEW`. |
| Recommendation without `approval:recommend` | `403`, `ACCESS_DENIED`. |
| Recommendation missing required reason | `422`, `RECOMMENDATION_REASON_REQUIRED`. |
| Recommendation happy path | `201`, recommendation recorded, Loan status moves to `APPROVAL_PENDING`. |
| Customer revision recommendation | `201`, controlled plan persisted, Loan status moves to `RETURNED_FOR_REVISION`. |
| Gated staff correction recommendation | `409`, `REVISION_WORKFLOW_NOT_AVAILABLE`, with no durable effect. |
| Approval decision without `approval:decide` | `403`, `ACCESS_DENIED`. |
| Approval decision maker-checker violation | `422`, `MAKER_CHECKER_VIOLATION`. |
| Approval decision reject path | `201`, decision recorded, Loan status moves to `REJECTED`, Salary Advance reservation is released. |
| Approval decision approve path | `201`, Salary Advance decision recorded, approved offer generated, Loan status moves to `CUSTOMER_ACCEPTANCE_PENDING`. |
| Gated approval correction action | `409`, `REVISION_WORKFLOW_NOT_AVAILABLE`, with no decision, audit, event, history, or Loan status effect. |
| Customer approved-offer view | `200`, customer-facing offer includes immutable terms, provisional repayment items, and available actions while pending. |
| Customer approved-offer accept | `200`, offer status `ACCEPTED`, application moves to `CONTRACT_PENDING`. |
| Customer approved-offer decline | `200`, offer status `DECLINED`, application moves to `CUSTOMER_DECLINED`, reservation released exactly once. |
| Expired offer accept/decline | `409`, `OFFER_EXPIRED`; discovery of pending expiry commits expiry and exact-once release before the response, while an already persisted expiry produces no additional writes or release. |
| Duplicate Salary Advance for same authenticated customer, including concurrent submissions through different verified employee links | `409`, `BLOCKING_APPLICATION_EXISTS`; one complete winner remains. |

Notes:

- Customer-owned endpoints now derive customer identity from the authenticated token.
- Refresh tokens, logout invalidation, and broader customer ownership hardening remain deferred IAM follow-ups.
- The optional Postman persisted-expiry check skips unless `persistedExpiredLoanApplicationId` is set to a customer-owned application already expired by scheduled processing.
