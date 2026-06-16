# API Error Code Catalog

This document standardizes the API error codes across all bounded contexts in the Meridian Lending Platform.

| HTTP Status | Error Code | Message | Resolution |
|---|---|---|---|
| **AUTH Domain** | | | |
| 401 | `AUTHENTICATION_REQUIRED` | Authentication required | Provide a valid Bearer token in the Authorization header |
| 401 | `INVALID_CREDENTIALS` | Invalid credentials | Check username and password |
| 401 | `TOKEN_EXPIRED` | Token expired | Refresh the access token using the refresh token flow |
| 401 | `INVALID_TOKEN` | Invalid token | Re-authenticate to obtain a valid token |
| 401 | `REFRESH_TOKEN_INVALID` | Refresh token invalid | Re-authenticate to obtain a new token pair |
| 403 | `ACCOUNT_SUSPENDED` | Account suspended | Contact support to review account status |
| 403 | `ACCESS_DENIED` | Access denied | The user's role does not include the required permission for this action |
| 409 | `CONCURRENT_SESSION_LIMIT_EXCEEDED` | Concurrent session limit exceeded | Log out of other devices before logging in |
| **CUSTOMER Domain** | | | |
| 404 | `CUSTOMER_NOT_FOUND` | Customer not found | Verify the requested customer ID |
| 409 | `DUPLICATE_NATIONAL_ID` | Duplicate national ID | A customer with this National ID already exists |
| 409 | `DUPLICATE_PHONE_NUMBER` | Duplicate phone number | A customer with this phone number already exists |
| 422 | `PROFILE_INCOMPLETE` | Customer profile incomplete | Complete required identity, contact, employment, bank account, and consent information |
| 422 | `CUSTOMER_VERIFICATION_REQUIRED` | Customer verification required | Complete the configured customer verification step before continuing |
| 409 | `BANK_ACCOUNT_UPDATE_NOT_ALLOWED` | Bank account update not allowed | Bank account information cannot be changed in the current application status |
| **LOAN CORE / PRODUCT Domain** | | | |
| 404 | `LOAN_APPLICATION_NOT_FOUND` | Loan application not found | Verify the requested loan application ID |
| 404 | `PRODUCT_NOT_FOUND` | Loan product not found | Verify the requested loan product ID |
| 422 | `PRODUCT_INACTIVE` | Loan product inactive | Select an active loan product for application |
| 422 | `PRODUCT_POLICY_INVALID` | Product policy invalid | Review product policy configuration before accepting applications |
| 422 | `INVALID_PRODUCT_TERM` | Invalid product term | Select a term allowed by the loan product policy |
| 422 | `INVALID_PRODUCT_AMOUNT` | Invalid product amount | Ensure requested amount is within product limits and policy rules |
| 409 | `BLOCKING_APPLICATION_EXISTS` | Blocking application exists | Wait for the existing active application for this product to reach a terminal status |
| 409 | `INVALID_APPLICATION_STATUS` | Invalid application status | Refresh application status and retry the operation |
| 422 | `INVALID_STATUS_TRANSITION` | Invalid status transition | Follow the configured loan application lifecycle transition rules |
| 409 | `APPLICATION_ALREADY_TERMINAL` | Application already terminal | No further normal workflow action is allowed for this application |
| 422 | `PRODUCT_VERIFICATION_PENDING` | Product verification pending | Complete or wait for product-specific verification before continuing |
| 422 | `PRODUCT_VERIFICATION_FAILED` | Product verification failed | Correct product-specific information or follow the configured review path |
| 422 | `PRODUCT_VERIFICATION_REQUIRES_MORE_INFORMATION` | Product verification requires more information | Provide the requested customer or staff correction before continuing |
| 409 | `SYSTEM_STATE_CONFLICT` | System state conflict | Retry after refreshing state or escalate for manual consistency review |
| **PARTNER Domain — Salary Advance Eligibility** | | | |
| 404 | `PARTNER_COMPANY_NOT_FOUND` | Partner company not found | Verify the requested Partner Company ID |
| 422 | `PARTNER_COMPANY_INACTIVE` | Partner company inactive | Use an active Partner Company for Salary Advance eligibility |
| 404 | `EMPLOYEE_NOT_FOUND` | Partner employee not found | Verify employee information or route to manual review if allowed |
| 422 | `EMPLOYEE_INACTIVE` | Partner employee inactive | Inactive Partner Employee records cannot be used for normal eligibility |
| 422 | `EMPLOYEE_DATA_STALE` | Partner employee data stale | Import current monthly Partner Employee data before eligibility verification |
| 409 | `EMPLOYEE_DUPLICATE_UNRESOLVED` | Duplicate partner employee unresolved | Resolve duplicate employee records before eligibility verification |
| 422 | `EMPLOYEE_VERIFICATION_REQUIRES_REVIEW` | Employee verification requires review | Route the case for authorized manual review with supporting evidence |
| 422 | `SALARY_ADVANCE_LIMIT_EXCEEDED` | Salary Advance limit exceeded | Reduce requested amount to the available Salary Advance limit |
| 409 | `BLOCKING_OVERDUE_EXPOSURE_EXISTS` | Blocking overdue exposure exists | Resolve overdue Salary Advance exposure before submitting a new request |
| 404 | `PARTNER_EMPLOYEE_IMPORT_BATCH_NOT_FOUND` | Partner employee import batch not found | Verify the requested import batch ID |
| 422 | `PARTNER_EMPLOYEE_IMPORT_INVALID` | Partner employee import invalid | Fix invalid rows and re-import before using the batch for eligibility |
| **APPROVAL Domain** | | | |
| 404 | `APPROVAL_REQUEST_NOT_FOUND` | Approval request not found | Verify the requested approval ID |
| 422 | `APPROVAL_REQUIRED` | Approval required | Complete Approver decision before moving to the next workflow step |
| 409 | `DECISION_ALREADY_SUBMITTED` | Decision already submitted | A decision has already been recorded for this approval request |
| 409 | `MAKER_CHECKER_VIOLATION` | Maker-checker violation | The same user cannot record both Loan Officer recommendation and final Approver decision |
| 422 | `REVIEW_RECOMMENDATION_REQUIRED` | Review recommendation required | Complete Loan Officer review before final approval decision |
| **DOCUMENT Domain** | | | |
| 404 | `DOCUMENT_NOT_FOUND` | Document not found | Verify the requested document ID |
| 415 | `FILE_TYPE_NOT_ALLOWED` | File type not allowed | Upload a supported file format (e.g., PDF, JPG, PNG) |
| 413 | `FILE_TOO_LARGE` | File too large | Ensure the file size is within the allowed limits |
| 422 | `DOCUMENT_REQUIRED` | Required document missing | Upload the required document or mark it `NOT_REQUIRED` or `WAIVED` where allowed |
| 422 | `DOCUMENT_NOT_READY` | Document checklist not ready | Complete required uploads and manual review before continuing |
| 422 | `DOCUMENT_REJECTED` | Document rejected | Upload a corrected replacement document |
| 422 | `DOCUMENT_EXPIRED` | Document expired | Upload a valid replacement document |
| 409 | `DOCUMENT_REPLACEMENT_REQUIRED` | Document replacement required | Replace the rejected or expired document before continuing |
| 409 | `DOCUMENT_ALREADY_ACCEPTED` | Document already accepted | Accepted documents cannot be replaced without an authorized correction flow |
| 503 | `DOCUMENT_STORAGE_UNAVAILABLE` | Document storage unavailable | Try again later or contact support |
| **DOCUMENT Domain — OCR-Assisted Processing (Planned Phase 2)** | | | |
| 404 | `OCR_JOB_NOT_FOUND` | OCR job not found | Verify the document has an associated OCR job |
| 409 | `OCR_JOB_PENDING` | OCR job pending | Wait for OCR-assisted processing to complete or continue with manual review where allowed |
| 500 | `OCR_JOB_FAILED` | OCR job failed | Retry processing or proceed with manual document review |
| 404 | `OCR_RESULT_NOT_AVAILABLE` | OCR result not available | Wait for OCR completion or perform manual review |
| 422 | `OCR_LOW_CONFIDENCE_REQUIRES_REVIEW` | Low confidence OCR result requires review | Await manual review of the OCR-assisted result |
| 503 | `OCR_SERVICE_UNAVAILABLE` | OCR service unavailable | Retry later or continue through manual document review where allowed |
| **LOAN CORE — Offer / Disbursement / Repayment** | | | |
| 409 | `OFFER_EXPIRED` | Offer expired | Generate or request a new approved offer according to product rules |
| 422 | `OFFER_NOT_ACCEPTED` | Offer not accepted | Customer must accept approved terms before contract preparation and disbursement |
| 422 | `CONTRACT_DOCUMENTS_NOT_READY` | Contract documents not ready | Complete required contract or disbursement documents before manual disbursement |
| 422 | `DISBURSEMENT_NOT_READY` | Disbursement not ready | Confirm approval, customer acceptance, document readiness, and bank account information |
| 409 | `DISBURSEMENT_ALREADY_COMPLETED` | Disbursement already completed | Cannot confirm manual disbursement more than once |
| 409 | `LOAN_ACCOUNT_NOT_ACTIVE` | Loan account not active | Activate the LoanAccount through manual disbursement confirmation before repayment operations |
| 422 | `REPAYMENT_RECORD_INVALID` | Repayment record invalid | Correct repayment amount, date, status, or outstanding balance information |
| 404 | `REPAYMENT_SCHEDULE_NOT_FOUND` | Repayment schedule not found | Generate the final repayment schedule during LoanAccount activation |
| **AUDIT & COMPLIANCE CONTROLS Domain** | | | |
| 409 | `AUDIT_RECORD_IMMUTABLE` | Audit record immutable | Audit events and status history cannot be modified |
| 503 | `AUDIT_TRAIL_UNAVAILABLE` | Audit trail unavailable | Retry later or escalate because important business actions require audit recording |
| **VALIDATION Domain** | | | |
| 400 | `VALIDATION_FAILED` | Input validation failed | Check the field errors in the response payload |
| 400 | `NATIONAL_ID_FORMAT_INVALID` | National ID format invalid | Ensure the National ID is exactly 9 (CMND) or 12 (CCCD) digits |
| 400 | `PHONE_NUMBER_FORMAT_INVALID` | Phone number format invalid | Provide a valid Vietnamese phone number format |
| **SYSTEM Domain** | | | |
| 500 | `INTERNAL_SERVER_ERROR` | Internal server error | An unexpected error occurred. Please contact support. |
| 503 | `SERVICE_TEMPORARILY_UNAVAILABLE` | Service temporarily unavailable | The system is under maintenance or overloaded. Try again later. |
| 429 | `RATE_LIMIT_EXCEEDED` | Rate limit exceeded | Wait for the specified duration before making more requests |
| **IDEMPOTENCY** | | | |
| 422 | `IDEMPOTENCY_PAYLOAD_MISMATCH` | Payload mismatch | Request body does not match original request for this idempotency key. Use a new idempotency key. |
| 409 | `IDEMPOTENCY_PREVIOUS_SERVER_ERROR` | Previous server error | Previous request encountered a server error. Manual verification required. |

---

## Future / Out-of-Scope Error Codes

These codes are retained only for future phases and must not be treated as MVP workflow errors.

| HTTP Status | Error Code | Message | Resolution |
|---|---|---|---|
| 409 | `CUSTOMER_ALREADY_ANONYMIZED` | Customer already anonymized | Future privacy/anonymization workflow only; not part of MVP customer operations |
| 403 | `APPROVER_DELEGATION_LIMIT_EXCEEDED` | Approver delegation limit exceeded | Future multi-level approval/delegation workflow only |
| 422 | `APPROVAL_SLA_EXCEEDED` | Approval SLA exceeded | Future escalation workflow only |
| 404 | `APPROVAL_DELEGATION_NOT_FOUND` | Approval delegation not found | Future delegated approval configuration only |
