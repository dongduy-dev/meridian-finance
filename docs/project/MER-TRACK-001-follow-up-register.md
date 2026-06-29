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

Status: Open

Blocks next major feature: No, but must be resolved before real demo/auth milestone

Problem:
Salary Advance application request accepts customerId from the caller.

Risk:
Without authentication and customer ownership enforcement, callers can submit applications for arbitrary customers.

Recommendation:
Document as temporary MVP/local testing shortcut. Later derive customerId from authenticated principal or enforce back-office/admin permission.

### MER-FU-005 - Add security/controller tests for sensitive endpoints

Area: Testing / Security

Type: Test gap

Priority: P1

Status: Open

Blocks next major feature: No

Problem:
Security and controller coverage was thin. This patch adds focused coverage for anonymous access denial, authenticated access to a protected Partner Employee endpoint, inactive Partner Company rejection, and the safe employee verification response shape. Broader role/action authorization and full controller matrix coverage are still missing.

Recommendation:
Keep expanding controller/security tests as IAM/RBAC matures, especially role-specific access, customer ownership, clean auth error responses, and full Partner/Loan endpoint matrices.

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

Status: Open

Blocks next major feature: No, but should be planned with the IAM/auth milestone before a real demo

Problem:
The P0 security patch protects sensitive endpoints with the current Spring Security authenticated gate and HTTP Basic development authentication. It does not yet enforce production JWT authentication, role/action permissions, or customer ownership.

Risk:
Any authenticated local user can reach protected Partner and Salary Advance endpoints until role/action checks are implemented. Customer-facing operations still depend on request-provided customer IDs until ownership enforcement exists.

Recommendation:
Implement JWT-backed authentication, endpoint-level role/action authorization, and ownership checks. Replace the temporary HTTP Basic posture and add role-specific controller/security tests.

Suggested future branch name:
`fix/identity-rbac-endpoint-permissions`

## Recommended Next Roadmap

1. Review/merge the completed P0 security/PII/inactive Partner Company patch.
2. Create current physical schema snapshot.
3. Continue with Loan review/approval workflow.
4. Plan JWT/RBAC and customer ownership enforcement before the real auth/demo milestone.
5. Continue with customer acceptance/disbursement/loan account activation.
6. Add repayment tracking.
7. Add audit trail.
8. Add document checklist/manual review/OCR.
