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
| POST | `/api/v1/partner-companies/{partnerCompanyId}/employee-verifications` | Bearer + `partner:employee:verify:own` | `PartnerEmployeeVerificationController` | Verify the authenticated customer's Partner Employee evidence and create/reuse a verified link. |
| POST | `/api/v1/loan-applications/salary-advance` | Bearer + `loan:submit` | `SalaryAdvanceLoanApplicationController` | Create a submitted Salary Advance application for the authenticated customer and reserve limit. |
| POST | `/api/v1/loan-applications/{loanApplicationId}/review/start` | Bearer + `loan:review` | `LoanApplicationReviewController` | Start Loan Officer review and transition a submitted application to `UNDER_REVIEW`. |
| POST | `/api/v1/loan-applications/{loanApplicationId}/review-recommendations` | Bearer + `approval:recommend` | `ReviewRecommendationController` | Record the authenticated Loan Officer recommendation and trigger Loan-owned status transition. |
| POST | `/api/v1/loan-applications/{loanApplicationId}/approval-decisions` | Bearer + `approval:decide` | `ApprovalDecisionController` | Record the authenticated Approver decision and trigger Loan-owned final/return status transition. |

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

### Employee Verification

`customerId` is derived from the authenticated customer token and is no longer accepted in the request body.

```json
{
  "identityReference": "IDREF-MER-001",
  "employeeCode": "MER-EMP-001"
}
```

Safe response fields: `customerId`, `partnerCompanyId`, `partnerEmployeeId`, `customerPartnerEmployeeLinkId`, `outcome`, `linkStatus`, `manualReviewRequired`.

The response intentionally does not expose salary, salary advance limit, identity reference, employee code, or raw matching evidence.

### Salary Advance Application

`customerId` is derived from the authenticated customer token and is no longer accepted in the request body.

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

Valid actions are `RECOMMEND_APPROVAL`, `RECOMMEND_REJECTION`, `RETURN_TO_CUSTOMER_REVISION`, and `REQUEST_STAFF_CORRECTION`. A nonblank `reason` is required for all actions except `RECOMMEND_APPROVAL`.

### Approval Decision

`approverUserId` is derived from the authenticated Approver token and is not accepted in the request body. The Approver must be different from the Loan Officer who submitted the latest recommendation.

```json
{
  "action": "APPROVE",
  "reason": "Optional for approval; required for reject/return/correction decisions.",
  "internalNotes": "Optional staff-only note."
}
```

Valid actions are `APPROVE`, `REJECT`, `RETURN_TO_LOAN_OFFICER_REVIEW`, and `REQUEST_CUSTOMER_OR_STAFF_CORRECTION`. A nonblank `reason` is required for all actions except `APPROVE`. `REJECT` transitions the Loan Application to `REJECTED` and releases the reserved Salary Advance limit.

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

Collection note: the Postman collection now uses `POST /api/v1/auth/login`, role-specific Bearer token variables, and the full current endpoint inventory including Loan Officer review, recommendation, and approval decision endpoints.

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
| Salary Advance happy path | `201`, `SUBMITTED`, limit reserved. |
| Start Loan Officer review | `200`, `UNDER_REVIEW`. |
| Recommendation without `approval:recommend` | `403`, `ACCESS_DENIED`. |
| Recommendation missing required reason | `422`, `RECOMMENDATION_REASON_REQUIRED`. |
| Recommendation happy path | `201`, recommendation recorded, Loan status moves to `APPROVAL_PENDING` or `RETURNED_FOR_REVISION`. |
| Approval decision without `approval:decide` | `403`, `ACCESS_DENIED`. |
| Approval decision maker-checker violation | `422`, `MAKER_CHECKER_VIOLATION`. |
| Approval decision reject path | `201`, decision recorded, Loan status moves to `REJECTED`, Salary Advance reservation is released. |
| Duplicate Salary Advance for same authenticated customer | `409`, `BLOCKING_APPLICATION_EXISTS`. |

Notes:

- Customer-owned endpoints now derive customer identity from the authenticated token.
- Refresh tokens, logout invalidation, and broader customer ownership hardening remain deferred IAM follow-ups.
