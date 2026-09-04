# MER-TRACK-001 - Meridian Follow-up Register

## Purpose

This file tracks known Meridian gaps, deferred work, risks, docs/code mismatches, and planned future slices so the project does not rely on memory. Items here should be updated as work is started, completed, accepted as risk, or superseded by GitHub Issues or PR decisions.

## Priority Guide

* P0: urgent integrity or security defect to resolve before further major work.
* P1: resolve before the next major workflow or deployment milestone.
* P2: planned hardening or future capability.
* P3: low-risk documentation or nice-to-have work.

## Status Guide

* Open
* In Progress
* Done
* Deferred
* Accepted Risk

## Follow-up Items

### MER-FU-001 - Lock down sensitive Partner and Salary Advance endpoints

Area: Security / Identity / Partner / Loan

Type: Security risk

Priority: P0

Status: Done

Blocking: No current blocker.

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

Blocking: No current blocker.

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

Blocking: No current blocker.

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

Blocking: No current blocker.

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

Blocking: No current blocker.

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

Blocking: No current blocker.

Problem:
API docs did not list implemented employee verification and Salary Advance application endpoints.

Resolution:
`MER-API-001` now owns the maintained HTTP contract and scenario view, including protected employee verification and Salary Advance origination. `MER-ARCH-006` owns the corresponding request, dependency, transaction, and communication-flow architecture.

### MER-FU-007 - Align ERD terminology drift

Area: Database docs

Type: Documentation mismatch

Priority: P2

Status: Done

Blocking: No current blocker.

Problem:
The logical ERD used conceptual names and target fields that could be mistaken for the current physical schema.

Resolution:
`MER-DB-001` now presents Meridian's durable high-level logical data model and ERD without claiming physical-schema authority. Flyway owns executable schema evolution, and `MER-DB-CURRENT-SCHEMA.sql` is the checked-in human-readable current physical-schema snapshot. The aligned database documentation reflects the implemented product boundary through V47 without turning the logical model into migration history.

### MER-FU-008 - Replace hardcoded Salary Advance salary cap/policy terms with policy config

Area: Loan / Product Policy

Type: Design improvement

Priority: P2

Status: Open

Blocking: No current blocker.

Problem:
Salary Advance policy currently uses hardcoded salary cap and term values instead of parsed product policy configuration.

Recommendation:
Keep acceptable for current foundation if documented, then later move product-specific configurable rules into loan product policy config.

### MER-FU-009 - Implement Partner eligibility manual review workflow

Area: Loan / Partner / Review

Type: Deferred feature

Priority: P2

Status: Open

Blocking: No current blocker.

Problem:
Document review queues, immutable version decisions, waivers/replacements, and Customer/Staff/mixed correction workflows are implemented. A dedicated Partner eligibility manual-review queue and authoritative outcome workflow for ambiguous employee matching are not implemented.

Recommendation:
Keep this follow-up scoped to Partner eligibility. Define assignment, evidence, maker-checker, outcome, expiry/revalidation, audit, and concurrency rules before implementation; do not duplicate the implemented Document review/correction workflow.

### MER-FU-010 - Implement review/approval/customer acceptance/disbursement lifecycle

Area: Loan workflow

Type: Deferred feature

Priority: P2

Status: Done

Blocking: No current blocker.

Resolution:
The Salary Advance workflow now includes Loan Officer review, independent approval, immutable offer and Customer response, document/correction readiness, operational contract readiness, manual disbursement and activation, servicing through contractual payoff, Administrative Full-Balance Settlement and administrative closure, and returned-correction cancellation. Activation atomically creates the LoanAccount and final schedule, converts reserved exposure to used exposure, transitions the application to `DISBURSED`, and records required history and audit evidence.

### MER-FU-011 - Implement repayment tracking

Area: Loan / Repayment

Type: Deferred feature

Priority: P2

Status: Done

Blocking: No current blocker.

Completion:
The authoritative final repayment schedule is immutable obligation evidence. Salary Advance, UCL, and Collateral Loan support manual repayment posting, deterministic allocation, paid/outstanding tracking, contractual payoff to `SETTLED`, date-driven overdue evaluation, secured servicing reads, payment-backed Administrative Full-Balance Settlement, and separate administrative closure to `CLOSED`. Salary Advance alone performs exact principal used-exposure release; UCL and Collateral report zero product exposure release and create no Salary Advance movement.

Evidence:
V32-V35 provide repayment, durable outcome, exposure-release, history, audit, and overdue-candidate foundations. V36 distinguishes settlement payment transactions, adds immutable approved-settlement and closure evidence, permissions, lifecycle vocabulary, and reciprocal deferred reconciliation. V42 makes the existing reconciliation functions product-aware so Salary Advance preserves exact release semantics and UCL enforces zero exposure release; V47 extends that explicit zero-exposure behavior to Collateral. The APIs preserve scheduled obligations separately from payment/allocation evidence and enforce ownership, role, and permission boundaries.

Deferred boundary:
Discounted or negotiated settlement, concession, reversal, refund, waiver, write-off, suspense/unapplied cash, payment/bank integration, reconciliation, ledger, collections, and notifications remain separate future capabilities. Administrative Full-Balance Settlement and LoanAccount closure are complete for the approved Salary Advance, UCL, and Collateral semantics.

### MER-FU-012 - Implement document checklist and manual document review foundation

Area: Document

Type: Workflow foundation

Priority: P2

Status: Done

Blocking: No current blocker.

Completion:
The Document foundation provides code-defined product checklist requirements and per-application snapshots for Salary Advance, UCL, and Collateral Loan. It supports immutable document versions, safe local storage behind a port, Customer and authorized-Staff upload, version-targeted acceptance, waiver and replacement, separate upload-completeness and processing-readiness contracts, content authorization, audit, rollback, and PostgreSQL concurrency proof.

OCR was intentionally not included. It is tracked separately by `MER-FU-033`;
production storage, malware scanning, and retention hardening are `MER-FU-032`.
### MER-FU-013 - Implement audit trail and Loan Application lifecycle history

Area: Audit / Workflow

Type: Deferred feature

Priority: P2

Status: Done

Blocking: No current blocker.

Problem:
No full audit trail or Loan Application lifecycle history tables existed for the implemented Salary Advance workflow.

Resolution:

V17 adds append-only `audit_events` for important cross-cutting business actions and Loan-owned `loan_application_status_transitions` for ordered Loan Application status changes. Later increments extend transactional audit/history through document correction, contract readiness, manual disbursement, repayment servicing, overdue evaluation, and exact payoff, including LoanAccount and installment histories. Current listeners are synchronous in the originating transaction. Future slices should extend this foundation rather than create duplicate generic histories.

### MER-FU-014 - Create current physical schema snapshot

Area: Database documentation

Type: Documentation improvement

Priority: P1

Status: Done

Blocking: No current blocker.

Problem:
Flyway migrations are growing and current schema is harder to inspect from migrations alone.

Resolution:
`MER-DB-CURRENT-SCHEMA.sql` is the checked-in human-readable current physical-schema snapshot through V52; Flyway V1-V52 remains the executable schema authority. Focused PostgreSQL migration and snapshot verification confirms their alignment.

### MER-FU-015 - Replace temporary HTTP Basic authenticated gate with JWT/RBAC endpoint permissions

Area: Identity / Security / API

Type: Deferred feature / security hardening

Priority: P1

Status: Done

Blocking: No current blocker.

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

Status: Done

Blocking: No current blocker.

Outcome:
Identity persists only SHA-256 refresh-token digests, rotates one locked token into one replacement, and revokes the active family when a consumed token is reused. The raw token remains confined to the HttpOnly refresh cookie.

Suggested future branch name:
`feature/iam-refresh-token-rotation`

### MER-FU-017 - Password reset

Area: Identity / Security

Type: Deferred feature

Priority: P2

Status: Done

Blocking: No current blocker.

Outcome:
Identity exposes enumeration-safe request and non-idempotent confirmation endpoints for active email-verified Customer and Staff Users. It stores only 30-minute SHA-256 token digests, replaces prior active reset state under the User lock, changes the BCrypt password and clears temporary login protection atomically with token consumption, and revokes every refresh-token family for the User. Notification renders and sends the fragment-link email after commit through the existing SMTP boundary; delivery failure leaves replacement state committed.

Suggested future branch name:
`feature/iam-password-reset`

### MER-FU-018 - Email verification

Area: Identity / Customer

Type: Deferred feature

Priority: P2

Status: Done

Blocking: No current blocker.

Outcome:
Identity exposes public Customer registration, verification-email request, and token confirmation while Customer owns creation of its `ACTIVE` / `UNVERIFIED` / `INCOMPLETE` aggregate. Registration atomically creates the Customer, Customer-linked User, role assignment, and digest-only verification-token state; controlled SMTP delivery occurs after commit through Notification and can be recovered by enumeration-safe resend. Email confirmation updates only Identity `email_verified_at`, gates login and refresh credentials until verified, and does not change Customer business verification status.

Suggested future branch name:
`feature/iam-email-verification`

### MER-FU-019 - Admin user management UI

Area: Identity / Back Office

Type: Deferred feature

Priority: P2

Status: Open

Blocking: No current blocker.

Recommendation:
Implement admin user management UI after backend user-management use cases exist.

Suggested future branch name:
`feature/admin-user-management-ui`

### MER-FU-020 - Account lockout policy

Area: Identity / Security

Type: Risk

Priority: P1

Status: Done

Blocking: No current blocker.

Outcome:
Identity serializes known-User password login on the User row, records consecutive failures durably, and applies a configurable temporary lock after the configured threshold. The default is 5 failed attempts and 15 minutes. Wrong passwords, unknown emails, and active locks share the same `INVALID_CREDENTIALS` response. Expiry starts a fresh sequence, while successful active-User login clears stale protection state. Lockout remains separate from administrative User status and does not revoke established access or refresh credentials.

Suggested future branch name:
`feature/iam-account-lockout`

### MER-FU-021 - MFA

Area: Identity / Security

Type: Deferred feature

Priority: P3

Status: Open

Blocking: No current blocker.

Recommendation:
Defer MFA until production-grade authentication hardening.

Suggested future branch name:
`feature/iam-mfa`

### MER-FU-022 - Full permission management UI

Area: Identity / Back Office

Type: Deferred feature

Priority: P3

Status: Open

Blocking: No current blocker.

Recommendation:
Defer full permission management UI until role management needs exceed seeded MVP roles.

Suggested future branch name:
`feature/iam-permission-management-ui`

### MER-FU-023 - Customer ownership enforcement hardening

Area: Identity / Customer / Loan / Document

Type: Security hardening

Priority: P1

Status: Open

Blocking: No current blocker.

Recommendation:
Current profile, bank-account, document/content, correction, offer/contract, LoanAccount, and repayment-history endpoints enforce token-derived ownership and approved concealment rules. Keep this item open only for future customer-facing surfaces and cross-endpoint consistency reviews; do not treat already-secured endpoints as unfinished.

Suggested future branch name:
`feature/customer-ownership-hardening`

### MER-FU-024 - Token blacklist / logout invalidation

Area: Identity / Security

Type: Security hardening

Priority: P2

Status: Done

Blocking: No current blocker.

Outcome:
Current-session logout revokes the refresh-token family represented by the presented cookie, durably invalidates the presented valid access token until expiry, clears the shared authentication-path cookie, and returns the same idempotent `204 No Content` response for missing or invalid credentials. PostgreSQL row locking preserves refresh/logout race safety, and independent login families for the same User remain usable.

Suggested future branch name:
`feature/iam-token-invalidation`

### MER-FU-025 - Audit trail for authentication events

Area: Identity / Audit

Type: Deferred feature

Priority: P1

Status: Open

Blocking: No current blocker.

Recommendation:
Extend the existing Audit foundation with a deliberately bounded authentication-event catalog (for example login success/failure and future logout/token invalidation) only after privacy, retention, rate/noise, actor, and failure-transaction semantics are approved.

Suggested future branch name:
`feature/iam-auth-audit-events`

### MER-FU-026 - Refresh Postman collection for JWT/Bearer flow

Area: Documentation / API

Type: Documentation gap

Priority: P2

Status: Done

Blocking: No current blocker.

Resolution:
`docs/api/Meridian-Platform.postman_collection.json` is aligned with the maintained v1 API across authentication, Salary Advance, UCL, Collateral Loan, common servicing, ownership and replay behavior, and representative negative coverage. It stores role-specific Bearer tokens and avoids request-provided Customer ownership identifiers.

Suggested future branch name:
`docs/update-postman-jwt-flow`

### MER-FU-027 - Harden Approval-to-Loan async event processing before using after-commit listeners

Area: Approval / Loan / Architecture

Type: Deferred architecture hardening

Priority: P2

Status: Open

Blocking: No current blocker.

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

Blocking: No current blocker.

Problem:
Customer Partner Employee links are refreshed when the Customer verifies again. They are not automatically refreshed when new Partner Employee imports are completed.

Risk:
Normal Salary Advance eligibility now fails closed when a reusable link is backed by stale or non-current-month Partner evidence. This prevents stale evidence from authorizing credit, but Customers remain blocked until re-verification or a future proactive refresh process updates the link.

Existing safety boundary:

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

Blocking: No current blocker.

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

Blocking: No current blocker.

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

Blocking: No current blocker.

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

Later UCL and Collateral correction continuations reuse the common workflow foundation and are closed by `MER-FU-038` and `MER-FU-039`.

### MER-FU-032 - Harden document storage, scanning, and retention

Area: Document / Security / Operations

Type: Production hardening

Priority: P1

Status: Open

Blocking: Production deployment.

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

Blocking: No current blocker.

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

Blocking: No current blocker.

Problem:
Correction requests currently have no due date, escalation, expiry, reminder, or
notification behavior. Notification currently owns controlled verification-email
rendering and SMTP transport only; correction notifications remain unimplemented.

Recommendation:
Confirm SLA and expiry rules before adding UTC-Clock deadlines, scheduler locking,
idempotent reminders, safe notification templates, cancellation semantics, and
overdue workflow transitions.

### MER-FU-035 - Support financial-term corrections and reservation adjustment

Area: Loan / Salary Advance

Type: Deferred financial workflow

Priority: P2

Status: Open

Blocking: No current blocker.

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

Blocking: No current blocker.

Problem:
Salary Advance, UCL, and Collateral Loan checklist requirements are defined in code by product-specific resolvers and snapshotted per application. The requirements are not externalized or independently versioned as operational product configuration.

Recommendation:
Keep the code-defined policies while they remain sufficient for the supported workflows. If operational template changes or product expansion require independent configuration, introduce versioned configuration with effective dates, immutable per-application snapshots, validation, administrative authorization, and migration/backfill rules.

### MER-FU-037 - Expand lending workflow read projections

Area: Loan / Application Queries / UX

Type: Deferred feature

Priority: P2

Status: In progress

Blocking: No current blocker.

Problem:
The common API exposes safe Customer-owned LoanApplication and LoanAccount indexes, a narrow Customer action/resume projection, and application-scoped document checklist/current-version/readiness state. Staff FE-CP2 adds a permission-scoped cross-product application index plus a consolidated safe case header, purpose-limited Customer readiness, and ordered LoanApplication transition history. Specialized operational queues, action evidence, aggregation, and richer product projections remain incomplete.

Resolved scope:

- Customer-owned LoanApplication index with lifecycle-active and proven Customer-action categories.
- Customer-owned compact LoanAccount index using authoritative repayment balances.
- Customer-owned submission-checklist, current-version, and readiness projection.
- Frontend product-policy presentation and Customer-safe Partner verification selection supporting the lending reads.
- Staff application paging and exact product/status filtering with deterministic ordering.
- Staff case header, purpose-limited Customer readiness, and immutable ordered LoanApplication transition history.
- Staff submission-checklist, current-version, immutable version-history, and safe review-history projection for document operations.
- Staff correction case projection with mixed task composition, backend-derived proof state, resubmission readiness, and current-actor maker-checker evidence.

Still deferred:

- Specialized Staff work queues beyond the executable document-review and Staff-correction queues, plus direct application-number lookup.
- Consolidated product-verification, review, decision, contract, and servicing evidence/history beyond the CP3 document/correction projections.
- Broader Dashboard aggregation beyond the narrow Customer indexes and action facts.
- Additional workflow command suggestions beyond the proven Customer action categories.
- Richer product-specific projections where the common reads are insufficient.

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

Blocking: No current blocker.

Outcome:
The approved UCL backend lifecycle is executable from authenticated Customer-owned origination through document-backed verification, correction, review, approval, offer response, contract, activation, servicing, contractual payoff, Administrative Full-Balance Settlement and administrative closure. It includes negative verification and decision outcomes, returned-correction cancellation, immutable evidence and terms, ownership and replay controls, and PostgreSQL-backed rollback and concurrency proof.

Product boundary:
UCL creates no Salary Advance exposure effect. Product-scoped outstanding debt blocks new UCL origination and correction resubmission while contractual outstanding is positive in `ACTIVE` or `OVERDUE`; zero-outstanding `SETTLED` or `CLOSED` UCL accounts do not block, and inconsistent evidence fails closed.

Still deferred:

- Customer cancellation from wider application states.
- Staff and administrative cancellation.
- Automated credit-bureau, income-verification, scoring, and bank-statement parsing.
- Payment-provider integration, reversal/refund, discounted settlement, collections, and ledger capabilities.

### MER-FU-039 - Complete Collateral Loan servicing

Area: Loan / Document / Approval / Servicing

Type: Deferred feature

Priority: P1

Status: Done

Blocking: No current blocker.

Completed in Collateral Loan CP1-CP5:

- Authenticated Customer-owned origination, ownership-evidence checklist, immutable manual verification and document-only correction, common review and approval, exact approved pricing and offer handling, operational contract preparation, and idempotent manual-disbursement activation.
- Activated LoanAccount and servicing reads, partial and early repayment, date-driven overdue evaluation and cure, contractual payoff, Administrative Full-Balance Settlement to `SETTLED`, and separate administrative closure to `CLOSED`.
- Product-specific constraints, immutable evidence and terms, ownership, maker-checker, replay, audit/history, rollback, concurrency, reconciliation, and PostgreSQL migration proof through terminal LoanAccount state.

Product boundary:
Collateral creates no Salary Advance limit, movement, or product-exposure effect and has no product-specific outstanding-LoanAccount origination restriction.

Still deferred:

- LTV, automated valuation, custody, registry, insurance, enforcement, repossession, liquidation, OCR, and external valuation integration.
- Supporting-photo policy and any rule for multiple Collateral assets through the API.
- Any product-scoped outstanding Collateral LoanAccount restriction; no such business rule is currently approved.

### MER-FU-040 - Add exact verification identity to UCL completion

Area: Loan / API

Type: Workflow hardening

Priority: P2

Status: Deferred

Blocking: No current blocker.

Problem:
Collateral Loan CP2 requires `expectedVerificationId` on manual-verification completion so a stale Staff client cannot complete a superseded cycle. The older UCL completion contract identifies only the application and still relies on latest-cycle locking and state checks.

Recommended resolution:
Add the exact expected verification-cycle identifier to the UCL completion request and validate it against the locked authoritative latest cycle. Preserve current UCL outcomes, correction behavior, response safety, and concurrency guarantees, and update its API/Postman/tests as one backward-compatibility decision.

Suggested future branch name:
`fix/ucl-exact-verification-completion`

### MER-FU-041 - Harden ApprovedOffer financial immutability at the database boundary

Area: Loan / Database

Type: Persistence hardening

Priority: P2

Status: Deferred

Blocking: No current blocker.

Problem:
The Loan domain treats generated ApprovedOffer financial terms and provisional repayment items as immutable, and current commands do not expose a mutation path. PostgreSQL enforces uniqueness, whole-VND values, arithmetic reconciliation, and valid status timestamps, but it does not independently reject a direct update to common financial snapshot columns or repayment-item amounts.

Recommended resolution:
Design one product-neutral migration that preserves the allowed pending-to-terminal offer status/timestamp transitions while rejecting changes to financial terms, policy identity, generation/expiry evidence, and existing repayment-item identity or amounts. Preflight current constraint/trigger state, prove clean migration and upgrade behavior on PostgreSQL, and regression-test Salary Advance, UCL, and Collateral offer response and expiry before enabling the guard.

Suggested future branch name:
`fix/approved-offer-db-immutability`

### MER-FU-042 - Enforce Meridian public module boundaries in source and architecture tests

Area: Architecture / Modularity / Testing

Type: Architecture conformance / hardening

Priority: P2

Status: Open

Blocking: No current documentation checkpoint or production workflow blocker.

Problem:
The aligned architecture defines narrow cross-context collaboration through public application contracts and boundary adapters, but current feature application and domain packages still contain some foreign internal imports. `ArchitectureRulesTest` proves layer isolation, concrete-security isolation, and `shared` independence, but does not fully enforce feature-module public surfaces, legal module dependencies, module cycles, or named-interface/public-contract boundaries. Spring Modulith dependency metadata alone is not executable proof of those rules.

Recommendation:
Inventory every foreign internal dependency and distinguish legitimate public application contracts from internal leakage. Replace illegal dependencies with the intended consumer-owned output port and boundary adapter, provider public application contract, or inbound event adapter where asynchronous intake is intended. Add focused architecture enforcement for module dependencies, public surfaces, and cycles while preserving bounded-context ownership and runtime behavior; do not redesign business workflows merely to satisfy package aesthetics.

Suggested future branch name:
`refactor/architecture-module-boundary-conformance`

### MER-FU-043 - Normalize executable API error identifier semantics

Area: API / Error Contract

Type: API contract conformance

Priority: P1

Status: Open

Blocking: No current documentation checkpoint.

Problem:
Executable source does not consistently use one error identifier with one canonical runtime status and default message. `DOCUMENT_CHECKLIST_NOT_FOUND`, for example, is constructed through both 404-family `EntityNotFoundException` and 409-family `BusinessStateConflictException` paths, and other shared identifiers have multiple runtime default messages. Documentation must not silently choose behavior that source does not consistently implement.

Recommendation:
Inventory every executable error identifier and identify all uses with multiple HTTP statuses, exception families, or default messages. For each inconsistency, decide whether to converge on one canonical identifier, status, and message or split the meanings into distinct identifiers. Update exception construction, global mapping, and focused tests first; then align `MER-ARCH-004`, `MER-API-001`, and Postman while preserving safe caller-facing disclosure.

Suggested future branch name:
`fix/api-error-contract-conformance`

### MER-FU-044 - Add authentication endpoint rate limiting

Area: Identity / Security / API

Type: Security hardening

Priority: P2

Status: Done

Blocking: No current blocker.

Outcome:
Login and refresh use separate configurable, bounded, single-instance request throttles keyed by the effective servlet remote address. Requests over a policy limit return `429 RATE_LIMIT_EXCEEDED` with `Retry-After` before password verification or refresh-token processing. Logout remains unthrottled. Distributed or Redis-backed throttling remains later operational hardening.

Suggested future branch name:
`feature/iam-auth-rate-limiting`

## Roadmap Boundary

`README.md` owns project sequencing and roadmap presentation. This register owns the status, priority, rationale, and recommended action for individual follow-ups.
