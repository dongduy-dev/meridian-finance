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
`SecurityConfig` now keeps only health and loan product catalog endpoints public. Partner Company, Partner Employee, employee verification, import batch, and Salary Advance application endpoints require authentication through the current Spring Security gate. The patch does not expand `permitAll`.

Notes:
This is a minimal authenticated gate using Spring Security HTTP Basic for local/development until full JWT/RBAC is implemented. Role/action permissions and customer ownership enforcement remain tracked separately.

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
Manual review outcomes exist conceptually, but approval/rejection workflow is not implemented.

Recommendation:
Implement after security/PII patch and before complex document flows.

### MER-FU-010 - Implement review/approval/customer acceptance/disbursement lifecycle

Area: Loan workflow

Type: Deferred feature

Priority: P2

Status: Open

Blocks next major feature: No

Problem:
Application creation exists, but the remaining lifecycle is deferred.

Recommendation:
Implement in vertical slices:

1. Loan officer review/recommendation.
2. Approval decision.
3. Customer acceptance.
4. Manual disbursement confirmation.
5. Loan account activation.

### MER-FU-011 - Implement repayment tracking

Area: Loan / Repayment

Type: Deferred feature

Priority: P2

Status: Open

Blocks next major feature: No

Problem:
Repayment schedule and repayment tracking are not implemented yet.

Recommendation:
Implement after loan account activation.

### MER-FU-012 - Implement document checklist/manual document review/OCR integration

Area: Document / OCR

Type: Deferred feature

Priority: P2

Status: Open

Blocks next major feature: No

Problem:
Docs describe documents/checklists/OCR, but implementation is not present.

Recommendation:
Implement after core Salary Advance lifecycle is stable.

### MER-FU-013 - Implement audit trail/status history

Area: Audit / Workflow

Type: Deferred feature

Priority: P2

Status: Open

Blocks next major feature: No

Problem:
No full audit trail or transition history tables exist yet.

Recommendation:
Implement audit trail for important business actions and status transitions before final demo.

### MER-FU-014 - Create current physical schema snapshot

Area: Database documentation

Type: Documentation improvement

Priority: P1

Status: Open

Blocks next major feature: No

Problem:
Flyway migrations are growing and current schema is harder to inspect from migrations alone.

Recommendation:
Create `docs/database/MER-DB-CURRENT-SCHEMA.sql` after the security/PII patch or after the next stable milestone. This file is documentation only and must not be placed in the Flyway migration folder.

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

Status: Open

Blocks next major feature: No

Recommendation:
Update `docs/api/Meridian-Platform.postman_collection.json` to call login, store `accessToken`, use Bearer auth, and remove request-provided customerId payload fields.

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
## Recommended Next Roadmap

1. Review/merge the completed P0 security/PII/inactive Partner Company patch.
2. Create current physical schema snapshot.
3. Continue with Loan review/approval workflow.
4. Continue with Approval Review Recommendation on top of IAM/RBAC foundation.
5. Continue with customer acceptance/disbursement/loan account activation.
6. Add repayment tracking.
7. Add audit trail.
8. Add document checklist/manual review/OCR.
