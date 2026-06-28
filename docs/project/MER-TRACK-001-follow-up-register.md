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

Status: Open

Blocks next major feature: Yes

Problem:
Sensitive Partner employee and Salary Advance application endpoints are currently public through SecurityConfig permitAll rules.

Risk:
Unauthenticated callers can perform employee verification and submit Salary Advance applications.

Recommendation:
Restrict sensitive endpoints behind authentication/RBAC or introduce a clearly documented local-only development security profile. Do not silently expand permitAll.

### MER-FU-002 - Remove or split PII-heavy Partner employee DTOs from public responses

Area: Partner / API / PII

Type: Security risk

Priority: P0

Status: Open

Blocks next major feature: Yes

Problem:
Partner employee API responses expose employee code, identity reference, salary, and limit data.

Risk:
Sensitive employment and salary data can leak through public/customer-facing endpoints.

Recommendation:
Create safe public DTOs for customer-facing use. Keep detailed internal/admin DTOs only behind protected back-office/admin endpoints.

### MER-FU-003 - Reject inactive Partner Companies during employee verification

Area: Partner / Salary Advance Eligibility

Type: Business-rule mismatch

Priority: P0

Status: Open

Blocks next major feature: Yes

Problem:
Employee verification checks partner company existence but not active status.

Risk:
Inactive Partner Companies may still be used for normal Salary Advance eligibility.

Recommendation:
Update verification service/policy to reject inactive Partner Companies with a clean business error or appropriate verification outcome. Add tests.

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
Current tests focus on domain/application behavior, but controller/security behavior is not covered enough.

Recommendation:
Add tests for protected endpoint access, PII-safe DTOs, inactive Partner Company rejection, and important error responses.

### MER-FU-006 - Align API docs with implemented endpoints

Area: Documentation / API

Type: Documentation gap

Priority: P1

Status: Open

Blocks next major feature: No

Problem:
API docs do not list implemented employee verification and Salary Advance application endpoints.

Recommendation:
Document method, path, request, response, security posture, and safe PII behavior for:

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

## Recommended Next Roadmap

1. Patch P0 security/PII/inactive Partner Company issues.
2. Add/adjust tests for those P0 fixes.
3. Update API docs for implemented endpoints.
4. Create current physical schema snapshot.
5. Continue with Loan review/approval workflow.
6. Continue with customer acceptance/disbursement/loan account activation.
7. Add repayment tracking.
8. Add audit trail.
9. Add document checklist/manual review/OCR.
