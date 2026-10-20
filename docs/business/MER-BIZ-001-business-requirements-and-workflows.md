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
| Scope | MVP multi-product lending platform centered on digital Salary Advance, with digital and Staff-assisted paper origination for Unsecured Consumer Loan and Collateral Loan |

---

## 2. Purpose, Authority, and Interpretation

This document is Meridian's primary business authority. It defines product scope, actors, permissions, business concepts, common lifecycle rules, product-specific workflows, status transitions, functional requirements, business rules, configuration values, and MVP boundaries.

Source code, migrations, configuration, and executable tests are authoritative for current executable behavior. This document is authoritative for intended business behavior. A conflict between them is a specification or implementation defect to resolve; neither source silently replaces the other.

Detailed database structures, API schemas, package placement, runtime lock mechanics, and deployment design belong to their owning database, API, architecture, and deployment documents unless a technical constraint is necessary to preserve a business outcome.

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
| Backend | Business rules, security, persistence, workflow control, product-policy selection, channel enforcement, and audit evidence |
| Customer Web | Customer-direct registration, profile completion, product selection, application submission, document upload, offer response, contract acknowledgment, and status tracking |
| Internal Web | Permission-scoped internal capabilities: Staff Web for Staff-assisted UCL and Collateral Loan intake and origination and the common lending operations in Section 6; Back-Office Administration for the bounded capabilities in Section 3.6; purpose-limited audit evidence within the relevant workflow |
| Mobile App | Outside the MVP |

Customer Web and Internal Web use the same backend and database.

Loan origination is channel-scoped by product:

| Product | `CUSTOMER_DIGITAL` | `STAFF_ASSISTED` |
|---|---|---|
| `SALARY_ADVANCE` | Allowed | Not allowed |
| `UNSECURED_CONSUMER_LOAN` | Allowed | Allowed |
| `COLLATERAL_LOAN` | Allowed | Allowed |

`CUSTOMER_DIGITAL` means the authenticated Customer operates Customer Web directly. `STAFF_ASSISTED` means the Customer supplies information, paper evidence, and required decisions through branch interaction while an authorized Staff user operates Meridian as the authenticated actor. A Staff-assisted Customer does not require a Customer Web login, and Meridian does not require a Staff-assisted application to switch to the Customer-digital channel later in its lifecycle.

### 3.3 MVP In Scope

- Customer business records, profile completion, controlled profile changes, and bank-account management, with Customer Web registration and authentication for digital Customers and no login requirement for Staff-assisted Customers.
- Staff authentication, role-based permissions, internal-user administration within Section 3.6, and actor traceability.
- Staff-assisted paper intake and origination for Unsecured Consumer Loan and Collateral Loan, including Customer selection or creation, paper evidence capture, Customer-sourced correction, and recorded Customer decisions without Customer impersonation.
- Loan-product catalog and selection, with administration limited to activation, deactivation, and minimum/maximum amounts under Section 3.6.
- One controlled LoanApplication lifecycle shared by all supported products.
- Salary Advance Partner Company setup, monthly Partner Employee imports, reusable employee verification, limit calculation, exposure reservation, disbursement conversion, and repayment release.
- Streamlined Unsecured Consumer Loan and Collateral Loan workflows.
- Document checklist creation, upload completeness, immutable versions, manual review, replacement, waiver, correction, and processing readiness, with advisory OCR-assisted intake and explicit application of reviewed suggestions under Section 5.10.
- Identity/security email for the four purposes in Section 3.7.
- Loan Officer review, Approver decision, maker-checker separation, approved offers, Customer response, contract readiness, manual disbursement, LoanAccount activation, repayment, overdue servicing, contractual payoff, Administrative Full-Balance Settlement, administrative closure, and audit.

### 3.4 MVP Out of Scope

The MVP excludes real financial and payroll integrations, production compliance operations, automated credit scoring, a full collateral valuation or enforcement platform, a financial ledger, production mobile delivery, and non-lending product families. Section 13 defines the complete boundary.

### 3.5 Architecture Boundary

Meridian is delivered as a modular-monolith backend with one database and multiple frontends. Business ownership and context collaboration are defined in `MER-ARCH-001`; source structure and dependencies are defined in `MER-ARCH-002` and `MER-ARCH-003`.

### 3.6 MVP Administration Scope

Back-Office Administration is limited to:

- Partner Company administration;
- Partner Employee imports, current roster, and import history;
- Partner eligibility review;
- Loan Product activation/deactivation and minimum/maximum amount changes;
- Internal User discovery, creation, status management, and password-setup-link recovery;
- assignment and removal of predefined backend-owned roles.

MVP Product administration does not edit product codes, types, names, descriptions, or policy definitions. Pricing algorithms, terms, repayment rules, exposure rules, document checklists, and workflow policies remain backend-owned business policy. Role assignment uses predefined role bundles; exact permissions authorize each action. Administration does not grant general Customer access or ownership of lending decisions. Section 13.3 excludes generic administration products.

### 3.7 Identity and Security Email

The MVP includes controlled email for four Identity purposes:

| Purpose | Business Outcome |
|---|---|
| Customer registration/email verification | The Customer confirms control of the registration email before authenticated Customer Web access; email confirmation does not verify Customer business identity |
| Password reset | An eligible Customer or Staff user chooses a replacement password through the controlled recovery flow |
| Staff password setup | A newly created Staff user chooses their own initial password; the administrator receives no credential |
| Existing-Customer digital-access activation invitation | The Customer confirms the activation email and chooses their first password for the existing Customer record under `BR-074` |

Identity owns account, verification, credential, and recovery state. Notification renders and delivers these emails after the Identity outcome commits. Delivery failure must not reverse that outcome; supported resend or recovery actions provide a new delivery attempt. Notification does not decide lending transitions. Broader notification-platform scope is excluded in Section 13.3.

---

## 4. Actors and Permission Rules

### 4.1 Actors

| Actor | Business Responsibilities |
|---|---|
| Customer | Maintains their own profile and bank accounts, selects products, verifies Salary Advance employment, and provides application facts, corrections, documents, offer decisions, and contract acknowledgments. In the Customer-digital channel, the Customer records those actions directly and views their own application and LoanAccount state. In the Staff-assisted channel, the Customer supplies UCL or Collateral Loan information, evidence, and decisions through branch interaction and Staff records the evidenced action. |
| Loan Officer | Performs authorized Staff-assisted UCL and Collateral Loan intake and origination, reviews application facts and documents, records recommendations, coordinates Customer-sourced branch corrections, requests Staff correction, and performs authorized document review or waiver actions |
| Approver | Records the independent final application decision, returns an application to review, requests structured correction, and performs authorized Loan-owned Administrative Full-Balance Settlement |
| Accounting Officer | Prepares operational contracts, confirms readiness, reveals a protected destination only for disbursement, confirms the external transfer, records authorized repayment updates, and closes eligible settled LoanAccounts |
| Back-Office Admin | Performs the concrete administrative capabilities in Section 3.6 with the required permissions |
| System | Applies validations, status transitions, calculations, expiry, overdue evaluation, idempotency, and audit rules |

### 4.2 Role and Action Matrix

Role names describe operational responsibilities. Exact permissions and the owning capability authorize actions; a role label alone does not grant Customer access or business authority.

| Action | Customer | Loan Officer | Approver | Accounting Officer | Back-Office Admin | System |
|---|---|---|---|---|---|---|
| Register and authenticate to Customer Web | Own account | No | No | No | No | Validate |
| Authenticate to Internal Web | No | Yes | Yes | Yes | Yes | Validate |
| Maintain Customer profile and bank accounts | Own profile and bank accounts | Staff-assisted selected Customer when authorized; otherwise view or review | No profile or bank-account access through decision authority | View purpose-limited destination facts | No through administration authority | Validate and audit |
| Manage product activation and amount limits | No | No | No | No | Yes under Section 3.6 | Enforce active policy |
| Manage Partner Companies and imports | No | No | No | No | Yes | Validate and store |
| Perform Staff-assisted Customer intake | No | UCL and Collateral Loan when authorized | No | No | No | Validate and audit |
| Originate, submit, or cancel application | Own application under allowed rules | Staff-assisted UCL and Collateral Loan under allowed rules; Customer-requested cancellation only where product policy permits | No | No | No | Validate and transition |
| Upload Customer-required documents | Own authorized checklist items | Upload or replace Staff-assisted intake and application evidence when authorized | No | No | No through administration authority | Store and version |
| Review or waive documents | No | Yes with required authority | View | Confirm readiness facts | No through administration authority | Calculate readiness |
| Record recommendation | No | Yes | No | No | No | Validate and audit |
| Record final approval decision | No | No | Yes | No | No | Validate and audit |
| Respond to approved offer | Own application | Record evidenced Customer response for Staff-assisted UCL and Collateral Loan when authorized | No | No | No | Validate and transition |
| Prepare or confirm contract readiness | Acknowledge own contract | Record evidenced Customer acknowledgment for Staff-assisted UCL and Collateral Loan when authorized; otherwise view | View | Yes | No | Validate and transition |
| Reveal full disbursement destination | No | No | No | Yes with `loan:disburse` | No | Authorize and audit |
| Confirm manual disbursement | No | No | No | Yes | No | Activate account atomically |
| Record repayment | No | No | No | Yes | No | Allocate and update servicing |
| Approve and apply Administrative Full-Balance Settlement | No | No | Yes | No | No | Verify exact outstanding, apply payment, and settle |
| Close an eligible settled LoanAccount | No | No | No | Yes | No | Verify evidence and close administratively |
| View audit evidence | No | Authorized | Authorized | Authorized | Authorized | Record |

Authentication and permission are necessary but not sufficient. The owning business capability also verifies resource ownership, status, maker-checker separation, and every applicable business rule. All Staff business actions record the authenticated Staff actor and the affected business subject. Staff-assisted processing never authenticates, impersonates, or acts as the Customer.

### 4.3 Customer Operational Context

Customer operational identity and contact access follows the lending task:

| Staff role | Operational Customer context |
|---|---|
| Loan Officer | Customer name, Customer number, and current phone in the authorized application case; masked Identity Reference and explicit protected reveal with dedicated authority |
| Approver | Customer name and Customer number in the independent decision case and in LoanAccount servicing with Administrative Full-Balance Settlement authority; no phone or Identity Reference |
| Accounting Officer | Customer name, Customer number, and current phone in contract and disbursement cases and in LoanAccount servicing with repayment or administrative-closure authority; no Identity Reference |

An authorized Loan Officer may view the masked Customer Identity Reference and explicitly reveal the full stored reference when needed for the exact application case. Reveal requires dedicated authority and records a successful-access audit containing only safe Customer and application identifiers. Approver, Accounting Officer, Back-Office Admin, and Customer receive no reveal capability. Identity-verification status does not authorize or prohibit this access. The identity-verification review remains a separate evidence-confirmation workflow: its reviewer re-enters the document reference, and it does not expose the stored value.

Customer owns the mutable name and phone. Operational context must come from the Customer associated with the exact LoanApplication and, for servicing, its exact LoanAccount. The phone is current contact information, not immutable contract, application, or servicing evidence. Missing or inconsistent required Customer context must block the purpose-specific read rather than present partial or inferred identity.

The generic Application Case retains its separate `customer:read` boundary. Approver and Accounting workflow authority does not grant contact in that generic case. Staff already entitled through `customer:read` may view current Customer number, name, and phone in LoanAccount servicing. Generic `loan:read` alone grants no Customer context, and a role name without the applicable operational authority is insufficient. Servicing Customer context is informational and does not affect financial calculations, repayment, settlement, or administrative closure.

Workflow authority permits only its purpose-specific context; it does not grant a general Customer profile, Customer login identity, or bank-account access. Back-Office administration receives no generic lending Customer context. Separately authorized destination operations retain their own purpose boundary.

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

A `LoanApplication` represents one Customer request for one selected product. MVP origination creates it directly at submission; persisted Staff-assisted intake is a separate pre-application workflow under Section 5.10. Persistent save-and-return application drafts are outside the MVP.

The application preserves:

- Customer and product identity;
- origination channel;
- requested amount and term;
- product-specific facts and verification evidence;
- checklist and correction state;
- review, recommendation, and decision references;
- offer, contract, and disbursement lifecycle state;
- status history and audit correlation.

`LoanApplication` governs origination through disbursement. Its origination channel is immutable. `CUSTOMER_DIGITAL` and `STAFF_ASSISTED` applications enter the same product lifecycle after submission; the channel changes who records Customer-sourced actions, not the financial or approval authority. Enabling Customer Web access later does not rewrite historical Staff-assisted channels or actions. `LoanApplication` is not the source of truth for post-disbursement balances or servicing state.

### 5.3 LoanAccount

A `LoanAccount` is created only when an approved, accepted, and ready application is confirmed as disbursed.

Section 6.9 defines the atomic activation outcome and its failure guarantees.

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
| Salary Advance Limit | What are the total, used, reserved, and available limit amounts? |
| Application Verification Snapshot | Which employee and limit facts supported this application at submission? |

### 5.6 Approved Offer, Operational Contract, and Disbursement

An approved offer is the immutable Customer-facing financial-terms snapshot generated after approval.

An operational Loan Contract copies the accepted offer terms and binds the disbursement destination. It is versioned operational evidence, not a PDF, uploaded agreement, electronic signature, digital signature, or legal execution of a contract. Paper evidence used to record a Staff-assisted Customer acknowledgment supports the acknowledgment action but does not replace the operational Loan Contract or turn it into a legal-execution artifact.

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

Upload completeness does not imply acceptance. A Customer-sourced correction and a Staff correction are distinct: the former requires new information or evidence from the Customer, while the latter corrects Staff-controlled work. For a Staff-assisted application, authorized Staff may coordinate and record a Customer-sourced correction, but the Customer remains the source of the corrected information or evidence. OCR output remains advisory evidence; Staff confirmation of extracted data does not by itself establish Customer verification, document acceptance, product verification, or approval.

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

Customer owns the mutable source profile and bank-account data. A Customer business record may exist without a Customer Web User or login. Sensitive identity and bank-account values must remain protected. An incomplete profile without verified identity permits Identity Reference entry or change through normal completion. After profile completion, an active Customer whose identity is not `VERIFIED` may correct it only through a controlled Customer command or authorized Staff-assisted Customer maintenance. Ordinary profile updates cannot change the completed profile reference, and correction is prohibited while identity is `VERIFIED`. A successful correction preserves profile completion, returns the verification summary to `UNVERIFIED`, and does not establish verified identity. Bank-account identity is replaced rather than edited in place. Permitted changes are audited and must not rewrite historical contract or disbursement evidence.

For Staff-assisted intake, Staff records Customer-provided profile, bank-account, and consent facts as the authenticated actor. Consent belongs to the Customer and must be supported by the signed paper loan application; Meridian does not treat Staff confirmation as Customer consent and does not require a separate consent document for that application.

### 5.9.1 Customer Identity Verification

Customer identity verification belongs to the Customer and is reusable for later loans. Profile completion, email confirmation, document upload, OCR extraction, and OCR transcription review do not establish identity verification.

An active Customer with a complete identity-bearing profile may submit one identity-document file for manual review before bank-account setup or any loan application. Document owns the file and immutable versions. Customer owns each ordered verification attempt, its exact evidence reference, source, manual-review method, outcome, and submission/reviewer times. Digital evidence is Customer-level. Staff-assisted verification references the exact `CUSTOMER_IDENTITY` intake version associated with the case-selected Customer; its bytes are not copied into another store or application checklist.

| Action | Attempt outcome | Customer summary |
|---|---|---|
| Submit evidence | `PENDING_REVIEW` | `UNVERIFIED` |
| Authorized Loan Officer verifies matching identity | `VERIFIED` | `VERIFIED` |
| Authorized Loan Officer rejects with a controlled reason | `REJECTED` | `REJECTED` |
| Submit replacement evidence | New `PENDING_REVIEW`; earlier pending attempt becomes `SUPERSEDED` | `UNVERIFIED` |

Completed decisions remain immutable. Replacement preserves earlier attempts and exact versions. Correcting a wrong profile reference preserves the existing pending attempt and exact evidence; an authorized reviewer may retry a mismatch against that same document only while it remains current and all full-name and review guards still hold. A reviewer must inspect the exact current evidence, attest that it supports the Customer identity, and re-enter the Identity Reference presented on that document. Customer compares its protected fingerprint without revealing the stored reference. Rejection uses only identity-reference mismatch, name mismatch, unreadable evidence, or unacceptable evidence. Competing decisions produce one terminal outcome; exact replay produces no duplicate effects.

Before identity verification, full name may be corrected through ordinary Customer or authorized Staff profile maintenance. Once Customer identity is `VERIFIED`, ordinary maintenance must not change full name or Identity Reference; controlled pre-verification reference correction is also prohibited. A rejected change preserves the verified summary, current readiness, successful verification reference, profile, and audit/history. Phone, residential address, employment status, employer name, and bank-account changes do not affect verified identity. Controlled Identity Reference correction under Section 5.9 repairs a pre-verification mistake only. Verified legal-name or identity amendments require a separate purpose-specific Staff-controlled workflow with fresh evidence; that workflow is outside the MVP. Earlier verification, Partner evidence, and LoanApplication provenance remain unchanged.

New Salary Advance, UCL, and Collateral origination requires a verified Customer with current successful verification evidence. Each product retains its permitted origination channels. An existing verified Customer may reuse that result without another identity document solely because another loan is requested. Each new application preserves the successful identity-verification result as immutable provenance. Salary Advance additionally requires its separate Partner Employee verification after Customer identity verification. Automatic employment verification and manual approval require an active Customer with a complete profile and verified identity. Current employment eligibility must match the current verified Customer identity to the identity recorded when the Partner relationship was verified. A mismatch requires employment verification again and must not rewrite earlier Partner evidence. Applications already created before this requirement continue through their existing downstream lifecycle without a new Customer identity gate.

Manual Staff document review is an MVP identity foundation. It does not authenticate document genuineness independently and makes no regulatory-compliance or production eKYC claim. Biometrics, face matching, liveness, NFC, authoritative-government checks, external eKYC providers, screening, and risk scoring are outside this workflow.

### 5.10 Staff-Assisted Intake

Staff-assisted intake is the persisted pre-application branch workflow that converts Customer-provided paper information and evidence into structured Meridian data before a UCL or Collateral Loan `LoanApplication` exists. It is not a loan decision, does not create financial exposure, and does not enter product verification, review, or approval.

Authorized Staff must select an existing Customer or create a new Customer before application submission. When intake creates a new Customer, Customer creation and the required identity-bearing profile must succeed as one business outcome; a duplicate protected identity must not leave a separate incomplete Customer created solely by the failed attempt.

Staff-assisted intake may capture the signed paper loan application and identity-document evidence before `LoanApplication` creation. Staff confirms the structured Customer and loan facts before Meridian applies them to Customer or Loan. Document owns OCR results and their review. After explicit Staff selection, finalized reviewed suggestions may populate intake inputs; Staff confirms or corrects them before invoking the owning Customer or Loan action. Applying a suggestion does not itself submit a business command or establish consent, identity verification, document acceptance, product verification, recommendation, approval, offer response, or contract acknowledgment. Manual intake remains valid without OCR and no OCR proposal is authoritative.

---

## 6. Common Loan Lifecycle and Workflow Controls

All products use the same lifecycle authority but may enter document and verification stages differently according to product policy.

| Stage | Business Outcome |
|---|---|
| Readiness and selection | Customer, channel, and product are eligible to begin |
| Intake and submission | Any Staff-assisted paper intake is confirmed and direct origination creates a controlled LoanApplication |
| Product verification | Product-specific facts are recorded and evaluated |
| Documents and correction | Required evidence becomes complete and processing-ready |
| Loan Officer review | A recommendation or correction outcome is recorded |
| Approval | An independent decision is recorded |
| Customer offer response | The Customer accepts, declines, or allows the offer to expire |
| Contract readiness | Accepted terms and destination are acknowledged and confirmed ready |
| Disbursement and activation | External transfer evidence creates the LoanAccount and final schedule |
| Servicing | Repayment, overdue state, contractual payoff, Administrative Full-Balance Settlement, and administrative closure are tracked |

### 6.1 Pre-Submission Readiness and Guards

**Customer readiness.** Before submission, the Customer must be active, the required profile must be complete, required consent must be satisfied, and the selected product's bank-account readiness requirement must be satisfied. Customer profile completeness, Customer identity verification, and bank-account readiness are separate controls. New submissions for all three products additionally require the current successful Customer verification defined in Section 5.9.1. A Staff-assisted Customer does not need a Customer Web login to satisfy Customer readiness.

Submission evaluates the Customer's current authoritative profile and eligible bank-account facts. For Staff-assisted origination, consent may be recorded only from Customer-provided signed application evidence as defined in Section 5.9. Section 5.9 defines Customer source-data ownership and mutation rules.

**Common submission guards.** Before submission, Loan validates:

- active Customer and complete required profile;
- active product;
- valid requested amount and term;
- required product-specific facts;
- submission-level document requirements;
- no blocking non-terminal `LoanApplication` for the same product, as defined by `BR-004`;
- product-specific prerequisites required before submission;
- applicable concurrency guards and product-specific exposure or financial guards.

In the product workflows, the `common blocking-application rule` refers only to the same-product `LoanApplication` restriction defined by `BR-004`, not to every common submission guard.

**Product-specific pre-submission requirements.** Salary Advance requires an active verified Customer–Partner Employee link, current and eligible Partner evidence, and a valid and sufficient Salary Advance limit. Employee eligibility remains a separate Partner and Salary Advance prerequisite in addition to the common Customer identity requirement. Section 6.2 defines the application verification snapshot recorded when Salary Advance submission succeeds.

UCL and Collateral do not require completion of their application-specific manual verification before submission. Successful submission creates the pending manual-verification cycle; Section 6.3 defines the later verification lifecycle.

### 6.2 Direct Origination and Submission

Submission occurs only after every check in Section 6.1 passes. Origination creates the application directly in its initial `SUBMITTED` or `DOCUMENTS_PENDING` state according to product policy and upload completeness. Unsubmitted form input is not a persisted LoanApplication. Staff-assisted intake creates no financial exposure and is not an application draft.

Customer-digital submission is performed by the authenticated Customer owner. Staff-assisted UCL and Collateral Loan submission is performed by authorized Staff for the selected Customer after the Customer-provided paper intake facts are confirmed. Salary Advance cannot be submitted through `STAFF_ASSISTED`.

Submission records a stable application reference, Customer, product, origination channel, requested amount and term, product-specific facts, submission time, initial status, and audit evidence.

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

Manual verification assessment is restricted lending evidence. Staff with exact `loan:review` or `approval:decide` may read the recorded assessment, result, reviewer, completion time, and source-correction linkage within the lending case. Customer, Accounting-only, generic `loan:read`, `approval:recommend` alone, and unrelated Back-Office authority do not authorize this evidence. Read access grants no verification, review-start, correction, recommendation, or decision command authority.

Product verification is a distinct business control. Loan Officer credit review and independent Approver assessment consume its recorded evidence without requiring the worker to reconstruct or repeat the assessment. Verification assessment and the later internal credit note are separate evidence. Salary Advance retains its automated immutable verification snapshot and has no manual reviewer or assessment note. Assessment notes remain outside Customer instructions, generic application history, audit presentation, payloads, and logs.

### 6.4 Document Review and Correction

Upload completeness is satisfied when every required checklist item has a current upload or an authorized non-upload outcome. Processing readiness requires every required item to be accepted or waived, while non-required items remain outside the readiness obligation.

An authorized reviewer may:

- accept the current version;
- waive the requirement with a controlled reason and separate authority;
- request a replacement.

A replacement creates a new immutable version and invalidates the previous version's readiness effect.

Document review evidence retains the exact reviewed version, reviewer, decision time, outcome, controlled waiver or replacement reason, Customer instruction, and separately restricted Document note. Authorized document reviewers and independent Approvers may inspect immutable current and historical evidence for their lending purpose. The note audience requires exact `document:review` or `approval:decide`; inspection alone grants no review, waiver, upload, or replacement authority. Missing historical reviewer identity must not erase the remaining recorded evidence. Restricted Document notes must not appear in Customer responses, correction instructions, logs, audit payloads, or general analytics.

Authorized correction operators may inspect every recorded correction request and its task ownership, instructions, creator, completion evidence, and readiness/resubmission/cancellation times. Staff-recorded Customer work retains the Customer as its business source and identifies Staff separately as recorder. Customer self-service attribution must not disclose Customer login identity. Historical inspection does not permit task completion, resubmission, or bypass of the current maker-checker rules. A missing durable proof-version or actor association must remain unavailable rather than being reconstructed from later evidence.

Signed Customer offer-response, contract-acknowledgment, and cancellation-request forms remain Document-owned immutable evidence. Authorized reviewers and the corresponding offer, accounting, or correction operators may inspect their purpose-specific historical versions after the action completes. The completed action identifies the exact form version consumed. Viewing does not reopen the action or grant its recording authority; Customer-facing paper-form inspection is outside this boundary.


When evidence is missing or requires correction, the application uses:

| Status | Use |
|---|---|
| `DOCUMENTS_PENDING` | Required Customer or Staff uploads are incomplete |
| `RETURNED_FOR_REVISION` | Structured Customer or Staff correction is required |

Requested amount and term are immutable after submission for every product. Correction addresses only the product-specific evidence or facts permitted by this specification; it cannot amend financial terms. Post-submission financial-term amendments, including `APPLICATION_TERMS` correction, are outside the MVP.

Every correction task has one operational owner: Customer or Staff. Mixed correction plans use separate tasks. Restricted Staff notes must not appear in Customer instructions. A Staff actor who requested a Staff correction must not complete that task.

Task completion requires the requested evidence. For `CUSTOMER_DIGITAL`, Customer-only corrections are resubmitted by the Customer owner. For `STAFF_ASSISTED`, a Customer-sourced correction is operationally assigned to authorized Staff: Staff contacts the Customer, records or uploads the Customer-provided correction, and resubmits when the requested evidence is complete. This does not convert the correction into a Staff correction. Staff-only and mixed corrections are resubmitted by authorized Staff. For UCL, verification, Loan Officer review, and Approver review may each produce a permitted structured correction over `INCOME_PROOF`, `BANK_STATEMENT`, or `EMPLOYMENT_PROOF`. Correction resubmission returns to `SUBMITTED` for a fresh product-verification cycle before another review begins.

For Collateral Loan, verification, Loan Officer review, or Approver decision may require only replacement or Staff review of the existing `COLLATERAL_OWNERSHIP_EVIDENCE` checklist item. The submitted Collateral type, description, estimated value, ownership status, and condition note also remain immutable. Correction cannot create another Collateral asset or supporting checklist item. Resubmission returns to `SUBMITTED` and requires a new linked manual-verification cycle before Loan Officer review.

An authenticated Customer owner may instead terminate a Salary Advance or UCL application while it is `RETURNED_FOR_REVISION`. For a Staff-assisted UCL, authorized Staff may record the Customer's evidenced cancellation request from the same state. This narrow cancellation ends the active correction request and changes the application to `CANCELLED`. Salary Advance releases the existing pre-disbursement reservation exactly once in the same transaction; UCL creates no product-exposure effect. It does not require current Partner eligibility because abandonment must remain possible when correction re-verification cannot succeed. Cancellation from other states and Staff-initiated or administrative cancellation require separately approved policies.

Resubmission revalidates every affected business condition and routes the application to the earliest stage that still requires work. Salary Advance preserves its existing reservation through correction unless a defined terminal or release rule applies.

### 6.5 Loan Officer Review

The Loan Officer reviews Customer readiness, product verification, document readiness, requested amount and term, product-specific facts, and internal evidence.

The first successful start of Loan Officer review establishes one durable assigned Loan Officer for that application review lineage. That assignment is not implied by Staff-assisted intake, document review, correction handling, or product verification. Only the assigned Loan Officer may restart a later review cycle or record its recommendation. Approver return, correction, re-verification, and review restart preserve the same assignment; reassignment is outside the MVP and requires a separate controlled administrative workflow.

| Action | Next Status |
|---|---|
| `RECOMMEND_APPROVAL` | `APPROVAL_PENDING` |
| `RECOMMEND_REJECTION` | `APPROVAL_PENDING` |
| `RETURN_TO_CUSTOMER_REVISION` | `RETURNED_FOR_REVISION` |
| `REQUEST_STAFF_CORRECTION` | `RETURNED_FOR_REVISION` |

A revision action requires a controlled reason and task ownership. `RETURN_TO_CUSTOMER_REVISION` routes directly to the Customer for `CUSTOMER_DIGITAL` and through authorized Staff coordination for `STAFF_ASSISTED`; `REQUEST_STAFF_CORRECTION` remains a correction of Staff-controlled work. The recommendation and LoanApplication transition form one business outcome with the correction plan for a revision action and audit evidence for every action.

Staff-assisted origination does not add a second maker-checker layer. A Staff actor who performed intake may perform later verification or Loan Officer review when otherwise authorized; the mandatory separation remains between the recommending Loan Officer and final Approver.

Authorized Staff case reads show purpose-limited provenance for the assigned Loan Officer and recorded recommendation, decision, and lifecycle actors using only Staff user identity, display name, and email. System transitions remain explicitly identified as System. Missing legacy Staff identity evidence is shown as unavailable and must not be guessed from current permissions, roles, intake ownership, or later activity.

The linked Review Cycle → Recommendation → Decision history preserves each recorded cycle's number, assignment, status, start/end times, normal rationale, and action provenance. A cycle without a recommendation and a recommendation without a decision remain incomplete evidence; later return/correction cycles do not replace earlier evidence. Authorized review, recommendation, and decision audiences may inspect normal historical rationale. Generic application-read authority alone does not authorize this credit-review history, and historical visibility grants no command authority.

Recommendation and decision `internalNotes` are restricted internal credit-analysis context, distinct from the official workflow `reason` and controlled reason. Recommendation notes are readable by an authorized Approver, the Loan Officer assigned to the associated Review Cycle, or the recommendation author who still holds review/recommendation authority. Decision notes are readable by an authorized Approver or that cycle's assigned Loan Officer. Authorship alone never bypasses current authorization. Customer, Accounting-only, generic Loan-read-only Staff, and unrelated Back-Office audiences receive neither note. Missing legacy assignment is never inferred from authorship or later activity; unavailable display identity does not erase authoritative recorded ownership. These notes remain outside Customer instructions, generic lifecycle history, audit payloads, and logs.

### 6.6 Approval Decision

The Approver reviews the application and Loan Officer recommendation. The Approver must be a different Staff actor from the Loan Officer who recorded the recommendation.

MVP approval is exact-request approval:

- approved principal equals the submitted requested amount;
- approved term equals the submitted requested term;
- approval does not create a counteroffer;
- neither review, correction, nor approval may amend the submitted amount or term; Section 6.4 defines the correction boundary.

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

For `CUSTOMER_DIGITAL`, the authenticated Customer owner may:

- accept a valid pending offer, moving the application to `CONTRACT_PENDING`;
- decline it, moving the application to `CUSTOMER_DECLINED`;
- take no action until System expiry moves it to `EXPIRED`.

For `STAFF_ASSISTED`, the Customer makes the offer decision through branch interaction and authorized Staff records the evidenced Customer acceptance or decline. Staff does not make the decision for the Customer. If no decision is recorded before expiry, System expiry moves the application to `EXPIRED`.

Customer decline and expiry release a Salary Advance reservation exactly once in the same business outcome as the terminal transition. Identical retries return the existing result. Contradictory terminal actions are conflicts.

### 6.8 Operational Contract and Readiness

After acceptance, Accounting prepares the current operational contract from the approved offer and an eligible disbursement destination.

The Customer acknowledges the exact current contract version. For `CUSTOMER_DIGITAL`, the authenticated Customer records the acknowledgment directly. For `STAFF_ASSISTED`, the Customer acknowledges through branch interaction and authorized Staff records the evidenced Customer acknowledgment. Staff does not acknowledge the contract for the Customer. Acknowledgment is immutable operational evidence and cannot be withdrawn.

Before readiness, Accounting may regenerate a `PREPARED` or `ACKNOWLEDGED` contract only for `DISBURSEMENT_ACCOUNT_REFRESH`. The previous version becomes `SUPERSEDED`; financial terms and repayment items remain unchanged; the new destination is captured; and the Customer must acknowledge the new version through the application's allowed channel.

Readiness is calculated from current authoritative facts and exposes its status and blockers. Confirmation requires:

- accepted offer;
- acknowledged current contract;
- active Customer;
- active captured source account;
- processing-ready documents;
- no active correction;
- valid product-specific pre-disbursement evidence;
- for UCL and Collateral Loan, the authoritative latest application-owned product-verification cycle in `VERIFIED`;
- for Salary Advance, an unreleased reservation.

For UCL, contract preparation captures the Customer's current eligible primary active bank account.

UCL and Collateral Loan readiness and activation do not read, reserve, convert, release, or create Salary Advance limit or movement evidence.

Confirmation marks the contract `READY_FOR_DISBURSEMENT` and the application `DISBURSEMENT_PENDING` atomically. It does not transfer funds, create the LoanAccount, create the final schedule, or convert Salary Advance reserved exposure to used exposure.

### 6.9 Manual Disbursement and Activation

Accounting performs the transfer outside Meridian using the contract-bound destination.

Full destination data is available only through a dedicated, authorized, audited reveal operation for the disbursement purpose. Ordinary Customer, LoanAccount, audit, history, log, and error views expose only masked or purpose-limited data.

After the external transfer, Accounting confirms disbursement against the ready contract. Loan uses the current ready contract as authority for Customer, product, destination, principal, term, pricing, and repayment items. The contract must preserve the accepted offer terms exactly.

Confirmation must create one atomic outcome:

1. immutable disbursement evidence;
2. one LoanAccount in `ACTIVE`;
3. one authoritative final dated repayment schedule, preserving contract amounts and item sequence, with initial installment progress and histories;
4. product-specific exposure conversion;
5. LoanApplication status `DISBURSED`;
6. audit and status history.

A failure must not leave a partial account, schedule, disbursement record, exposure movement, application transition, history, or audit outcome. Section 11.7 defines provisional-item reconciliation and final schedule dates.

Identical replay returns the original outcome without duplicate writes. Reusing the request identity with different content is a conflict.

### 6.10 Repayment, Overdue State, Contractual Payoff, Administrative Full-Balance Settlement, and Administrative Closure

The final repayment schedule is immutable contractual obligation evidence. Payment transactions, allocations, and servicing progress do not rewrite original schedule amounts or due dates.

Repayment follows these rules:

- allocate installments by due date, then installment number;
- allocate within an installment in `FEE -> INTEREST -> PRINCIPAL` order;
- allow partial and early payment without repricing, rebate, reduction of contractual interest, schedule regeneration, or due-date mutation;
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

Full contractual payoff requires the complete contractual outstanding and must atomically settle the account and complete the product-specific exposure result defined above. Date-driven overdue evaluation and payment may move an account between `ACTIVE` and `OVERDUE`; payment may cure an overdue account without rewriting earlier servicing evidence.

Administrative Full-Balance Settlement is an exceptional Loan-owned servicing operation performed by an authorized Approver. It records an actual payment equal to the complete current contractual outstanding, applies the same deterministic allocation rules as repayment, preserves `paid + outstanding = originated` for every component, applies the product-specific exposure result, and moves an `ACTIVE` or `OVERDUE` LoanAccount to `SETTLED`. It is not a discounted settlement, concession, waiver, forgiveness, write-off, negotiation, or debt adjustment.

Administrative closure is a separate Loan-owned operation performed by an authorized Accounting Officer. A `SETTLED` LoanAccount is immediately eligible when contractual outstanding is zero, installment progress is fully paid, financial-settlement provenance and status history are consistent, and product-specific exposure evidence is reconciled. Closure changes the account to `CLOSED` and records closure/history/audit evidence; it does not change the final schedule, payment evidence, allocations, balances, installment progress, product exposure, or LoanApplication state. Either ordinary contractual payoff or Administrative Full-Balance Settlement may provide the financial-settlement provenance.

Product-specific origination restrictions remain in the applicable product workflow. Section 13.3 defines the servicing and accounting capabilities excluded from the MVP.

---

## 7. Product Workflows

All product workflows inherit Section 6. This section adds only the product-specific preparation, eligibility, verification, offer, and servicing rules.

### 7.1 Salary Advance

Salary Advance is Meridian's flagship MVP product. It is a limit-based product in which reusable employment verification and current exposure capacity exist before application submission. Salary Advance origination is `CUSTOMER_DIGITAL` only; Staff-assisted paper intake or origination is not allowed for this product.

#### Partner Setup and Employee Imports

- Back-Office Admin creates, activates, suspends, or deactivates Partner Companies.
- Back-Office Admin imports the complete Partner Employee roster for one Partner Company and effective month. A successful same-month import replaces that month's authoritative snapshot; older snapshots remain immutable history and their rows are never merged into the replacement.
- System validates every submitted row. Any invalid or duplicate-code row produces a non-authoritative `FAILED` batch with safe validation feedback and no employee writes or link refreshes. The previous valid snapshot remains authoritative. Valid and invalid counts describe validation, not partial acceptance. Exact request replay returns the committed outcome; a corrected complete roster requires a new request identity.
- Normal eligibility uses the authoritative latest valid `COMPLETED` import batch for the current UTC effective month. If no such batch exists, or a verified link and its employee source do not both reference that batch, the evidence is stale and normal eligibility fails closed.
- After an authoritative current-month import completes, Partner refreshes an existing `VERIFIED` link only when its stored verified identity reference and employee code resolve to exactly one active employee in that batch. A pending eligibility review for that Customer and Partner Company keeps the relationship under manual-review authority. Missing, ambiguous, or inactive evidence leaves the link on its prior source batch so normal eligibility fails closed.
- An inactive Partner Company or Partner Employee is a hard stop.

#### Customer Verification and Dashboard

The Salary Advance product page shows:

- employee-verification status;
- active Partner relationship;
- Salary Advance limit status;
- total, used, reserved, and available amounts;
- last refresh time;
- the business reason normal application creation is blocked.

A Customer has at most one current `VERIFIED` employment relationship for Salary Advance. A Customer without a current relationship completes employee verification before starting an application, and a Customer with a current relationship may declare an employment update through the same verification flow. A link remains reusable while its status is `VERIFIED`, no current-month Partner eligibility review is pending, and current Partner evidence remains eligible.

Exact active proof for the same employer and the same verified evidence refreshes the current link. Different evidence for the same employer routes to manual review without overwriting that link. Exact active proof for a different employer disables the old current link as historical Partner evidence and creates a fresh `VERIFIED` link for the new employer. Unresolved proof leaves the prior relationship unchanged but blocks normal readiness while any relevant current-month review remains pending. Approval completes the same-company evidence update or different-employer switch; rejection leaves the prior current relationship unchanged. Historical relationships never participate in current eligibility.

Import-time link refresh follows the rules under Partner Setup and Employee Imports. Otherwise, re-verification against the authoritative current-month batch refreshes the current relationship when the evidence matches or routes an eligible unresolved relationship to manual review.

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

Partner creates one unresolved eligibility review for each Customer and Partner Company when current-month verification returns `NOT_FOUND`, `MULTIPLE_MATCHES`, lacks usable current-month source evidence, or cannot safely replace a reusable verified link with different evidence. `MATCHED_INACTIVE` remains a hard stop and does not create a manually approvable review. Repeated verification reuses the unresolved review while its month and source authority remain unchanged. A conclusive `MATCHED_ACTIVE` verification supersedes every unresolved current-month alternative employment review for that Customer in the same outcome. `MATCHED_INACTIVE` supersedes only the chosen Partner Company's unresolved review; it does not establish current employment or supersede alternative employers' reviews. Missing, ambiguous, otherwise unresolved, and rejected attempts do not supersede alternatives.

The unresolved reviews form one shared Back-Office queue. `partner:read` permits queue and detail inspection; `partner:manage` permits a Staff actor to approve or reject. The workflow has no persisted assignment or claim. Customer verification is the maker action and the authorized Back-Office decision is the checker action; Meridian does not require the reviewer to differ from a Partner Employee importer.

Approval requires the reviewer to select one exact Partner Employee and use `CURRENT_EMPLOYEE_CONFIRMED`. Partner revalidates the active Partner Company, current UTC effective month, latest authoritative `COMPLETED` batch, active employment, usable Customer identity evidence, and the selected employee's identity match. Approval atomically updates the current same-employer relationship or disables the old current relationship and creates a fresh `VERIFIED` relationship for a different employer. It records `MANUAL_REVIEW_APPROVED`, the reviewer, reason, selected employee, source batch, time, and PII-safe audit evidence, and supersedes competing unresolved current-month employment reviews for that Customer atomically. Import-time refresh does not resolve alternative Customer-declared employment attempts. It does not create or transfer a Salary Advance limit, exposure reservation, LoanApplication, or Loan-owned verification snapshot.

Rejection uses `NO_ELIGIBLE_CURRENT_EMPLOYEE`, `IDENTITY_EVIDENCE_MISMATCH`, or `INSUFFICIENT_SOURCE_EVIDENCE`. It records `MANUAL_REVIEW_REJECTED`, the reviewer, reason, and time without changing the prior current relationship. Once no relevant current-month review remains pending, that relationship may support readiness if its evidence remains independently current and eligible. A prior-month review cannot be approved. A review bound to a batch that is no longer authoritative remains historical and non-actionable. Its source must not be retargeted. The Customer verifies employment again against the authoritative snapshot: an exact active result establishes current employment and supersedes obsolete pending reviews; an unresolved result creates or reuses the correct current review; an inactive result remains a hard stop. Refreshing the historical review cannot repair its source authority. Competing decisions permit one terminal outcome; exact replay of that outcome returns the recorded result, while a different later decision conflicts.

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
- Successful submission reserves the requested principal.
- Rejection, cancellation, Customer decline, expiry, or another defined pre-disbursement release frees reserved exposure exactly once.
- Disbursement converts the reservation to used exposure.
- Ordinary repayment and Administrative Full-Balance Settlement release only allocated principal.
- Existing loans and application history remain after suspension or disablement.

Import-time link refresh changes only Partner-owned relationship evidence. Salary Advance readiness projects the effective current limit from the refreshed Partner evidence, and Loan persists its own limit refresh and movement when a later submission requires it. Partner import does not bulk mutate Salary Advance limits or exposure.

An employment switch does not transfer or mutate Salary Advance limit state. Limits, movements, reservations, and used exposure remain attached to their original relationship and Loan history. The new relationship receives its own link identity, and normal future submission initializes or refreshes only that link's limit through Loan's existing behavior. Submitted applications, accepted offers, contracts, LoanAccounts, schedules, and servicing evidence keep the employment snapshot captured by their workflow.

Each submitted application records the employee-link, limit identity, limit values, and verification result used at submission.

The common blocking-application rule applies. Separately, a matching `ACTIVE` or `OVERDUE` Salary Advance `LoanAccount` with positive contractual outstanding blocks new Salary Advance submission. See `BR-004` and `BR-012`.

#### Salary Advance Workflow

1. Back-Office Admin configures the active Partner Company and imports the authoritative effective-month roster under the import rules above.
2. Customer completes the required profile, obtains Customer identity verification, and maintains one primary active bank account.
3. Customer opens the Salary Advance product page. System resolves the reusable employee link; Customer verifies employment or declares an employment update through the matching and manual-review rules above.
4. System projects the eligible current limit. Customer views verification, eligibility, and total, used, reserved, and available amounts before entering requested principal, term, and product-required information.
5. Customer submits directly under Sections 6.1 and 6.2. Successful submission atomically creates the LoanApplication and checklist, reserves requested principal, and records the `VERIFIED` application snapshot. Section 11.4 defines initial and correction evidence; upload completeness determines `SUBMITTED` or `DOCUMENTS_PENDING`.
6. Document correction, Loan Officer recommendation, and independent approval follow Sections 6.4–6.6. The approved offer uses Section 11.2 pricing. Customer response, operational contract acknowledgment, readiness, and manual disbursement remain separate actions under Sections 6.7–6.9.
7. Disbursement converts the existing reservation to used exposure in the atomic activation outcome. Defined pre-disbursement terminal outcomes instead release the reservation exactly once; Section 6.4 defines returned-correction cancellation.
8. Repayment and overdue servicing follow Section 6.10. Only allocated principal releases used exposure. Contractual payoff or payment-backed Administrative Full-Balance Settlement produces `SETTLED`; Accounting may then perform the separate administrative closure.

Salary Advance excludes automated payroll deduction and real employer, payroll, bank-transfer, and HR integrations, as well as an employer-facing production portal, counteroffers, and Approver-modified terms.

### 7.2 Unsecured Consumer Loan

Unsecured Consumer Loan is a streamlined document-based product. It requires income and employment evidence but no collateral. Its allowed channels are defined in Section 3.2; both channels retain the same product and downstream lending rules. Loan purpose may be an optional product-policy field or document. Section 11.3 defines UCL financial policy.

The common blocking-application rule is `BR-004`. The separate UCL outstanding-debt guard in `BR-020B` applies both to new origination and correction resubmission; unrelated product debt does not satisfy that guard. UCL creates no product exposure and does not affect Salary Advance exposure under Section 6.10.

UCL workflow:

1. Customer completes readiness and supplies requested amount, term, income, employment, and other required product facts directly, or authorized Staff selects or creates the Customer and confirms those Customer-provided facts through the persisted intake in Section 5.10. Section 5.9 governs profile, bank-account, and signed application consent facts.
2. Customer or authorized Staff supplies the income, bank-statement, and employment evidence in Section 11.4. Direct origination follows Sections 6.1 and 6.2 and creates the initial `PENDING_MANUAL_REVIEW` verification cycle.
3. An authorized Staff reviewer evaluates income and employment consistency and basic repayment capacity under Section 6.3. Verification, credit-review recommendation, and final approval remain distinct decisions.
4. Verification, Loan Officer review, or Approver assessment may require the product-specific corrections in Section 6.4. Completed verification remains immutable and resubmission requires the linked pending cycle and re-verification before review. Customer-requested cancellation from `RETURNED_FOR_REVISION` follows the channel and zero-exposure rules in Section 6.4.
5. Loan Officer recommendation and independent Approver decision follow Sections 6.5 and 6.6. Approval generates one immutable exact-request offer using Section 11.3 pricing.
6. Customer response, contract acknowledgment, readiness, manual disbursement, activation, and servicing follow Sections 6.7–6.10. Staff records evidenced Customer decisions through the Staff-assisted channel; Customer Web access is not required. Section 11.7 defines the dated monthly schedule.

The UCL MVP excludes credit-bureau integration, automated income verification, bank-statement parsing, automated credit scoring, and fully automated approval.

### 7.3 Collateral Loan

Collateral Loan is a streamlined secured product based on one Customer-provided structured Collateral fact, required ownership evidence, and manual assessment. Section 5.8 defines the submitted facts and supported types; Section 3.2 defines the two allowed channels. Both channels use the same Collateral invariants and downstream lending lifecycle.

The common blocking-application rule is `BR-004`. Under `BR-021I`, an existing Collateral LoanAccount creates no additional product-specific origination restriction. Collateral origination, activation, and servicing do not create or mutate Salary Advance limits, exposure, or movements.

Collateral Loan workflow:

1. Customer completes readiness and supplies requested amount, term, and the structured Collateral facts in Section 5.8 directly, or authorized Staff selects or creates the Customer and confirms those Customer-provided facts through Section 5.10 intake. Section 5.9 governs profile, bank-account, and signed application consent facts.
2. Customer or authorized Staff supplies the ownership evidence in Section 11.4. Direct origination follows Sections 6.1 and 6.2 and creates the initial numbered `PENDING_MANUAL_REVIEW` verification cycle.
3. After ownership evidence is processing-ready, an authorized Staff reviewer records one of the outcomes and the mandatory restricted assessment note in Section 6.3. `FAILED` is terminal; verification is not credit approval.
4. Corrections follow the document-only boundary in Section 6.4. Staff coordinates Customer-sourced replacement for Staff-assisted applications. Resubmission preserves completed evidence, creates the linked pending cycle, and requires re-verification before Loan Officer review; submitted Collateral facts and financial terms remain immutable.
5. Loan Officer recommendation and Approver decision follow Sections 6.5 and 6.6. The authoritative latest cycle must be `VERIFIED` for review entry and remain so for any of the four Approver actions. Only approval generates the immutable exact-request offer using Section 11.5 pricing.
6. Customer response, contract acknowledgment, readiness, manual disbursement, activation, and servicing follow Sections 6.7–6.10, with the dated monthly schedule in Section 11.7. Collateral retains zero product-exposure effects through repayment, contractual payoff, Administrative Full-Balance Settlement, and administrative closure.

The Collateral Loan MVP excludes post-submission structured-fact editing, supporting photos or documents without an explicit product-policy decision, more than one Collateral asset per application, automated valuation, automated loan-to-value decisions, collateral custody, notarization, asset-registry integration, insurance integration, repossession, liquidation, and legal enforcement.

---

## 8. Status Model and Transition Rules

Status names are namespace-scoped. Similar labels in different concepts do not create shared ownership or interchangeable states.

### 8.1 LoanApplication Statuses

| Status | Business Meaning |
|---|---|
| `DRAFT` | Reserved status vocabulary for saved applications outside the MVP; no MVP save-and-return workflow originates in this state |
| `SUBMITTED` | Submission is accepted and ready for the next required verification, document, or review action |
| `VERIFICATION_PENDING` | Formal product verification or review is pending |
| `VERIFICATION_FAILED` | Product verification failed |
| `DOCUMENTS_PENDING` | Required upload or replacement work remains |
| `UNDER_REVIEW` | Loan Officer review is active |
| `RETURNED_FOR_REVISION` | Customer-sourced or Staff correction is required; Staff-assisted Customer work is coordinated and recorded by authorized Staff |
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
| No LoanApplication yet | Originate and submit application | Customer or authorized Staff | All submission checks and product-channel rules pass | `SUBMITTED` or `DOCUMENTS_PENDING` | No |
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
| `CUSTOMER_ACCEPTANCE_PENDING` | Accept pending offer | Customer or authorized Staff recorder | Customer-digital owner is authenticated, or Staff-assisted Customer acceptance is evidenced; offer is unexpired | `CONTRACT_PENDING` | No |
| `CUSTOMER_ACCEPTANCE_PENDING` | Decline pending offer | Customer or authorized Staff recorder | Customer-digital owner is authenticated, or Staff-assisted Customer decline is evidenced | `CUSTOMER_DECLINED` | No |
| `CUSTOMER_ACCEPTANCE_PENDING` | Expire pending offer | System | Current time is at or after expiry | `EXPIRED` | No |
| `CONTRACT_PENDING` | Confirm readiness | Accounting Officer | Current contract acknowledged and every blocker is cleared | `DISBURSEMENT_PENDING` | No |
| `DISBURSEMENT_PENDING` | Confirm manual disbursement | Accounting Officer | Ready contract and valid transfer evidence | `DISBURSED` | No |
| `RETURNED_FOR_REVISION` | Cancel returned correction | Customer or authorized Staff recorder | Customer-digital owner is authenticated, or Staff-assisted UCL Customer cancellation is evidenced; product is Salary Advance or UCL; active correction exists; any Salary Advance reservation remains consistent | `CANCELLED` | No |

Customer cancellation from other pre-disbursement states and every Staff-initiated or administrative cancellation require a separately approved policy with defined authority, reason, and financial effects. Recording a permitted Staff-assisted Customer cancellation is not Staff-initiated cancellation.

A transition and its financial, correction, document, offer, contract, exposure, history, and audit effects must commit as one business outcome where the rule requires atomicity.

---

## 9. Functional Requirements

These identifiers define required capabilities. The referenced scope, workflow, and policy sections define their detailed conditions and product-specific exceptions.

| ID | Requirement |
|---|---|
| FR-CUST-001 | The system shall let Customers register, authenticate, maintain their own profile, manage their own bank accounts, and provide the identity, contact, residential, employment, consent, and destination facts required by supported products. |
| FR-CUST-002 | The system shall restrict and audit identity, profile, and bank-account changes according to business state and historical-data rules. |
| FR-CUST-003 | The system shall permit a Customer business record to exist without a Customer Web login and shall let authorized Staff select, create, and maintain that Customer only through Staff-assisted business capabilities that preserve Staff as actor and Customer as subject. |
| FR-IAM-001 | The system shall authenticate Customer and Staff actors before protected Customer Web or Internal Web access. |
| FR-IAM-002 | The system shall enforce role and action permissions and preserve the authenticated actor for Staff business actions. |
| FR-PROD-001 | The system shall store and display active products with amount limits, terms, pricing, repayment method, required documents, and eligibility notes. |
| FR-PROD-002 | The system shall let authorized Back-Office Admins manage product activation and minimum/maximum amounts within Section 3.6. |
| FR-APP-001 | The system shall support direct origination/submission, permitted cancellation, status tracking, and transition control through one common LoanApplication lifecycle under Section 6; persisted Staff-assisted intake remains separate, and persistent save-and-return LoanApplication drafts are outside the MVP. |
| FR-APP-002 | The system shall validate Customer readiness, active product, amount, term, product-specific facts, checklist requirements, exposure, and concurrency rules before submission. |
| FR-APP-003 | The system shall prevent a new submitted application for a product while the Customer has another blocking non-terminal application for that product. |
| FR-APP-004 | The system shall record each LoanApplication origination channel and enforce `CUSTOMER_DIGITAL` for Salary Advance and either `CUSTOMER_DIGITAL` or `STAFF_ASSISTED` for UCL and Collateral Loan. |
| FR-INTAKE-001 | The system shall support Staff-assisted pre-application intake for UCL and Collateral Loan so authorized Staff can capture Customer-provided paper application and identity evidence, confirm structured facts, and create or submit the normal LoanApplication without creating financial exposure or entering review before submission. |
| FR-SA-001 | The system shall let Back-Office Admins manage Partner Companies and monthly Partner Employee imports for Salary Advance. |
| FR-SA-002 | The system shall validate import rows, track batches, enforce freshness, and prevent invalid, stale, inactive, or unresolved duplicate employee evidence from normal eligibility. |
| FR-SA-003 | The system shall verify Salary Advance employment before normal application creation and maintain a reusable Customer–Partner Employee link after successful verification or authorized manual-review approval. |
| FR-SA-004 | The system shall show employee-verification status and total, used, reserved, and available Salary Advance limit to the Customer. |
| FR-SA-005 | The system shall calculate and maintain Salary Advance limit using product, Partner, employee, salary-cap, used-exposure, reserved-exposure, status, and freshness rules. |
| FR-SA-006 | The system shall block normal Salary Advance creation or submission when employee eligibility is absent, the limit is not usable or sufficient, or matching Salary Advance debt has positive contractual outstanding. |
| FR-SA-007 | The system shall reserve limit at successful submission, release it exactly once on defined pre-disbursement outcomes, convert it to used exposure at disbursement, and release used exposure through allocated principal or another approved policy. |
| FR-SA-008 | The system shall refresh Partner-owned links and Loan-owned limits at their respective points defined in Section 7.1; Partner import shall not bulk mutate Loan exposure. |
| FR-SA-009 | The system shall record one Salary Advance verification snapshot for each submitted Salary Advance application. |
| FR-UCL-001 | The system shall support the UCL workflow in Section 7.2, including both allowed channels, income/employment evidence and manual verification, structured correction and re-verification, Customer-requested correction cancellation, the product-scoped outstanding-debt guard, and the common lifecycle through administrative closure with zero product exposure. |
| FR-CL-001 | The system shall support the Collateral workflow in Section 7.3, including both allowed channels, one structured Collateral fact, required ownership evidence, manual verification and re-verification, document-only correction, and the common lifecycle through administrative closure with zero product exposure and no Salary Advance movement. |
| FR-DOC-001 | The system shall let Customers and authorized Staff upload and retrieve purpose-authorized documents associated with Staff-assisted intake, Customer, application, collateral, contract, or disbursement requirements. |
| FR-DOC-002 | The system shall calculate upload completeness separately from processing readiness and document-review outcomes. |
| FR-DOC-003 | The system shall support immutable document versions, acceptance, waiver, replacement, controlled reasons, and readiness queries. |
| FR-REV-001 | The system shall let Loan Officers recommend approval or rejection and request Customer or Staff correction. |
| FR-REV-002 | The system shall route Customer-sourced correction directly to the Customer for Customer-digital applications and through authorized Staff coordination for Staff-assisted applications, while keeping Customer-sourced correction distinct from Staff correction. |
| FR-REV-003 | The system shall establish durable Loan Officer stewardship on first review start, preserve it across later review cycles, and permit review restart and recommendation only for the assigned Loan Officer; reassignment requires a separate controlled workflow outside the MVP. |
| FR-APR-001 | The system shall let Approvers approve, reject, return to Loan Officer review, or request structured Customer or Staff correction. |
| FR-APR-002 | The system shall enforce maker-checker separation between the Loan Officer recommendation and final Approver decision. |
| FR-OFFER-001 | The system shall generate one immutable approved offer after approval, present it through the application's allowed channel, support idempotent Customer acceptance or decline recorded directly by the authenticated Customer or as an evidenced Staff-assisted Customer decision, and expire pending offers after the configured validity period. |
| FR-CON-001 | The system shall let Accounting prepare an immutable operational contract from the accepted offer and a protected destination snapshot, let the Customer acknowledge the exact current version directly or through an evidenced Staff-assisted branch interaction, and expose a structured readiness result containing readiness status and blocker codes. |
| FR-CON-002 | The system shall permit contract regeneration before readiness only for a controlled destination refresh and shall confirm readiness without performing disbursement. |
| FR-DIS-001 | The system shall expose the full destination only through a dedicated authorized and audited disbursement operation and shall let Accounting confirm an external transfer only against a ready contract. |
| FR-DIS-002 | The system shall create the LoanAccount, final schedule, disbursement evidence, exposure effects, application transition, history, and audit as one atomic activation outcome. |
| FR-REP-001 | The system shall preserve the final schedule, record payments and allocations, track installment and account servicing state, support contractual payoff and Administrative Full-Balance Settlement, and close eligible settled accounts through a separate administrative action. |
| FR-REP-002 | The system shall make repayment idempotent by logical request and payment reference and shall prevent duplicate evidence from creating duplicate allocation, exposure, history, or audit effects. |
| FR-PORTAL-001 | The system shall provide Customer Web capabilities for registration, authentication, profile and bank-account maintenance, product browsing, eligibility, application submission, document upload, offer response, contract acknowledgment, and status viewing. |
| FR-PORTAL-002 | The system shall provide permission-scoped Internal Web capabilities under Section 3.2, with Back-Office Administration limited to Section 3.6 and audit evidence scoped to its business purpose. |
| FR-AUD-001 | The system shall record auditable business actions and transitions with actor, affected business subject or reference, action, time, status change, reason, and operation correlation where applicable. |
| FR-AUD-002 | The system shall keep audit evidence append-only for normal users, PII-safe, and sufficient for maker-checker and business-operation traceability. |

---

## 10. Business Rules

These stable rule identifiers retain the business constraints; referenced sections contain their full conditions, evidence, and exceptions.

| ID | Rule |
|---|---|
| BR-001 | An application may be submitted only for an active `LoanProduct` through an origination channel allowed for that product. |
| BR-002 | The Customer profile must satisfy the selected product's completeness rule before submission. |
| BR-003 | A LoanApplication must pass every required submission validation before becoming submitted. |
| BR-004 | A Customer cannot submit the same product while another blocking non-terminal application for that product exists. The blocking states are `SUBMITTED`, `VERIFICATION_PENDING`, `DOCUMENTS_PENDING`, `UNDER_REVIEW`, `RETURNED_FOR_REVISION`, `RETURNED_TO_REVIEW`, `APPROVAL_PENDING`, `APPROVED`, `CUSTOMER_ACCEPTANCE_PENDING`, `CONTRACT_PENDING`, and `DISBURSEMENT_PENDING`. |
| BR-005 | A Customer may cancel their own Salary Advance or UCL application from `RETURNED_FOR_REVISION`; for a Staff-assisted UCL, authorized Staff may record the Customer's evidenced cancellation request from the same state. The active correction ends, Salary Advance releases its reservation atomically, and UCL creates no product-exposure effect. |
| BR-006 | Customer cancellation from another pre-disbursement state or Staff-initiated cancellation requires a separately approved policy defining actor authority, reason, permitted state, and financial effects. Recording a permitted Staff-assisted Customer cancellation under `BR-005` is not Staff-initiated cancellation. |
| BR-007 | A `DISBURSED` application cannot be cancelled. |
| BR-008 | Normal Salary Advance creation requires the Customer's single current active `VERIFIED` employment relationship. Historical relationships do not participate, and any current-month pending Partner eligibility review blocks normal readiness until it becomes terminal or is superseded. |
| BR-009 | Salary Advance requested principal must not exceed active available limit. |
| BR-010 | An inactive Partner Company cannot be manually overridden for normal Salary Advance eligibility. |
| BR-011 | An inactive Partner Employee cannot support normal Salary Advance eligibility. |
| BR-012 | A matching `ACTIVE` or `OVERDUE` Salary Advance LoanAccount with positive contractual outstanding blocks new Salary Advance submission independently of overdue-evaluation freshness. |
| BR-013 | Disbursed unreleased Salary Advance principal contributes to used exposure; submitted unreleased applications contribute to reserved exposure. |
| BR-014 | Salary Advance calculation and refresh use authoritative Partner evidence for the current UTC month under Section 7.1. Import-time link refresh requires exactly one active employee matching stored proof and no pending eligibility review; unresolved evidence fails closed. |
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
| BR-021B | Collateral verification and correction must preserve completed numbered cycles, latest-cycle authority, and linked re-verification before review under Sections 6.3 and 6.4. |
| BR-021C | Collateral correction is limited to the existing ownership-evidence item under Section 6.4; submitted Collateral facts and requested terms remain immutable. |
| BR-021D | Collateral `FAILED` is an unsuccessful application outcome and is not reopened; correctable evidence issues use `REQUIRES_MORE_INFORMATION`. |
| BR-021E | Collateral Approver actions require the latest verification to remain `VERIFIED`; Section 6.6 defines all four actions and permits offer creation only for approval. |
| BR-021F | Collateral approval preserves exact-request terms and applies the 1.5% monthly flat original-principal, zero-fee pricing and one-time whole-VND `HALF_UP` rounding of total interest in Section 11.5, with reconciled provisional items under Section 11.7. |
| BR-021G | Collateral offers remain undated; final schedules require the first-date window and monthly anchoring with final-calendar-day clipping in Section 11.7. |
| BR-021H | Collateral activation and servicing follow Sections 6.9 and 6.10 with zero product exposure and no Salary Advance movement. Early or partial payment preserves contractual obligations; contractual payoff and Administrative Full-Balance Settlement require the exact complete outstanding. |
| BR-021I | The common `BR-004` blocking-application rule applies to Collateral Loan; an existing Collateral `LoanAccount` creates no additional product-specific origination restriction. |
| BR-022 | Collateral estimated value is advisory in the MVP and does not create an automated loan-to-value decision. |
| BR-023 | Upload completeness, manual document review and processing readiness, and product verification are separate controls. |
| BR-024 | Product policy defines which checklist items must be upload-complete before submission. |
| BR-025 | Contract readiness requires every required document item to be accepted, not required, or validly waived. |
| BR-026 | Missing, rejected, expired, or replacement-required evidence must route to the correct operational task. Customer-sourced work routes directly to the Customer for `CUSTOMER_DIGITAL` and through authorized Staff coordination for `STAFF_ASSISTED`; Staff correction remains Staff-owned. |
| BR-027 | Loan Officer review and Approver decision are separate responsibilities. |
| BR-027A | First review start establishes one assigned Loan Officer for the application review lineage; later review cycles preserve that assignment, and only that actor may restart review or record a recommendation. |
| BR-027B | Staff-assisted intake, verification, document work, and correction activity do not establish or transfer Loan Officer review stewardship. Unresolved legacy assignment fails closed rather than being inferred. |
| BR-028 | One Staff actor cannot record both the recommendation and final decision for the same application. |
| BR-029 | Rejection, return, Staff cancellation, request-more-information, Staff correction, manual override, waiver, and other controlled exception actions require a reason where defined. |
| BR-030 | The Customer must accept valid approved terms before contract preparation and disbursement. Customer-digital acceptance is recorded by the authenticated Customer owner; Staff-assisted acceptance is an evidenced Customer decision recorded by authorized Staff. |
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
| BR-042 | MVP approval must accept the exact submitted amount and term. Review and correction must not amend either value or create a counteroffer; Section 6.4 defines permitted correction. |
| BR-043 | Each LoanApplication has at most one approved offer in the MVP; Section 6.7 defines its immutable financial and timing evidence. |
| BR-044 | Customer-digital offer viewing and response derive Customer identity from authentication and verify ownership through the LoanApplication. Staff-assisted offer response uses the selected application Customer as subject and the authenticated Staff user as recorder; it must not impersonate or authenticate as the Customer. |
| BR-045 | Viewing an offer is read-only; expiry is performed by System processing or a guarded state-changing action. |
| BR-046 | Repeating the same offer action is idempotent; contradictory terminal actions are conflicts. |
| BR-047 | Salary Advance approval preserves the submitted principal and the submitted allowed 1-, 2-, or 3-month term under Sections 6.6 and 11.1. |
| BR-048 | Salary Advance total interest follows the flat original-principal formula and whole-VND `HALF_UP` rounding in Section 11.2. |
| BR-049 | Salary Advance fees are zero and total repayment equals approved principal plus total interest. |
| BR-050 | Salary Advance generates one provisional item per approved month with `ON_SALARY_DATE` timing and no offer-time due dates under Sections 11.2 and 11.7. |
| BR-051 | Salary Advance provisional items must preserve whole-VND allocation, final-item remainders, zero fees, and exact item/component/offer reconciliation under Section 11.7. |
| BR-052 | Salary Advance requested principal must be mathematically whole VND; scale-only trailing zeros are valid and non-zero fractional VND is rejected before financial persistence. |
| BR-053 | An operational contract copies accepted offer terms and provisional items exactly and does not treat mutable Customer data as historical financial authority. |
| BR-054 | Contract acknowledgment is immutable evidence for the exact current version and is not an electronic signature, digital signature, or legal execution. Customer-digital acknowledgment is recorded directly by the authenticated Customer; Staff-assisted acknowledgment records the Customer's evidenced branch acknowledgment with the authenticated Staff user as actor. |
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
| BR-067 | Salary Advance permits only `CUSTOMER_DIGITAL` origination. UCL and Collateral Loan permit `CUSTOMER_DIGITAL` and `STAFF_ASSISTED` origination. |
| BR-068 | A Staff-assisted Customer may exist without a Customer Web User. The authenticated Staff user remains the actor and the selected Customer remains the business subject for every Staff-assisted action; Staff must not impersonate or authenticate as the Customer. |
| BR-069 | Staff-assisted intake precedes UCL or Collateral Loan `LoanApplication` creation. Intake may hold Customer-provided application and identity evidence but creates no financial exposure and does not enter verification, review, or approval. |
| BR-070 | Staff-assisted Customer consent must be supported by the Customer's signed paper loan application. Staff records that consent was obtained; Staff does not provide the consent, and Meridian does not require a separate consent document for that application. |
| BR-071 | A Customer-sourced correction remains Customer-sourced even when authorized Staff coordinates it for a Staff-assisted application. Staff must obtain the requested information or evidence from the Customer before recording, uploading, and resubmitting it. |
| BR-072 | OCR suggestions may affect authoritative Customer or Loan data only after authorized Staff selects reviewed suggestions, confirms or corrects inputs, and invokes the owning actions in Section 5.10; OCR and transcription confirmation do not establish business verification or decisions. |
| BR-073 | When Staff-assisted intake creates a new Customer, creation of the Customer and required identity-bearing profile must fail as one business outcome when protected identity evidence duplicates another Customer; the failed attempt must not leave a separate incomplete Customer shell. |
| BR-074 | An authorized Staff Customer-intake actor may enable Customer Web access for an existing active identity-bearing Customer only after matching the Customer's presented identity reference to that exact record. One Customer has at most one linked Customer User. The Customer must verify control of the activation email before choosing their first password. The activation invitation must lead directly from email confirmation to first-password setup. Staff must not choose or learn the Customer password. Activation preserves the Customer, profile, bank accounts, historical `STAFF_ASSISTED` application channels, and their lack of Customer-digital actions; it neither creates nor merges Customer records. |

Operational history identifies a Customer self-service action as **Customer — self-service** without exposing the Customer Identity User ID, login email, Identity display name, roles, permissions, session information, or credentials. An evidenced Staff-assisted Customer decision or request identifies the Customer as its source and Staff as its recorder. Staff-owned review and decision actions identify the Staff actor. System actions identify System; unresolved historical actors remain unavailable without guessing their identity. Customer number or name appears only through an already-authorized business Customer projection.

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

Sections 11.2, 11.3, and 11.5 define product pricing. Section 11.7 defines shared item reconciliation and UCL/Collateral final schedule dates. Catalog and policy values do not expand the administrative edit scope in Section 3.6.

### 11.2 Salary Advance Policy Values

Section 7.1 defines limit calculation, freshness, manual eligibility review, exposure, and link-refresh behavior. The configured inputs are:

| Policy Item | Value |
|---|---|
| Partner Company policy limit | 20,000,000 VND |
| Salary percentage cap | 40% of monthly salary |
| Employee-configured limit | Imported from the eligible Partner Employee record |
| Interest method | Flat interest on original principal, not declining-balance interest |
| Monthly interest rate | 1.2% |
| Fee | 0 VND |
| Repayment method | `ON_SALARY_DATE` |

Salary Advance pricing:

```text
approvedPrincipal = submitted requested amount
approvedTermMonths = submitted requested term
unroundedTotalInterest = approvedPrincipal × 0.012 × approvedTermMonths
totalInterest = round(unroundedTotalInterest, whole VND, HALF_UP)
feeAmount = 0 VND
totalRepaymentAmount = approvedPrincipal + totalInterest
```

Section 11.1 defines the allowed 1-, 2-, and 3-month terms. Provisional items use the first, second, and third salary-cycle timing as applicable, without exact calendar due dates at offer time. Section 11.7 defines whole-VND allocation, final-item remainders, and exact reconciliation.

### 11.3 Unsecured Consumer Loan Policy Values

UCL uses the exact-request approval rule in Section 6.6 and the 3-, 6-, 9-, and 12-month terms in Section 11.1. The default policy uses `FLAT_ORIGINAL_PRINCIPAL` interest at a monthly rate of `0.018000`, zero fee, and `MONTHLY_INSTALLMENT` repayment.

```text
approvedPrincipal = submitted requested amount
approvedTermMonths = submitted requested term
unroundedTotalInterest = approvedPrincipal × 0.018 × approvedTermMonths
totalInterest = round(unroundedTotalInterest, whole VND, HALF_UP)
feeAmount = 0 VND
totalRepaymentAmount = approvedPrincipal + totalInterest
```

Section 11.7 defines provisional items and final monthly dates. Sections 6.8–6.10 define accepted-offer authority, destination capture and refresh, UCL-specific readiness, atomic activation, and servicing with zero product-exposure effect.

### 11.4 LoanApplication Checklist Evidence by Product

This table defines product-specific LoanApplication checklist evidence. Customer readiness in Section 6.1 and the contract-bound destination in Sections 6.8 and 6.9 remain separate controls.

| Product | Checklist Evidence |
|---|---|
| `SALARY_ADVANCE` | No Customer document is required at initial submission; a correction may require `RECENT_PAYSLIP` |
| `UNSECURED_CONSUMER_LOAN` | `INCOME_PROOF`, `BANK_STATEMENT`, and `EMPLOYMENT_PROOF` |
| `COLLATERAL_LOAN` | `COLLATERAL_OWNERSHIP_EVIDENCE`; supporting photos or additional supporting documents require an explicit product-policy decision |

Product policy determines which evidence must exist before submission and which may be introduced through correction.

For `STAFF_ASSISTED` UCL and Collateral Loan, the signed paper loan application is intake evidence and contains the Customer declaration and consent required for Staff-assisted submission. Identity-document evidence may also be captured during intake before a `LoanApplication` exists. These intake artifacts do not become separate LoanApplication checklist requirements unless product policy explicitly requires them; consent is not represented as a separate paper document.

### 11.5 Collateral Loan Policy Values

Section 5.8 defines supported Collateral types and advisory estimated value. Sections 6.3, 6.4, and 6.6 define manual assessment, mandatory verification notes, document-only correction, and the latest-`VERIFIED` requirement for Approver actions. Approval uses the exact submitted amount and term without a counteroffer or automated loan-to-value adjustment.

| Policy Item | Value |
|---|---|
| Interest method | Flat interest on original principal |
| Monthly interest rate | 1.5% (`0.015000`) |
| Fee | 0 VND |
| Repayment method | Monthly installment |

Collateral Loan pricing rounds total interest once to whole VND using `HALF_UP` before installment allocation:

```text
approvedPrincipal = submitted requested amount
approvedTermMonths = submitted requested term
unroundedTotalInterest = approvedPrincipal × 0.015 × approvedTermMonths
totalInterest = round(unroundedTotalInterest, whole VND, HALF_UP)
feeAmount = 0 VND
totalRepaymentAmount = approvedPrincipal + totalInterest
```

Section 11.7 defines provisional items and final monthly dates. Section 6.10 defines early and partial payment, exact contractual payoff, and Administrative Full-Balance Settlement. Collateral Loan introduces no product-specific activation or closure control beyond the common lifecycle.

### 11.6 Offer Validity

The default offer-validity period is seven calendar days and is configurable by product.

```text
expiresAt = generatedAt + offerValidityDays calendar days
```

A pending offer expires when the current time is at or after `expiresAt`.

Expiry processing must eventually find overdue pending offers. A guarded Customer action also checks expiry before accepting or declining, so an expired offer cannot be accepted between processing runs.

Viewing an offer remains read-only and does not expire it, transition the application, release exposure, or create audit or financial effects.

### 11.7 Provisional Items and Final Schedule Dates

For every product, the immutable offer contains one provisional repayment item per approved term month and no calendar due dates. Principal and total interest are each divided into equal whole-VND base portions; any remainder goes only to the final item. Every item has zero fee. Each item's total due must equal its principal due plus interest due plus fee due, and all item sums must reconcile exactly to the offer totals:

```text
sum(item.principalDue) = approvedPrincipal
sum(item.interestDue) = totalInterest
sum(item.feeDue) = 0 VND
sum(item.totalDue) = totalRepaymentAmount
```

The operational contract copies accepted offer amounts and items without repricing or recomputation. At activation, the final schedule preserves the contract amounts and item sequence and adds the controlled calendar dates under Section 6.9.

For UCL and Collateral Loan, the first repayment date must be after the disbursement value date and no later than one calendar month after it. The first installment uses that date. Later installments use its day-of-month as the monthly anchor, clipped to the final calendar day when a month lacks that day. For example, a January 30 first repayment date produces January 30, February 28 or 29, March 30, April 30, and the same anchored sequence thereafter. Salary Advance retains its `ON_SALARY_DATE` product timing.

---

## 12. Business Quality Requirements

Sections 5 through 8 define Meridian's business concepts and required evidence. `MER-DB-001` owns the high-level logical data model; Flyway owns the executable physical schema, `MER-DB-CURRENT-SCHEMA.sql` is its current human-readable snapshot, and the architecture documents own context placement and communication.

| Category | Requirement |
|---|---|
| Security | Protected actions require authenticated identity and narrow permissions; Customer-digital operations enforce ownership, while Staff-assisted operations authorize the Staff actor and selected Customer subject separately |
| Privacy | APIs, logs, errors, audit, and history disclose only the minimum purpose-authorized personal, employment, financial, collateral, and document data |
| Integrity | Status, offer, contract, disbursement, schedule, payment, and exposure rules must preserve their stated invariants |
| Atomicity | A business outcome that combines workflow, financial, document, exposure, history, or audit effects must not commit partially |
| Idempotency | Retried offer, contract, disbursement, correction, and repayment commands must not duplicate business effects |
| Concurrency | Competing requests must preserve one blocking application, one active workflow outcome, one disbursement, and consistent financial evidence |
| Auditability | Important actions are attributable to actor, business subject or reference, time, reason, and operation; Staff-assisted actions preserve Staff actor and Customer subject separately |
| Availability | Failure of an external provider or worker must not corrupt business state; any permitted manual fallback remains visible and controlled |

---

## 13. MVP Boundaries and Controlled Future Decisions

### 13.1 Required MVP Business Capabilities

The MVP business target includes every capability defined as in scope in Section 3.3 and every requirement in Sections 9 and 10.

Salary Advance receives full product depth through Customer-digital origination. UCL and Collateral Loan retain the same common lifecycle and streamlined product-specific verification and manual review while supporting both Customer-digital and Staff-assisted paper origination.

### 13.2 Optional Future Enhancements

The following capabilities may be added without changing the core lending lifecycle:

- Customer application-history and Staff queue dashboards beyond the minimum operational views;
- broader notification channels and delivery management beyond Section 3.7;
- OCR coverage beyond the reviewed, advisory Staff-intake capability in Section 5.10;
- lightweight analytics;
- a mobile client.

Extensions must preserve the OCR authority boundary in Section 5.10 and Identity/Notification ownership in Section 3.7. Optional enhancements do not make those accepted MVP capabilities optional.

### 13.3 Excluded from the MVP

- persistent save-and-return LoanApplication drafts; direct origination and the separate persisted Staff-assisted intake remain in scope;
- post-submission financial-term amendments, including `APPLICATION_TERMS` correction and reservation adjustment;
- generic role creation, permission CRUD or matrix editing, generic configuration centers, arbitrary policy builders, and generic audit products; concrete administration remains bounded by Section 3.6;
- broader notification-platform capabilities such as lending-event notifications, SMS or in-app delivery, preferences, durable delivery status, and retry management; the four Identity/security email purposes in Section 3.7 remain in scope, while correction deadlines, reminders, and notifications are outside the MVP;
- real bank-transfer, payment-gateway, payroll, employer-API, HR-synchronization, or bank-reconciliation integrations;
- real SMS OTP, biometric authentication, or production identity verification;
- credit-bureau integration, automated credit scoring, or fully automated approval;
- full collateral valuation, custody, registration, notarization, repossession, liquidation, or legal enforcement;
- double-entry ledger, unapplied cash, suspense, reversal, refund, write-off, or production accounting;
- production compliance case management;
- full electronic signature or legal agreement-execution platform;
- Customer-account claiming or automatic conversion of a Staff-assisted application into the Customer-digital channel;
- integrated scanner hardware, a generic paper-form designer, or arbitrary document-template management;
- microservice deployment requirements;
- full mobile delivery;
- savings, entrusted, corporate, or other non-lending products.

### 13.4 Collateral Loan Boundary Guard

Section 11.5 owns Collateral Loan pricing, Section 11.7 owns shared provisional items and UCL/Collateral final schedule dates, and Section 6.10 owns servicing behavior. Those policies do not introduce automated loan-to-value assessment, a counteroffer, discounted settlement, custody, valuation, enforcement, more than one Collateral asset, or an outstanding Collateral LoanAccount origination restriction. Each capability requires a separately approved business policy before it enters the MVP boundary.

### 13.5 Post-MVP Product Extensions

Post-MVP product work may introduce multi-level approval, automated repayment simulation, simple collateral loan-to-value validation, and the agreement-execution or external integrations excluded in Section 13.3. Each capability requires an approved business specification before it enters Meridian's executable product contract. `MER-TRACK-001` owns follow-up identifiers, implementation status, rationale, and remaining work.

---

## 14. Design Principles

1. Use one common lending lifecycle for every supported product and allowed origination channel.
2. Keep product and origination-channel variation explicit in business policy.
3. Treat Salary Advance as the flagship and deepest MVP product, with Customer-digital origination only.
4. Keep UCL and Collateral Loan streamlined but complete at the common-lifecycle level, with Customer-digital and Staff-assisted paper origination.
5. Preserve clear ownership among Customer, Partner, Loan, Approval, Document, Audit, and Notification.
6. Separate upload completeness, manual document review, processing readiness, product verification, review recommendation, and approval decision.
7. Separate approval, Customer acceptance, contract readiness, and disbursement.
8. Preserve immutable historical financial terms, contract versions, schedules, payments, allocations, and audit evidence.
9. Prefer manual review and operational confirmation over unsupported automation.
10. Keep real integrations and production banking features outside the MVP.
11. Make retries, concurrency, and failure behavior part of the business rule rather than an implementation afterthought.
12. Keep Customer-facing steps understandable without weakening business control.
13. Preserve actor and subject separately: Staff-assisted processing records Staff as actor and Customer as business subject without impersonation.
14. Treat paper evidence and OCR extraction as inputs to human-confirmed business actions, not as authoritative Customer, document, verification, or approval state by themselves.
