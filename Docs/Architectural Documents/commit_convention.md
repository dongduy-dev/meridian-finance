# Meridian — Git Commit Convention

## Format

```
<type>(<scope>): <subject>

[optional body]

[optional footer]
```

---

## Type Prefixes

| Type | When to Use |
|---|---|
| `feat` | New feature or capability |
| `fix` | Bug fix |
| `refactor` | Code restructuring with no behavior change |
| `perf` | Performance improvement |
| `docs` | Documentation only (README, Javadoc, comments) |
| `test` | Adding or fixing tests |
| `build` | Build system, dependencies, CI pipeline (Maven, Gradle, GitHub Actions) |
| `ci` | CI configuration changes only |
| `style` | Formatting, whitespace, semicolons — no logic change |
| `chore` | Maintenance tasks (dependency bumps, tooling, config) |
| `revert` | Reverting a previous commit |

---

## Scopes

### Module Scopes

| Scope | Bounded Context |
|---|---|
| `loan` | Loan Origination |
| `approval` | Approval Workflow |
| `identity` | Identity & Access (auth, JWT, RBAC) |
| `customer` | Customer Management |
| `document` | Document Management |
| `ocr` | OCR Processing |
| `audit` | Audit & Compliance |
| `notification` | Notification |
| `risk` | Risk Assessment |

### Cross-Cutting Scopes

| Scope | Area |
|---|---|
| `shared` | Shared kernel (base entities, Money VO, domain events) |
| `security` | Security infrastructure (JWT filter, encryption) |
| `api` | API layer (global error handling, response format, versioning) |
| `db` | Database (Flyway migrations, schema changes) |
| `config` | Application configuration |
| `docker` | Docker / Docker Compose |
| `ci` | CI/CD pipeline |
| `deps` | Dependency updates |
| `ui` | Frontend / React |

### Omit scope when the change is truly global:
```
chore: update .gitignore
docs: add architecture decision records
```

---

## Subject Rules

- Imperative mood (command form)
- Lowercase first letter
- No period at the end
- Max 50 characters
- Describe the outcome rather than implementation details.

---

## Body (Optional)

Use when the **why** isn't obvious from the subject. Wrap at 72 characters.

```
feat(loan): add idempotency check for loan submissions

Loan submissions through the REST API were vulnerable to duplicate
processing on network retries. This adds Idempotency-Key header
validation using the shared IdempotencyService, storing keys in
PostgreSQL with a 24-hour TTL.

Financial operations must be idempotent per our architecture rules.
```

---

## Footer (Optional)

| Footer | Usage |
|---|---|
| `BREAKING CHANGE: <description>` | API or behavior breaking changes |
| `Closes #123` | Links to GitHub issue |
| `Refs #456` | References related issue without closing |

```
feat(api)!: change loan response schema to v2 format

BREAKING CHANGE: LoanApplicationDto now uses Money value object
instead of raw BigDecimal for amount fields. All API consumers
must update their deserialization.

Closes #42
```

> The `!` after the scope is a shorthand for `BREAKING CHANGE`.

---

## Real-World Examples

### Features
```
feat(loan): add loan application state machine
feat(approval): add multi-level approval chain
```

### Bug Fixes
```
fix(loan): prevent state transition from DISBURSED to DRAFT
fix(db): fix flyway migration checksum mismatch on V3
```

### Documentation
```
docs: update readme with corrected roadmap
docs(loan): add javadoc to loan application aggregate
```

### Reverts
```
revert: revert "feat(approval): add auto-escalation on timeout"

This reverts commit a1b2c3d. Auto-escalation caused approval loops
when managers were unavailable. Needs redesign.
```

---

## Database Migration & Rollback Strategy

Meridian follows a forward-only migration strategy using Flyway.

- Never modify a migration that has already been applied.
- Never delete migration files from version control.
- Schema changes must be introduced through new versioned migrations.
- Production rollbacks should be performed through a new migration that restores the desired state rather than editing or reverting existing migrations.
- Destructive operations (dropping columns or tables) should only occur after the application code no longer depends on them and has been deployed in a previous release.
---