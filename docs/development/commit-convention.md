# Meridian — Git Commit Convention

## Format

```text
<type>(<scope>): <subject>

[optional body]

[optional footer]
```

Use `!` before the colon for a breaking change:

```text
feat(api)!: change the loan response contract
```

The scope is optional.

## Type Prefixes

| Type | Use |
|---|---|
| `feat` | New behavior or capability |
| `fix` | Defect correction |
| `refactor` | Code restructuring without a behavior change |
| `perf` | Performance improvement |
| `docs` | Documentation-only change |
| `test` | Test addition or correction |
| `build` | Build system, dependency wiring, or packaging change |
| `ci` | CI workflow change |
| `style` | Formatting-only change with no logic change |
| `chore` | Repository or tooling maintenance not covered above |
| `revert` | Reversal of an earlier commit |

## Scopes

A scope identifies the principal change area. It does not create or rename a bounded context.

### Context and Module Scopes

| Scope | Area |
|---|---|
| `identity` | Users, authentication, JWT, roles, permissions, and Spring Security |
| `customer` | Customer profile, readiness, protected identity evidence, and bank accounts |
| `partner` | Partner Companies, employee imports, Partner Employees, and employment links |
| `loan` | All three lending products, application workflow, offers, contracts, activation, and servicing |
| `approval` | Loan Officer recommendations and Approver decisions |
| `document` | Checklists, document versions, storage, review, waiver, and replacement |
| `audit` | Append-only business audit persistence |

`shared` is a technical package scope, not a bounded context. `notification` may be used only for a change to the existing placeholder/future notification area.

Salary Advance, UCL, and Collateral are product behaviors inside `loan`; they are not module scopes. OCR is a planned Document capability, not a top-level module. Risk Assessment is not a Meridian bounded context.

### Technical and Change-Area Scopes

| Scope | Area |
|---|---|
| `shared` | Narrow shared contracts and cross-cutting infrastructure |
| `security` | Cross-cutting security or protection configuration |
| `api` | HTTP contract, global web behavior, or Postman scenarios |
| `db` | Flyway migrations, physical schema, or schema snapshot |
| `config` | Application configuration |
| `build` | Maven wrapper, packaging, or build configuration |
| `ci` | GitHub Actions and CI behavior |
| `deps` | Dependency updates |
| `docs` | Cross-document structure or documentation tooling |

A narrower pragmatic scope is acceptable when it makes the commit easier to understand, provided the PR explains the area and the name does not misrepresent Meridian's architecture.

Omit the scope when the change is genuinely repository-wide:

```text
chore: update gitignore rules
docs: align Meridian documentation
```

## Subject Rules

- Use imperative mood.
- Start with a lowercase letter.
- Do not end with a period.
- Keep the subject at or below 50 characters when a clear subject fits.
- Describe the outcome rather than a low-level edit.

## Body

Use a body when the reason, invariant, or compatibility effect is not clear from the subject. Wrap prose at approximately 72 characters.

```text
fix(loan): preserve repayment replay outcome

Repayment replay must return the durable first outcome even after later
payments change the LoanAccount. Keep requestId reuse detection separate
from the immutable operation result.
```

Meridian uses command-specific request UUID fields where a workflow defines replay behavior. Do not describe a generic `Idempotency-Key` header or shared idempotency service unless the HTTP contract and source actually introduce one.

## Footer

| Footer | Use |
|---|---|
| `BREAKING CHANGE: <description>` | Describe a breaking API or behavior change |
| `Closes #123` | Close an issue when the commit/PR completes it |
| `Refs #456` | Link related work without closing it |

```text
feat(api)!: change approved-offer response fields

BREAKING CHANGE: approved-offer responses rename the repayment item field.

Refs #123
```

## Examples

```text
feat(loan): add collateral verification cycle
fix(document): reject stale version review
fix(db): add forward repair for policy terms
docs(api): document repayment replay behavior
test(customer): cover bank-account concealment
ci: verify backend with Java 25
```

Revert commits identify the reverted subject and explain why when the reason is not obvious:

```text
revert: revert "perf(db): add application queue index"

This reverts commit a1b2c3d because the index regressed the production-shaped
queue query. A replacement needs query-plan evidence.
```

## Flyway Migration Discipline

Meridian uses forward-only Flyway migrations.

- Never modify, rename, or delete a migration that has entered shared or deployed history.
- Introduce a schema correction through a new versioned migration.
- A feature-branch migration may be amended before merge only when it has not entered shared history and the branch context is confirmed.
- Roll back a deployed schema behavior through a new migration that restores the desired state.
- Remove a column or table only after the application no longer depends on it and the rollout sequence is safe.
- Keep JPA mappings, persistence tests, and `MER-DB-CURRENT-SCHEMA.sql` aligned with the resulting physical schema.
