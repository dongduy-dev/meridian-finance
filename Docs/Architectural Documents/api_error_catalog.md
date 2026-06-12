# API Error Code Catalog

This document standardizes the API error codes across all bounded contexts in the Meridian Finance platform.

| HTTP Status | Error Code | Message | Resolution |
|---|---|---|---|
| **AUTH Domain** | | | |
| 401 | `AUTH_001` | Invalid credentials | Check username and password |
| 401 | `AUTH_002` | Token expired | Refresh the access token using the refresh endpoint |
| 401 | `AUTH_003` | Invalid token signature | Re-authenticate to obtain a valid token |
| 401 | `AUTH_004` | Refresh token invalid | Re-authenticate to obtain a new token pair |
| 403 | `AUTH_005` | Account suspended | Contact support to review account status |
| 403 | `AUTH_006` | Insufficient permissions | The user's role does not include the required permission for this action. Refer to the RBAC matrix in `infrastructure_security_observability.md` for the full role→permission mapping. |
| 401 | `AUTH_007` | Token not found | Provide a valid Bearer token in the Authorization header |
| 409 | `AUTH_008` | Concurrent session limit exceeded | Log out of other devices before logging in |
| **CUSTOMER Domain** | | | |
| 404 | `CUST_001` | Customer not found | Verify the requested customer ID |
| 409 | `CUST_002` | Duplicate national ID | A customer with this National ID already exists |
| 409 | `CUST_003` | Duplicate phone number | A customer with this phone number already exists |
| 403 | `CUST_004` | KYC not verified | Complete the KYC verification process |
| 422 | `CUST_005` | Eligibility check failed | Customer does not meet minimum requirements for platform usage |
| 409 | `CUST_006` | Customer already anonymized | Cannot perform actions on an anonymized customer profile |
| **LOAN Domain** | | | |
| 404 | `LOAN_001` | Loan application not found | Verify the requested loan application ID |
| 422 | `LOAN_002` | Invalid state transition | Refresh loan status and retry the operation |
| 422 | `LOAN_003` | Amount below minimum | Ensure requested amount meets the product minimum |
| 422 | `LOAN_004` | Amount exceeds maximum | Ensure requested amount is within product limits |
| 404 | `LOAN_005` | Loan product not found | Verify the requested loan product ID |
| 422 | `LOAN_006` | Loan product inactive | Select an active loan product for application |
| 409 | `LOAN_007` | Duplicate application in progress | Wait for the existing application to complete before submitting a new one |
| 409 | `LOAN_008` | Disbursement already executed | Cannot disburse a loan that has already been disbursed |
| 404 | `LOAN_009` | Repayment schedule not found | The repayment schedule for this loan has not been generated yet |
| 422 | `LOAN_010` | Loan not in approvable state | Ensure the loan is in PENDING_APPROVAL state |
| **APPROVAL Domain** | | | |
| 404 | `APPR_001` | Approval request not found | Verify the requested approval ID |
| 409 | `APPR_002` | Decision already submitted | A decision has already been recorded for this approval request |
| 403 | `APPR_003` | Approver not authorized for amount | Assign an approver with a higher delegation limit |
| 422 | `APPR_004` | SLA exceeded | The approval request has expired and must be escalated |
| 404 | `APPR_005` | Delegation not found | Verify the delegation rule configuration |
| **DOCUMENT Domain** | | | |
| 404 | `DOC_001` | Document not found | Verify the requested document ID |
| 415 | `DOC_002` | File type not allowed | Upload a supported file format (e.g., PDF, JPG, PNG) |
| 413 | `DOC_003` | File too large | Ensure the file size is within the allowed limits |
| 409 | `DOC_004` | Document already verified | Cannot re-upload or modify a verified document |
| 503 | `DOC_005` | Storage unavailable | Try again later or contact support |
| 404 | `DOC_006` | OCR job not found | Verify the document has an associated OCR job |
| **OCR Domain** | | | |
| 404 | `OCR_001` | OCR job not found | Verify the OCR job ID |
| 500 | `OCR_002` | OCR processing failed | Retry the document upload or submit for manual review |
| 422 | `OCR_003` | Low confidence result pending review | Await manual operator review of the OCR results |
| 503 | `OCR_004` | Model not loaded | The OCR worker is currently initializing, retry shortly |
| **VALIDATION Domain** | | | |
| 400 | `VAL_001` | Input validation failed | Check the field errors in the response payload |
| 400 | `VAL_002` | National ID format invalid | Ensure the National ID is exactly 9 (CMND) or 12 (CCCD) digits |
| 400 | `VAL_003` | Phone number format invalid | Provide a valid Vietnamese phone number format |
| **SYSTEM Domain** | | | |
| 500 | `SYS_001` | Internal server error | An unexpected error occurred. Please contact support. |
| 503 | `SYS_002` | Service temporarily unavailable | The system is under maintenance or overloaded. Try again later. |
| 429 | `SYS_003` | Rate limit exceeded | Wait for the specified duration before making more requests |
| **IDEMPOTENCY** | | | |
| 422 | `IDEM_001` | Payload mismatch | Request body does not match original request for this idempotency key. Use a new idempotency key. |
| 409 | `IDEM_002` | Previous server error | Previous request encountered a server error. Manual verification required. |
