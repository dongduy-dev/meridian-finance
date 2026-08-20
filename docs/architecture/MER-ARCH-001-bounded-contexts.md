# Meridian Bounded Context Design — DDD Context Map

## Purpose and Interpretation

This document defines Meridian's intended bounded contexts, ownership boundaries, public capabilities, and collaboration rules.

Public capabilities are application-level contracts exposed by a context. They do not prescribe exact Java interface names, method signatures, HTTP endpoints, or package structures.

Entity and event names are representative architecture vocabulary unless another authoritative specification defines an exact contract. Implementation and delivery status belong in the project roadmap and follow-up register.

`shared` is a technical shared kernel, not a bounded context. It may contain only minimal, stable cross-cutting abstractions such as common exceptions, actor representations, audit contracts, time configuration, and generic value types. It must not own lending behavior or depend on a feature context.

---

## Context Map Overview

```mermaid
graph TB
    subgraph Core["Core Domain"]
        LOAN["Loan Core / Lending Lifecycle"]
        APPROVAL["Approval Workflow"]
    end

    subgraph Supporting["Supporting Domains"]
        IAM["Identity & Access"]
        CUSTOMER["Customer Management"]
        PARTNER["Partner Management"]
        DOC["Document Management"]
        AUDIT["Audit & Compliance Controls"]
    end

    subgraph Generic["Generic Subdomains"]
        NOTIF["Notification"]
    end

    CUSTOMER -->|Customer identifier for account association| IAM

    IAM -->|Authenticated actor and authorization facts| LOAN
    IAM -->|Authenticated actor and authorization facts| APPROVAL
    IAM -->|Authenticated actor and authorization facts| CUSTOMER
    IAM -->|Authenticated actor and authorization facts| PARTNER
    IAM -->|Authenticated actor and authorization facts| DOC

    CUSTOMER -->|Customer readiness and purpose-limited bank-account facts| LOAN
    CUSTOMER -->|Identity evidence for employment verification| PARTNER
    PARTNER -->|Verified employee links and eligibility facts| LOAN
    DOC -->|Checklist state and processing-readiness facts| LOAN

    LOAN -->|Application and active review-cycle context| APPROVAL
    APPROVAL -->|Recommendation and decision outcomes| LOAN
```

Business contexts may publish PII-safe auditable facts to Audit & Compliance Controls.

Business contexts may publish notification-triggering events to Notification without transferring workflow ownership.

---

## Strategic Classification

| Classification | Contexts | Rationale |
|---|---|---|
| **Core Domain** | Loan Core / Lending Lifecycle, Approval Workflow | Contains Meridian's differentiating lending rules, controlled decision workflow, product behavior, financial state, and servicing lifecycle. |
| **Supporting Domain** | Identity & Access, Customer Management, Partner Management, Document Management, Audit & Compliance Controls | Provide identity, Customer, employment, evidence, and compliance capabilities required by the lending workflow. |
| **Generic Subdomain** | Notification | Provides reusable communication capabilities without owning lending decisions or workflow state. |

---

## 1. Identity & Access (IAM) — Supporting Domain

| Aspect | Detail |
|---|---|
| **Responsibilities** | User registration, authentication, access-token issuance and validation, refresh-token and session lifecycle, logout and revocation, account status and security controls, roles, permissions, role assignments, and authorization facts. |
| **Owns** | User accounts, credentials and credential metadata, role and permission definitions, role assignments, session records, refresh-token records, account-security state, and the association between a user account and an optional Customer identity. |
| **Public Capabilities** | Authenticate a principal; issue, refresh, validate, and revoke sessions or tokens; resolve the authenticated actor; query authorization facts; manage users, roles, permissions, and account status. |
| **Publishes** | Representative events include user registration, authentication success or failure, role assignment, session revocation, account suspension, and account reactivation. |
| **Consumes** | A Customer identity identifier when associating a user account with a Customer; administrative inputs used to create or manage Staff accounts and role assignments. |
| **Must Not Own** | Customer profile data, Partner employee data, Loan applications, approval decisions, documents, or lending permissions implemented as domain rules outside the authorization model. |

Identity remains the only owner of the login-to-Customer association. Customer owns the Customer aggregate and its business data and must not maintain a competing association.

---

## 2. Customer Management — Supporting Domain

| Aspect | Detail |
|---|---|
| **Responsibilities** | Customer lifecycle, profile management, profile completeness, identity and verification status, bank-account management, Customer ownership checks, and protection of sensitive Customer data. |
| **Owns** | `Customer`, Customer profile information, verification state, Customer status, bank accounts, primary-account designation, and source identity or bank-account evidence. |
| **Public Capabilities** | Manage a Customer's own profile and bank accounts; query Customer readiness for lending; resolve purpose-limited identity evidence for employment verification; provide eligible bank-account facts for contract preparation and disbursement. |
| **Publishes** | Representative events include Customer created, profile updated, verification status changed, bank account added or deactivated, primary bank account changed, and Customer suspended or reactivated. |
| **Consumes** | Authenticated Customer identity and authorization facts from Identity & Access. |
| **Must Not Own** | User credentials, Partner employee relationships, `LoanApplication` state, lending exposure, operational contracts, approval decisions, or repayment servicing. |

Customer owns source identity and bank-account information and protects sensitive values at rest. Other contexts receive only purpose-limited facts, masked representations, or explicitly protected values through narrow application contracts.

Audit records may contain only PII-safe identifiers, statuses, reason codes, and timestamps required for the audited action. Audit must not become a secondary store of unrestricted Customer evidence.

---

## 3. Partner Management — Supporting Domain

| Aspect | Detail |
|---|---|
| **Responsibilities** | Partner Company lifecycle, Partner Employee source data, monthly employee imports, import validation, employment matching, authorized manual-review outcomes, and reusable Customer–Partner Employee relationships used for Salary Advance eligibility. |
| **Owns** | `PartnerCompany`, `PartnerEmployee`, employee import batches and row outcomes, reusable `CustomerPartnerEmployeeLink`, employment-verification evidence, and Partner-owned eligibility facts. |
| **Public Capabilities** | Manage Partner Companies; import and validate employee data; verify Customer employment; query or refresh an active verified employee link; provide purpose-limited eligibility facts to Loan. |
| **Publishes** | Representative events include Partner Company activated or suspended, employee import completed, Customer employee link verified, refreshed, suspended, rejected, or expired. |
| **Consumes** | Purpose-limited Customer identity evidence and authenticated actor information. |
| **Must Not Own** | Salary Advance limit state, `LoanApplication` verification snapshots, lending exposure, `LoanAccount` state, approved offers, or repayment servicing. |

Partner owns the reusable Customer-to-Partner Employee relationship because it answers whether a Customer is verified as an employee of a Partner Company.

Loan may reference that relationship by identifier and consume eligibility facts through application-level contracts. Loan must not own or duplicate Partner Employee source records.

---

## 4. Loan Core / Lending Lifecycle — Core Domain

Loan Core owns the lending lifecycle from product eligibility and application submission through disbursement, servicing, contractual payoff or Administrative Full-Balance Settlement, and administrative closure.

Salary Advance, Unsecured Consumer Loan, and Collateral Loan are product behaviors inside Loan Core rather than separate top-level bounded contexts. Product policies specialize behavior that differs by product while preserving the common lending lifecycle.

| Aspect | Detail |
|---|---|
| **Responsibilities** | Loan product definitions and policy configuration; `LoanApplication` lifecycle; product-specific application data, eligibility, and verification snapshots; Salary Advance limit and exposure; review-cycle and correction workflow state; approved offers; operational contracts and readiness; disbursement evidence; `LoanAccount` activation; final repayment schedules; repayment transactions and allocations; overdue servicing; contractual payoff; Administrative Full-Balance Settlement; administrative closure; and application, account, and installment histories. |
| **Owns** | `LoanProduct`, product-policy configuration, `LoanApplication`, product-specific application details, product verification snapshots, `SalaryAdvanceLimit` and limit movements, review cycles, correction requests and tasks, approved offers, operational loan contracts, immutable contract-bound destinations, disbursement evidence, `LoanAccount`, final repayment schedules, repayment transactions, allocations, servicing progress, contractual-payoff evidence, Administrative Full-Balance Settlement evidence, administrative-closure evidence, and lifecycle histories. Product-specific application details include one Collateral Loan asset's type, description, estimated value, ownership status, and condition facts. |
| **Public Capabilities** | Query products and eligibility; create or save drafts; submit applications; query application state and Salary Advance limits; start Loan Officer review; manage correction workflows and resubmit completed corrections; apply recommendation and approval outcomes; view and respond to offers; prepare and acknowledge contracts; confirm contract readiness; record manual disbursement; query LoanAccounts and schedules; record and query repayments; evaluate overdue state; perform contractual payoff or Administrative Full-Balance Settlement; and close eligible settled accounts administratively. |
| **Publishes** | Representative events include application submitted, verification recorded, limit reserved or released, review started, correction requested, recommendation applied, application approved or rejected, offer generated or resolved, contract prepared or acknowledged, readiness confirmed, loan disbursed, repayment recorded, account status changed, account settled by contractual payoff or Administrative Full-Balance Settlement, and account closed administratively. |
| **Consumes** | Customer readiness and eligible bank-account facts; Partner employee-link and eligibility facts; Document checklist and processing-readiness facts; Approval recommendation and decision outcomes; authenticated actor and authorization facts. |
| **Must Not Own** | User credentials, Customer source profile or bank-account aggregates, Partner Employee source records, document binaries or document-review decisions, or Approval's immutable recommendation and decision records. |

### LoanApplication and LoanAccount State Ownership

`LoanApplication` governs origination from draft or submission through verification, document readiness, controlled review, approval, Customer acceptance, contract readiness, disbursement, and pre-disbursement terminal outcomes.

After disbursement, `LoanAccount` becomes the authoritative servicing aggregate. It moves among `ACTIVE`, `OVERDUE`, `SETTLED`, and `CLOSED` according to repayment, overdue, contractual-payoff, Administrative Full-Balance Settlement, and administrative-closure policies.

`LoanApplication` status must not become the source of truth for post-disbursement balances or servicing state.

### Product-Specific Application Data

Loan owns the structured lending facts needed to evaluate and service a product.

Document Management owns uploaded supporting files, document versions, and document-review decisions. For Collateral Loan, Document owns the required ownership-evidence file and its review state, while Loan owns the submitted ownership status and other structured Collateral facts. A supporting document may evidence a Loan-owned fact without transferring that lending concept to Document Management.

### Salary Advance Ownership

Partner owns Partner Companies, Partner Employees, employee imports, and the reusable Customer employee link.

Loan owns Salary Advance lending state:

- total, used, reserved, and available limit;
- limit status and movements;
- reservation and release;
- disbursement conversion from reserved to used exposure;
- repayment-driven principal exposure release;
- application-level verification snapshots.

The application verification snapshot records the Partner relationship and limit evidence used for one `LoanApplication`. It does not replace the reusable Partner-owned employee relationship or the current Loan-owned limit account.

### Contract and Disbursement Destination Ownership

Customer owns mutable source bank-account data.

Loan owns the immutable, contract-bound disbursement destination used after Customer acceptance. Loan obtains eligible destination facts through a narrow, purpose-limited Customer contract. It must not access Customer persistence or reuse Customer's encryption ownership.

A material change to a contract-bound destination before readiness requires a new contract version. Supersession must not silently alter accepted financial terms, repayment items, or Customer acknowledgment evidence.

### Product Policy Ownership

Loan's common lifecycle remains generic. Product policies own only behavior that legitimately differs by product, including:

- eligibility and required evidence;
- amount and term constraints;
- pricing and repayment construction;
- activation effects;
- exposure reservation and release;
- collateral-specific controls;
- contractual-payoff, Administrative Full-Balance Settlement, and administrative-closure effects.

A product policy must not bypass common lifecycle, security, audit, document-readiness, or maker-checker controls.

---

## 5. Approval Workflow — Core Domain

| Aspect | Detail |
|---|---|
| **Responsibilities** | Authoritative Loan Officer recommendation records, Approver decision records, decision authority, maker-checker controls, structured correction intent, and immutable recommendation and decision history. |
| **Owns** | `ReviewRecommendation`, `ApprovalDecision`, controlled reason codes, decision metadata, decision authority evidence, and the immutable trail linking a recommendation to its resulting decision. |
| **Public Capabilities** | Record a Loan Officer recommendation; record an Approver decision; validate maker-checker separation; query recommendation and decision history; publish structured outcomes for Loan to apply. |
| **Publishes** | Representative events include recommendation recorded, approval decision recorded, correction requested, returned to Loan Officer review, approved, and rejected. |
| **Consumes** | `LoanApplication` identity, the Loan-owned active review-cycle identifier, application context required for the decision, and authenticated Staff identity and authorization facts. |
| **Must Not Own** | `LoanApplication` status, review-cycle lifecycle, correction tasks, resubmission, product revalidation, approved offers, contracts, disbursement, or `LoanAccount` servicing. |

Loan owns the active review cycle and every `LoanApplication` transition.

Approval owns the immutable recommendation and decision records. It references the Loan-owned application and active review cycle by identifier and publishes structured outcomes for Loan to apply to the `LoanApplication` lifecycle.

A recommendation or decision must not directly mutate Loan-owned persistence.

---

## 6. Document Management — Supporting Domain

| Aspect | Detail |
|---|---|
| **Responsibilities** | Application checklists, checklist items, document upload and storage, logical documents, immutable document versions, current-version selection, metadata, authorized content access, manual review, replacement, waiver, expiration, and processing readiness. |
| **Owns** | Application document checklists, checklist items, logical documents, immutable versions, storage references, review decisions, review status, replacement and waiver evidence, and document-processing results. |
| **Public Capabilities** | Create and query checklists; upload and retrieve authorized document content; review a document version; accept, waive, or request replacement; query upload completeness and processing readiness; provide narrow readiness facts to Loan. |
| **Publishes** | Representative events include document uploaded, version superseded, document reviewed, replacement requested, checklist upload-complete, and checklist processing-ready. |
| **Consumes** | `LoanApplication` ownership and workflow facts, correction-task proof, and authenticated Customer or Staff authorization facts. |
| **Must Not Own** | `LoanApplication` status, review cycles, correction requests or tasks, product eligibility, approval decisions, contract readiness, lending exposure, or the structured lending facts merely evidenced by uploaded documents. |

### OCR-Assisted Processing Boundary

OCR-assisted processing belongs inside Document Management as an advisory document-processing capability rather than a separate top-level bounded context.

Document Management may own OCR jobs, extracted text, parsed fields, confidence scores, and processing history. OCR results remain Document-owned evidence.

OCR must not independently:

- approve or reject a `LoanApplication`;
- mark a checklist item accepted;
- waive required evidence;
- decide processing readiness;
- mutate `LoanApplication` state.

Authorized review remains the source of checklist acceptance, replacement, waiver, and readiness decisions. Loan consumes checklist and readiness facts rather than raw OCR results. Document remains the owner of OCR evidence.

---

## 7. Audit & Compliance Controls — Supporting Cross-Cutting Domain

| Aspect | Detail |
|---|---|
| **Responsibilities** | Immutable records of important business actions, actor and time evidence, compliance-oriented state-change history, and secure audit querying. Audit observes business outcomes and never becomes a workflow source of truth. |
| **Owns** | Append-only audit events, PII-safe audit payloads, audit correlation identifiers, retention and archival policy, and compliance-oriented query models. |
| **Public Capabilities** | Record an auditable business action; query authorized audit history; correlate actions belonging to one business operation; support retention, archival, and compliance review. |
| **Consumes** | Explicit, PII-safe auditable facts published by business contexts. |
| **Must Not Own** | `LoanApplication` state, balances, approval authority, document readiness, Customer evidence, Partner eligibility, or commands that change another context. |

Audit recording must either participate in the originating transaction or use durable delivery with defined retry and reconciliation. The selected mechanism must prevent a completed business outcome from becoming permanently unaudited.

Audit payloads must remain closed and purpose-limited. Audit is not a secondary document store, Customer evidence store, or financial ledger.

---

## 8. Notification — Generic Subdomain

| Aspect | Detail |
|---|---|
| **Responsibilities** | Message templates, notification requests, channel selection, delivery attempts, delivery status, retry policy, and Customer or Staff communication preferences. |
| **Owns** | `Notification`, `NotificationTemplate`, delivery attempts, channel-specific delivery metadata, and notification status. |
| **Public Capabilities** | Request a notification; render a template; select an eligible channel; deliver or retry a message; query delivery status. |
| **Consumes** | Notification-triggering business events from Identity, Customer, Partner, Document, Approval, and Loan. |
| **Must Not Own** | Lending workflow state, approval decisions, eligibility, repayment balances, or the business rule that determines whether an event occurred. |

A notification failure must not rewrite or reverse the business outcome that triggered the message. The publishing context remains authoritative for that outcome.

---

## Context Ownership Matrix

| Capability or Information | Authoritative Context |
|---|---|
| User accounts, credentials, roles, permissions, sessions | Identity & Access |
| Login-to-Customer association | Identity & Access |
| Customer profile, verification state, source bank accounts | Customer Management |
| Partner Companies and Partner Employees | Partner Management |
| Employee import batches and employment matching | Partner Management |
| Reusable Customer–Partner Employee link | Partner Management |
| Loan products and product policies | Loan Core |
| `LoanApplication` lifecycle and status | Loan Core |
| Product-specific structured application and lending facts | Loan Core |
| Salary Advance limit and exposure movements | Loan Core |
| Application-level eligibility and verification snapshots | Loan Core |
| Review cycles, correction requests, tasks, and resubmission | Loan Core |
| Loan Officer recommendation and Approver decision records | Approval Workflow |
| Approved offers and Customer response state | Loan Core |
| Operational contracts and contract-bound destinations | Loan Core |
| Contract readiness and disbursement evidence | Loan Core |
| LoanAccounts, schedules, repayments, overdue state, contractual payoff, Administrative Full-Balance Settlement, administrative closure | Loan Core |
| Checklists, document versions, review decisions, readiness | Document Management |
| OCR jobs and extracted document evidence | Document Management |
| Immutable cross-cutting audit evidence | Audit & Compliance Controls |
| Templates and message-delivery state | Notification |

---

## Context Communication Rules

This section defines collaboration at the bounded-context level. `MER-ARCH-003-dependency-rules.md` owns Java and package dependency rules and their enforcement.

| Collaboration | Rule | Boundary |
|---|---|---|
| **Synchronous application contracts** | A context may query immediate authentication, authorization, ownership, readiness, eligibility, or other purpose-limited facts through another context's public application contract. | The caller must not mutate the provider's aggregates or use its repositories, persistence entities, or internal services. |
| **Business events** | A context may publish a state-change fact for another context. Transaction-participating consumers preserve the originating outcome's atomicity; durable asynchronous consumers handle delivery idempotently. Both preserve the publisher's authority over the originating state. | A consumer projection must not become the publishing context's source of truth. |
| **References and data** | Contexts exchange stable identifiers and the minimum immutable facts required by the collaboration. | They must not exchange cross-context aggregate object graphs or unrestricted sensitive evidence. |
| **Persistence authority** | Each context owns its aggregates, repositories, and tables. | Contexts must not share aggregate ownership, create cross-context JPA relationships, perform direct cross-context joins, or use another context's persistence as their own model. |
| **Reliability** | Transaction-participating collaboration or durable asynchronous delivery must define atomicity, retry, idempotency, ordering, reconciliation, and replay behavior required by the business outcome. | Business-critical coordination must not rely on fire-and-forget delivery without defined failure and recovery semantics. |
| **Security and privacy** | Contracts expose only the facts required for the caller's purpose and apply ownership and authorization checks. Sensitive values remain masked or protected according to their owner. | Contexts must not propagate unrestricted PII or expose infrastructure secrets through public contracts. |

Public application contracts and event schemas must evolve without exposing internal classes, tables, storage keys, or implementation frameworks. `MER-ARCH-003-dependency-rules.md` defines the corresponding code-level dependency restrictions and enforcement.

### Identity Coordination

Identity supplies authenticated actor and authorization facts to protected contexts.

Each business context remains responsible for its own ownership and business-rule checks. A permission authorizes an attempted capability; it does not prove that the requested Customer, application, document, contract, or account belongs to the actor.

### Document and Correction Coordination

Document owns checklist and document evidence, versions, review decisions, and processing readiness.

Loan owns `LoanApplication` state, review cycles, correction requests and tasks, resubmission, and product revalidation. Approval owns immutable recommendation and decision records and may produce structured correction intent.

The contexts collaborate through identifiers and public application contracts. Document does not change `LoanApplication` status, and Loan does not decide document acceptance by modifying Document-owned evidence.

---

## Extraction Principles

Bounded contexts are logical ownership boundaries inside the modular monolith. They do not require independent deployment.

Consider extracting a context only when:

- its application contracts are stable;
- data ownership is already independent;
- cross-context consistency requirements are understood;
- operational scale or organizational ownership justifies the cost;
- retry, idempotency, observability, and failure-handling requirements are designed.

Strategic extraction suitability:

| Context | Extraction Suitability |
|---|---|
| Identity & Access | High |
| Notification | High |
| Customer Management | Medium to high |
| Partner Management | Medium to high |
| Document Management | Medium to high |
| Audit & Compliance Controls | Medium |
| Approval Workflow | Medium |
| Loan Core / Lending Lifecycle | Low; consider last |

The modular monolith remains the preferred deployment model while Meridian benefits from strong transactional consistency, simpler operations, and close collaboration among the core lending contexts.
