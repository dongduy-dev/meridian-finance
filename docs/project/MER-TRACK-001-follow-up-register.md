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

Status: Open

Blocks next major feature: No

Problem:
Database docs mention verification_status while current migration/JPA uses verification_outcome. Docs also mention loan_applications.product_details while V11 does not create that column.

Recommendation:
Update ERD/database docs to match current physical schema or intentionally record product_details as future/deferred.

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

### MER-FU-009 - Implement manual review workflow

Area: Loan / Partner / Review

Type: Deferred feature

Priority: P2

Status: Open

Blocks next major feature: No

Problem:
Manual review outcomes exist conceptually, but a dedicated manual review queue/outcome workflow is not implemented. Approval decision now exists for applications that have completed Loan Officer recommendation.

Recommendation:
Implement after the common approval lifecycle is stable and before complex document flows.

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

### MER-FU-011 - Implement repayment tracking

Area: Loan / Repayment

Type: Deferred feature

Priority: P2

Status: Open

Blocks next major feature: No

Problem:
The authoritative final repayment schedule is generated at LoanAccount activation. Increments 2-3 implement application-layer manual Salary Advance repayment posting, deterministic allocation, paid/outstanding tracking, automatic contractual payoff, exact principal used-exposure release, date-driven overdue evaluation, ACTIVE/OVERDUE history and audit, and outstanding-account submission blocking. Repayment REST/query APIs, negotiated settlement, administrative closure, and reversal remain deferred, so this follow-up stays open.

Progress:
V32-V33 provide audit and physical servicing foundations. V34 adds the minimum immutable safe operation-outcome snapshot and repayment audit entity support required for exact replay after later payments. V35 adds only the bounded overdue-candidate index after exact V34 preflight; overdue operation/history support already existed in V33. Salary Advance is the only executable repayment product; no UCL or Collateral placeholders exist.

Recommendation:
Continue with the separately approved secured API/query visibility slice without mixing scheduled obligations with payment transactions or allocations. Settlement administration, closure, and reversal remain separate later capabilities.

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

V17 adds append-only `audit_events` for important cross-cutting business actions and Loan-owned `loan_application_status_transitions` for ordered Loan Application status changes. The current implementation records the implemented Salary Advance submission, review, recommendation, decision, approved-offer, customer response, expiry, and reservation-release actions synchronously in the originating transaction. Future modules and future workflow slices should extend this foundation rather than create duplicate approval-history or generic status-history tables.

### MER-FU-014 - Create current physical schema snapshot

Area: Database documentation

Type: Documentation improvement

Priority: P1

Status: Done

Blocks next major feature: No

Problem:
Flyway migrations are growing and current schema is harder to inspect from migrations alone.

Recommendation:
`docs/database/MER-DB-CURRENT-SCHEMA.sql` now tracks the current physical schema through V17. This file is documentation only and must not be placed in the Flyway migration folder.

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
Broaden ownership enforcement as additional customer-facing profile, document, offer, and repayment endpoints are implemented.

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
Record important authentication events when Audit foundation is implemented.

Suggested future branch name:
`feature/iam-auth-audit-events`

### MER-FU-026 - Refresh Postman collection for JWT/Bearer flow

Area: Documentation / API

Type: Documentation gap

Priority: P2

Status: Done

Blocks next major feature: No

Resolution:
`docs/api/Meridian-Platform.postman_collection.json` now calls login, stores role-specific Bearer token variables, uses JWT auth for protected endpoints, removes request-provided customerId payload fields, and covers the current endpoint inventory including Loan Officer review/recommendation.

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
Customer Partner Employee links are currently refreshed when the customer verifies again. They are not automatically refreshed when new Partner Employee imports are completed.

Risk:
Reusable employee links and Salary Advance limits may continue to reference older imported employee rows until the customer re-verifies, even when fresher active employee data is available.

Recommendation:
Automatically refresh verified customer employee links when new valid Partner Employee imports are completed. The refresh should preserve inactive Partner Company and inactive Partner Employee hard stops, update the linked Partner Employee/source batch when the verified evidence still matches, and trigger Salary Advance limit recalculation where applicable.

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

## Recommended Next Roadmap

1. Audit, commit, and merge the Manual Disbursement + LoanAccount Activation checkpoint.
2. Implement repayment posting/allocation, overdue servicing, settlement, and closure.
3. Add complete UCL and Collateral activation policies only when their product rules are approved.
4. Complete production document hardening before deployment.
