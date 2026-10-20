# Meridian API Error Code Catalog

## Purpose and Authority

This document defines Meridian's error vocabulary, representative HTTP mappings and safe messages, and caller-facing resolutions for `meridian-platform`. [MER-API-001](../api/MER-API-001-endpoints-and-postman-scenarios.md) owns endpoint-specific authorization, concealment, request/response, and recovery contracts.

Checked-in exception construction, global error mapping, controllers, and executable tests remain authoritative for runtime behavior. A catalog entry defines the intended error contract but does not by itself establish that an endpoint or workflow is available. Implementation status belongs in the project roadmap and follow-up register.

Reserved codes are listed separately. They do not become part of an executable API contract until the owning capability exposes and tests them.

---

## 1. Catalog Rules

- An identifier names a failure meaning; the HTTP status comes from the exception/handler used by the operation. Tables show normal or representative mappings and messages, not a universal status/message guarantee.
- Clients must use the endpoint-specific contract, HTTP status, and `errorCode`, and must not parse message text. Messages may differ by executable context.
- The resolution states the caller's next action; it must not expose sensitive evidence or internal implementation details.
- Operation-specific conditions and mappings must remain explicit when one identifier is reused. A shared identifier does not make permission denial, concealed absence, and state conflict interchangeable.
- Clients must not infer endpoint availability from this catalog alone.

---

### 1.1 HTTP mapping and known exceptions

The global handler maps `AuthenticationFailedException` to `401`, `AuthorizationException` to `403`, `EntityNotFoundException` to `404`, `BusinessStateConflictException` to `409`, `BusinessRuleViolationException` to `422`, and `ServiceUnavailableException` to `503`. Malformed JSON, type/Bean Validation, constraint, and argument failures use `400 VALIDATION_FAILED`. The security entry point and JWT filter retain their separate authentication codes; method/route permission denial uses `403 ACCESS_DENIED`.

| Identifier | Executable distinction | Caller resolution |
|---|---|---|
| `DOCUMENT_CHECKLIST_NOT_FOUND` | `404` on checklist/content/upload/review lookup, including own-read concealment; `409` when workflow processing-readiness validation lacks its required checklist. | Re-read the authorized checklist/case; do not treat a readiness conflict as proof that another Customer's resource exists. |
| `PRIMARY_BANK_ACCOUNT_REQUIRED` | `422` in origination/correction readiness; `409` when contract destination capture cannot find a primary active account. | Establish an eligible primary account and refresh the relevant application or contract. |
| `SALARY_ADVANCE_LIMIT_UNAVAILABLE` | `422` for an unusable reservation limit; `409` for missing authoritative limit during correction resubmission. | Refresh eligibility/limit context; reconcile missing evidence rather than blindly retrying. |
| `SALARY_ADVANCE_RESERVATION_INVALID` | `409` in workflow reconciliation; `422` in the domain reservation-conversion guard. | Reconcile the exact reservation before further financial commands. |
| `VALIDATION_FAILED` | `400` in transport handlers; `422` in the internal Identity Reference correction validation guard, if reached (HTTP DTO validation normally rejects first). | Correct input under the endpoint contract; do not assume one global status from this name. |
| `INVALID_DOCUMENT_UPLOAD` | `413` for multipart transport limits; `422` for invalid file content, size, MIME/signature, or filename. | Correct the file/request; no usable version is created. |
| `MAKER_CHECKER_VIOLATION` versus `STAFF_CORRECTION_MAKER_CHECKER_VIOLATION` | Approval separation is `422`; Staff-task separation is `403` under a distinct identifier. | Use the different authorized actor required by that operation. |

Messages also vary by context: `SYSTEM_STATE_CONFLICT`, `IDEMPOTENCY_KEY_REUSED`, and `DOCUMENT_CHECKLIST_NOT_FOUND` do not each have one runtime string. These differences must not expose raw identity, bank, document-storage, provider, or credential evidence.

---

## 2. Identity & Access

| HTTP Status | Error Code | Message | Resolution |
|---|---|---|---|
| 401 | `AUTHENTICATION_REQUIRED` | Authentication required | Provide a valid Bearer token in the Authorization header |
| 401 | `INVALID_CREDENTIALS` | Invalid credentials. | Check email/password; unknown email, wrong password, mismatched optional `expectedUserType`, and active temporary lock share this response |
| 401 | `TOKEN_EXPIRED` | Token expired. | Refresh the session or log in again; do not replay an uncertain business command automatically |
| 401 | `INVALID_TOKEN` | Invalid token. | Replace a malformed, revoked, or authorization-stale access token through the supported refresh/login flow |
| 401 | `INVALID_REFRESH_TOKEN` | Refresh authentication failed | Log in again to create a new refresh-token family |
| 401 | `EMAIL_VERIFICATION_REQUIRED` | Email verification required. | Confirm the Customer email, then log in again |
| 401 | `INVALID_EMAIL_VERIFICATION_TOKEN` | Email verification token is invalid or expired. | Request a replacement verification email and submit its token |
| 401 | `INVALID_PASSWORD_RESET_TOKEN` | Password reset token is invalid or expired. | Request a replacement password-reset email and submit its token with a new password |
| 409 | `EMAIL_ALREADY_REGISTERED` | An account with this email already exists. | Log in or use the account-recovery flow instead of registering again |
| 409 | `CUSTOMER_DIGITAL_ACCESS_ALREADY_ENABLED` | Digital access is already enabled for this Customer. | Read the Customer's digital-access status before another enable request |
| 401 | `ACCOUNT_SUSPENDED` | Account is not active. | Use an active account; suspension/disablement is separate from temporary password-login lockout |
| 403 | `ACCESS_DENIED` | Access denied | Use a principal whose role includes the required permission |
| 403 | `CUSTOMER_CONTEXT_REQUIRED` | Customer context required | Use an authenticated customer-linked token for a customer-owned flow |
| 403 | `SALARY_ADVANCE_READINESS_ACCESS_DENIED` | Salary Advance readiness access denied | Use an authenticated Customer principal with `loan:submit` |
| 403 | `LOAN_REVIEW_ACCESS_DENIED` | Loan review access is denied. | Use a Staff principal with the exact `loan:review` permission |
| 403 | `LOAN_REVIEW_ASSIGNED_TO_ANOTHER_OFFICER` | The Loan Officer review is assigned to another Staff user. | Use the assigned Loan Officer, or a future controlled reassignment workflow when one is approved |
| 403 | `APPROVAL_WORK_ACCESS_DENIED` | Staff approval work access is denied. | Use a Staff principal with the exact `approval:recommend` or `approval:decide` permission required by the read |
| 404 | `INTERNAL_USER_NOT_FOUND` | Internal User was not found. | Refresh the Staff User list and use an existing internal User ID |
| 404 | `INTERNAL_ROLE_NOT_FOUND` | Internal role was not found. | Refresh assignable roles and use a predefined non-Customer role code |
| 429 | `RATE_LIMIT_EXCEEDED` | Too many requests. | Retry after the number of seconds stated by the `Retry-After` response header |

---

Missing or unsupported Bearer authentication on a protected route returns `AUTHENTICATION_REQUIRED`; a supplied invalid/expired Bearer token has its JWT-specific code even on public routes. Refresh and logout bypass the JWT filter. Missing/invalid refresh authority uses `INVALID_REFRESH_TOKEN`; logout returns `204` even for absent or invalid credentials. Purpose-specific actor checks retain their own `403` identifiers. Customer ownership is concealed only by the endpoint families that define concealment; approved-offer foreign ownership, for example, uses `403 ACCESS_DENIED`.

---

## 3. Customer Management

| HTTP Status | Error Code | Message | Resolution |
|---|---|---|---|
| 404 | `CUSTOMER_NOT_FOUND` | Customer not found | Verify the requested Customer ID |
| 409 | `CUSTOMER_NOT_ACTIVE` | Customer not active | Restore the Customer to `ACTIVE` before using customer-owned lending flows |
| 409 | `IDENTITY_REFERENCE_IMMUTABLE` | Identity reference immutable | Ordinary profile updates cannot change a completed reference; use controlled correction only while identity is not verified |
| 409 | `VERIFIED_IDENTITY_CHANGE_NOT_ALLOWED` | Full name cannot be changed through profile maintenance after identity verification. | Retain the verified full name in Customer and Staff maintenance; verified identity amendment requires a separate deferred Staff workflow |
| 409 | `IDENTITY_REFERENCE_ALREADY_IN_USE` | Identity reference already in use | Use an identity reference that does not belong to another Customer |
| 422 | `CUSTOMER_DIGITAL_ACCESS_OWNERSHIP_NOT_VERIFIED` | Customer identity could not be verified for digital access. | Recheck the Customer's presented identity reference; do not enable access until it matches the selected Customer |
| 422 | `PROFILE_INCOMPLETE` | Customer profile incomplete | Complete the required identity, contact, residential, employment, and consent fields |
| 422 / 409 | `PRIMARY_BANK_ACCOUNT_REQUIRED` | Primary bank account required | Add or select a primary active bank account |
| 409 | `BANK_ACCOUNT_UPDATE_NOT_ALLOWED` | Bank account update not allowed | An inactive account cannot become primary; select another active primary before deactivating a primary when other active accounts remain. This is not a loan-status mutation policy |
| 404 | `BANK_ACCOUNT_NOT_FOUND` | Bank account not found | Verify that the bank account belongs to the authenticated Customer |
| 409 | `DUPLICATE_BANK_ACCOUNT` | Duplicate bank account | Use the existing active bank account |

---

### 3.1 Customer Identity Verification

| HTTP | Error code | Meaning | Caller action |
|---|---|---|---|
| 403 | `IDENTITY_VERIFICATION_ACCESS_DENIED` | Customer identity verification access is denied | Use the exact own capability or dedicated Staff review capability |
| 404 | `IDENTITY_VERIFICATION_NOT_FOUND` | Verification missing or concealed | Own reads conceal foreign Customer attempts with the same result |
| 404 | `IDENTITY_EVIDENCE_NOT_FOUND` | Exact identity evidence missing or inconsistent | Reconcile the case and exact version; never select arbitrary evidence |
| 409 | `IDENTITY_VERIFICATION_EVIDENCE_STALE` | Exact evidence or full-name context changed | Submit and review the authoritative current evidence |
| 409 | `IDENTITY_VERIFICATION_ALREADY_COMPLETED` | A conflicting terminal decision exists | Read the recorded outcome; only exact logical replay succeeds |
| 409 | `IDENTITY_VERIFICATION_NOT_ALLOWED` | Customer identity is already verified | Reuse the valid Customer verification |
| 422 | `INVALID_IDENTITY_VERIFICATION_REQUEST` | Missing confirmation, invalid rejection shape, or invalid queue pagination | Supply the controlled operation fields |
| 422 | `IDENTITY_REFERENCE_MISMATCH` | Presented reference differs from protected Customer evidence | Re-check the exact document; the attempt remains pending. A wrong profile reference may be corrected through the authorized pre-verification command before retrying |
| 422 | `CUSTOMER_IDENTITY_VERIFICATION_REQUIRED` | New origination or Partner employment verification/approval lacks current verified Customer identity | Complete Customer identity verification before submitting or verifying employment |

### 3.2 Customer Identity Reference Reveal

| HTTP | Error code | Message | Caller action |
|---|---|---|---|
| 403 | `CUSTOMER_IDENTITY_REFERENCE_ACCESS_DENIED` | Customer Identity Reference access is denied. | For operational reveal, use a Staff actor without a Customer association, Loan Officer role, and exact `loan:read`, `customer:read`, and `customer:identity:reveal`; verification authority is insufficient. Controlled correction instead requires the exact Customer own-profile or Staff intake actor/authority from MER-API-001 Section 3.12.1. Missing endpoint permissions use the established security denial. |
| 409 | `CUSTOMER_IDENTITY_REFERENCE_UNAVAILABLE` | The stored Customer Identity Reference is unavailable. | Clear the temporary value; reconcile the Customer profile through its authorized workflow or escalate for protected-evidence repair. Do not infer cryptographic details or retry automatically. |

Application lookup retains `404 LOAN_APPLICATION_NOT_FOUND`; refresh the exact case rather than choosing a Customer ID. Invalid path UUIDs use the established `400` validation contract. Unknown network/server/schema results require successful case revalidation before another explicit reveal. Audit persistence failure cannot produce a successful reveal response.

### 3.3 Staff Customer intake

| HTTP | Error code | Meaning | Caller action |
|---|---|---|---|
| 403 | `STAFF_CUSTOMER_INTAKE_ACCESS_DENIED` | Invalid Staff intake actor or authority | Use Staff without Customer context and the exact intake capability. |
| 422 | `INVALID_CUSTOMER_SEARCH` | Not exactly one supported search field | Supply only exact Customer number or Identity Reference in the body. |

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
| 422 | `INVALID_PRODUCT_LIMITS` | Loan product limits are invalid. | Supply nonnegative `NUMERIC(19,2)` limits with maximum greater than or equal to minimum |
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

### 4.1 Product and assisted-origination guards

| HTTP | Error code | Meaning | Caller action |
|---|---|---|---|
| 404 | `PRODUCT_CODE_NOT_FOUND` | Unknown product code | Use an executable catalogue code. |
| 403 | `ASSISTED_ORIGINATION_ACCESS_DENIED` | Invalid Staff origination actor/authority | Use Staff without Customer context and `loan:originate:staff`. |
| 404 | `ASSISTED_ORIGINATION_CASE_NOT_FOUND` | Missing intake case | Read the exact case identity. |
| 409 | `ASSISTED_ORIGINATION_CASE_NOT_OPEN` | Intake is terminal | Reconcile its completed application or abandoned state. |
| 409 | `ASSISTED_ORIGINATION_CUSTOMER_REQUIRED` | Case has no selected Customer | Associate an active Customer before verification/conversion. |
| 409 | `ASSISTED_ORIGINATION_PRODUCT_MISMATCH` | Conversion does not match intake product | Use the matching UCL or Collateral conversion route. |
| 422 | `ASSISTED_ORIGINATION_PRODUCT_NOT_ALLOWED` | Product forbids Staff-assisted intake | Salary Advance uses Customer-digital origination. |
| 422 | `UCL_PAPER_APPLICATION_REQUIRED`, `COLLATERAL_PAPER_APPLICATION_REQUIRED` | Current matching signed paper form absent | Upload the correct intake evidence before conversion. |
| 403 | `CUSTOMER_DIRECT_ACTION_NOT_ALLOWED` | Customer-direct mutation against assisted application | Use the dedicated evidenced Staff-mediated action, when supported. |
| 422 | `INVALID_UCL_VERIFICATION_OUTCOME`, `INVALID_COLLATERAL_VERIFICATION_OUTCOME` | Unsupported manual outcome or missing expected Collateral cycle | Supply the supported outcome and required cycle fields. |

## 5. Partner Management — Salary Advance Eligibility

| HTTP Status | Error Code | Message | Resolution |
|---|---|---|---|
| 404 | `PARTNER_COMPANY_NOT_FOUND` | Partner company not found | Verify the Partner Company ID |
| 422 | `PARTNER_COMPANY_INACTIVE` | Partner company inactive | Use an active Partner Company |
| 422 | `EMPLOYEE_NOT_VERIFIED` | Customer employee status not verified | Complete Salary Advance employee verification before creating an application |
| 422 | `SALARY_ADVANCE_ELIGIBILITY_DATA_STALE` | Salary Advance eligibility data stale | Refresh the Partner Employee data before eligibility or limit use |
| 409 | `PARTNER_COMPANY_CODE_ALREADY_EXISTS` | Partner company code is already in use | Use a different stable company code or open the existing company |
| 422 | `INVALID_EFFECTIVE_MONTH` | Effective month is invalid | Supply a real year-month in `YYYY-MM` form |

---

### 5.1 Import and eligibility-review errors

| HTTP | Error code | Meaning | Caller action |
|---|---|---|---|
| 422 | `PARTNER_EMPLOYEE_ROWS_REQUIRED` | Missing roster rows | Submit a complete nonempty roster. |
| 403 | `PARTNER_ELIGIBILITY_REVIEW_ACCESS_DENIED` | Invalid review actor or authority | Use exact Partner read/manage authority for the operation. |
| 404 | `PARTNER_ELIGIBILITY_REVIEW_NOT_FOUND` | Missing review | Refresh the shared queue/detail. |
| 409 | `PARTNER_ELIGIBILITY_REVIEW_ALREADY_RESOLVED` | Conflicting terminal decision | Read the recorded result; only exact terminal replay succeeds. |
| 409 | `PARTNER_ELIGIBILITY_REVIEW_STALE` | Prior-month or replaced source evidence | Customer must verify against current evidence; do not retarget historical source. |
| 422 | `PARTNER_ELIGIBILITY_EMPLOYEE_INVALID` | Selected employee is not valid current matching evidence | Select one exact eligible employee from the authoritative batch. |
| 422 | `PARTNER_EMPLOYEE_INACTIVE` | Selected review employee is inactive | Use current active evidence; automatic matching may instead return `MATCHED_INACTIVE` as an outcome. |

## 6. Loan Core — Salary Advance Limit and Exposure

| HTTP Status | Error Code | Message | Resolution |
|---|---|---|---|
| 422 / 409 | `SALARY_ADVANCE_LIMIT_UNAVAILABLE` | Salary Advance limit unavailable | Verify employee eligibility and refresh the limit |
| 422 | `INSUFFICIENT_AVAILABLE_LIMIT` | Insufficient available Salary Advance limit | Reduce the requested amount to the available limit |
| 409 / 422 | `SALARY_ADVANCE_RESERVATION_INVALID` | Salary Advance reservation invalid | Reconcile the reservation before correction resubmission, readiness confirmation, or disbursement |
| 422 | `SALARY_ADVANCE_RESERVATION_RELEASED` | Salary Advance reservation released | Do not continue readiness or activation after the reservation has been released |

---

## 7. Approval Workflow

| HTTP Status | Error Code | Message | Resolution |
|---|---|---|---|
| 422 | `MAKER_CHECKER_VIOLATION` | Approver must be different from the Loan Officer who submitted the recommendation. | Use a different authorized user for the final Approver decision |
| 409 | `STALE_REVIEW_RECOMMENDATION` | Expected recommendation is stale | Refresh the Staff decision evidence and review the latest applicable recommendation |

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
| 403 | `CORRECTION_RESUBMISSION_DENIED` | Correction resubmission denied | Customer-digital Customer-only corrections require the owner; Staff/mixed corrections and eligible Staff-assisted Customer-only corrections use authorized Staff |
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
| 503 | `DOCUMENT_STORAGE_UNAVAILABLE` | Document storage unavailable | Retry later or contact support if the failure persists |
| 403 | `DOCUMENT_ACCESS_DENIED` | Document access denied | Use the exact Customer owner or an authorized Staff actor |
| 403 | `DOCUMENT_UPLOAD_DENIED` | Document upload not authorized | Use the exact Customer-own, initial assisted-upload, or task-scoped Staff upload contract and required authority |
| 409 | `STALE_DOCUMENT_VERSION` | Document version is stale | Refresh the current immutable version before replacing or reviewing |
| 409 | `DOCUMENT_ALREADY_REVIEWED` | Document version already reviewed. | Refresh the current document evidence. Only an unreviewed current version may receive a new review decision. |
| 409 | `DOCUMENT_UPLOAD_REQUIRED` | Current upload required | Upload a current document version before manual review |
| 403 | `DOCUMENT_WAIVER_DENIED` | Waiver permission required | Use a Loan Officer with separate `document:waive` authority |
| 422 | `DOCUMENT_WAIVER_REASON_REQUIRED` | Controlled waiver reason required | Supply an approved waiver reason code |
| 409 | `CORRECTION_TASK_PROOF_MISSING` | Correction proof missing | Upload the required new version before completing the task |
| 403 | `ASSISTED_ACTION_EVIDENCE_ACCESS_DENIED` | Assisted-action evidence access denied | Use exact `document:upload:assisted-action` authority and the action-specific Staff role |
| 422 | `ASSISTED_ACTION_EVIDENCE_REQUIRED` | Current signed assisted-action evidence required | Upload the signed form for the exact current offer, contract version, or active cancellation correction |
| 409 | `ASSISTED_ACTION_EVIDENCE_INVALID` | Assisted-action evidence does not match the business target | Refresh and use the current evidence version bound to the exact target and decision |

---

`DOCUMENT_REPLACEMENT_REQUIRED` is the active controlled correction/replacement **reason code**, not an emitted `409` error identifier. The actionable replacement and current-version rules are defined in MER-API-001 Section 5.5.

### 9.1 Upload, version, and intake failures

| HTTP | Error code | Meaning | Caller action |
|---|---|---|---|
| 413 / 422 | `INVALID_DOCUMENT_UPLOAD` | Transport limit or content validation failure | Follow MER-API-001 Section 1.9; status depends on the failure path. |
| 422 | `UNSAFE_DOCUMENT_UPLOAD` | Mandatory safety scan rejected bytes | Provide safe fictional evidence; no usable version was created. |
| 503 | `DOCUMENT_SCAN_UNAVAILABLE` | Scanner unavailable, timed out, or invalid response | Wait for scanning recovery; do not bypass scanning. |
| 404 / 409 | `DOCUMENT_CHECKLIST_NOT_FOUND` | Missing or concealed checklist; workflow conflict variant | Use Section 1.1 and the endpoint-specific contract. |
| 404 | `DOCUMENT_CHECKLIST_ITEM_NOT_FOUND` | Missing checklist item | Refresh the exact checklist. |
| 404 | `DOCUMENT_VERSION_NOT_FOUND` | Missing or unrelated immutable version | Use the exact authorized version; never fall back to current. |
| 409 | `DOCUMENT_CHECKLIST_ITEM_EXISTS` | Attempt to add an existing item | Use the supported replacement path. |
| 409 | `DOCUMENT_CHECKLIST_ITEM_MISMATCH` | Item does not belong to the application | Refresh the exact application checklist. |
| 403 | `DOCUMENT_REVIEW_DENIED` | Reviewer/authentication or target association invalid | Use the authenticated authorized reviewer and exact item. |
| 422 | `DOCUMENT_WAIVER_REASON_NOT_ALLOWED` | Waiver reason supplied to another outcome | Supply waiver reasons only with a waiver. |
| 422 | `INVALID_DOCUMENT_REVIEW_NOTES` | Invalid restricted note | Correct the permitted note shape/length; do not disclose it elsewhere. |
| 403 | `INTAKE_EVIDENCE_ACCESS_DENIED` | Invalid Staff intake evidence actor/authority | Use exact intake and Loan origination authority. |
| 404 | `INTAKE_EVIDENCE_NOT_FOUND` | Missing case/type document | Upload/read the matching case evidence. |
| 404 | `INTAKE_EVIDENCE_VERSION_NOT_FOUND` | Missing or unrelated intake version | Use the exact case/type/version relationship. |
| 422 | `INTAKE_EVIDENCE_PRODUCT_MISMATCH` | Paper evidence type incompatible with product | Use the matching intake product/form. |

### 9.2 OCR start, status, and final review

| HTTP | Error code | Meaning | Caller action |
|---|---|---|---|
| 403 | `INTAKE_OCR_ACCESS_DENIED`, `INTAKE_OCR_REVIEW_ACCESS_DENIED` | Invalid Staff OCR actor/authority | Use Staff without Customer context, exact intake permission, and authorized case access. |
| 404 | `OCR_JOB_NOT_FOUND` | Authorized immutable intake version has no job | Explicitly start OCR for the current version when desired; historical status does not create a job. |
| 409 | `OCR_REQUIRES_CURRENT_INTAKE_VERSION` | Start targets a replaced version | Read current metadata before starting. |
| 404 | `OCR_RESULT_NOT_FOUND` | Missing job/result for review | Read OCR status; review requires persisted completed output. |
| 409 | `OCR_RESULT_NOT_COMPLETED` | Job is not completed | Wait or continue manual intake; OCR is advisory. |
| 409 | `OCR_REVIEW_REQUIRES_CURRENT_INTAKE_VERSION` | Finalization targets a replaced version | Review the current version. |
| 409 | `OCR_RESULT_CHANGED` | Expected result differs | Refresh the exact review projection. |
| 409 | `OCR_RESULT_ALREADY_REVIEWED` | Conflicting replay of immutable final review | Read the saved review; exact replay remains successful. |
| 422 | `OCR_REVIEW_FIELDS_REQUIRED` | Reviewed field map absent | Supply a map; an empty map is allowed. |
| 422 | `OCR_REVIEW_FIELD_NOT_ALLOWED` | Field outside evidence vocabulary | Use only allowlisted advisory fields, never consent or decision fields. |
| 422 | `OCR_REVIEW_FIELD_INVALID` | Invalid field value | Correct generic value bounds; owning commands still validate business facts. |
| 422 | `OCR_REVIEW_PAYLOAD_TOO_LARGE` | Reviewed map exceeds payload bound | Reduce the reviewed field payload. |
| 503 | `OCR_REVIEW_UNAVAILABLE` | Protected review data cannot be read/written safely | Retry after recovery; do not expose cipher/provider details. |

OCR `FAILED` job state and controlled `failureCategory` are status projection data, not evidence that reserved `OCR_JOB_FAILED` or `OCR_SERVICE_UNAVAILABLE` is emitted as an HTTP error. MER-API-001 Section 3.19 defines the advisory boundary; the deployed portfolio disables the worker.

## 10. Loan Core — Offer, Contract, Disbursement, and Servicing

| HTTP Status | Error Code | Message | Resolution |
|---|---|---|---|
| 409 | `OFFER_EXPIRED` | Offer expired | Refresh the offer/application; an expired offer cannot be accepted, and no caller-controlled offer-regeneration endpoint is defined |
| 409 | `OFFER_ACTION_CONFLICT` | Offer action conflict | Refresh the offer and do not submit contradictory terminal actions |
| 404 | `APPROVED_OFFER_NOT_FOUND` | Approved offer not found | Verify that the application has an approved offer for the authenticated Customer |
| 404 | `CURRENT_CONTRACT_MISSING` | Current operational contract not found | Prepare the first operational contract for the accepted offer |
| 409 | `CONTRACT_VERSION_STALE` | Expected contract version is stale | Refresh the current contract and retry with its exact version |
| 409 | `CONTRACT_SUPERSESSION_REASON_NOT_ALLOWED` | Contract supersession reason not allowed | Omit the supersession reason when preparing version 1 |
| 409 | `CONTRACT_SUPERSESSION_REASON_REQUIRED` | Contract supersession reason required | Use the controlled reason `DISBURSEMENT_ACCOUNT_REFRESH` |
| 409 | `CONTRACT_REGENERATION_NOT_ALLOWED` | Contract regeneration is not allowed | Do not regenerate a ready contract or a superseded version |
| 409 | `CONTRACT_ACKNOWLEDGMENT_NOT_ALLOWED` | Contract acknowledgment is not allowed | Acknowledge only the current `PREPARED` contract version |
| 403 | `ASSISTED_ACTION_ACCESS_DENIED` | Staff-assisted Customer action access denied | Use the exact purpose-specific Staff permission with a Staff-only principal |
| 403 | `ASSISTED_ACTION_ROLE_REQUIRED` | Required Staff business role missing | Use a Loan Officer for offer response or Accounting Officer for contract acknowledgment |
| 409 | `ASSISTED_ACTION_NOT_ALLOWED` | Staff-assisted Customer action not allowed | Use only an eligible `STAFF_ASSISTED` UCL or Collateral application in the required state |
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

### 10.1 Staff operational access and evidence

| HTTP | Error code | Meaning | Caller action |
|---|---|---|---|
| 403 | `ACCOUNTING_ROLE_REQUIRED` | Accounting business role missing for contract/disbursement work | Use the exact Accounting role and operation capability. |
| 403 | `ACCOUNTING_OFFICER_ROLE_REQUIRED` | Accounting role missing for closure | Use Accounting Officer plus `loan:account:close`. |
| 403 | `APPROVER_ROLE_REQUIRED` | Approver role missing for settlement | Use Approver plus `loan:settlement:approve`. |
| 403 | `STAFF_CONTEXT_REQUIRED` | Repayment lacks a Staff-shaped actor | Use Staff without Customer association and repayment authority. |
| 403 | `LOAN_CONTRACT_WORK_ACCESS_DENIED`, `LOAN_DISBURSEMENT_WORK_ACCESS_DENIED`, `LOAN_SERVICING_WORK_ACCESS_DENIED`, `LOAN_SETTLEMENT_WORK_ACCESS_DENIED`, `LOAN_CLOSURE_WORK_ACCESS_DENIED` | Staff workspace actor/permission denied | Use the purpose-specific authority from MER-API-001 Sections 6.5 and 7.1–7.6. |
| 403 | `LOAN_SERVICING_PROVENANCE_ACCESS_DENIED`, `LOAN_SETTLEMENT_EVIDENCE_ACCESS_DENIED` | Purpose-specific evidence audience denied | Use the exact Staff read or Approver settlement capability. |
| 403 | `ASSISTED_UCL_CANCELLATION_ACCESS_DENIED` | Staff UCL cancellation actor/authority denied | Use Loan Officer plus exact `loan:cancel:staff`. |
| 404 | `APPROVED_LOAN_SETTLEMENT_NOT_FOUND` | Approved settlement absent or concealed | Reconcile the selected account; GET does not prove an unresolved request UUID. |
| 409 | `INVALID_APPLICATION_STATE` | Application outside contract/disbursement workspace state | Refresh the exact workspace; do not infer action from generic application status. |

## 11. Audit & Compliance Controls

Audit has no public mutation API. Internal payload construction can raise `INVALID_AUDIT_PAYLOAD`, `DUPLICATE_AUDIT_PAYLOAD_KEY`, or `AUDIT_PAYLOAD_TOO_LARGE` (each maps to `422` if propagated) for invalid shape, repeated keys, or excessive safe payload size. These are guard failures, not instructions for an HTTP client to submit audit payloads. Escalate a repeated failure through the owning business operation; never omit required audit to make a command succeed. Reserved immutability/unavailability vocabulary remains in Section 14.

---

## 12. Validation and Idempotency

| HTTP Status | Error Code | Message | Resolution |
|---|---|---|---|
| 400 / 422 | `VALIDATION_FAILED` | Input validation failed. | Correct request shape, types, or required fields; transport mapping is `400`, while the internal Identity Reference correction guard can propagate `422`. The envelope does not include field-level errors |
| 409 | `IDEMPOTENCY_KEY_REUSED` | Request ID reused for different content | Replay the original logical content or use a new request ID |

---

## 13. System and Consistency

| HTTP Status | Error Code | Message | Resolution |
|---|---|---|---|
| 409 | `SYSTEM_STATE_CONFLICT` | System state conflict | Refresh the resource. Escalate if workflow, account, schedule, reservation, or exposure evidence remains inconsistent |

---

### 13.1 Workflow and invariant guards

These identifiers are constructed by executable services/domain guards. Their mapped status describes the exception family if propagated; this inventory does not promise that every guard is directly reachable through a valid HTTP DTO. Reconcile authoritative state before another command; repeated internal-evidence failures require repair rather than client-supplied replacement facts.

| HTTP | Error code | Meaning | Caller action |
|---|---|---|---|
| 409 | `LOAN_REVIEW_START_NOT_ALLOWED`, `LOAN_RECOMMENDATION_NOT_ALLOWED`, `APPROVAL_DECISION_NOT_ALLOWED` | Lifecycle disallows review/recommendation/decision or already has its outcome | Refresh exact cycle/recommendation evidence. |
| 409 | `DOCUMENT_UPLOAD_COMPLETION_NOT_ALLOWED`, `DOCUMENT_REPLACEMENT_NOT_ALLOWED`, `DOCUMENT_REPLACEMENT_ALREADY_REQUESTED` | Document lifecycle disallows completion/replacement or already has its replacement task | Refresh the checklist and active correction. |
| 409 | `ACTIVE_CORRECTION_REQUEST_EXISTS`, `CORRECTION_RESUBMISSION_NOT_ALLOWED` | Conflicting active correction or invalid resubmission lifecycle | Use the active correction and its authorized completion path. |
| 409 | `CONTRACT_READINESS_NOT_ALLOWED`, `MANUAL_DISBURSEMENT_CONFIRMATION_NOT_ALLOWED` | Application not in required contract/disbursement state | Reconcile current contract and application before command. |
| 409 | `LOAN_ACCOUNT_ACTIVATION_NOT_ALLOWED`, `MANUAL_DISBURSEMENT_NOT_ALLOWED` | Missing ready-contract basis for activation/evidence | Reconcile readiness and exact contract version. |
| 404 | `SALARY_ADVANCE_VERIFICATION_NOT_FOUND`, `SALARY_ADVANCE_LIMIT_NOT_FOUND` | Required Salary Advance verification/limit absent | Escalate missing authoritative financial evidence. |
| 409 | `SALARY_ADVANCE_VERIFICATION_REQUIRED` | Correction resubmission lacks its saved Salary Advance verification | Reconcile original verification before resubmission. |
| 422 | `SALARY_ADVANCE_LIMIT_STATE_INVALID`, `SALARY_ADVANCE_RESERVATION_RELEASE_NOT_ALLOWED`, `REPAYMENT_RELEASE_NOT_ALLOWED`, `REPAYMENT_RELEASE_EVIDENCE_INVALID` | Salary Advance amounts, release, or movement evidence inconsistent | Reconcile exact product exposure; do not alter another product's exposure. |
| 422 | `LOAN_ACCOUNT_INVALID`, `MANUAL_DISBURSEMENT_INVALID`, `REPAYMENT_SCHEDULE_INVALID`, `REPAYMENT_BALANCE_INVALID`, `REPAYMENT_INSTALLMENT_PROGRESS_INVALID`, `REPAYMENT_SERVICING_INVALID`, `REPAYMENT_TRANSACTION_INVALID`, `REPAYMENT_ALLOCATION_INVALID` | Inconsistent financial/account/schedule/allocation evidence | Escalate after authoritative reload; clients cannot supply substitute persisted facts. |
| 422 | `LOAN_APPLICATION_CANCELLATION_COMMAND_INVALID`, `ASSISTED_UCL_CANCELLATION_COMMAND_INVALID`, `LOAN_ACCOUNT_CLOSURE_COMMAND_INVALID`, `REPAYMENT_COMMAND_INVALID`, `SETTLEMENT_COMMAND_INVALID` | Invalid application command fields/identities | Correct the command under its endpoint DTO; transport validation may reject earlier with `400`. |
| 422 | `LOAN_APPLICATION_CANCELLATION_EVIDENCE_INVALID`, `LOAN_ACCOUNT_CLOSURE_EVIDENCE_INVALID`, `LOAN_SETTLEMENT_EVIDENCE_INVALID` | Terminal action and financial evidence do not reconcile | Escalate; do not manufacture terminal evidence. |
| 422 | `RECOMMENDATION_REASON_REQUIRED`, `APPROVAL_DECISION_REASON_REQUIRED` | Action requires a reason | Provide the required rationale. |
| 422 | `INVALID_OPERATION_CONTEXT` | Operation actor/identity conflicts with authoritative source | Escalate inconsistent orchestration evidence. |
| 422 | `VALIDATION_ERROR` | Internal Staff provisioning validation failed | Correct required email/display-name/role input; normal HTTP Bean Validation may reject earlier. |
| 409 | `INTERNAL_USER_NOT_ACTIVE` | Setup target is not active eligible verified Staff | Restore eligible status before explicit setup resend. |

`ADMINISTRATOR_ALREADY_ESTABLISHED` is an executable **startup-only** bootstrap refusal. It has no HTTP endpoint/status contract. Disable bootstrap and use supported administration/recovery under MER-DEP-001; do not treat the historical accounts as setup fixtures.

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
| 422 | `EMPLOYEE_INACTIVE` | Partner employee inactive | Reserved alternative vocabulary; active review rejection uses `PARTNER_EMPLOYEE_INACTIVE`, while automatic matching returns outcomes. |

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
| 422 | `REVIEW_RECOMMENDATION_REQUIRED` | Review recommendation required | Reserved vocabulary; the executable decision path uses expected recommendation/cycle validation. |

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
