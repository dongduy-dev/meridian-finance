# Meridian API Error Code Catalog

## Purpose and Authority

This document defines Meridian's API error identifiers, HTTP statuses, default messages, and caller-facing resolutions for `meridian-platform`.

Checked-in exception construction, global error mapping, controllers, and executable tests remain authoritative for runtime behavior. A catalog entry defines the intended error contract but does not by itself establish that an endpoint or workflow is available. Implementation status belongs in the project roadmap and follow-up register.

Reserved codes are listed separately. They do not become part of an executable API contract until the owning capability exposes and tests them.

---

## 1. Catalog Rules

- Each error code has one canonical HTTP status and default message.
- The resolution states the caller's next action; it must not expose sensitive evidence or internal implementation details.
- Context-specific details may narrow a resolution, but they must not change the code's meaning.
- Clients must not infer endpoint availability from this catalog alone.

---

## 2. Identity & Access

| HTTP Status | Error Code | Message | Resolution |
|---|---|---|---|
| 401 | `AUTHENTICATION_REQUIRED` | Authentication required | Provide a valid Bearer token in the Authorization header |
| 401 | `INVALID_CREDENTIALS` | Invalid credentials | Check the username and password |
| 401 | `TOKEN_EXPIRED` | Token expired | Log in again to obtain a new access token |
| 401 | `INVALID_TOKEN` | Invalid token | Log in again to obtain a valid access token |
| 401 | `INVALID_REFRESH_TOKEN` | Refresh authentication failed | Log in again to create a new refresh-token family |
| 401 | `EMAIL_VERIFICATION_REQUIRED` | Email verification required. | Confirm the Customer email, then log in again |
| 401 | `INVALID_EMAIL_VERIFICATION_TOKEN` | Email verification token is invalid or expired. | Request a replacement verification email and submit its token |
| 401 | `INVALID_PASSWORD_RESET_TOKEN` | Password reset token is invalid or expired. | Request a replacement password-reset email and submit its token with a new password |
| 409 | `EMAIL_ALREADY_REGISTERED` | An account with this email already exists. | Log in or use the account-recovery flow instead of registering again |
| 403 | `ACCOUNT_SUSPENDED` | Account suspended | Contact support to review the account status |
| 403 | `ACCESS_DENIED` | Access denied | Use a principal whose role includes the required permission |
| 403 | `CUSTOMER_CONTEXT_REQUIRED` | Customer context required | Use an authenticated customer-linked token for a customer-owned flow |
| 403 | `SALARY_ADVANCE_READINESS_ACCESS_DENIED` | Salary Advance readiness access denied | Use an authenticated Customer principal with `loan:submit` |
| 429 | `RATE_LIMIT_EXCEEDED` | Too many requests. | Retry after the number of seconds stated by the `Retry-After` response header |

---

## 3. Customer Management

| HTTP Status | Error Code | Message | Resolution |
|---|---|---|---|
| 404 | `CUSTOMER_NOT_FOUND` | Customer not found | Verify the requested Customer ID |
| 409 | `CUSTOMER_NOT_ACTIVE` | Customer not active | Restore the Customer to `ACTIVE` before using customer-owned lending flows |
| 409 | `IDENTITY_REFERENCE_IMMUTABLE` | Identity reference immutable | Do not change the identity reference after the profile first becomes complete |
| 409 | `IDENTITY_REFERENCE_ALREADY_IN_USE` | Identity reference already in use | Use an identity reference that does not belong to another Customer |
| 422 | `PROFILE_INCOMPLETE` | Customer profile incomplete | Complete the required identity, contact, residential, employment, and consent fields |
| 422 | `PRIMARY_BANK_ACCOUNT_REQUIRED` | Primary bank account required | Add or select a primary active bank account |
| 409 | `BANK_ACCOUNT_UPDATE_NOT_ALLOWED` | Bank account update not allowed | Refresh the application state; bank-account changes are blocked in this status |
| 404 | `BANK_ACCOUNT_NOT_FOUND` | Bank account not found | Verify that the bank account belongs to the authenticated Customer |
| 409 | `DUPLICATE_BANK_ACCOUNT` | Duplicate bank account | Use the existing active bank account |

---

## 4. Loan Product and Application

| HTTP Status | Error Code | Message | Resolution |
|---|---|---|---|
| 404 | `LOAN_APPLICATION_NOT_FOUND` | Loan application not found | Verify the requested LoanApplication ID |
| 403 | `LOAN_APPLICATION_CANCELLATION_ACCESS_DENIED` | Loan application cancellation access denied | Use the authenticated Customer owner with `loan:cancel:own` |
| 409 | `LOAN_APPLICATION_CANCELLATION_NOT_ALLOWED` | Loan application cancellation not allowed | Cancel only an active returned correction; otherwise refresh the application state |
| 404 | `PRODUCT_NOT_FOUND` | Loan product not found | Verify the requested product ID |
| 422 | `PRODUCT_INACTIVE` | Loan product inactive | Select an active loan product |
| 422 | `PRODUCT_POLICY_INVALID` | Product policy invalid | Correct the product policy configuration before accepting applications |
| 422 | `INVALID_PRODUCT_TERM` | Invalid product term | Select a term allowed by the product policy |
| 422 | `INVALID_PRODUCT_AMOUNT` | Invalid product amount | Use an amount within the product and policy limits |
| 422 | `INVALID_COLLATERAL_DETAILS` | Invalid Collateral details | Supply complete Collateral facts within technical limits and use a positive whole-VND estimated value |
| 409 | `BLOCKING_APPLICATION_EXISTS` | Blocking application exists | Wait for the existing application for this product to reach a terminal status |
| 409 | `OUTSTANDING_LOAN_ACCOUNT_EXISTS` | Outstanding loan account exists | Fully repay the product-matching `ACTIVE` or `OVERDUE` LoanAccount. A zero-outstanding `SETTLED` account clears this guard for products that define it |
| 409 | `INVALID_APPLICATION_STATUS` | Invalid application status | Refresh the application and retry only from an allowed status |
| 422 | `PRODUCT_VERIFICATION_PENDING` | Product verification pending | Complete or wait for product-specific verification |
| 422 | `PRODUCT_VERIFICATION_FAILED` | Product verification failed | No normal retry is available for a terminal failed verification; use only a separately approved product workflow if one exists |
| 422 | `PRODUCT_VERIFICATION_REQUIRES_MORE_INFORMATION` | Product verification requires more information | Complete the requested Customer or Staff correction |
| 409 | `PRODUCT_VERIFICATION_START_NOT_ALLOWED` | Product verification start not allowed | Start verification only from an eligible submitted application |
| 409 | `PRODUCT_VERIFICATION_COMPLETION_NOT_ALLOWED` | Product verification completion not allowed | Complete verification only while the application is verification-pending |
| 409 | `PRODUCT_VERIFICATION_NOT_PENDING` | Product verification not pending | Refresh the application; only a pending product-verification record can be decided |
| 422 | `UCL_VERIFICATION_NOT_APPLICABLE` | UCL verification not applicable | Use the command only for an Unsecured Consumer Loan application |
| 409 | `UCL_VERIFICATION_REQUIRED` | UCL verification record required | Repair the missing application-owned verification state before review progression |
| 409 | `UCL_VERIFICATION_DOCUMENTS_NOT_READY` | UCL verification documents not ready | Accept or validly waive all required evidence before starting or completing verification |
| 422 | `UCL_VERIFICATION_ASSESSMENT_REQUIRED` | UCL verification assessment required | Supply a nonblank restricted assessment note no longer than 2,000 characters |
| 422 | `COLLATERAL_VERIFICATION_NOT_APPLICABLE` | Collateral Loan verification not applicable | Use the command only for a `COLLATERAL_LOAN` / `SECURED` application |
| 409 | `COLLATERAL_VERIFICATION_REQUIRED` | Collateral Loan verification record required | Repair the missing application-owned verification state before review progression |
| 409 | `COLLATERAL_VERIFICATION_DOCUMENTS_NOT_READY` | Collateral Loan verification documents not ready | Accept or validly waive the required ownership evidence before starting or completing verification |
| 422 | `COLLATERAL_VERIFICATION_ASSESSMENT_REQUIRED` | Collateral Loan verification assessment required | Supply a nonblank restricted assessment note no longer than 2,000 characters |
| 409 | `STALE_COLLATERAL_VERIFICATION` | Expected Collateral Loan verification is stale | Refresh the application and complete only the authoritative latest numbered cycle |

---

## 5. Partner Management — Salary Advance Eligibility

| HTTP Status | Error Code | Message | Resolution |
|---|---|---|---|
| 404 | `PARTNER_COMPANY_NOT_FOUND` | Partner company not found | Verify the Partner Company ID |
| 422 | `PARTNER_COMPANY_INACTIVE` | Partner company inactive | Use an active Partner Company |
| 422 | `EMPLOYEE_INACTIVE` | Partner employee inactive | Use an active Partner Employee record |
| 422 | `EMPLOYEE_NOT_VERIFIED` | Customer employee status not verified | Complete Salary Advance employee verification before creating an application |
| 422 | `SALARY_ADVANCE_ELIGIBILITY_DATA_STALE` | Salary Advance eligibility data stale | Refresh the Partner Employee data before eligibility or limit use |

---

## 6. Loan Core — Salary Advance Limit and Exposure

| HTTP Status | Error Code | Message | Resolution |
|---|---|---|---|
| 422 | `SALARY_ADVANCE_LIMIT_UNAVAILABLE` | Salary Advance limit unavailable | Verify employee eligibility and refresh the limit |
| 422 | `INSUFFICIENT_AVAILABLE_LIMIT` | Insufficient available Salary Advance limit | Reduce the requested amount to the available limit |
| 409 | `SALARY_ADVANCE_RESERVATION_INVALID` | Salary Advance reservation invalid | Reconcile the reservation before correction resubmission, readiness confirmation, or disbursement |
| 409 | `SALARY_ADVANCE_RESERVATION_RELEASED` | Salary Advance reservation released | Do not continue readiness or activation after the reservation has been released |

---

## 7. Approval Workflow

| HTTP Status | Error Code | Message | Resolution |
|---|---|---|---|
| 409 | `MAKER_CHECKER_VIOLATION` | Maker-checker violation | Use a different authorized user for the final Approver decision |
| 422 | `REVIEW_RECOMMENDATION_REQUIRED` | Review recommendation required | Record the Loan Officer recommendation before the final decision |

---

## 8. Loan Review and Correction

| HTTP Status | Error Code | Message | Resolution |
|---|---|---|---|
| 409 | `REVIEW_CYCLE_REQUIRED` | Active review cycle required | Start or return to the active Loan Officer review cycle |
| 409 | `STALE_REVIEW_CYCLE` | Expected review cycle is stale | Refresh the application and submit against the active review cycle |
| 422 | `INVALID_CORRECTION_PLAN` | Invalid structured correction plan | Use one to ten supported document tasks with the required ownership fields |
| 422 | `CORRECTION_FIELD_NOT_ALLOWED` | Financial correction field not allowed | Requested amount and term cannot be changed through the correction workflow |
| 403 | `CORRECTION_ACCESS_DENIED` | Customer correction access denied | Use the authenticated owner of the LoanApplication |
| 403 | `STAFF_CORRECTION_ACCESS_DENIED` | Staff correction access denied | Use an authorized Staff actor assigned to the task |
| 403 | `STAFF_CORRECTION_MAKER_CHECKER_VIOLATION` | Staff correction maker-checker violation | Use a different authorized Staff actor to complete the task |
| 403 | `CORRECTION_RESUBMISSION_DENIED` | Correction resubmission denied | Customer-only corrections require the owner; Staff or mixed corrections require authorized Staff |
| 404 | `CORRECTION_REQUEST_NOT_FOUND` | Correction request not found | Refresh the LoanApplication correction state |
| 404 | `CORRECTION_TASK_NOT_FOUND` | Correction task not found | Refresh the correction task queue |
| 409 | `CORRECTION_REQUEST_CONFLICT` | Correction request conflict | Refresh the application; resubmission requires an active correction request |
| 409 | `CORRECTION_TASK_ALREADY_COMPLETED` | Correction task already completed | Treat an identical completion as replay; otherwise refresh the task |
| 422 | `INVALID_STAFF_CORRECTION_QUERY` | Invalid Staff correction query | Use a valid status, a non-negative page, and a page size from 1 to 50 |
| 409 | `REVIEW_CYCLE_CONFLICT` | Correction review-cycle conflict | Refresh the application and active review cycle |
| 409 | `CORRECTION_TASKS_INCOMPLETE` | Correction tasks incomplete | Complete every authorized task and its required evidence |
| 409 | `CORRECTION_ALREADY_RESUBMITTED` | Correction already resubmitted | Treat an identical resubmission as replay; otherwise refresh the application |
| 409 | `CORRECTION_DOCUMENTS_INCOMPLETE` | Correction uploads incomplete | Upload a current version for every required checklist item |
| 409 | `LOAN_REVIEW_DOCUMENTS_NOT_READY` | Documents not processing-ready | Complete manual acceptance or an authorized waiver before review |

---

## 9. Document Management

| HTTP Status | Error Code | Message | Resolution |
|---|---|---|---|
| 404 | `DOCUMENT_NOT_FOUND` | Document not found | Verify the document ID |
| 409 | `DOCUMENT_REPLACEMENT_REQUIRED` | Document replacement required | Replace the rejected or expired document |
| 503 | `DOCUMENT_STORAGE_UNAVAILABLE` | Document storage unavailable | Retry later or contact support if the failure persists |
| 403 | `DOCUMENT_ACCESS_DENIED` | Document access denied | Use the exact Customer owner or an authorized Staff actor |
| 403 | `DOCUMENT_UPLOAD_DENIED` | Document upload not authorized | Upload through the open correction task for this checklist item |
| 409 | `STALE_DOCUMENT_VERSION` | Document version is stale | Refresh the current immutable version before replacing or reviewing |
| 409 | `DOCUMENT_ALREADY_REVIEWED` | Document version already reviewed. | Refresh the current document evidence. Only an unreviewed current version may receive a new review decision. |
| 409 | `DOCUMENT_UPLOAD_REQUIRED` | Current upload required | Upload a current document version before manual review |
| 403 | `DOCUMENT_WAIVER_DENIED` | Waiver permission required | Use a Loan Officer with separate `document:waive` authority |
| 422 | `DOCUMENT_WAIVER_REASON_REQUIRED` | Controlled waiver reason required | Supply an approved waiver reason code |
| 409 | `CORRECTION_TASK_PROOF_MISSING` | Correction proof missing | Upload the required new version before completing the task |

---

## 10. Loan Core — Offer, Contract, Disbursement, and Servicing

| HTTP Status | Error Code | Message | Resolution |
|---|---|---|---|
| 409 | `OFFER_EXPIRED` | Offer expired | Request or generate a new offer under the product rules |
| 409 | `OFFER_ACTION_CONFLICT` | Offer action conflict | Refresh the offer and do not submit contradictory terminal actions |
| 404 | `APPROVED_OFFER_NOT_FOUND` | Approved offer not found | Verify that the application has an approved offer for the authenticated Customer |
| 404 | `CURRENT_CONTRACT_MISSING` | Current operational contract not found | Prepare the first operational contract for the accepted offer |
| 409 | `CONTRACT_VERSION_STALE` | Expected contract version is stale | Refresh the current contract and retry with its exact version |
| 409 | `CONTRACT_SUPERSESSION_REASON_NOT_ALLOWED` | Contract supersession reason not allowed | Omit the supersession reason when preparing version 1 |
| 409 | `CONTRACT_SUPERSESSION_REASON_REQUIRED` | Contract supersession reason required | Use the controlled reason `DISBURSEMENT_ACCOUNT_REFRESH` |
| 409 | `CONTRACT_REGENERATION_NOT_ALLOWED` | Contract regeneration is not allowed | Do not regenerate a ready contract or a superseded version |
| 409 | `CONTRACT_ACKNOWLEDGMENT_NOT_ALLOWED` | Contract acknowledgment is not allowed | Acknowledge only the current `PREPARED` contract version |
| 409 | `ACKNOWLEDGMENT_MISSING` | Current version has not been acknowledged | The Customer must acknowledge the exact current contract version |
| 409 | `DOCUMENTS_NOT_PROCESSING_READY` | Documents are not processing-ready | Complete required document review before contract preparation or readiness confirmation |
| 409 | `ACTIVE_CORRECTION_REQUEST` | Active correction blocks contract readiness | Complete and resubmit the active correction before confirming readiness |
| 409 | `CUSTOMER_INACTIVE` | Customer is inactive | Restore the Customer to an eligible active state |
| 409 | `CAPTURED_ACCOUNT_MISSING` | Captured destination no longer exists | Regenerate the contract from the primary active destination before readiness |
| 409 | `CAPTURED_ACCOUNT_INACTIVE` | Captured destination is inactive | Regenerate the contract with `DISBURSEMENT_ACCOUNT_REFRESH` before readiness |
| 409 | `READINESS_ALREADY_CONFIRMED` | Contract readiness was already confirmed | Treat an identical request ID as replay; otherwise refresh the contract state |
| 422 | `OFFER_NOT_ACCEPTED` | Offer not accepted | The Customer must accept the offer before contract preparation or disbursement |
| 409 | `UCL_VERIFICATION_INVALID` | UCL verification is invalid for contract execution | Complete the authoritative latest application-owned UCL verification cycle positively before contract preparation or readiness confirmation |
| 409 | `COLLATERAL_VERIFICATION_INVALID` | Collateral Loan verification is invalid for contract execution | Complete the authoritative latest application-owned Collateral verification cycle positively before contract preparation or readiness confirmation |
| 409 | `DISBURSEMENT_ALREADY_COMPLETED` | Disbursement already completed | Refresh the application; disbursement can be confirmed only once |
| 409 | `DUPLICATE_TRANSFER_REFERENCE` | Transfer evidence already recorded | Reconcile the existing transfer without exposing the conflicting reference |
| 422 | `DISBURSEMENT_VALUE_DATE_INVALID` | Disbursement value date is invalid | Use a date from final readiness through the current UTC business date |
| 422 | `FIRST_REPAYMENT_DATE_INVALID` | First repayment date is invalid | Use a date after the value date and no later than one calendar month after it |
| 422 | `PRODUCT_ACTIVATION_NOT_SUPPORTED` | Loan product activation is not supported | The selected product has no supported activation policy |
| 403 | `DISBURSEMENT_DESTINATION_ACCESS_DENIED` | Destination access denied | Use an authenticated Staff principal with `loan:disburse` |
| 409 | `DISBURSEMENT_DESTINATION_REVEAL_NOT_ALLOWED` | Destination reveal is not allowed | Reveal only the current ready contract while the application is `DISBURSEMENT_PENDING` |
| 409 | `DISBURSEMENT_DESTINATION_UNAVAILABLE` | Protected destination is unavailable | Refresh the contract state or escalate for secure evidence reconciliation |
| 404 | `LOAN_ACCOUNT_NOT_FOUND` | Loan Account not found | Staff: verify that activation completed. Customer responses also conceal missing, foreign-owned, and not-yet-activated accounts |
| 403 | `LOAN_APPLICATION_ACCESS_DENIED` | Loan Application access denied | Use `loan:read:own` for an owned application or `loan:read` for authorized Staff access |
| 422 | `REPAYMENT_VALUE_DATE_INVALID` | Repayment value date is invalid | Use a date from the disbursement value date through the current UTC business date |
| 422 | `REPAYMENT_EXCEEDS_OUTSTANDING` | Repayment exceeds contractual outstanding | Submit an amount no greater than the current contractual outstanding balance |
| 422 | `PRODUCT_REPAYMENT_NOT_SUPPORTED` | Loan product repayment is not supported | The selected product has no supported repayment policy |
| 409 | `DUPLICATE_PAYMENT_REFERENCE` | Payment evidence already recorded | Reconcile the existing payment without exposing the conflicting reference |
| 422 | `REPAYMENT_AMOUNT_INVALID` | Repayment amount is invalid | Submit a positive whole-VND repayment amount |
| 409 | `REPAYMENT_NOT_ALLOWED` | Repayment is not allowed | Use an `ACTIVE` or `OVERDUE` account with positive contractual outstanding debt |
| 422 | `SETTLEMENT_AMOUNT_INVALID` | Settlement amount is invalid | Submit a positive whole-VND amount equal to the locked current contractual outstanding |
| 422 | `SETTLEMENT_VALUE_DATE_INVALID` | Settlement value date is invalid | Use a date from the disbursement value date through the current UTC business date |
| 409 | `SETTLEMENT_NOT_ALLOWED` | Settlement is not allowed | Use an `ACTIVE` or `OVERDUE` LoanAccount for an executable servicing product with positive contractual outstanding |
| 409 | `LOAN_ACCOUNT_CLOSURE_NOT_ALLOWED` | Loan Account closure is not allowed | Close only a financially reconciled `SETTLED` LoanAccount |

---

## 11. Audit & Compliance Controls

Executable source does not define an Audit-specific caller-facing error code. Planned Audit vocabulary remains in Section 14 rather than implying an active Audit API contract.

---

## 12. Validation and Idempotency

| HTTP Status | Error Code | Message | Resolution |
|---|---|---|---|
| 400 | `VALIDATION_FAILED` | Input validation failed | Correct the field errors in the response payload |
| 409 | `IDEMPOTENCY_KEY_REUSED` | Request ID reused for different content | Replay the original logical content or use a new request ID |

---

## 13. System and Consistency

| HTTP Status | Error Code | Message | Resolution |
|---|---|---|---|
| 409 | `SYSTEM_STATE_CONFLICT` | System state conflict | Refresh the resource. Escalate if workflow, account, schedule, reservation, or exposure evidence remains inconsistent |

---

## 14. Reserved Error Codes

These codes describe planned contract vocabulary. They are not active workflow errors until the owning capability is delivered and verified.

### Customer Validation and Identity Uniqueness

| HTTP Status | Error Code | Message | Intended caller resolution |
|---|---|---|---|
| 409 | `DUPLICATE_NATIONAL_ID` | Duplicate national ID | Use the existing Customer record or correct the National ID |
| 409 | `DUPLICATE_PHONE_NUMBER` | Duplicate phone number | Use the existing Customer record or correct the phone number |
| 422 | `CUSTOMER_VERIFICATION_REQUIRED` | Customer verification required | Complete the configured Customer verification step |
| 400 | `NATIONAL_ID_FORMAT_INVALID` | National ID format invalid | Use exactly 9 digits for CMND or 12 digits for CCCD |
| 400 | `PHONE_NUMBER_FORMAT_INVALID` | Phone number format invalid | Use a valid Vietnamese phone number |

### Loan Application State

| HTTP Status | Error Code | Message | Intended caller resolution |
|---|---|---|---|
| 422 | `INVALID_STATUS_TRANSITION` | Invalid status transition | Use a transition allowed by the LoanApplication lifecycle |
| 409 | `APPLICATION_ALREADY_TERMINAL` | Loan application already terminal | No further normal workflow action is allowed |

### Partner Import and Manual Matching

| HTTP Status | Error Code | Message | Intended caller resolution |
|---|---|---|---|
| 404 | `EMPLOYEE_NOT_FOUND` | Partner employee not found | Verify the employee details or use the authorized manual-review path |
| 409 | `EMPLOYEE_DUPLICATE_UNRESOLVED` | Duplicate partner employee unresolved | Resolve the duplicate employee records before verification |
| 422 | `EMPLOYEE_VERIFICATION_REQUIRES_REVIEW` | Employee verification requires review | Send the case through authorized manual review with supporting evidence |
| 404 | `PARTNER_EMPLOYEE_IMPORT_BATCH_NOT_FOUND` | Partner employee import batch not found | Verify the import batch ID |
| 422 | `PARTNER_EMPLOYEE_IMPORT_INVALID` | Partner employee import invalid | Correct the invalid rows and import the batch again |

### Salary Advance Limit States

| HTTP Status | Error Code | Message | Intended caller resolution |
|---|---|---|---|
| 409 | `SALARY_ADVANCE_LIMIT_SUSPENDED` | Salary Advance limit suspended | Resolve the stale data, manual review, or operational hold |
| 409 | `SALARY_ADVANCE_LIMIT_DISABLED` | Salary Advance limit disabled | The Customer is not eligible for a normal Salary Advance application |

### Approval Workflow

| HTTP Status | Error Code | Message | Intended caller resolution |
|---|---|---|---|
| 404 | `APPROVAL_REQUEST_NOT_FOUND` | Approval request not found | Verify the approval ID |
| 422 | `APPROVAL_REQUIRED` | Approval required | Record the Approver decision before the next workflow step |
| 409 | `DECISION_ALREADY_SUBMITTED` | Decision already submitted | Refresh the approval record; the decision is immutable once recorded |

### Document Upload and Review

| HTTP Status | Error Code | Message | Intended caller resolution |
|---|---|---|---|
| 415 | `FILE_TYPE_NOT_ALLOWED` | File type not allowed | Upload a supported format such as PDF, JPG, or PNG |
| 413 | `FILE_TOO_LARGE` | File too large | Upload a file within the configured size limit |
| 422 | `DOCUMENT_REQUIRED` | Required document missing | Upload the required document or use an authorized `NOT_REQUIRED` or `WAIVED` outcome |
| 422 | `DOCUMENT_NOT_READY` | Document checklist not ready | Complete the required uploads and manual review |
| 422 | `DOCUMENT_REJECTED` | Document rejected | Upload a corrected replacement document |
| 422 | `DOCUMENT_EXPIRED` | Document expired | Upload a valid replacement document |
| 409 | `DOCUMENT_ALREADY_ACCEPTED` | Document already accepted | Use an authorized correction flow before replacing an accepted document |

### Contract, Disbursement, and Servicing

| HTTP Status | Error Code | Message | Intended caller resolution |
|---|---|---|---|
| 422 | `CONTRACT_DOCUMENTS_NOT_READY` | Contract documents not ready | Complete the required contract or disbursement documents |
| 422 | `DISBURSEMENT_NOT_READY` | Disbursement not ready | Complete approval, Customer acceptance, document readiness, and destination requirements |
| 409 | `LOAN_ACCOUNT_NOT_ACTIVE` | Loan account not active | Activate the LoanAccount through disbursement confirmation before repayment |
| 422 | `REPAYMENT_RECORD_INVALID` | Repayment record invalid | Correct the amount, value date, status, or outstanding-balance data |
| 404 | `REPAYMENT_SCHEDULE_NOT_FOUND` | Repayment schedule not found | Complete LoanAccount activation so the final repayment schedule exists |

### Audit and Platform Controls

| HTTP Status | Error Code | Message | Intended caller resolution |
|---|---|---|---|
| 409 | `AUDIT_RECORD_IMMUTABLE` | Audit record immutable | Do not modify an audit event or status-history record |
| 503 | `AUDIT_TRAIL_UNAVAILABLE` | Audit trail unavailable | Retry or escalate; the business action requires a durable audit record |
| 500 | `INTERNAL_SERVER_ERROR` | Internal server error | Retry later or contact support if the error persists |
| 503 | `SERVICE_TEMPORARILY_UNAVAILABLE` | Service temporarily unavailable | Retry later |

### Generic Idempotency

| HTTP Status | Error Code | Message | Intended caller resolution |
|---|---|---|---|
| 422 | `IDEMPOTENCY_PAYLOAD_MISMATCH` | Idempotency payload mismatch | Replay the original request body or use a new idempotency key |
| 409 | `IDEMPOTENCY_PREVIOUS_SERVER_ERROR` | Previous idempotent request failed | Verify the prior operation before retrying with the same key |

### Identity and Session Management

| HTTP Status | Error Code | Message | Reserved use |
|---|---|---|---|
| 401 | `REFRESH_TOKEN_INVALID` | Refresh token invalid | Reserved for refresh-token validation |
| 409 | `CONCURRENT_SESSION_LIMIT_EXCEEDED` | Concurrent session limit exceeded | Reserved for concurrent-session enforcement |

### OCR-Assisted Document Processing

| HTTP Status | Error Code | Message | Reserved use |
|---|---|---|---|
| 404 | `OCR_JOB_NOT_FOUND` | OCR job not found | Reserved for OCR job lookup |
| 409 | `OCR_JOB_PENDING` | OCR job pending | Reserved for incomplete OCR processing |
| 500 | `OCR_JOB_FAILED` | OCR job failed | Reserved for failed OCR processing |
| 404 | `OCR_RESULT_NOT_AVAILABLE` | OCR result not available | Reserved for unavailable OCR output |
| 422 | `OCR_LOW_CONFIDENCE_REQUIRES_REVIEW` | Low confidence OCR result requires review | Reserved for low-confidence OCR results that require manual review |
| 503 | `OCR_SERVICE_UNAVAILABLE` | OCR service unavailable | Reserved for OCR provider unavailability |

### Future Customer and Approval Workflows

| HTTP Status | Error Code | Message | Reserved use |
|---|---|---|---|
| 409 | `CUSTOMER_ALREADY_ANONYMIZED` | Customer already anonymized | Reserved for a future Customer anonymization workflow |
| 403 | `APPROVER_DELEGATION_LIMIT_EXCEEDED` | Approver delegation limit exceeded | Reserved for future approval delegation limits |
| 422 | `APPROVAL_SLA_EXCEEDED` | Approval SLA exceeded | Reserved for a future approval escalation workflow |
| 404 | `APPROVAL_DELEGATION_NOT_FOUND` | Approval delegation not found | Reserved for future delegated-approval configuration |
