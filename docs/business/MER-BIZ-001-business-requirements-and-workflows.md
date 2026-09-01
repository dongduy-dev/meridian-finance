# MER-BIZ-001 — Business Requirements and Workflow Specification

## 1. Document Information

| Field | Value |
|---|---|
| Project | Meridian |
| Product | Meridian Lending Platform |
| Document Type | Business Requirements and Workflow Specification |
| Version | 1.0 |
| Status | Authoritative living specification |
| Author | Dong Duy |
| Scope | MVP multi-product digital lending platform centered on Salary Advance, with streamlined Unsecured Consumer Loan and Collateral Loan workflows |

---

## 2. Purpose, Authority, and Interpretation

This document is Meridian's primary business authority. It defines product scope, actors, permissions, business concepts, common lifecycle rules, product-specific workflows, status transitions, functional requirements, business rules, configuration values, and MVP boundaries.

Source code, migrations, configuration, and executable tests are authoritative for current executable behavior. This document is authoritative for intended business behavior. A conflict between them is a specification or implementation defect to resolve; neither source silently replaces the other.

Detailed database structures, API schemas, package placement, runtime lock mechanics, and deployment design belong to the database, API, and architecture documents unless a technical constraint is necessary to preserve a business outcome.

The specification describes the approved MVP business target. Delivery status, temporary gaps, and sequencing belong in the README roadmap and `docs/project/MER-TRACK-001-follow-up-register.md`.

---

## 3. Product, Channel, and MVP Scope

### 3.1 Supported Loan Products

Meridian uses one lending lifecycle with product-specific policy behavior.

| Product Code | Product Name | Product Type | MVP Depth | Verification Model |
|---|---|---|---|---|
| `SALARY_ADVANCE` | Salary Advance | `SALARY_BASED` | Flagship | Reusable employee verification and limit-based eligibility |
| `UNSECURED_CONSUMER_LOAN` | Unsecured Consumer Loan | `UNSECURED` | Streamlined | Document-based income and employment review |
| `COLLATERAL_LOAN` | Collateral Loan | `SECURED` | Streamlined | Structured Collateral facts, ownership-evidence review, and manual verification |

`productCode` identifies the exact product. `productType` groups related products for reporting and policy selection.

### 3.2 Application Channels

| Component | MVP Responsibility |
|---|---|
| Backend | Business rules, security, persistence, workflow control, product-policy selection, and audit evidence |
| Customer Web | Registration, profile completion, product selection, application submission, document upload, offer response, and status tracking |
| Internal Web | Permission-scoped internal capabilities: Staff Web for review, approval, correction, contract, disbursement, and repayment; Back-Office Administration for product, Partner, import, internal-user, role, permission, and configuration administration; audit operations within the relevant area |
| Mobile App | Outside the MVP |

Customer Web and Internal Web use the same backend and database.

### 3.3 MVP In Scope

- Customer registration, authentication, profile completion, controlled profile changes, and bank-account management.
- Staff authentication, role-based permissions, internal-user administration, and actor traceability.
- Loan-product catalog, activation, deactivation, policy configuration, and product selection.
- One controlled LoanApplication lifecycle shared by all supported products.
- Salary Advance Partner Company setup, monthly Partner Employee imports, reusable employee verification, limit calculation, exposure reservation, disbursement conversion, and repayment release.
- Streamlined Unsecured Consumer Loan and Collateral Loan workflows.
- Document checklist creation, upload completeness, immutable versions, manual review, replacement, waiver, correction, and processing readiness.
- Loan Officer review, Approver decision, maker-checker separation, approved offers, Customer response, contract readiness, manual disbursement, LoanAccount activation, repayment, overdue servicing, contractual payoff, Administrative Full-Balance Settlement, administrative closure, and audit.

### 3.4 MVP Out of Scope

The MVP excludes real financial and payroll integrations, production compliance operations, automated credit scoring, a full collateral valuation or enforcement platform, a financial ledger, production mobile delivery, and non-lending product families. Section 13 defines the complete boundary.

### 3.5 Architecture Boundary

Meridian is delivered as a modular-monolith backend with one database and multiple frontends. Business ownership and context collaboration are defined in `MER-ARCH-001`; source structure and dependencies are defined in `MER-ARCH-002` and `MER-ARCH-003`.

---

## 4. Actors and Permission Rules

### 4.1 Actors

| Actor | Business Responsibilities |
|---|---|
| Customer | Maintains their own profile and bank accounts, selects products, verifies Salary Advance employment, creates and submits applications, completes Customer-owned corrections, uploads documents, accepts or declines offers, acknowledges contracts, and views their own application and LoanAccount state |
| Loan Officer | Reviews application facts and documents, records recommendations, requests Customer or Staff correction, and performs authorized document review or waiver actions |
| Approver | Records the independent final application decision, returns an application to review, requests structured correction, and performs authorized Loan-owned Administrative Full-Balance Settlement |
| Accounting Officer | Prepares operational contracts, confirms readiness, reveals a protected destination only for disbursement, confirms the external transfer, records authorized repayment updates, and closes eligible settled LoanAccounts |
| Back-Office Admin | Manages product configuration, Partner Companies, Partner Employee imports, internal users, role assignments, and operational configuration |
| System | Applies validations, status transitions, calculations, expiry, overdue evaluation, idempotency, and audit rules |

### 4.2 Role and Action Matrix

| Action | Customer | Loan Officer | Approver | Accounting Officer | Back-Office Admin | System |
|---|---|---|---|---|---|---|
| Register and authenticate to Customer Web | Own account | No | No | No | No | Validate |
| Authenticate to Internal Web | No | Yes | Yes | Yes | Yes | Validate |
| Maintain Customer profile | Own profile | View or review | View | View purpose-limited destination facts | Support as configured | Validate and audit |
| Manage products | No | No | No | No | Yes | Enforce active policy |
| Manage Partner Companies and imports | No | No | No | No | Yes | Validate and store |
| Create, save, submit, or cancel application | Own application under allowed rules | No | No | No | No | Validate and transition |
| Upload Customer-required documents | Own authorized checklist items | Assist when authorized | No | No | Assist when authorized | Store and version |
| Review or waive documents | No | Yes with required authority | View | Confirm readiness facts | Correct administratively when authorized | Calculate readiness |
| Record recommendation | No | Yes | No | No | No | Validate and audit |
| Record final approval decision | No | No | Yes | No | No | Validate and audit |
| Prepare or confirm contract readiness | Acknowledge own contract | View | View | Yes | No | Validate and transition |
| Reveal full disbursement destination | No | No | No | Yes with `loan:disburse` | No | Authorize and audit |
| Confirm manual disbursement | No | No | No | Yes | No | Activate account atomically |
| Record repayment | No | No | No | Yes | No | Allocate and update servicing |
| Approve and apply Administrative Full-Balance Settlement | No | No | Yes | No | No | Verify exact outstanding, apply payment, and settle |
| Close an eligible settled LoanAccount | No | No | No | Yes | No | Verify evidence and close administratively |
| View audit evidence | No | Authorized | Authorized | Authorized | Authorized | Record |

Authentication and permission are necessary but not sufficient. The owning business capability also verifies resource ownership, status, maker-checker separation, and every applicable business rule. All Staff business actions record the authenticated actor.

---

## 5. Core Business Concepts

### 5.1 Loan Product

A `LoanProduct` defines customer-visible product information and the policy used to validate, price, activate, and service one lending product.

Each active product defines:

- code, name, type, description, and active status;
- minimum and maximum amount;
- allowed terms;
- pricing method and repayment method;
- required documents and eligibility notes;
- offer-validity period;
- the product policy used for eligibility, offer construction, activation, repayment, contractual payoff, Administrative Full-Balance Settlement, and administrative closure.

A Customer can select and submit only an active product.

### 5.2 LoanApplication

A `LoanApplication` represents one Customer request for one selected product. It may begin as a draft or be created directly at submission according to the product flow.

The application preserves:

- Customer and product identity;
- requested amount and term;
- product-specific facts and verification evidence;
- checklist and correction state;
- review, recommendation, and decision references;
- offer, contract, and disbursement lifecycle state;
- status history and audit correlation.

`LoanApplication` governs origination through disbursement. It is not the source of truth for post-disbursement balances or servicing state.

### 5.3 LoanAccount

A `LoanAccount` is created only when an approved, accepted, and ready application is confirmed as disbursed.

The activation transaction must:

1. move the LoanApplication to `DISBURSED`;
2. create one LoanAccount;
3. create one authoritative final repayment schedule;
4. set the LoanAccount to `ACTIVE`;
5. record immutable disbursement evidence;
6. apply product-specific exposure effects;
7. record the required audit and status history.

A failure must not leave a partial account, schedule, exposure movement, application transition, or audit outcome.

After activation, the LoanAccount owns servicing state, contractual outstanding, overdue status, contractual payoff, Administrative Full-Balance Settlement, and administrative closure.

### 5.4 Partner Company and Partner Employee

A Partner Company is an employer configured for Salary Advance eligibility.

A Partner Employee record is monthly employer source data. It includes the Partner Company, employee code, employee identity reference, salary, employment status, employee-level limit, effective month, import batch, and active status.

Invalid, stale, inactive, or unresolved duplicate source data cannot support normal eligibility.

### 5.5 Customer–Partner Employee Link, Limit, and Snapshot

A `CustomerPartnerEmployeeLink` is the reusable relationship between one Customer and one verified Partner Employee record. It answers whether the Customer is verified as an employee of that Partner Company.

A `SalaryAdvanceLimit` is the Customer's current Salary Advance exposure capacity. It tracks total, used, reserved, and available amount, status, and refresh evidence.

A `SalaryAdvanceVerification` snapshot belongs to one LoanApplication. It preserves the employee-link and limit facts used at submission even when the reusable link or current limit later changes.

These concepts must remain separate:

| Concept | Question Answered |
|---|---|
| Customer–Partner Employee Link | Is this Customer verified against this Partner Employee relationship? |
| Salary Advance Limit | How much limit is currently total, used, reserved, and available? |
| Application Verification Snapshot | Which employee and limit facts supported this application at submission? |

### 5.6 Approved Offer, Operational Contract, and Disbursement

An approved offer is the immutable Customer-facing financial-terms snapshot generated after approval.

An operational Loan Contract copies the accepted offer terms and binds the disbursement destination. It is versioned operational evidence, not a PDF, uploaded agreement, electronic signature, digital signature, or legal execution of a contract.

Customer owns the mutable source bank account. Loan owns only the protected, immutable destination snapshot bound to the contract.

A manual disbursement record is immutable evidence that an authorized Accounting Officer confirmed an external transfer. It does not represent a real bank integration.

### 5.7 Verification, Checklist, Document Review, and Correction

Meridian separates five controls:

| Control | Purpose | Business Owner or Actor | Output |
|---|---|---|---|
| Product verification | Determines whether product-specific facts support progression | Loan; evaluated by System or an authorized reviewer | `ProductVerificationResult` |
| Upload completeness | Determines whether every required checklist item has acceptable upload-level evidence | Document | Upload-complete result |
| Manual document review | Decides whether the current document version is accepted, waived, or requires replacement | Authorized Document reviewer | `DocumentReviewOutcome` |
| Document processing readiness | Determines whether every required item is accepted or validly waived | Document | Processing-ready result |
| Correction workflow | Assigns and tracks Customer- or Staff-owned remediation before resubmission | Loan, using Document evidence where required | Correction request and tasks |

Upload completeness does not imply acceptance. OCR output remains advisory evidence and does not replace authorized review.

### 5.8 Collateral

Collateral is one structured asset fact submitted by the Customer for a Collateral Loan application.

MVP collateral information includes:

- type;
- description;
- estimated value;
- ownership status;
- condition note.

Document owns the required `COLLATERAL_OWNERSHIP_EVIDENCE` and its review readiness. Loan owns the application-specific Collateral verification cycle and its restricted manual assessment note. The submitted Collateral facts remain immutable after submission.

Supported collateral types are `MOTORBIKE`, `CAR`, `ELECTRONICS`, `PROPERTY_DOCUMENT`, and `OTHER`.

Estimated value supports manual assessment. It does not trigger an automated loan-to-value decision in the MVP.

### 5.9 Customer Profile and Bank Account

Customer owns the mutable source profile and bank-account data. Sensitive identity and bank-account values must remain protected. The Customer identity reference becomes immutable after the profile first becomes complete. Bank-account identity is replaced rather than edited in place. Permitted changes are audited and must not rewrite historical contract or disbursement evidence.

---

## 6. Common Loan Lifecycle and Workflow Controls

All products use the same lifecycle authority but may enter document and verification stages differently according to product policy.

| Phase | Business Outcome |
|---|---|
| Readiness and selection | Customer and product are eligible to begin |
| Draft and submission | The request becomes a controlled LoanApplication |
| Product verification | Product-specific facts are recorded and evaluated |
| Documents and correction | Required evidence becomes complete and processing-ready |
| Loan Officer review | A recommendation or correction outcome is recorded |
| Approval | An independent decision is recorded |
| Customer offer response | The Customer accepts, declines, or allows the offer to expire |
| Contract readiness | Accepted terms and destination are acknowledged and confirmed ready |
| Disbursement and activation | External transfer evidence creates the LoanAccount and final schedule |
| Servicing | Repayment, overdue state, contractual payoff, Administrative Full-Balance Settlement, and administrative closure are tracked |

### 6.1 Pre-Submission Readiness and Guards

**Customer readiness.** Before submission, the Customer must be active, the required profile must be complete, required consent must be satisfied, and the selected product's bank-account readiness requirement must be satisfied. Customer profile completeness and bank-account readiness are separate controls.

Submission evaluates the Customer's current authoritative profile and eligible bank-account facts. Section 5.9 defines Customer source-data ownership and mutation rules.

**Common submission guards.** Before submission, Loan validates:

- active Customer and complete required profile;
- active product;
- valid requested amount and term;
- required product-specific facts;
- submission-level document requirements;
- no blocking non-terminal `LoanApplication` for the same product, as defined by `BR-004`;
- product-specific prerequisites required before submission;
- applicable concurrency guards and product-specific exposure or financial guards.

A Customer may retain multiple drafts. In the product workflows, the `common blocking-application rule` refers only to the same-product `LoanApplication` restriction defined by `BR-004`, not to every common submission guard.

**Product-specific pre-submission requirements.** Salary Advance requires an active verified Customer–Partner Employee link, current and eligible Partner evidence, and a valid and sufficient Salary Advance limit. Employee eligibility is a Partner and Salary Advance prerequisite, not a universal Customer-verification requirement. A separate generic Customer-verification status is not required for Salary Advance unless the Customer policy later introduces it. Section 6.2 defines the application verification snapshot recorded when Salary Advance submission succeeds.

UCL and Collateral do not require completion of their application-specific manual verification before submission. Successful submission creates the pending manual-verification cycle; Section 6.3 defines the later verification lifecycle.

### 6.2 Draft and Submission

A draft does not create financial exposure.

Submission occurs only after every required profile, product, amount, term, eligibility, product-specific, document, and concurrency check passes. A saved draft transitions from `DRAFT`; a product flow may also create the application directly in its initial submitted or document-pending state.

Submission records a stable application reference, Customer, product, requested amount and term, product-specific facts, submission time, initial status, and audit evidence.

Salary Advance submission additionally reserves limit and records the application verification snapshot in the same controlled outcome.

### 6.3 Product Verification

Each submitted application records a formal product-verification result.

| Product | Verification Behavior |
|---|---|
| `SALARY_ADVANCE` | Records the verified employee-link and limit snapshot used at submission |
| `UNSECURED_CONSUMER_LOAN` | Evaluates income, employment evidence, and basic repayment capacity |
| `COLLATERAL_LOAN` | Evaluates collateral facts, ownership evidence, and manual assessment |

`VERIFIED` permits progression. `FAILED` records an unsuccessful check. `PENDING_MANUAL_REVIEW` waits for an authorized decision. `REQUIRES_MORE_INFORMATION` requires a correction path and is not an approval outcome.

For Unsecured Consumer Loan, an authorized Staff reviewer records exactly one of `VERIFIED`, `FAILED`, or `REQUIRES_MORE_INFORMATION`, together with the authoritative Staff actor, completion time, and restricted internal assessment evidence. `VERIFIED` means that manual evidence-consistency and basic repayment-capacity assessment is complete; it is not credit approval. `FAILED` moves the application to `VERIFICATION_FAILED` and is unsuccessful for that application. `REQUIRES_MORE_INFORMATION` atomically returns the application for a structured correction. The latest verification cycle is authoritative. Correction after a completed verification preserves that cycle as immutable evidence and creates a new `PENDING_MANUAL_REVIEW` cycle linked to the resubmitted correction. The same Loan Officer may verify and later record the review recommendation; maker-checker separation remains between that recommendation and the Approver's final decision.

For Collateral Loan, an authorized Staff reviewer records the same three outcomes against the submitted Collateral facts and processing-ready ownership evidence. The restricted assessment note is required for every outcome. `VERIFIED` means only that the evidence is sufficient for Loan Officer review; it is not credit approval. `FAILED` moves the application to `VERIFICATION_FAILED`. The application cannot be reopened after `FAILED`. `REQUIRES_MORE_INFORMATION` creates a document-only correction. Completed numbered cycles are immutable, the latest cycle is authoritative, and every resubmitted correction creates a linked pending cycle that must be completed before review can restart. The Staff actor who verifies may also perform the later Loan Officer review and recommendation; Approver maker-checker remains measured against the recommending Loan Officer.

### 6.4 Document Review and Correction

Upload completeness is satisfied when every required checklist item has a current upload or an authorized non-upload outcome. Processing readiness requires every required item to be accepted or waived, while non-required items remain outside the readiness obligation.

An authorized reviewer may:

- accept the current version;
- waive the requirement with a controlled reason and separate authority;
- request a replacement.

A replacement creates a new immutable version and invalidates the previous version's readiness effect.

When evidence is missing or requires correction, the application uses:

| Status | Use |
|---|---|
| `DOCUMENTS_PENDING` | Required Customer or Staff uploads are incomplete |
| `RETURNED_FOR_REVISION` | Structured Customer or Staff correction is required |

Every correction task has one owner: Customer or Staff. Mixed correction plans use separate tasks. Restricted Staff notes must not appear in Customer instructions. A Staff actor who requested a Staff correction must not complete that task.

Task completion requires the requested evidence. Customer-only corrections are resubmitted by the Customer owner. Staff-only and mixed corrections are resubmitted by authorized Staff. For UCL, verification, Loan Officer review, and Approver review may each produce a permitted structured correction over `INCOME_PROOF`, `BANK_STATEMENT`, or `EMPLOYMENT_PROOF`. Requested amount and term remain immutable, and correction resubmission returns to `SUBMITTED` for a fresh product-verification cycle before another review begins.

For Collateral Loan, verification, Loan Officer review, or Approver decision may require only replacement or Staff review of the existing `COLLATERAL_OWNERSHIP_EVIDENCE` checklist item. The submitted Collateral type, description, estimated value, ownership status, condition note, requested amount, and requested term remain immutable. Correction cannot create another Collateral asset or supporting checklist item. Resubmission returns to `SUBMITTED` and requires a new linked manual-verification cycle before Loan Officer review.

An authenticated Customer owner may instead terminate a Salary Advance or UCL application while it is `RETURNED_FOR_REVISION`. This narrow cancellation ends the active correction request and changes the application to `CANCELLED`. Salary Advance releases the existing pre-disbursement reservation exactly once in the same transaction; UCL creates no product-exposure effect. It does not require current Partner eligibility because abandonment must remain possible when correction re-verification cannot succeed. Cancellation from other states and Staff or administrative cancellation require separately approved policies.

Resubmission revalidates every affected business condition and routes the application to the earliest stage that still requires work. Salary Advance amount and term remain immutable through correction, and the existing reservation is preserved unless a defined terminal or release rule applies.

### 6.5 Loan Officer Review

The Loan Officer reviews Customer readiness, product verification, document readiness, requested amount and term, product-specific facts, and internal evidence.

| Action | Next Status |
|---|---|
| `RECOMMEND_APPROVAL` | `APPROVAL_PENDING` |
| `RECOMMEND_REJECTION` | `APPROVAL_PENDING` |
| `RETURN_TO_CUSTOMER_REVISION` | `RETURNED_FOR_REVISION` |
| `REQUEST_STAFF_CORRECTION` | `RETURNED_FOR_REVISION` |

A revision action requires a controlled reason and task ownership. The recommendation and LoanApplication transition form one business outcome with the correction plan for a revision action and audit evidence for every action.

### 6.6 Approval Decision

The Approver reviews the application and Loan Officer recommendation. The Approver must be a different Staff actor from the Loan Officer who recorded the recommendation.

MVP approval is exact-request approval:

- approved principal equals the submitted requested amount;
- approved term equals the submitted requested term;
- approval does not create a counteroffer;
- amount or term changes return through review or correction.

| Action | Next Status |
|---|---|
| `APPROVE` | `APPROVED`, then `CUSTOMER_ACCEPTANCE_PENDING` after offer generation |
| `REJECT` | `REJECTED` |
| `RETURN_TO_LOAN_OFFICER_REVIEW` | `RETURNED_TO_REVIEW` |
| `REQUEST_CUSTOMER_OR_STAFF_CORRECTION` | `RETURNED_FOR_REVISION` |

Correction decisions require a controlled reason. When both Customer and Staff must act, the correction plan uses separate single-owner tasks. Rejection and return outcomes preserve the Approver's reason. The decision and resulting LoanApplication transition form one business outcome with approved terms for approval, the correction plan for correction, and audit evidence for every action.

Approval and approved-offer generation must complete as one controlled operation. The application must not remain permanently `APPROVED` without Customer-visible approved terms.

For Collateral Loan, the authoritative latest manual-verification cycle must remain `VERIFIED` when the Approver acts. All four common actions are available. `APPROVE` generates the exact-request offer defined in Section 11.5; `REJECT`, `RETURN_TO_LOAN_OFFICER_REVIEW`, and `REQUEST_CUSTOMER_OR_STAFF_CORRECTION` use the common lifecycle and reason requirements without creating an offer.

### 6.7 Approved Offer and Customer Response

After approval, Meridian generates the immutable Customer-facing approved offer defined in Section 5.6.

Each LoanApplication has at most one approved offer in the MVP.

Once generated, the offer's principal, term, pricing, fees, total repayment, repayment method, provisional items, generation time, and expiry time are immutable. Later product or policy changes must not alter an offer already generated. Viewing the offer is read-only.

The authenticated Customer owner may:

- accept a valid pending offer, moving the application to `CONTRACT_PENDING`;
- decline it, moving the application to `CUSTOMER_DECLINED`;
- take no action until System expiry moves it to `EXPIRED`.

Customer decline and expiry release a Salary Advance reservation exactly once in the same business outcome as the terminal transition. Identical retries return the existing result. Contradictory terminal actions are conflicts.

### 6.8 Operational Contract and Readiness

After acceptance, Accounting prepares the current operational contract from the approved offer and an eligible disbursement destination.

The Customer owner acknowledges the exact current contract version. Acknowledgment is immutable operational evidence and cannot be withdrawn.

Before readiness, Accounting may regenerate a `PREPARED` or `ACKNOWLEDGED` contract only for `DISBURSEMENT_ACCOUNT_REFRESH`. The previous version becomes `SUPERSEDED`; financial terms and repayment items remain unchanged; the new destination is captured; and the Customer must acknowledge the new version.

Readiness is calculated from current authoritative facts. Confirmation requires:

- accepted offer;
- acknowledged current contract;
- active Customer;
- active captured source account;
- processing-ready documents;
- no active correction;
- valid product-specific pre-disbursement evidence;
- for Salary Advance, an unreleased reservation.

Confirmation marks the contract `READY_FOR_DISBURSEMENT` and the application `DISBURSEMENT_PENDING` atomically. It does not transfer funds, create the LoanAccount, create the final schedule, or convert Salary Advance reserved exposure to used exposure.

### 6.9 Manual Disbursement and Activation

Accounting performs the transfer outside Meridian using the contract-bound destination.

Full destination data is available only through a dedicated, authorized, audited reveal operation for the disbursement purpose. Ordinary Customer, LoanAccount, audit, history, log, and error views expose only masked or purpose-limited data.

After the external transfer, Accounting confirms disbursement against the ready contract. Loan uses the current ready contract as authority for Customer, product, destination, principal, term, pricing, and repayment items. The contract must preserve the accepted offer terms exactly.

Confirmation creates one atomic outcome:

1. immutable disbursement evidence;
2. one LoanAccount in `ACTIVE`;
3. one authoritative final dated repayment schedule;
4. product-specific exposure conversion;
5. LoanApplication status `DISBURSED`;
6. audit and status history.

Identical replay returns the original outcome without duplicate writes. Reusing the request identity with different content is a conflict.

### 6.10 Repayment, Overdue State, Contractual Payoff, Administrative Full-Balance Settlement, and Administrative Closure

The final repayment schedule is immutable contractual obligation evidence. Payment transactions, allocations, and servicing progress do not rewrite original schedule amounts or due dates.

Repayment follows these rules:

- allocate installments by due date, then installment number;
- allocate within an installment in `FEE -> INTEREST -> PRINCIPAL` order;
- allow partial and early payment without repricing, rebate, schedule regeneration, or due-date mutation;
- reject the entire payment when it exceeds total contractual outstanding;
- reject a value date before disbursement or after the current business date;
- never rewrite earlier allocations or servicing history;
- prevent duplicate payment evidence from producing duplicate allocations or financial effects.

`principalAllocated` is the contractual principal satisfied by a payment. `principalReleased` is the product-exposure amount released by that allocation. For Salary Advance, allocated principal releases used exposure by the exact allocated amount. UCL and Collateral Loan have no product-exposure model: their principal release is always zero, and their servicing creates no Salary Advance movement. Fee and interest allocations never release exposure.

An installment is:

- `NOT_DUE` before its due date when unpaid;
- `DUE` on its due date when unpaid;
- `PARTIALLY_PAID` when partly satisfied and not overdue;
- `OVERDUE` after its due date while an amount remains;
- `PAID` when fully satisfied.

A LoanAccount is:

- `ACTIVE` while contractual outstanding remains and no unpaid obligation is past due;
- `OVERDUE` while any unpaid obligation is past due;
- `SETTLED` after full contractual repayment or Administrative Full-Balance Settlement;
- `CLOSED` only after an eligible settled account completes administrative closure.

Full contractual payoff must atomically settle the account and complete the product-specific exposure result. Salary Advance must release its allocated principal exactly; UCL and Collateral Loan must retain zero product exposure and create no Salary Advance movement.

Administrative Full-Balance Settlement is an exceptional Loan-owned servicing operation performed by an authorized Approver. It records an actual payment equal to the complete current contractual outstanding, applies the same deterministic allocation rules as repayment, preserves `paid + outstanding = originated` for every component, applies the product-specific exposure result, and moves an `ACTIVE` or `OVERDUE` LoanAccount to `SETTLED`. It is not a discounted settlement, concession, waiver, forgiveness, write-off, negotiation, or debt adjustment.

Administrative closure is a separate Loan-owned operation performed by an authorized Accounting Officer. A `SETTLED` LoanAccount is immediately eligible when contractual outstanding is zero, installment progress is fully paid, financial-settlement provenance and status history are consistent, and product-specific exposure evidence is reconciled. Closure changes the account to `CLOSED` and records closure/history/audit evidence; it does not change the final schedule, payment evidence, allocations, balances, installment progress, product exposure, or LoanApplication state. Either ordinary contractual payoff or Administrative Full-Balance Settlement may provide the financial-settlement provenance.

Product-specific origination restrictions remain in the applicable product workflow. Section 13.3 defines the servicing and accounting capabilities excluded from the MVP.

---

## 7. Product Workflows

All product workflows inherit Section 6. This section adds only the product-specific preparation, eligibility, verification, offer, and servicing rules.

### 7.1 Salary Advance

Salary Advance is Meridian's flagship MVP product. It is a limit-based product in which reusable employment verification and current exposure capacity exist before application submission.

#### Partner Setup and Employee Imports

- Back-Office Admin creates, activates, suspends, or deactivates Partner Companies.
- Back-Office Admin imports Partner Employee data by effective month.
- System records the import batch, validates each row, stores valid records, and prevents invalid or unresolved duplicate rows from normal eligibility.
- Normal eligibility uses the authoritative latest valid `COMPLETED` import batch for the current UTC effective month. If no such batch exists, or a verified link and its employee source do not both reference that batch, the evidence is stale and normal eligibility fails closed.
- An inactive Partner Company or Partner Employee is a hard stop.

#### Customer Verification and Dashboard

The Salary Advance product page shows:

- employee-verification status;
- active Partner relationship;
- Salary Advance limit status;
- total, used, reserved, and available amounts;
- last refresh time;
- the business reason normal application creation is blocked.

A Customer without a valid employee link completes employee verification before starting a Salary Advance application. A link remains reusable while its status is `VERIFIED` and current Partner evidence remains eligible. Re-verification against the authoritative current-month batch refreshes the reusable link and restores eligibility when the current evidence matches.

Employee-verification outcomes:

| Outcome | Product Verification | Business Handling |
|---|---|---|
| `MATCHED_ACTIVE` | `VERIFIED` | Create or refresh the reusable active link |
| `MATCHED_INACTIVE` | `FAILED` | Hard stop; normal manual override is not allowed |
| `NOT_FOUND` | `PENDING_MANUAL_REVIEW` | Review may proceed only with sufficient evidence linked to an active Partner Employee |
| `MULTIPLE_MATCHES` | `PENDING_MANUAL_REVIEW` | Reviewer must resolve the correct active employee |
| `PENDING_MANUAL_REVIEW` | `PENDING_MANUAL_REVIEW` | Wait for authorized review |
| `MANUAL_REVIEW_APPROVED` | `VERIFIED` | Create or refresh the link and audit the reason and reviewer |
| `MANUAL_REVIEW_REJECTED` | `FAILED` | Customer cannot proceed until source information is corrected |

Manual review requires a controlled reason and identifies the supporting evidence. It cannot override an inactive Partner Company or inactive Partner Employee.

#### Limit Calculation and Exposure

The total limit considers:

- product maximum;
- Partner Company policy limit;
- employee-configured limit;
- salary percentage cap;
- active employment and import freshness.

Available limit subtracts used and reserved exposure:

```text
totalLimit = min(
  productMaximumAmount,
  partnerCompanyLimit,
  employeeConfiguredLimit,
  salaryBasedLimit
)

availableLimit = totalLimit - usedAmount - reservedAmount
```

Limit behavior:

- `ACTIVE` permits normal use when available amount is positive.
- `SUSPENDED` blocks normal use while temporary evidence or operational review is unresolved.
- `STALE` blocks normal use until eligible Partner data is refreshed.
- `DISABLED` blocks normal use after the Partner relationship becomes ineligible.
- Draft creation does not reserve limit.
- Successful submission reserves the requested principal.
- Rejection, cancellation, Customer decline, expiry, or another defined pre-disbursement release frees reserved exposure exactly once.
- Disbursement converts the reservation to used exposure.
- Ordinary repayment and Administrative Full-Balance Settlement release only allocated principal.
- Existing loans and application history remain after suspension or disablement.

Each submitted application records the employee-link, limit identity, limit values, and verification result used at submission.

The common blocking-application rule applies. Separately, a matching `ACTIVE` or `OVERDUE` Salary Advance `LoanAccount` with positive contractual outstanding blocks new Salary Advance submission. See `BR-004` and `BR-012`.

#### End-to-End Salary Advance Workflow

1. Back-Office Admin configures an active Partner Company.
2. Back-Office Admin imports the effective Partner Employee batch.
3. System validates the batch and excludes invalid, stale, inactive, or unresolved duplicate evidence from normal eligibility.
4. Customer completes the required profile and maintains one primary active bank account.
5. Customer opens the Salary Advance product page.
6. System resolves the Customer's reusable employee link.
7. Without a valid link, Customer submits employee-verification input.
8. System matches Partner Employee data or routes an eligible unresolved case to authorized review.
9. Successful verification creates or refreshes the reusable link.
10. System calculates or refreshes the Salary Advance limit.
11. Customer views verification and current limit state.
12. Customer starts an application and may save a draft; no limit is reserved.
13. Customer enters requested principal, term, and product-required information and uploads any evidence required before submission.
14. Customer submits the application.
15. System validates Customer readiness, active product, employee eligibility, current Partner data, requested amount and term, blocking applications, outstanding Salary Advance debt, document requirements, and sufficient available limit.
16. System reserves the requested principal, creates or submits the LoanApplication, creates the checklist, and records the `VERIFIED` application snapshot atomically.
17. System uses `DOCUMENTS_PENDING` while required uploads remain and `SUBMITTED` when upload completeness is satisfied.
18. Document review and correction follow Section 6.4.
19. A Loan Officer starts review when the application and documents are ready for that stage.
20. The Loan Officer records approval recommendation, rejection recommendation, Customer revision, or Staff correction.
21. The Approver records approval, rejection, return to review, or structured correction.
22. Approval generates one immutable Salary Advance offer and moves the application to `CUSTOMER_ACCEPTANCE_PENDING`.
23. Customer views the offer without changing financial or workflow state.
24. Customer accepts, declines, or allows the offer to expire.
25. Acceptance moves the application to `CONTRACT_PENDING`; decline, expiry, cancellation, rejection, or another defined pre-disbursement release frees the reservation exactly once.
26. Accounting prepares the operational contract from the accepted offer and captures the eligible destination.
27. Customer acknowledges the exact current version. A permitted destination refresh supersedes the old version and requires new acknowledgment.
28. Accounting confirms readiness. The contract becomes `READY_FOR_DISBURSEMENT` and the application becomes `DISBURSEMENT_PENDING`.
29. Accounting performs the external transfer.
30. Accounting confirms manual disbursement against the ready contract.
31. System creates the LoanAccount and final schedule, records disbursement evidence, moves reserved exposure to used exposure, transitions the application to `DISBURSED`, and records audit and history atomically.
32. Repayment and overdue evaluation move the LoanAccount between `ACTIVE` and `OVERDUE`; allocated principal releases used exposure.
33. Full contractual payoff or an Approver's payment-backed Administrative Full-Balance Settlement moves the account to `SETTLED`.
34. Accounting may separately close an eligible settled LoanAccount without changing financial evidence or LoanApplication state.

Salary Advance excludes automated payroll deduction and real employer, payroll, bank-transfer, and HR integrations, as well as an employer-facing production portal, counteroffers, and Approver-modified terms.

### 7.2 Unsecured Consumer Loan

Unsecured Consumer Loan is a streamlined document-based product. It requires income and employment evidence but no collateral.

Required evidence is defined in Section 11.4. Loan purpose may be an optional product-policy field or document.

The common blocking-application rule applies. Separately, a matching `ACTIVE` or `OVERDUE` UCL `LoanAccount` with positive contractual outstanding blocks new UCL creation or correction resubmission. Product-matching `SETTLED` or `CLOSED` accounts with zero outstanding do not block, unrelated products do not satisfy the UCL outstanding-account guard, and inconsistent account/status/outstanding evidence fails closed. See `BR-004` and `BR-020B`.

A Customer may cancel an owned UCL only from `RETURNED_FOR_REVISION`. Cancellation terminalizes the active correction and application without creating, releasing, converting, or otherwise changing product exposure. Salary Advance cancellation retains its exact reservation-release behavior.

UCL financial policy is defined in Section 11.3.

End-to-end workflow:

1. Customer completes the required profile and primary bank-account setup.
2. Customer selects an active Unsecured Consumer Loan product.
3. Customer enters requested amount, term, income, employment, and other required product facts.
4. Customer uploads the required income and employment evidence.
5. System validates Customer readiness, product rules, requested amount and term, required fields, checklist upload completeness, and blocking applications.
6. Customer submits the application.
7. System records an initial `PENDING_MANUAL_REVIEW` verification cycle.
8. An authorized Staff reviewer records `VERIFIED`, `FAILED`, or `REQUIRES_MORE_INFORMATION`, authoritative actor and time, and restricted internal assessment evidence for income and employment consistency and basic repayment capacity. A `VERIFIED` outcome permits review entry but is not credit approval; `FAILED` ends the application as `VERIFICATION_FAILED`; `REQUIRES_MORE_INFORMATION` creates a structured correction atomically.
9. Document replacement and correction follow Section 6.4. Resubmission after completed verification creates a linked pending cycle and requires re-verification before review. The Customer may instead cancel an owned application from `RETURNED_FOR_REVISION` without an exposure effect.
10. The Loan Officer records a recommendation or permitted Customer or Staff correction outcome.
11. The Approver records the independent decision or a permitted mixed Customer/Staff correction outcome.
12. Approval generates one immutable offer under the configured UCL pricing and repayment policy.
13. Customer accepts, declines, or allows the offer to expire.
14. Accounting prepares the operational contract and Customer acknowledges the current version.
15. Accounting confirms document, Customer, destination, and product readiness.
16. Accounting performs and confirms the external transfer.
17. System creates the LoanAccount and final schedule and moves the application to `DISBURSED`.
18. Repayment, overdue state, contractual payoff, Administrative Full-Balance Settlement, and administrative closure follow Section 6.10.

The UCL MVP excludes credit-bureau integration, automated income verification, bank-statement parsing, automated credit scoring, and fully automated approval.

### 7.3 Collateral Loan

Collateral Loan is a streamlined secured product based on one Customer-submitted structured Collateral fact, required ownership evidence, and manual assessment.

The common blocking-application rule applies, but an existing Collateral LoanAccount does not create an additional product-specific origination restriction. See `BR-004` and `BR-021I`. Collateral origination, activation, and servicing are independent of Salary Advance limit and exposure; no Collateral action creates a Salary Advance limit or movement effect.

End-to-end workflow:

1. Customer completes the required profile and primary bank-account setup.
2. Customer selects an active Collateral Loan product.
3. Customer enters requested amount and term.
4. Customer records collateral type, description, estimated value, ownership status, and condition information.
5. Customer uploads the required ownership evidence.
6. System validates Customer readiness, product rules, requested amount and term, required collateral facts, checklist upload completeness, and blocking applications.
7. Customer submits the application.
8. System records the initial numbered `PENDING_MANUAL_REVIEW` verification cycle.
9. After required ownership evidence is processing-ready, an authorized Staff reviewer records `VERIFIED`, `FAILED`, or `REQUIRES_MORE_INFORMATION` with a restricted assessment note. Verification is not credit approval.
10. `REQUIRES_MORE_INFORMATION` permits only replacement or Staff review of the existing ownership-evidence item. Resubmission preserves the completed cycle, returns the application to `SUBMITTED`, and creates a linked pending cycle for re-verification. Submitted structured Collateral facts and requested terms are not editable.
11. Only the authoritative latest `VERIFIED` cycle permits Loan Officer review. The Loan Officer records a recommendation or a permitted document-only correction outcome; any correction must return through re-verification.
12. The application enters `APPROVAL_PENDING` after a valid Loan Officer recommendation.
13. The Approver approves, rejects, returns the application to Loan Officer review, or requests the permitted document-only correction while the authoritative latest Collateral verification remains `VERIFIED`.
14. After a valid approval decision, Loan generates one immutable offer under the configured Collateral Loan pricing and repayment policy.
15. Customer accepts, declines, or allows the offer to expire.
16. Accounting prepares the operational contract and Customer acknowledges the current version.
17. Accounting confirms document, Customer, destination, and product readiness.
18. Accounting performs and confirms the external transfer.
19. System creates the LoanAccount and final schedule and moves the application to `DISBURSED`, with zero product-exposure effect.
20. Repayment, overdue state, contractual payoff, Administrative Full-Balance Settlement, and administrative closure follow Section 6.10 and retain zero product exposure.

The Collateral Loan MVP excludes post-submission structured-fact editing, supporting photos or documents without an explicit product-policy decision, more than one Collateral asset per application, automated valuation, automated loan-to-value decisions, collateral custody, notarization, asset-registry integration, insurance integration, repossession, liquidation, and legal enforcement.

---

## 8. Status Model and Transition Rules

Status names are namespace-scoped. Similar labels in different concepts do not create shared ownership or interchangeable states.

### 8.1 LoanApplication Statuses

| Status | Business Meaning |
|---|---|
| `DRAFT` | Application exists but has not been submitted |
| `SUBMITTED` | Submission is accepted and ready for the next required verification, document, or review action |
| `VERIFICATION_PENDING` | Formal product verification or review is pending |
| `VERIFICATION_FAILED` | Product verification failed |
| `DOCUMENTS_PENDING` | Required upload or replacement work remains |
| `UNDER_REVIEW` | Loan Officer review is active |
| `RETURNED_FOR_REVISION` | Customer or Staff correction is required |
| `RETURNED_TO_REVIEW` | Approver returned the application to Loan Officer review |
| `APPROVAL_PENDING` | Recommendation is complete and the application awaits an Approver decision |
| `APPROVED` | Approval is recorded and offer generation must complete |
| `REJECTED` | Application is rejected |
| `CUSTOMER_ACCEPTANCE_PENDING` | Immutable approved terms await Customer response |
| `CUSTOMER_DECLINED` | Customer declined the approved offer |
| `CONTRACT_PENDING` | Offer is accepted and operational contract readiness is in progress |
| `DISBURSEMENT_PENDING` | Current contract and required evidence are ready for transfer confirmation |
| `DISBURSED` | Disbursement is confirmed and the LoanAccount is activated |
| `CANCELLED` | Application was cancelled before disbursement under an allowed rule |
| `EXPIRED` | The pending approved offer expired |

Terminal statuses for normal LoanApplication processing are:

```text
REJECTED
CUSTOMER_DECLINED
DISBURSED
CANCELLED
EXPIRED
VERIFICATION_FAILED
```

`VERIFICATION_FAILED` is terminal for UCL and Collateral Loan. A product may permit correction after verification failure only through a separately approved product policy; UCL and Collateral Loan use `REQUIRES_MORE_INFORMATION` for correctable evidence issues instead.

### 8.2 Related Status and Outcome Groups

| Group | Values |
|---|---|
| `LoanAccountStatus` | `ACTIVE`, `OVERDUE`, `SETTLED`, `CLOSED` |
| `ProductVerificationResult` | `VERIFIED`, `FAILED`, `PENDING_MANUAL_REVIEW`, `REQUIRES_MORE_INFORMATION` |
| `ApprovedOfferStatus` | `PENDING`, `ACCEPTED`, `DECLINED`, `EXPIRED` |
| `LoanContractStatus` | `PREPARED`, `ACKNOWLEDGED`, `READY_FOR_DISBURSEMENT`, `SUPERSEDED` |
| `SalaryAdvanceLimitStatus` | `ACTIVE`, `SUSPENDED`, `DISABLED`, `STALE` |
| `DocumentRequirementStatus` | `REQUIRED`, `OPTIONAL`, `NOT_REQUIRED` |
| `DocumentReviewOutcome` | `ACCEPT_DOCUMENT`, `WAIVE_DOCUMENT`, `REQUEST_REPLACEMENT` |
| `RepaymentInstallmentStatus` | `NOT_DUE`, `DUE`, `PARTIALLY_PAID`, `PAID`, `OVERDUE` |

Upload completeness and processing readiness are calculated results. They must not be collapsed into one document-review enum.

### 8.3 Core LoanApplication Transition Matrix

| Current Status | Trigger or Action | Actor | Guard | Next Status | Reason Required |
|---|---|---|---|---|---|
| `DRAFT` | Submit application | Customer | All submission checks pass | `SUBMITTED` or `DOCUMENTS_PENDING` | No |
| `SUBMITTED` | Start product verification when required | System or authorized reviewer | Product policy requires a separate verification stage | `VERIFICATION_PENDING` | No |
| `SUBMITTED` | Start Loan Officer review | Loan Officer | Product verification is complete and documents meet the review-entry rule | `UNDER_REVIEW` | No |
| `VERIFICATION_PENDING` | Verification passes | System or authorized reviewer | Result is `VERIFIED` | `DOCUMENTS_PENDING` or `SUBMITTED` | No |
| `VERIFICATION_PENDING` | Verification fails | System or authorized reviewer | Result is `FAILED` | `VERIFICATION_FAILED` | Yes |
| `VERIFICATION_PENDING` | More information required | System or authorized reviewer | Correctable issue exists | `RETURNED_FOR_REVISION` | Yes |
| `DOCUMENTS_PENDING` | Upload work completes | Customer or Staff | Checklist is upload-complete | `SUBMITTED` | No |
| `SUBMITTED` | Request pre-review document replacement | Authorized reviewer | A current upload requires correction | `RETURNED_FOR_REVISION` | Yes |
| `UNDER_REVIEW` or `RETURNED_TO_REVIEW` | Recommend approval or rejection | Loan Officer | Review is complete | `APPROVAL_PENDING` | Rejection requires a reason |
| `UNDER_REVIEW` or `RETURNED_TO_REVIEW` | Request Customer or Staff correction | Loan Officer | Correctable issue exists | `RETURNED_FOR_REVISION` | Yes |
| `RETURNED_FOR_REVISION` | Resubmit completed corrections | Customer or Staff | Every required task and evidence item is complete | `SUBMITTED` or `UNDER_REVIEW` | No |
| `APPROVAL_PENDING` | Approve | Approver | Maker-checker and decision rules pass | `APPROVED` | No |
| `APPROVAL_PENDING` | Reject | Approver | Decision is complete | `REJECTED` | Yes |
| `APPROVAL_PENDING` | Return to Loan Officer review | Approver | Further review is required | `RETURNED_TO_REVIEW` | Yes |
| `APPROVAL_PENDING` | Request structured correction | Approver | Correctable issue exists | `RETURNED_FOR_REVISION` | Yes |
| `APPROVED` | Generate approved offer | System | Offer generation succeeds | `CUSTOMER_ACCEPTANCE_PENDING` | No |
| `CUSTOMER_ACCEPTANCE_PENDING` | Accept pending offer | Customer | Authenticated owner and offer is unexpired | `CONTRACT_PENDING` | No |
| `CUSTOMER_ACCEPTANCE_PENDING` | Decline pending offer | Customer | Authenticated owner | `CUSTOMER_DECLINED` | No |
| `CUSTOMER_ACCEPTANCE_PENDING` | Expire pending offer | System | Current time is at or after expiry | `EXPIRED` | No |
| `CONTRACT_PENDING` | Confirm readiness | Accounting Officer | Current contract acknowledged and every blocker is cleared | `DISBURSEMENT_PENDING` | No |
| `DISBURSEMENT_PENDING` | Confirm manual disbursement | Accounting Officer | Ready contract and valid transfer evidence | `DISBURSED` | No |
| `RETURNED_FOR_REVISION` | Cancel returned correction | Customer | Authenticated owner; product is Salary Advance or UCL; active correction exists; any Salary Advance reservation remains consistent | `CANCELLED` | No |

Customer cancellation from other pre-disbursement states and every Staff or administrative cancellation require a separately approved policy with defined authority, reason, and financial effects.

A transition and its financial, correction, document, offer, contract, exposure, history, and audit effects must commit as one business outcome where the rule requires atomicity.

---

## 9. Functional Requirements

| ID | Requirement |
|---|---|
| FR-CUST-001 | The system shall let Customers register, authenticate, maintain their own profile, manage their own bank accounts, and provide the identity, contact, residential, employment, consent, and destination facts required by supported products. |
| FR-CUST-002 | The system shall restrict and audit identity, profile, and bank-account changes according to business state and historical-data rules. |
| FR-IAM-001 | The system shall authenticate Customer and Staff actors before protected Customer Web or Internal Web access. |
| FR-IAM-002 | The system shall enforce role and action permissions and preserve the authenticated actor for Staff business actions. |
| FR-PROD-001 | The system shall store and display active products with amount limits, terms, pricing, repayment method, required documents, and eligibility notes. |
| FR-PROD-002 | The system shall let Back-Office Admins manage product configuration, activation, and deactivation. |
| FR-APP-001 | The system shall support draft creation, submission, permitted cancellation, status tracking, and transition control through one common LoanApplication lifecycle. |
| FR-APP-002 | The system shall validate Customer readiness, active product, amount, term, product-specific facts, checklist requirements, exposure, and concurrency rules before submission. |
| FR-APP-003 | The system shall prevent a new submitted application for a product while the Customer has another blocking non-terminal application for that product. |
| FR-SA-001 | The system shall let Back-Office Admins manage Partner Companies and monthly Partner Employee imports for Salary Advance. |
| FR-SA-002 | The system shall validate import rows, track batches, enforce freshness, and prevent invalid, stale, inactive, or unresolved duplicate employee evidence from normal eligibility. |
| FR-SA-003 | The system shall verify Salary Advance employment before normal application creation and maintain a reusable Customer–Partner Employee link after successful verification or authorized manual-review approval. |
| FR-SA-004 | The system shall show employee-verification status and total, used, reserved, and available Salary Advance limit to the Customer. |
| FR-SA-005 | The system shall calculate and maintain Salary Advance limit using product, Partner, employee, salary-cap, used-exposure, reserved-exposure, status, and freshness rules. |
| FR-SA-006 | The system shall block normal Salary Advance creation or submission when employee eligibility is absent, the limit is not usable or sufficient, or matching Salary Advance debt has positive contractual outstanding. |
| FR-SA-007 | The system shall reserve limit at successful submission, release it exactly once on defined pre-disbursement outcomes, convert it to used exposure at disbursement, and release used exposure through allocated principal or another approved policy. |
| FR-SA-008 | The system shall refresh employee links and Salary Advance limit when eligible Partner Employee data changes. |
| FR-SA-009 | The system shall record one Salary Advance verification snapshot for each submitted Salary Advance application. |
| FR-UCL-001 | The system shall support Unsecured Consumer Loan submission, income and employment evidence, positive and negative manual verification, structured correction and re-verification, review, approval, offer response, correction cancellation, contract readiness, disbursement, activation, repayment, overdue servicing, contractual payoff, Administrative Full-Balance Settlement, administrative closure, zero product exposure, and product-scoped outstanding-debt protection. |
| FR-CL-001 | The system shall support Collateral Loan submission with one structured Collateral fact, required ownership evidence, manual verification and re-verification, document-only correction, review, approval, offer response, contract readiness, disbursement, activation, repayment, overdue servicing, contractual payoff, Administrative Full-Balance Settlement, administrative closure, zero product exposure, and no Salary Advance movement. |
| FR-DOC-001 | The system shall let Customers and authorized Staff upload and retrieve purpose-authorized documents associated with Customer, application, collateral, contract, or disbursement requirements. |
| FR-DOC-002 | The system shall calculate upload completeness separately from processing readiness and document-review outcomes. |
| FR-DOC-003 | The system shall support immutable document versions, acceptance, waiver, replacement, controlled reasons, and readiness queries. |
| FR-REV-001 | The system shall let Loan Officers recommend approval or rejection and request Customer or Staff correction. |
| FR-APR-001 | The system shall let Approvers approve, reject, return to Loan Officer review, or request structured Customer or Staff correction. |
| FR-APR-002 | The system shall enforce maker-checker separation between the Loan Officer recommendation and final Approver decision. |
| FR-OFFER-001 | The system shall generate one immutable approved offer after approval, present it to the authenticated Customer owner, support idempotent acceptance or decline, and expire pending offers after the configured validity period. |
| FR-CON-001 | The system shall let Accounting prepare an immutable operational contract from the accepted offer and a protected destination snapshot, let the Customer owner acknowledge the exact current version, and expose a structured readiness result containing readiness status and blocker codes. |
| FR-CON-002 | The system shall permit contract regeneration before readiness only for a controlled destination refresh and shall confirm readiness without performing disbursement. |
| FR-DIS-001 | The system shall expose the full destination only through a dedicated authorized and audited disbursement operation and shall let Accounting confirm an external transfer only against a ready contract. |
| FR-DIS-002 | The system shall create the LoanAccount, final schedule, disbursement evidence, exposure effects, application transition, history, and audit as one atomic activation outcome. |
| FR-REP-001 | The system shall preserve the final schedule, record payments and allocations, track installment and account servicing state, support contractual payoff and Administrative Full-Balance Settlement, and close eligible settled accounts through a separate administrative action. |
| FR-REP-002 | The system shall make repayment idempotent by logical request and payment reference and shall prevent duplicate evidence from creating duplicate allocation, exposure, history, or audit effects. |
| FR-PORTAL-001 | The system shall provide Customer Web capabilities for registration, authentication, profile and bank-account maintenance, product browsing, eligibility, application submission, document upload, offer response, contract acknowledgment, and status viewing. |
| FR-PORTAL-002 | The system shall provide permission-scoped Internal Web capabilities: Staff Web contains review, approval, correction, contract, disbursement, and repayment operations; Back-Office Administration contains product, Partner, import, internal-user, role, permission, and configuration administration; audit operations remain scoped to the relevant area. |
| FR-AUD-001 | The system shall record auditable business actions and transitions with actor, action, time, affected business reference, status change, reason, and operation correlation where applicable. |
| FR-AUD-002 | The system shall keep audit evidence append-only for normal users, PII-safe, and sufficient for maker-checker and business-operation traceability. |

---

## 10. Business Rules

| ID | Rule |
|---|---|
| BR-001 | A Customer can submit an application only for an active `LoanProduct`. |
| BR-002 | The Customer profile must satisfy the selected product's completeness rule before submission. |
| BR-003 | A LoanApplication must pass every required submission validation before becoming submitted. |
| BR-004 | A Customer may keep multiple drafts but cannot submit the same product while another blocking non-terminal application for that product exists. |
| BR-005 | A Customer may cancel their own Salary Advance or UCL application from `RETURNED_FOR_REVISION`; the active correction ends, Salary Advance releases its reservation atomically, and UCL creates no product-exposure effect. |
| BR-006 | Customer cancellation from another pre-disbursement state or Staff cancellation requires a separately approved policy defining actor authority, reason, permitted state, and financial effects. |
| BR-007 | A `DISBURSED` application cannot be cancelled. |
| BR-008 | Normal Salary Advance creation requires an active verified Customer–Partner Employee link. |
| BR-009 | Salary Advance requested principal must not exceed active available limit. |
| BR-010 | An inactive Partner Company cannot be manually overridden for normal Salary Advance eligibility. |
| BR-011 | An inactive Partner Employee cannot support normal Salary Advance eligibility. |
| BR-012 | A matching `ACTIVE` or `OVERDUE` Salary Advance LoanAccount with positive contractual outstanding blocks new Salary Advance submission independently of overdue-evaluation freshness. |
| BR-013 | Disbursed unreleased Salary Advance principal contributes to used exposure; submitted unreleased applications contribute to reserved exposure. |
| BR-014 | Salary Advance calculation and refresh use the authoritative latest valid `COMPLETED` Partner Employee import batch for the current UTC effective month. Missing current-month evidence or a verified link/employee sourced from another batch is stale and fails closed until re-verification refreshes the link. |
| BR-015 | `SUSPENDED`, `DISABLED`, `STALE`, absent, or insufficient Salary Advance limit blocks normal creation and submission. |
| BR-016 | Every submitted Salary Advance application records its own verification snapshot even when the reusable link already exists. |
| BR-017 | Rejection, cancellation, Customer decline, offer expiry, or another approved pre-disbursement release frees the Salary Advance reservation exactly once in the same controlled operation as the application outcome. |
| BR-018 | Manual disbursement converts the Salary Advance reservation to used exposure when the LoanAccount is created. |
| BR-019 | Salary Advance used exposure is released only for principal allocated by an actual ordinary repayment or Administrative Full-Balance Settlement payment and by exactly that amount; fee and interest allocations release none. Any non-payment release requires an explicitly approved future product rule. |
| BR-020 | Unsecured Consumer Loan requires income and employment evidence and does not require collateral. |
| BR-020A | Unsecured Consumer Loan `VERIFIED` records completed manual evidence and basic repayment-capacity verification, not credit approval. The same Loan Officer may verify and recommend; the Approver remains a separate actor. |
| BR-020B | The common `BR-004` blocking-application rule applies to UCL. Separately, a matching `ACTIVE` or `OVERDUE` UCL `LoanAccount` with positive contractual outstanding blocks new UCL creation or correction resubmission; product-matching `SETTLED` or `CLOSED` accounts with zero outstanding do not block, unrelated products do not satisfy the UCL outstanding-account guard, and inconsistent account/status/outstanding evidence fails closed. |
| BR-021 | Collateral Loan requires one structured Collateral fact and the existing required ownership-evidence checklist item. |
| BR-021A | Collateral Loan `VERIFIED` records completed manual assessment sufficient for Loan Officer review and is not credit approval; the same Loan Officer may verify and recommend. |
| BR-021B | Completed Collateral verification cycles are immutable, the latest numbered cycle is authoritative, and a document correction must create a linked pending cycle and be re-verified before review. |
| BR-021C | Collateral correction may replace or review only the existing ownership-evidence checklist item; submitted structured Collateral facts and requested terms are not editable after submission. |
| BR-021D | Collateral `FAILED` is an unsuccessful application outcome and is not reopened; correctable evidence issues use `REQUIRES_MORE_INFORMATION`. |
| BR-021E | A Collateral Approver action requires the authoritative latest manual-verification cycle to remain `VERIFIED`; all four common Approver actions are available, and only `APPROVE` creates an offer. |
| BR-021F | Collateral approval preserves the submitted requested amount and term, applies 1.5% monthly flat original-principal interest, charges zero fee, rounds total interest once to whole VND using `HALF_UP`, and creates one reconciled provisional monthly item per approved month. |
| BR-021G | A Collateral offer contains no due dates; later schedule construction requires a first repayment date after the disbursement value date and no later than one calendar month after it, followed by monthly anchoring with final-calendar-day clipping. |
| BR-021H | Collateral activation, early or partial repayment, contractual payoff, Administrative Full-Balance Settlement, and administrative closure use the common controls, release zero product exposure, and create no Salary Advance movement; early or partial payment does not reprice, rebate, or mutate contractual obligations, and both payoff paths require the exact complete contractual outstanding. |
| BR-021I | The common `BR-004` blocking-application rule applies to Collateral Loan; an existing Collateral `LoanAccount` creates no additional product-specific origination restriction. |
| BR-022 | Collateral estimated value is advisory in the MVP and does not create an automated loan-to-value decision. |
| BR-023 | Upload completeness, manual document review and processing readiness, and product verification are separate controls. |
| BR-024 | Product policy defines which checklist items must be upload-complete before submission. |
| BR-025 | Contract readiness requires every required document item to be accepted, not required, or validly waived. |
| BR-026 | Missing, rejected, expired, or replacement-required evidence must route to the correct Customer or Staff task. |
| BR-027 | Loan Officer review and Approver decision are separate responsibilities. |
| BR-028 | One Staff actor cannot record both the recommendation and final decision for the same application. |
| BR-029 | Rejection, return, Staff cancellation, request-more-information, Staff correction, manual override, waiver, and other controlled exception actions require a reason where defined. |
| BR-030 | The authenticated Customer owner must accept valid approved terms before contract preparation and disbursement. |
| BR-031 | A pending approved offer expires when the current time reaches its generated time plus the configured positive calendar-day validity period; the default is seven days. |
| BR-032 | Approval and disbursement are separate responsibilities. |
| BR-033 | Disbursement may be confirmed only after offer acceptance, current-contract acknowledgment, document readiness, destination validity, and product-specific readiness. |
| BR-034 | A LoanAccount is created only through successful manual disbursement confirmation. |
| BR-035 | Application `DISBURSED`, LoanAccount creation, final schedule creation, LoanAccount `ACTIVE`, product exposure effects, history, and audit form one atomic activation outcome. |
| BR-036 | Permitted post-submission Customer bank-account changes are audited and must not rewrite an existing contract-bound destination. |
| BR-036A | Customer retains ownership of mutable source bank accounts; Loan retains only purpose-protected immutable snapshots needed for contract and disbursement history. |
| BR-037 | Repayment updates are manually entered or confirmed in the MVP. |
| BR-038 | A LoanAccount is `OVERDUE` while any unpaid obligation is past its due date. |
| BR-039 | Full contractual repayment or an authorized Administrative Full-Balance Settlement payment equal to the complete current contractual outstanding moves the LoanAccount to `SETTLED`. |
| BR-040 | Administrative closure may move only an eligible `SETTLED` LoanAccount to `CLOSED`; it is separate from financial settlement and cannot alter financial or LoanApplication evidence. |
| BR-041 | Every important transition and financial outcome records the required audit evidence. |
| BR-042 | MVP approval accepts the exact submitted amount and term; a change returns through review or correction and is not a counteroffer. |
| BR-043 | Each LoanApplication has at most one approved offer in the MVP; financial terms are immutable after generation. |
| BR-044 | Offer viewing and response derive Customer identity from authentication and verify ownership through the LoanApplication. |
| BR-045 | Viewing an offer is read-only; expiry is performed by System processing or a guarded state-changing action. |
| BR-046 | Repeating the same offer action is idempotent; contradictory terminal actions are conflicts. |
| BR-047 | Salary Advance approved principal equals submitted principal, and approved term equals the submitted allowed term of 1, 2, or 3 months. |
| BR-048 | Salary Advance total interest is `approvedPrincipal × 0.012 × approvedTermMonths`, rounded to whole VND using `HALF_UP`. |
| BR-049 | Salary Advance fees are zero and total repayment equals approved principal plus total interest. |
| BR-050 | Salary Advance generates one provisional repayment item per approved term month using `ON_SALARY_DATE` timing without exact calendar due dates. |
| BR-051 | Salary Advance provisional principal and interest are allocated in whole VND, remainders go to the final item, fee due is zero for every item, each item's total due equals its principal due plus interest due plus fee due, and all item sums reconcile exactly to the approved offer totals. |
| BR-052 | Salary Advance requested principal must be mathematically whole VND; scale-only trailing zeros are valid and non-zero fractional VND is rejected before financial persistence. |
| BR-053 | An operational contract copies accepted offer terms and provisional items exactly and does not treat mutable Customer data as historical financial authority. |
| BR-054 | Contract acknowledgment is immutable evidence for the exact current version and is not an electronic signature, digital signature, or legal execution. |
| BR-055 | Contract regeneration before readiness is allowed only for `DISBURSEMENT_ACCOUNT_REFRESH`; it supersedes the current version and requires fresh acknowledgment. |
| BR-056 | The full destination account number remains protected at rest and is excluded from ordinary APIs, logs, audits, errors, and history. It may be returned only through the dedicated authorized, audited, non-cacheable disbursement reveal operation. |
| BR-057 | Readiness confirmation recomputes blockers and atomically marks the contract ready and application `DISBURSEMENT_PENDING` with PII-safe history and audit. |
| BR-058 | Readiness confirmation does not transfer funds, create the LoanAccount or final schedule, or convert Salary Advance reservation to used exposure. |
| BR-059 | The final repayment schedule is immutable after activation; servicing progress and allocations do not rewrite contractual amounts or due dates. |
| BR-060 | Repayment allocation orders installments by due date and number and components by `FEE`, then `INTEREST`, then `PRINCIPAL`. |
| BR-061 | A payment greater than total contractual outstanding is rejected as one whole operation. |
| BR-062 | Identical repayment replay returns the original result; reused request or payment identity with different content is a conflict. |
| BR-063 | Full contractual payoff atomically settles the LoanAccount and completes the required product-specific principal-exposure release. |
| BR-064 | Read operations do not evaluate overdue state, alter servicing results, or publish new business evidence. |
| BR-065 | UCL repayment and Administrative Full-Balance Settlement may allocate contractual principal but release zero product exposure and create no Salary Advance movement. |
| BR-066 | UCL supports date-driven `ACTIVE` and `OVERDUE` servicing, exact contractual payoff or Administrative Full-Balance Settlement to `SETTLED`, and separate administrative closure to `CLOSED`. |

---

## 11. MVP Product Configuration

These values define Meridian's portfolio and test configuration. They do not represent real financial products or financial advice.

### 11.1 Product Catalog Values

| Product Code | Product Name | Product Type | Active | Minimum | Maximum | Allowed Terms | Display Rate | Repayment Method | Offer Validity |
|---|---|---|---|---:|---:|---|---|---|---|
| `SALARY_ADVANCE` | Salary Advance | `SALARY_BASED` | Yes | 500,000 VND | 20,000,000 VND | 1, 2, 3 months | 1.2% per month | `ON_SALARY_DATE` | 7 calendar days |
| `UNSECURED_CONSUMER_LOAN` | Unsecured Consumer Loan | `UNSECURED` | Yes | 2,000,000 VND | 50,000,000 VND | 3, 6, 9, 12 months | 1.8% per month | Monthly installment | 7 calendar days |
| `COLLATERAL_LOAN` | Collateral Loan | `SECURED` | Yes | 5,000,000 VND | 100,000,000 VND | 6, 12, 18, 24 months | 1.5% per month | Monthly installment | 7 calendar days |

For Salary Advance, the term is both the approved month count and the number of provisional repayment items.

The UCL and Collateral Loan rates and installment rules are executable business policies as defined in Sections 11.3 and 11.5.

### 11.2 Salary Advance Policy Values

| Policy Item | Value |
|---|---|
| Partner Company policy limit | 20,000,000 VND |
| Salary percentage cap | 40% of monthly salary |
| Employee-configured limit | Imported from the eligible Partner Employee record |
| Total-limit rule | Minimum of product, Partner, employee, and salary-cap limits |
| Used-exposure rule | Disbursed unreleased principal contributes to used amount |
| Reserved-exposure rule | Submitted unreleased applications contribute to reserved amount; drafts do not |
| Blocking-debt rule | Matching `ACTIVE` or `OVERDUE` debt with positive contractual outstanding blocks submission |
| Freshness rule | Use the authoritative latest valid `COMPLETED` import batch for the current UTC effective month; stale/non-current link evidence fails closed until re-verification |
| Limit statuses | `ACTIVE`, `SUSPENDED`, `DISABLED`, `STALE` |
| Manual-review rule | `NOT_FOUND` and `MULTIPLE_MATCHES` may be reviewed; inactive Partner evidence cannot be overridden |
| Interest method | Flat interest on original principal |
| Monthly interest rate | 1.2% |
| Fee | 0 VND |
| Repayment method | `ON_SALARY_DATE` |
| Offer validity | 7 calendar days |

Salary Advance pricing:

```text
approvedPrincipal = submitted requested amount
approvedTermMonths = submitted requested term
unroundedTotalInterest = approvedPrincipal × 0.012 × approvedTermMonths
totalInterest = round(unroundedTotalInterest, whole VND, HALF_UP)
feeAmount = 0 VND
totalRepaymentAmount = approvedPrincipal + totalInterest
```

Salary Advance uses flat original-principal interest, not declining-balance interest.

Provisional repayment items:

- one item per approved term month;
- first, second, and third salary-cycle timing as applicable;
- no exact calendar due date at offer time;
- whole-VND principal and interest allocation;
- remainder assigned to the final item;
- zero fee for every item;
- each item's total due equals its principal due plus interest due plus fee due.

Reconciliation:

```text
sum(item.principalDue) = approvedPrincipal
sum(item.interestDue) = totalInterest
sum(item.feeDue) = 0 VND
sum(item.totalDue) = totalRepaymentAmount
```

### 11.3 Unsecured Consumer Loan Policy Values

UCL uses exact-request approval. The approved principal and term equal the submitted requested amount and term; the Approver cannot create a counteroffer or edit either value. Supported terms are exactly 3, 6, 9, and 12 months.

The active default policy uses `FLAT_ORIGINAL_PRINCIPAL` interest at a monthly rate of `0.018000`, zero fee, `MONTHLY_INSTALLMENT` repayment, and seven-calendar-day offer validity. Pricing is:

```text
approvedPrincipal = submitted requested amount
approvedTermMonths = submitted requested term
unroundedTotalInterest = approvedPrincipal × 0.018 × approvedTermMonths
totalInterest = round(unroundedTotalInterest, whole VND, HALF_UP)
feeAmount = 0 VND
totalRepaymentAmount = approvedPrincipal + totalInterest
```

The immutable offer contains one provisional repayment item per approved term month and no calendar due dates. Principal and total interest are each divided into equal whole-VND base portions; any remainder is assigned only to the final item. Every item has zero fee, and item totals must reconcile exactly to the offer principal, interest, fee, and total repayment.

An accepted UCL offer is the exact financial authority for its operational contract. Contract preparation copies the accepted principal, term, pricing method, monthly rate, fee, repayment method, totals, and provisional items without repricing or recomputation. It captures the Customer's current eligible primary active bank account through the common purpose-protected contract destination mechanism. A contract may be regenerated only for `DISBURSEMENT_ACCOUNT_REFRESH`; regeneration supersedes the prior version, preserves every financial term and item, captures the newly eligible destination, and requires fresh Customer acknowledgment.

UCL contract readiness requires the accepted offer, acknowledgment of the exact current contract, processing-ready documents, no active correction, an active Customer, a valid captured destination, and the authoritative latest application-owned UCL verification cycle in `VERIFIED`. UCL readiness and activation do not read, reserve, convert, release, or create Salary Advance limit or movement evidence. Readiness confirmation moves the contract to `READY_FOR_DISBURSEMENT` and the application to `DISBURSEMENT_PENDING` without activating a LoanAccount.

At later manual disbursement, the controlled `firstRepaymentDate` must be after the disbursement value date and no later than one calendar month after it. The first installment uses that date. Later installments use its day-of-month as the monthly anchor; when a month does not contain that day, its final calendar day applies.

Manual-disbursement confirmation atomically creates the UCL LoanAccount, immutable disbursement evidence, one authoritative final monthly schedule, initial installment progress and histories, and the `DISBURSED` application transition. The final schedule copies the contract amounts and item sequence exactly and adds only the controlled calendar dates. UCL activation has no product-exposure effect.

For example, a January 30 first repayment date produces January 30, February 28 or 29, March 30, April 30, and the same anchored sequence thereafter.

Early or partial payment does not reprice the loan, rebate future interest, regenerate the schedule, mutate contractual due dates, or reduce contractual interest. Allocation follows the common `FEE → INTEREST → PRINCIPAL` order. Date-driven evaluation moves a UCL LoanAccount between `ACTIVE` and `OVERDUE`, and payment may cure an overdue account. Full contractual payoff requires the complete contractual outstanding and moves the account to `SETTLED`. Administrative Full-Balance Settlement is backed by payment of that exact complete outstanding and is not a discount, concession, forgiveness, waiver, or repricing event. UCL repayment and Administrative Full-Balance Settlement report contractual principal allocation while releasing zero product exposure and creating no Salary Advance movement. A fully reconciled `SETTLED` account may then be closed administratively without changing the `DISBURSED` LoanApplication.

### 11.4 LoanApplication Checklist Evidence by Product

This table defines product-specific LoanApplication checklist evidence. Customer readiness in Section 6.1 and the contract-bound destination in Sections 6.8 and 6.9 remain separate controls.

| Product | Checklist Evidence |
|---|---|
| `SALARY_ADVANCE` | No Customer document is required at initial submission; a correction may require `RECENT_PAYSLIP` |
| `UNSECURED_CONSUMER_LOAN` | `INCOME_PROOF`, `BANK_STATEMENT`, and `EMPLOYMENT_PROOF` |
| `COLLATERAL_LOAN` | `COLLATERAL_OWNERSHIP_EVIDENCE`; supporting photos or additional supporting documents require an explicit product-policy decision |

Product policy determines which evidence must exist before submission and which may be introduced through correction.

### 11.5 Collateral Loan Policy Values

| Policy Item | Value |
|---|---|
| Supported types | `MOTORBIKE`, `CAR`, `ELECTRONICS`, `PROPERTY_DOCUMENT`, `OTHER` |
| Estimated value | Informational for manual assessment |
| Automated loan-to-value validation | Not enforced |
| Decision model | Manual verification followed by Loan Officer review and independent approval; the latest Collateral verification must remain `VERIFIED` when the Approver acts |
| Verification assessment note | Required for every manual-verification outcome |
| Approval basis | Exact submitted requested amount and term; no counteroffer or loan-to-value adjustment |
| Interest method | Flat interest on original principal |
| Monthly interest rate | 1.5% (`0.015000`) |
| Fee | 0 VND |
| Repayment method | Monthly installment |
| Offer validity | 7 calendar days |

Collateral Loan pricing:

```text
approvedPrincipal = submitted requested amount
approvedTermMonths = submitted requested term
unroundedTotalInterest = approvedPrincipal × 0.015 × approvedTermMonths
totalInterest = round(unroundedTotalInterest, whole VND, HALF_UP)
feeAmount = 0 VND
totalRepaymentAmount = approvedPrincipal + totalInterest
```

The immutable offer contains one provisional repayment item per approved term month and no calendar due dates. Principal and total interest are each divided into equal whole-VND base portions; any remainder is assigned only to the final item. Every item has zero fee, and the items reconcile exactly to the offer totals.

At later manual disbursement, the controlled first repayment date must be after the disbursement value date and no later than one calendar month after it. The first installment uses that date. Later installments use its day-of-month as the monthly anchor, with final-calendar-day clipping when a month does not contain that day.

Early or partial payment does not reprice the loan, rebate future interest, regenerate the schedule, mutate contractual due dates, or reduce contractual interest. Full contractual payoff and Administrative Full-Balance Settlement require the exact complete contractual outstanding. Collateral Loan introduces no product-specific activation or closure control beyond the common lifecycle.

### 11.6 Offer Validity

The default offer-validity period is seven calendar days and is configurable by product.

```text
expiresAt = generatedAt + offerValidityDays calendar days
```

A pending offer expires when the current time is at or after `expiresAt`.

Expiry processing must eventually find overdue pending offers. A guarded Customer action also checks expiry before accepting or declining, so an expired offer cannot be accepted between processing runs.

Viewing an offer remains read-only and does not expire it, transition the application, release exposure, or create audit or financial effects.

---

## 12. Business Quality Requirements

Sections 5 through 8 define Meridian's business concepts and required evidence. `MER-DB-001` owns the high-level logical data model; Flyway owns the executable physical schema, `MER-DB-CURRENT-SCHEMA.sql` is its current human-readable snapshot, and the architecture documents own context placement and communication.

| Category | Requirement |
|---|---|
| Security | Protected actions require authenticated identity and narrow permissions; Customer-owned operations also enforce ownership |
| Privacy | APIs, logs, errors, audit, and history disclose only the minimum purpose-authorized personal, employment, financial, collateral, and document data |
| Integrity | Status, offer, contract, disbursement, schedule, payment, and exposure rules must preserve their stated invariants |
| Atomicity | A business outcome that combines workflow, financial, document, exposure, history, or audit effects must not commit partially |
| Idempotency | Retried offer, contract, disbursement, correction, and repayment commands must not duplicate business effects |
| Concurrency | Competing requests must preserve one blocking application, one active workflow outcome, one disbursement, and consistent financial evidence |
| Auditability | Important actions are attributable to actor, time, business reference, reason, and operation |
| Availability | Failure of an external provider or worker must not corrupt business state; any permitted manual fallback remains visible and controlled |

---

## 13. MVP Boundaries and Controlled Future Decisions

### 13.1 Required MVP Business Capabilities

The MVP business target includes every capability defined as in scope in Section 3.3 and every requirement in Sections 9 and 10.

Salary Advance receives full product depth. UCL and Collateral Loan retain the same common lifecycle but use streamlined product-specific verification and manual review.

### 13.2 Optional Enhancements

The following capabilities may be added without changing the core lending lifecycle:

- Customer application-history and Staff queue dashboards beyond the minimum operational views;
- notification delivery;
- OCR-assisted document extraction;
- lightweight analytics;
- a mobile client after Customer Web is stable.

OCR remains advisory to Document review. Notification remains observational and does not own workflow decisions.

### 13.3 Excluded from the MVP

- real bank-transfer, payment-gateway, payroll, employer-API, HR-synchronization, or bank-reconciliation integrations;
- real SMS OTP, biometric authentication, or production identity verification;
- credit-bureau integration, automated credit scoring, or fully automated approval;
- full collateral valuation, custody, registration, notarization, repossession, liquidation, or legal enforcement;
- double-entry ledger, unapplied cash, suspense, reversal, refund, write-off, or production accounting;
- production compliance case management;
- full electronic signature or legal agreement-execution platform;
- microservice deployment requirements;
- full mobile delivery;
- savings, entrusted, corporate, or other non-lending products.

### 13.4 Collateral Loan Boundary Guard

Section 11.5 is the authority for Collateral Loan pricing, provisional installments, final schedule dates, and payment behavior. Those policies do not introduce automated loan-to-value assessment, a counteroffer, discounted settlement, custody, valuation, enforcement, more than one Collateral asset, or an outstanding Collateral LoanAccount origination restriction. Each capability requires a separately approved business policy before it enters the MVP boundary.

### 13.5 Post-MVP Product Extensions

Post-MVP product work may introduce multi-level approval, automated repayment simulation, simple collateral loan-to-value validation, full electronic signature, and external credit-bureau, payment, employer, payroll, bank-transfer, and asset-registry integrations. Each capability requires an approved business specification before it enters Meridian's executable product contract.

---

## 14. Design Principles

1. Use one common lending lifecycle for every supported product.
2. Keep product variation explicit in product policies.
3. Treat Salary Advance as the flagship and deepest MVP product.
4. Keep UCL and Collateral Loan streamlined but complete at the common-lifecycle level.
5. Preserve clear ownership among Customer, Partner, Loan, Approval, Document, Audit, and Notification.
6. Separate upload completeness, manual document review, processing readiness, product verification, review recommendation, and approval decision.
7. Separate approval, Customer acceptance, contract readiness, and disbursement.
8. Preserve immutable historical financial terms, contract versions, schedules, payments, allocations, and audit evidence.
9. Prefer manual review and operational confirmation over unsupported automation.
10. Keep real integrations and production banking features outside the MVP.
11. Make retries, concurrency, and failure behavior part of the business rule rather than an implementation afterthought.
12. Keep Customer-facing steps understandable without weakening business control.
