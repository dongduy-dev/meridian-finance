# MER-TRACK-001 - Meridian Follow-up Register

## Purpose

This file tracks known Meridian gaps, deferred work, risks, docs/code mismatches, and planned future slices so the project does not rely on memory. Items here should be updated as work is started, completed, accepted as risk, or superseded by GitHub Issues or PR decisions.

## Priority Guide

* P0: must fix before next major feature / urgent patch on main.
* P1: fix before next major workflow milestone.
* P2: planned future module/slice.
* P3: documentation or nice-to-have.

## Status Guide

* Open
* In Progress
* Done
* Deferred
* Accepted Risk

## Open Items

### MER-FU-001 - Lock down sensitive Partner and Salary Advance endpoints

Area: Security / Identity / Partner / Loan

Type: Security risk

Priority: P0

Status: Done

Blocks next major feature: No

Problem:
Sensitive Partner employee and Salary Advance application endpoints were public through SecurityConfig permitAll rules.

Risk:
Unauthenticated callers could perform employee verification and submit Salary Advance applications.

Resolution:
`SecurityConfig` now keeps only health, login, and loan product catalog endpoints public. Partner Company, Partner Employee, employee verification, import batch, and Salary Advance application endpoints require JWT Bearer authentication. Endpoint-specific role/permission checks are enforced through method security where implemented.

Notes:
This item started as the minimal authenticated gate. MER-FU-015 has since completed JWT/RBAC endpoint permissions and token-derived customer identity for customer-owned flows. Refresh tokens, logout invalidation, account hardening, and auth event auditing remain tracked separately.

Suggested future branch name:
`fix/identity-rbac-endpoint-permissions`

### MER-FU-002 - Remove or split PII-heavy Partner employee DTOs from public responses

Area: Partner / API / PII

Type: Security risk

Priority: P0

Status: Done

Blocks next major feature: No

Problem:
Partner employee API responses exposed employee code, identity reference, salary, and limit data while the endpoint was public.

Risk:
Sensitive employment and salary data could leak through public/customer-facing endpoints.

Resolution:
Detailed `PartnerEmployeeDto` remains only on the protected Partner Employee query endpoint, which is treated as an internal/admin surface. The customer-facing employee verification response now returns only IDs, outcome, link status, and manual-review flag; it no longer returns salary amount, salary advance limit, identity reference, employee code, or raw matching evidence.

Notes:
Internal Partner-to-Loan eligibility snapshots still carry salary and limit values where needed for Salary Advance limit calculation. Those snapshots are not REST customer response DTOs.

### MER-FU-003 - Reject inactive Partner Companies during employee verification

Area: Partner / Salary Advance Eligibility

Type: Business-rule mismatch

Priority: P0

Status: Done

Blocks next major feature: No

Problem:
Employee verification checked partner company existence but not active status.

Risk:
Inactive Partner Companies could still be used for normal Salary Advance eligibility.

Resolution:
`PartnerEmployeeVerificationPolicy` now rejects non-active Partner Companies with `PARTNER_COMPANY_INACTIVE` before import-batch lookup, employee matching, link creation, or manual-review routing. This keeps inactive Partner Companies as a hard stop for normal Salary Advance eligibility.

### MER-FU-004 - Request-provided customerId is temporary until auth ownership exists

Area: Identity / Customer / Loan

Type: Security/ownership risk

Priority: P1

Status: Done

Blocks next major feature: No

Problem:
Salary Advance application and employee verification requests accepted customerId from the caller.

Risk:
Without authentication and customer ownership enforcement, callers could submit applications or verify employee evidence for arbitrary customers.

Resolution:
IAM/RBAC foundation now derives customerId from the authenticated customer token through `CurrentUserProvider`. `PartnerEmployeeVerificationRequest` and `SalaryAdvanceApplicationRequest` no longer accept request-provided customerId.
### MER-FU-005 - Add security/controller tests for sensitive endpoints

Area: Testing / Security

Type: Test gap

Priority: P1

Status: Done

Blocks next major feature: No

Problem:
Security and controller coverage was thin.

Resolution:
IAM/RBAC foundation adds focused coverage for public login/catalog access, anonymous access denial, authenticated-but-unauthorized 403 responses, permission-specific Partner access, customer Salary Advance access, safe employee verification response shape, token issue/parse/expiry behavior, login failure behavior, and security architecture boundaries.

Notes:
Continue expanding controller matrices as new workflow endpoints are implemented.
### MER-FU-006 - Align API docs with implemented endpoints

Area: Documentation / API

Type: Documentation gap

Priority: P1

Status: Done

Blocks next major feature: No

Problem:
API docs did not list implemented employee verification and Salary Advance application endpoints.

Resolution:
`docs/architecture/MER-ARCH-006-api-request-flow-and-dependencies.md` now documents method, path, current security posture, request/response shape, and safe PII behavior for:

* `POST /api/v1/partner-companies/{partnerCompanyId}/employee-verifications`
* `POST /api/v1/loan-applications/salary-advance`

### MER-FU-007 - Align ERD terminology drift

Area: Database docs

Type: Documentation mismatch

Priority: P2

Status: Done

Blocks next major feature: No

Problem:
The logical ERD used conceptual names and target fields that could be mistaken for the current physical schema.

Resolution:
`MER-DB-001` now labels Sections 1-13 as a logical/current-plus-target model, maps conceptual disbursement and repayment names to the current V42 physical structures, and identifies deferred `product_details`, refresh-token, Collateral, and OCR structures. Flyway history and `MER-DB-CURRENT-SCHEMA.sql` remain the physical authorities.

### MER-FU-008 - Replace hardcoded Salary Advance salary cap/policy terms with policy config

Area: Loan / Product Policy

Type: Design improvement

Priority: P2

Status: Open

Blocks next major feature: No

Problem:
Salary Advance policy currently uses hardcoded salary cap and term values instead of parsed product policy configuration.

Recommendation:
Keep acceptable for current foundation if documented, then later move product-specific configurable rules into loan product policy config.

### MER-FU-009 - Implement Partner eligibility manual review workflow

Area: Loan / Partner / Review

Type: Deferred feature

Priority: P2

Status: Open

Blocks next major feature: No

Problem:
Document review queues, immutable version decisions, waivers/replacements, and Customer/Staff/mixed correction workflows are implemented. A dedicated Partner eligibility manual-review queue and authoritative outcome workflow for ambiguous employee matching are not implemented.

Recommendation:
Keep this follow-up scoped to Partner eligibility. Define assignment, evidence, maker-checker, outcome, expiry/revalidation, audit, and concurrency rules before implementation; do not duplicate the implemented Document review/correction workflow.

### MER-FU-010 - Implement review/approval/customer acceptance/disbursement lifecycle

Area: Loan workflow

Type: Deferred feature

Priority: P2

Status: Done

Blocks next major feature: No

Problem:
Application creation, review/recommendation, approval, accepted offers, document/correction readiness, immutable operational contract readiness, and Salary Advance manual-disbursement activation now exist. Confirmation atomically creates the LoanAccount/final schedule, converts reserved exposure to used exposure, transitions to `DISBURSED`, and records audit/history. The secured confirmation, pre-confirmation destination reveal, and owned/staff account query APIs are complete.

Recommendation:
Continue implementation in vertical slices:

1. Loan officer review/recommendation. Done.
2. Approval decision. Done.
3. Salary Advance approved offer and customer acceptance. Done.
4. Document/correction readiness through `MER-FU-012` and `MER-FU-031`. Done in V22-V24.
5. Contract readiness and `CONTRACT_PENDING → DISBURSEMENT_PENDING`. Done in V25-V26.
6. Manual disbursement confirmation and LoanAccount activation. Done in V28-V31.
7. Salary Advance repayment, overdue servicing, contractual payoff, Administrative Full-Balance Settlement, and administrative LoanAccount closure. Done through V36.
8. Customer-owned cancellation of a returned Salary Advance correction with exact reservation release. Done in V37.

### MER-FU-011 - Implement repayment tracking

Area: Loan / Repayment

Type: Deferred feature

Priority: P2

Status: Done

Blocks next major feature: No

Completion:
The authoritative final repayment schedule is immutable obligation evidence. Salary Advance and UCL support manual repayment posting, deterministic allocation, paid/outstanding tracking, contractual payoff to `SETTLED`, date-driven overdue evaluation, secured servicing reads, payment-backed Administrative Full-Balance Settlement, and separate administrative closure to `CLOSED`. Salary Advance alone performs exact principal used-exposure release; UCL reports zero product exposure release and creates no Salary Advance movement.

Evidence:
V32-V35 provide repayment, durable outcome, exposure-release, history, audit, and overdue-candidate foundations. V36 distinguishes settlement payment transactions, adds immutable approved-settlement and closure evidence, permissions, lifecycle vocabulary, and reciprocal deferred reconciliation. V42 makes the existing reconciliation functions product-aware so Salary Advance preserves exact release semantics and UCL enforces zero exposure release. The APIs preserve scheduled obligations separately from payment/allocation evidence and enforce ownership, role, and permission boundaries.

Deferred boundary:
Discounted or negotiated settlement, concession, reversal, refund, waiver, write-off, suspense/unapplied cash, payment/bank integration, reconciliation, ledger, collections, notifications, and Collateral servicing remain separate future capabilities. Administrative Full-Balance Settlement and LoanAccount closure are complete for the approved Salary Advance and UCL semantics.

### MER-FU-012 - Implement document checklist and manual document review foundation

Area: Document

Type: Workflow foundation

Priority: P2

Status: Done

Blocks next major feature: No

Completion:
V22-V24 implement code-configured per-application checklists, an on-demand
`RECENT_PAYSLIP` correction requirement, immutable document versions, safe local
storage behind a port, Customer and authorized-Staff upload, version-targeted manual
acceptance/waiver/replacement, separate upload-completeness and processing-readiness
contracts, content authorization, audit, rollback, and PostgreSQL concurrency proof.

OCR was intentionally not included. It is tracked separately by `MER-FU-033`;
production storage, malware scanning, and retention hardening are `MER-FU-032`.
### MER-FU-013 - Implement audit trail and Loan Application lifecycle history

Area: Audit / Workflow

Type: Deferred feature

Priority: P2

Status: Done

Blocks next major feature: No

Problem:
No full audit trail or Loan Application lifecycle history tables existed for the implemented Salary Advance workflow.

Resolution:

V17 adds append-only `audit_events` for important cross-cutting business actions and Loan-owned `loan_application_status_transitions` for ordered Loan Application status changes. Later increments extend transactional audit/history through document correction, contract readiness, manual disbursement, repayment servicing, overdue evaluation, and exact payoff, including LoanAccount and installment histories. Current listeners are synchronous in the originating transaction. Future slices should extend this foundation rather than create duplicate generic histories.

### MER-FU-014 - Create current physical schema snapshot

Area: Database documentation

Type: Documentation improvement

Priority: P1

Status: Done

Blocks next major feature: No

Problem:
Flyway migrations are growing and current schema is harder to inspect from migrations alone.

Recommendation:
`docs/database/MER-DB-CURRENT-SCHEMA.sql` now tracks the current physical schema through V42. This file is documentation only and must not be placed in the Flyway migration folder.

### MER-FU-015 - Replace temporary HTTP Basic authenticated gate with JWT/RBAC endpoint permissions

Area: Identity / Security / API

Type: Deferred feature / security hardening

Priority: P1

Status: Done

Blocks next major feature: No

Problem:
The P0 security patch protected sensitive endpoints with the current Spring Security authenticated gate and HTTP Basic development authentication. It did not enforce JWT authentication, role/action permissions, or customer ownership.

Resolution:
IAM/RBAC foundation replaces the Basic gate with JWT Bearer authentication, database-backed demo users, role-permission seed data, method-level permission checks, token-derived customer identity for customer-owned flows, and focused security tests.

Notes:
Refresh tokens, logout invalidation, account hardening, auth event auditing, and broader ownership hardening remain tracked as separate follow-ups.
### MER-FU-016 - Refresh token rotation

Area: Identity / Security

Type: Deferred feature

Priority: P2

Status: Open

Blocks next major feature: No

Recommendation:
Implement refresh token rotation after access-token-only JWT foundation is stable.

Suggested future branch name:
`feature/iam-refresh-token-rotation`

### MER-FU-017 - Password reset

Area: Identity / Security

Type: Deferred feature

Priority: P2

Status: Open

Blocks next major feature: No

Recommendation:
Implement password reset when real user lifecycle management starts.

Suggested future branch name:
`feature/iam-password-reset`

### MER-FU-018 - Email verification

Area: Identity / Customer

Type: Deferred feature

Priority: P2

Status: Open

Blocks next major feature: No

Recommendation:
Implement email verification with customer registration/profile flows.

Suggested future branch name:
`feature/iam-email-verification`

### MER-FU-019 - Admin user management UI

Area: Identity / Back Office

Type: Deferred feature

Priority: P2

Status: Open

Blocks next major feature: No

Recommendation:
Implement admin user management UI after backend user-management use cases exist.

Suggested future branch name:
`feature/admin-user-management-ui`

### MER-FU-020 - Account lockout policy

Area: Identity / Security

Type: Risk

Priority: P1

Status: Open

Blocks next major feature: No

Recommendation:
Add lockout policy before exposing login beyond local/demo use.

Suggested future branch name:
`feature/iam-account-lockout`

### MER-FU-021 - MFA

Area: Identity / Security

Type: Deferred feature

Priority: P3

Status: Open

Blocks next major feature: No

Recommendation:
Defer MFA until production-grade authentication hardening.

Suggested future branch name:
`feature/iam-mfa`

### MER-FU-022 - Full permission management UI

Area: Identity / Back Office

Type: Deferred feature

Priority: P3

Status: Open

Blocks next major feature: No

Recommendation:
Defer full permission management UI until role management needs exceed seeded MVP roles.

Suggested future branch name:
`feature/iam-permission-management-ui`

### MER-FU-023 - Customer ownership enforcement hardening

Area: Identity / Customer / Loan / Document

Type: Security hardening

Priority: P1

Status: Open

Blocks next major feature: No

Recommendation:
Current profile, bank-account, document/content, correction, offer/contract, LoanAccount, and repayment-history endpoints enforce token-derived ownership and approved concealment rules. Keep this item open only for future customer-facing surfaces and cross-endpoint consistency reviews; do not treat already-secured endpoints as unfinished.

Suggested future branch name:
`feature/customer-ownership-hardening`

### MER-FU-024 - Token blacklist / logout invalidation

Area: Identity / Security

Type: Deferred feature

Priority: P2

Status: Open

Blocks next major feature: No

Recommendation:
Implement token invalidation when logout/session management becomes in scope.

Suggested future branch name:
`feature/iam-token-invalidation`

### MER-FU-025 - Audit trail for authentication events

Area: Identity / Audit

Type: Deferred feature

Priority: P1

Status: Open

Blocks next major feature: No

Recommendation:
Extend the existing Audit foundation with a deliberately bounded authentication-event catalog (for example login success/failure and future logout/token invalidation) only after privacy, retention, rate/noise, actor, and failure-transaction semantics are approved.

Suggested future branch name:
`feature/iam-auth-audit-events`

### MER-FU-026 - Refresh Postman collection for JWT/Bearer flow

Area: Documentation / API

Type: Documentation gap

Priority: P2

Status: Done

Blocks next major feature: No

Resolution:
`docs/api/Meridian-Platform.postman_collection.json` calls login, stores role-specific Bearer token variables, uses JWT auth for protected endpoints, removes request-provided customerId payload fields, and covers the executable Salary Advance path through manual repayment, immutable history, servicing reads, overdue evaluation, exact payoff, Administrative Full-Balance Settlement, administrative closure, and post-settlement submission behavior.

Suggested future branch name:
`docs/update-postman-jwt-flow`

### MER-FU-027 - Harden Approval-to-Loan async event processing before using after-commit listeners

Area: Approval / Loan / Architecture

Type: Deferred architecture hardening

Priority: P2

Status: Open

Blocks current PR: No

Problem:
The Approval Review Recommendation slice intentionally uses same-transaction event handling so Loan status transition failures roll back the saved recommendation. Moving this coordination to after-commit or asynchronous event handling without extra state would risk persisted recommendations whose Loan status transition failed later.

Recommendation:
Before switching Approval-to-Loan review/approval coordination to after-commit or asynchronous processing, add recommendation/decision processing status, failure tracking, idempotent Loan event handling, and retry behavior.

Suggested future branch name:
`feature/approval-event-processing-hardening`

### MER-FU-028 - Automatically refresh customer employee links after completed Partner Employee imports

Area: Partner / Salary Advance Limit Refresh

Type: Deferred feature

Priority: P1

Status: Open

Blocks current PR: No

Problem:
Customer Partner Employee links are refreshed when the Customer verifies again. They are not automatically refreshed when new Partner Employee imports are completed.

Risk:
Normal Salary Advance eligibility now fails closed when a reusable link is backed by stale or non-current-month Partner evidence. This prevents stale evidence from authorizing credit, but Customers remain blocked until re-verification or a future proactive refresh process updates the link.

Completed for v0.1.0:

- Partner evidence freshness is enforced for normal Salary Advance eligibility.
- Verified links backed by stale/non-current effective-month evidence fail closed.
- Current eligibility uses the authoritative latest valid `COMPLETED` current-month import evidence.
- Re-verification can refresh the reusable link.
- Submission and correction resubmission cannot authorize credit using stale Partner evidence.

Still deferred:

- Automatic refresh immediately after Partner import completion.
- Background reconciliation of all affected Customer links.
- Automatic bulk Salary Advance limit recalculation.
- Event- or scheduler-based proactive refresh.
- Operational reconciliation and retry tooling.
- Richer configurable aging windows beyond the approved current-month MVP rule if business policy later changes.

Recommendation:
Implement the proactive refresh and reconciliation capabilities above without weakening inactive Partner Company/Employee hard stops or the completed fail-closed boundary.

Suggested future branch name:
`feature/partner-employee-import-link-refresh`

### MER-FU-029 - Harden Salary Advance same-customer cross-link submission concurrency

Area: Loan / Salary Advance / Concurrency

Type: Completed architecture hardening

Priority: P2

Status: Done

Blocks current PR: No

Resolution:
Salary Advance submission now acquires a transaction-scoped PostgreSQL advisory lock keyed by customer and product before the authoritative blocking-application check. It retains the customer and employee-link advisory lock before limit initialization or row locking and repeats the blocking check defensively.

Database fallback:
The existing `uq_loan_applications_customer_product_active` partial unique index remains authoritative. Insert-specific flush handling translates only SQLSTATE `23505` carrying that exact index name to `BLOCKING_APPLICATION_EXISTS`; unrelated integrity violations pass through unchanged.

Evidence:
PostgreSQL integration tests cover literal same-link and different-link concurrency, exact fallback translation, unrelated-constraint pass-through, and rollback of a prior limit mutation when the fallback conflict occurs.

### MER-FU-030 - Enforce Loan-status-sensitive Customer mutation restrictions

Area: Customer / Loan / Disbursement readiness

Type: Deferred feature

Priority: P1

Status: Open

Blocks current PR: No

Problem:
Customer profile and bank-account changes after loan submission still need a deliberately scoped status-aware mutation policy. Customer must not acquire a dependency on Loan.

Risk:
V25 now captures a Loan-owned immutable, purpose-protected destination snapshot for each operational contract version, and final readiness requires the captured source account to remain active. This controls historical contract/disbursement preparation without restricting Customer-owned mutations. Broader status-sensitive mutation policy remains unresolved.

Recommendation:
Design the remaining mutation policy as a separate non-circular checkpoint. Preserve Loan-owned snapshots, Customer ownership, explicit regeneration through `DISBURSEMENT_ACCOUNT_REFRESH`, and module-boundary tests.

Suggested future branch name:
`feature/customer-loan-status-mutation-policy`

### MER-FU-031 - Implement Loan correction and resubmission workflow

Area: Loan / Approval / Document / Customer correction

Type: Workflow

Priority: P1

Status: Done

Blocks current checkpoint: No

Completion:
V23-V24 provide executable continuations for `RETURN_TO_CUSTOMER_REVISION`,
`REQUEST_STAFF_CORRECTION`, and `REQUEST_CUSTOMER_OR_STAFF_CORRECTION`. Structured
plans create persisted single-owner Customer or Staff tasks, including separate
tasks for mixed requests. Customer and Staff queues/actions enforce identity,
permission, task proof, stale-version protection, and Staff maker-checker.

Guarded resubmission consumes one request exactly once, revalidates Customer,
Partner, product, blocking-application, Document, effective-limit, and existing
reservation invariants, writes a new immutable Salary Advance verification, and
returns to `SUBMITTED` or a new `UNDER_REVIEW` cycle. Amount and term remain
immutable, so no reservation adjustment or movement occurs. Review-cycle linkage
provides immutable recommendation/decision supersession semantics.

Proof:
Domain/service/controller/security tests and PostgreSQL integration tests cover
each entry action, synchronous rollback, Customer/Staff/mixed ownership, upload and
review proof, duplicate completion/resubmission, stale cycles/versions, concurrent
replacement/review/resubmission, audit/history, and the full return-to-review path.

### MER-FU-032 - Harden document storage, scanning, and retention

Area: Document / Security / Operations

Type: Production hardening

Priority: P1

Status: Open

Blocks production deployment: Yes

Problem:
The MVP local-filesystem adapter validates declared type, file signature, filename,
size, and opaque references, but does not provide production object storage,
malware/quarantine scanning, retention schedules, legal hold, or retirement purge.

Recommendation:
Add a private object-storage adapter, quarantine-before-availability workflow,
malware scanner port with fail-closed policy, encrypted storage and key rotation,
retention/legal-hold decisions, secure retirement, operational retry states, and
provider-failure integration tests. Never expose provider URLs or storage keys.

### MER-FU-033 - Add OCR-assisted document processing

Area: Document / OCR

Type: Deferred integration

Priority: P2

Status: Open

Blocks current checkpoint: No

Problem:
V22-V24 intentionally implement manual review only; no OCR job, extracted text,
confidence contract, provider, retry lease, or secure result retention exists.

Recommendation:
Design OCR as an outbound port with explicit job state, retry/idempotency, manual
fallback, field-level encryption, restricted access, PII-safe audit, and no raw OCR
text in broad DTOs, logs, URLs, or exceptions.

### MER-FU-034 - Add correction deadlines and notifications

Area: Loan / Notification / Operations

Type: Deferred workflow

Priority: P2

Status: Open

Blocks current checkpoint: No

Problem:
Correction requests currently have no due date, escalation, expiry, reminder, or
notification behavior; the Notification module remains a placeholder.

Recommendation:
Confirm SLA and expiry rules before adding UTC-Clock deadlines, scheduler locking,
idempotent reminders, safe notification templates, cancellation semantics, and
overdue workflow transitions.

### MER-FU-035 - Support financial-term corrections and reservation adjustment

Area: Loan / Salary Advance

Type: Deferred financial workflow

Priority: P2

Status: Open

Blocks current checkpoint: No

Problem:
Requested amount and term are immutable during V23-V24 correction. Reservation
increase, decrease, and insufficient-limit semantics therefore remain undefined.

Recommendation:
Obtain explicit product decisions before allowing term or amount changes. Preserve
the Loan-Application-to-limit lock order and add exact-once movements for deltas,
insufficient-limit conflicts, full rollback proof, and concurrent resubmission tests.

### MER-FU-036 - Externalize document checklist templates

Area: Document / Product configuration

Type: Deferred configuration

Priority: P2

Status: Open

Blocks current checkpoint: No

Problem:
Salary Advance checklist policy is intentionally code-configured: no documents on
initial submission and `RECENT_PAYSLIP` only for a controlled correction.

Recommendation:
When multiple products or operational template changes require it, introduce
versioned database configuration with effective dates, immutable per-application
snapshots, validation, administrative authorization, and migration/backfill rules.

### MER-FU-037 - Expand lending workflow read projections

Area: Loan / Application Queries / UX

Type: Deferred feature

Priority: P2

Status: Open

Blocks current checkpoint: No

Problem:
The v0.1.0 query foundation intentionally exposes only the minimum safe reads needed
to inspect pre-submission Salary Advance readiness and recover one durable
LoanApplication status. It is not a generic workflow projection engine.

Completed for v0.1.0:

- Customer Salary Advance readiness and safe limit read.
- Safe Customer-owned or authorized Staff LoanApplication status read.

Still deferred:

- Richer next-action projection and workflow command suggestions.
- Staff work queues and application search/filtering.
- Consolidated lifecycle and history views.
- Dashboard aggregation and frontend-specific query composition.
- Broader read models for Unsecured Consumer Loan and Collateral Loan.

Cancellation note:
The narrow Customer-owned command from `RETURNED_FOR_REVISION` to `CANCELLED`
supports Salary Advance and UCL, including terminal correction state, history,
audit, request idempotency, and cancellation-versus-resubmission concurrency.
Salary Advance releases its reservation exactly once; UCL creates no exposure
effect. Customer cancellation from other states, Staff or administrative
cancellation, Collateral policy, richer reasons, and operational tooling remain
deferred.

Suggested future branch name:
`feature/lending-workflow-read-projections`

### MER-FU-038 - Complete UCL negative and termination flows

Area: Loan / Document / Approval

Type: Deferred feature

Priority: P1

Status: Done

Blocks current checkpoint: No

Outcome:
The approved UCL MVP backend lifecycle is executable from origination through
positive or negative verification, structured correction and re-verification,
review, approval, offer handling, correction cancellation, contract, activation,
servicing, settlement, and closure. Product-scoped outstanding debt blocks new UCL
origination and correction resubmission while contractual outstanding remains
positive in `ACTIVE` or `OVERDUE`.

Completed:

- Authenticated Customer-owned UCL origination.
- Required income-proof, bank-statement, and employment-proof checklist creation.
- Initial `PENDING_MANUAL_REVIEW` product-verification persistence.
- Document-readiness-gated manual verification start and `VERIFIED`, `FAILED`, or `REQUIRES_MORE_INFORMATION` completion with authoritative Staff actor, time, and restricted assessment evidence.
- Immutable sequenced UCL verification cycles, with the latest cycle authoritative and re-verification linked to its source correction.
- Structured UCL correction from verification, Loan Officer review, or Approver review over application-owned income, bank-statement, and employment evidence.
- Customer, Staff, and mixed task completion and resubmission, with amount and term immutable and a fresh verification required before review.
- Verified-only entry into common Loan Officer review and approval or rejection recommendation through `APPROVAL_PENDING`.
- Common Approver rejection and return-to-review decisions for UCL.
- Exact-request UCL approval under the active 1.8% flat monthly policy.
- Immutable monthly-installment offer generation with exact whole-VND reconciliation and seven-day validity.
- Generic Customer offer read, accept, decline, and expiry without Salary Advance exposure effects.
- Immutable operational-contract preparation that copies the accepted UCL offer terms and items exactly and captures a purpose-protected destination.
- Controlled destination refresh through `DISBURSEMENT_ACCOUNT_REFRESH`, prior-version supersession, and fresh Customer acknowledgment.
- Product-aware readiness that requires application-owned `VERIFIED` UCL evidence and never reads or mutates Salary Advance reservation or exposure state.
- Idempotent and concurrency-safe manual disbursement that creates the active LoanAccount, immutable transfer evidence, exact final monthly schedule, progress, histories, transition, and audit atomically.
- Truthful product activation results with no synthetic UCL limit, movement, or exposure evidence.
- Partial and early repayment without repricing, rebate, schedule regeneration, or due-date mutation.
- Deterministic oldest-installment and `FEE -> INTEREST -> PRINCIPAL` allocation, whole-operation overpayment rejection, and immutable servicing reads.
- Date-driven `ACTIVE <-> OVERDUE` evaluation and repayment cure.
- Ordinary contractual payoff and exact Administrative Full-Balance Settlement to `SETTLED`.
- Separate Accounting closure to `CLOSED`, with zero UCL product-exposure release and no Salary Advance movement.
- Customer-owned UCL cancellation from `RETURNED_FOR_REVISION`, with exact replay and no product-exposure effect.
- Product-scoped outstanding-debt protection for UCL origination and resubmission, with zero-outstanding `SETTLED` or `CLOSED` accounts permitted and inconsistent state failing closed.
- Fail-closed Collateral Loan contract-execution guards.

Still deferred:

- Customer cancellation from wider application states.
- Staff and administrative cancellation.
- Automated credit-bureau, income-verification, scoring, and bank-statement parsing.
- Payment-provider integration, reversal/refund, discounted settlement, collections, and ledger capabilities.

### MER-FU-039 - Complete Collateral Loan beyond manual verification and review recommendation

Area: Loan / Document / Approval / Servicing

Type: Deferred feature

Priority: P1

Status: Deferred

Blocks current checkpoint: No

Completed in Collateral Loan CP1 and CP2:

- Authenticated Customer-owned origination for the active `COLLATERAL_LOAN` / `SECURED` product.
- Customer readiness, current product amount bounds, whole-VND amount, and exact 6/12/18/24-month term validation.
- One API-submitted Loan-owned Collateral fact record, with a physical model that permits later multi-asset extension.
- Required Document-owned `COLLATERAL_OWNERSHIP_EVIDENCE` checklist evidence and a safe returned checklist-item identifier for the existing upload flow.
- Initial `DOCUMENTS_PENDING` application state and application-owned sequence-1 `PENDING_MANUAL_REVIEW` Collateral verification.
- Customer/product submission serialization, blocking-application protection, transactional history, and PII-safe audit.
- Explicit start and exact-ID completion of immutable, numbered manual-verification cycles with `VERIFIED`, terminal `FAILED`, and `REQUIRES_MORE_INFORMATION` outcomes.
- Document-only Customer replacement or Staff review correction for the existing `COLLATERAL_OWNERSHIP_EVIDENCE` item, followed by concurrency-safe resubmission into one linked next verification cycle.
- Common Loan Officer review and recommendation through `APPROVAL_PENDING` only after the authoritative latest Collateral verification is `VERIFIED`.
- Fail-closed Approver actions: every Collateral approval decision remains unsupported and rolls back without decision, transition, audit, or offer evidence.
- PostgreSQL-backed migration, immutability, reconciliation, rollback, and concurrency proof for the CP2 workflow.

Still deferred:

- Approver decision and approved-offer generation; no Collateral approval action is executable yet.
- Customer offer handling, contract, activation, and LoanAccount creation.
- Pricing, interest and fee calculation, installment allocation, schedules, and every unresolved decision in `MER-BIZ-001` Section 13.4, including the non-executable 1.5% catalog target.
- Collateral repayment, overdue behavior, settlement, closure, and every servicing/exposure policy.
- LTV, automated valuation, custody, registry, insurance, enforcement, repossession, liquidation, OCR, and external valuation integration.
- Supporting-photo policy and any rule for multiple Collateral assets through the API.
- Any product-scoped outstanding Collateral LoanAccount restriction; no such business rule is currently approved.

Suggested future branch name:
`feature/collateral-approval-pricing`

### MER-FU-040 - Add exact verification identity to UCL completion

Area: Loan / API

Type: Workflow hardening

Priority: P2

Status: Deferred

Blocks current checkpoint: No

Problem:
Collateral Loan CP2 requires `expectedVerificationId` on manual-verification completion so a stale Staff client cannot complete a superseded cycle. The older UCL completion contract identifies only the application and still relies on latest-cycle locking and state checks.

Recommended resolution:
Add the exact expected verification-cycle identifier to the UCL completion request and validate it against the locked authoritative latest cycle. Preserve current UCL outcomes, correction behavior, response safety, and concurrency guarantees, and update its API/Postman/tests as one backward-compatibility decision.

Suggested future branch name:
`fix/ucl-exact-verification-completion`

## Recommended Next Roadmap

1. Define reversal/refund, suspense/unapplied cash, waiver/write-off, discounted settlement, reconciliation, ledger, and collections rules before selecting another financial-servicing continuation.
2. Resolve the Collateral pricing, repayment, and operational decisions in `MER-BIZ-001` Section 13.4 before implementing Approver decisions or offer execution; keep activation and servicing fail-closed until their rules are approved.
3. Complete production document storage, malware-scanning, retention, and operational hardening before deployment.
