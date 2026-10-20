# MER-FE-003 — Back-Office Administration Frontend Blueprint

## 1. Document Information

| Field | Value |
|---|---|
| Project | Meridian |
| Product | Meridian Lending Platform |
| Document Type | Back-Office Administration frontend rulebook and implementation blueprint |
| Version | 1.0 |
| Status | Maintained frontend authority |
| Author | Dong Duy |
| Scope | Back-Office Administration feature area within Internal Web for product, Partner, import, internal-user, and predefined role-assignment administration |

---

## 2. Purpose and Authority

This document defines the stable frontend decisions for Back-Office Administration inside Meridian's shared `internal-web` application: feature ownership, source and route boundaries, authentication and capability-aware entry, navigation, state ownership, presentation, supported interactions, and capability exclusions.

It does not define lending rules, Partner eligibility, product-policy invariants, role assignments, permission semantics, audit policy, or HTTP contracts. Those rules remain with their existing authorities.

| Subject | Authority |
|---|---|
| Intended actors, administrative responsibilities, Partner rules, product policies, and business outcomes | [MER-BIZ-001](../business/MER-BIZ-001-business-requirements-and-workflows.md) |
| HTTP methods, paths, requests, responses, authorization, concealment, and errors | [MER-API-001](../api/MER-API-001-endpoints-and-postman-scenarios.md) and generated OpenAPI |
| Executable behavior | Internal Web routes, feature code, API modules, and tests; backend source, security configuration, migrations, and tests for server behavior |
| Bounded-context ownership and dependency direction | [MER-ARCH-001](../architecture/MER-ARCH-001-bounded-contexts.md), [MER-ARCH-003](../architecture/MER-ARCH-003-dependency-rules.md), and [MER-ARCH-006](../architecture/MER-ARCH-006-api-request-flow-and-dependencies.md) |
| Staff lending operations and shared Internal Web operator conventions | [MER-FE-002](MER-FE-002-staff-web-blueprint.md) |
| Follow-up status, sequencing, and future work | [MER-TRACK-001](../project/MER-TRACK-001-follow-up-register.md) |
| Back-Office Administration presentation and client implementation rules | This document |

This blueprint defines the accepted Partner, Loan Product, and Internal User administration surfaces. It does not authorize persistence access, Customer self-service impersonation, browser-derived management policy, or fabricated command contracts. Frontend source and routes establish the executable administration surface; backend endpoint existence alone is insufficient. MER-ARCH-006 owns transaction, lock, replay, and rollback mechanics; MER-API-001 owns exact HTTP schemas and endpoint-specific errors.

---

## 3. Internal Web Trust and Feature Boundaries

Back-Office Administration is a feature area inside `internal-web`, primarily under `/admin/*`. It is not a third frontend application.

| Application or feature area | Boundary |
|---|---|
| Customer Web | Separate application and Customer trust boundary |
| Staff Web | Lending operations under `/staff/*` |
| Back-Office Administration | Administrative capabilities under `/admin/*` |
| Internal authentication | Shared by Staff Web and Back-Office Administration |

Identity uses `STAFF` as the `userType` for internal actors. `STAFF` does not grant membership in Staff Web or Back-Office Administration. Exact permissions determine the frontend capabilities an authenticated internal actor may enter, and the backend remains authoritative for every request.

### 3.1 Staff Web and Back-Office Administration Ownership

Staff Web owns lending operations under [MER-FE-002 Section 4](MER-FE-002-staff-web-blueprint.md#4-staff-web-and-back-office-administration-boundary), including assisted intake, evidence/review/decision, contract, disbursement, and servicing work.

Back-Office Administration owns administrative work:

- Loan Product administration;
- Partner Company administration;
- Partner Employee administration and imports;
- internal-user administration;
- assignment of predefined backend-owned roles.

Generic operational or system configuration is outside the accepted Administration boundary.

A lending command stays under `/staff/*` even when an actor also has Back-Office capabilities. The `document:upload:staff` permission remains a narrow Staff correction capability; it does not establish Back-Office membership.

### 3.2 Shared and Separated Concerns

Internal Web shares authentication, session restoration, protected transport, error handling, exact-permission helpers, route focus, Meridian styling, accessible UI primitives, responsive chrome, actor identity, logout, and test infrastructure.

Staff and Back-Office features keep separate route metadata, navigation, pages, queries, commands, and feature-owned presentation. Shared chrome does not merge Loan, Approval, Document, Partner, Identity, or administrative ownership.

```text
internal-web/
└── src/
    ├── app/                         # providers, router, shared Internal Web composition
    ├── components/
    │   ├── layout/                  # shared authenticated Internal Web chrome
    │   └── ui/                      # repository-owned primitives
    ├── features/
    │   ├── auth/                    # internal session and exact-capability helpers
    │   ├── staff*/                  # Staff lending operations
    │   └── admin*/                  # Back-Office Administration features
    ├── routes/                      # authentication and capability guards
    └── lib/                         # transport and technical utilities
```

This tree shows ownership, not a required file inventory. Source organization may become more specific when an executable administration feature requires it.

---

## 4. Back-Office Capability Model

### 4.1 Administrative Area Permissions

Top-level Back-Office access requires exact membership in at least one of these capabilities:

| Permission | Administrative area |
|---|---|
| `loan:product:manage` | Loan Product administration |
| `partner:read` | Partner administration discovery |
| `partner:manage` | Partner administration commands |
| `identity:user:manage` | Internal-user status and role assignment |
| `admin:config` | Administrative-area entry only; no generic configuration route or command |

The client must not use the `BACK_OFFICE_ADMIN` role name, permission prefixes, wildcard-looking strings, `audit:read` alone, or `document:upload:staff` alone as the top-level membership test.

Identity supports composable role assignments, so a user may receive administrative capabilities through more than one role. `audit:read` is also assigned to the Approver role, while a generic audit explorer is outside the accepted boundary. `document:upload:staff` authorizes a Staff correction task rather than administrative data access.

The seeded Back-Office Admin role combines the five administrative-area capabilities with `audit:read` and `document:upload:staff`. That seed is a backend role assignment, not a frontend persona definition.

### 4.2 Capability Enforcement

- Internal Web accepts a session only when `userType = STAFF` and `customerId = null`.
- Route metadata declares exact permissions for every executable administration route.
- A direct route receives the same capability check as its navigation entry.
- The shell exposes the union of feature areas the actor may enter; it does not ask the actor to select a role or persona.
- Query functions do not run until the route capability guard succeeds.
- Hidden navigation is not authorization. The backend must authorize every administration endpoint.
- A role name without one of the five exact permissions exposes no Back-Office route or navigation.

### 4.3 Capability Change

Session restoration and refresh replace the actor's current permission set. A permission change re-evaluates routes and visible navigation and clears actor-private Query state through the existing session boundary. A `403` remains authoritative even when the browser previously showed an action.

---

## 5. Route Ownership

### 5.1 Administration Routes

| Route | Purpose | Required capability |
|---|---|---|
| `/admin` | Back-Office landing | Any exact administrative-area capability from Section 4.1 |
| `/admin/partners` | Company discovery and supported creation | `partner:read`; creation also needs `partner:manage` |
| `/admin/partners/:partnerCompanyId` | Company detail, current employees, import history, management | `partner:read`; mutation also needs `partner:manage` |
| `/admin/partner-eligibility-reviews` | Pending queue, selected detail, controlled decision | `partner:read`; decision also needs `partner:manage` |
| `/admin/products` | Active/inactive Loan Product discovery, limits, activation | `loan:product:manage` |
| `/admin/users` | Internal Users and predefined-role assignments | `identity:user:manage` |

Route authorization precedes all feature queries. Other children return the safe not-found view without placeholder records, disabled speculative forms, or inferred contracts. The public `/set-password` route belongs to the shared authentication boundary and follows Section 12.

### 5.2 Unsupported Routes

`/admin/permissions`, `/admin/roles`, `/admin/configuration`, and `/admin/audit` have no accepted executable surface. `admin:config` alone grants administrative-area entry, not a configuration product. Any expansion requires an approved use case and its authorized backend contract.

### 5.3 Route Metadata

Each executable administration route declares a path, label, and exact required permissions. Back-Office route metadata remains separate from Staff route metadata so a new administration permission cannot accidentally grant a lending route, and a Staff permission cannot accidentally grant an administration route.

---

## 6. Authentication and Destination Resolution

Shared internal session acceptance, in-memory access, refresh/logout, cleanup, and safe requested/default destination resolution follow [MER-FE-002 Section 11](MER-FE-002-staff-web-blueprint.md#11-authentication-and-session). `/admin` remains reachable for administrative capability; admin-only actors default here, while dual-capability actors default to `/staff`. Area membership never bypasses a child route’s exact permission.

---

## 7. Shared Internal Web Shell

[MER-FE-002 Sections 17–20](MER-FE-002-staff-web-blueprint.md#17-visual-language-and-operational-density) own shared operator language, density, shell/navigation, responsive layouts, accessibility, and confirmation patterns. Administration supplies separate route metadata and the accessible label **Administration navigation**; Staff retains **Staff navigation**. Authorized cross-area links represent capability unions without role switching.

The shell does not preload another feature area. Entering `/admin` runs no Partner, Product, Internal User, audit, or configuration request. Administrative data loads only after the corresponding feature route guard succeeds.

---

## 8. Landing and Access States

### 8.1 Back-Office Landing

The `/admin` landing introduces Partner records/eligibility, lending products, and Staff access, with capability-scoped navigation to their workspaces. It contains no Partner, Product, or User records, metrics, charts, forms, or API-backed summaries.

The landing uses the shared route-heading and focus convention. Its introductory content does not substitute for the feature-specific queries or imply broader administration authority.

### 8.2 No Administrative Access

An authenticated internal actor without a required Back-Office capability receives `No administrative access`. The view states that authentication does not imply administrative authorization and that no administrative data was loaded.

The view offers `/staff` only when the actor has Staff Web access. Logout remains available. It does not reveal hidden route structure, resource existence, or administration data.

### 8.3 No Operational Access

An authenticated Back-Office actor who enters `/staff` continues to receive the Staff-owned `No operational access` outcome. The view may link to `/admin` when the actor has Back-Office access; it does not duplicate the permission definition.

---

## 9. Client State and Data Safety

| State | Owner | Rule |
|---|---|---|
| Authentication | Shared session provider | Holds the accepted internal actor, roles, permissions, and session lifecycle |
| Route capability | Area route metadata and guards | Uses exact permissions before rendering a feature route |
| Server state | Feature-owned TanStack Query definitions | Begins only after route authorization and follows endpoint-specific cache rules |
| Form state | Feature-owned React Hook Form state | Exists only for executable backend commands |
| URL state | Router | Contains safe route identities, supported filters, and paging only |
| Presentation state | Local component state | Controls non-authoritative layout and disclosure state |

The browser must not calculate Partner eligibility, choose the authoritative employee import batch, reinterpret Loan Product policy, derive user authority from role labels, or treat local state as an administrative record.

Administrative pages must not place raw identity references, employee codes, salary, bank-account data, restricted notes, tokens, or command payloads in URLs, browser persistence, logs, or general telemetry. Each executable feature defines the minimum authorized projection and cache boundary for every sensitive response it consumes.

---

## 10. Partner Administration

### 10.1 Supported Surface

Partner Administration provides:

- Partner Company discovery;
- Partner Company detail and supported create/update commands;
- Partner status management;
- Partner Employee list;
- employee import history;
- Partner Employee import by effective month.
- Partner eligibility pending-review queue, current candidate detail, and controlled approve/reject decisions.

Partner owns company, employee, import-batch, employment, status, and freshness rules. The browser presents returned states and command outcomes. It does not calculate Salary Advance eligibility or select the latest valid `COMPLETED` batch for the current UTC effective month.

### 10.2 Authority, Imports, and Eligibility Review

| Classification | Statement |
|---|---|
| Backend fact | `partner:read` protects Partner Company list/detail, the all-batch employee read, the current employee snapshot read, and company import-batch history reads. |
| Backend fact | Partner Company states are `ACTIVE`, `INACTIVE`, and `SUSPENDED`; Partner owns their effect on eligibility. |
| Backend fact | Import-batch states are `PENDING`, `PROCESSING`, `COMPLETED`, and `FAILED`; Partner owns batch authority and freshness. |
| Backend fact | `partner:manage` protects Partner Company create, supported-fact update, status change, and effective-month employee import commands. |
| Backend fact | Import is a synchronous complete effective-month roster snapshot with all-or-nothing authority, durable `requestId` replay, and safe per-row rejection details. |
| Frontend decision | The import form may accept a UTF-8 CSV as a browser-side input convenience. Internal Web validates whether each CSV row can become the existing structured import shape, presents a bounded review, and sends only the mapped JSON rows; Partner remains authoritative for import validation and reconciliation. |
| Backend fact | `partner:read` protects Partner eligibility queue/detail reads; `partner:manage` protects approve/reject decisions. |
| Backend fact | Review states are `PENDING`, `APPROVED`, `REJECTED`, and `SUPERSEDED`; Partner owns current-month, source-batch, identity-match, and terminal-outcome authority. |

The company list uses the existing deterministic backend order. The detail route loads the company, current employee snapshot, and import history only after exact `partner:read` authorization. The main table shows only the employees returned in the backend-selected authoritative snapshot, with its effective month and batch ID. Without a current-month completed batch, it shows an explicit no-snapshot state while retaining import history. History marks only the batch whose ID matches the snapshot's `authoritativeBatchId`; the browser does not derive authority from batch order, status, or its own clock. The snapshot displays employee code, masked Identity Reference, salary, Salary Advance limit, employment status, and active state for authorized source-data review. Ordinary current and historical employee responses provide `maskedIdentityReference` and exclude the raw reference. Full references are needed only in mounted import input/preview memory. Eligibility review selects safe employee identifiers and relies on backend identity matching, so the MVP has no separate Partner reveal action. The browser never obtains full imported references through ordinary employee reads. It uses a zero-retention Query cache after unmount, never places sensitive values in query keys, URLs, browser persistence, operation recovery, logs, or telemetry, and clears with the shared session boundary.

Create, edit, status, and import controls appear only when the actor also has exact `partner:manage`. Changing a Partner Company to `INACTIVE` or `SUSPENDED` requires confirmation of the current status, target status, and effect on future Salary Advance eligibility. Returning a company to `ACTIVE` remains a direct target-state command. Raw CSV content, parsed employee source data, and the mapped command remain in mounted page memory only; the browser does not persist or upload the source file. Consequential commands do not retry automatically. An unknown import result retains the exact `requestId` and unchanged mapped payload in mounted page memory only and offers explicit exact replay. While that result remains unresolved, the import controls remain locked and the page does not present loss of local recovery information as proof that the original import failed. The form states that the selected effective-month import replaces the complete roster and must include every employee used for eligibility. It shows the current snapshot employee count from the existing read. A `COMPLETED` result confirms the stored snapshot; authority is marked only from the subsequent current-snapshot read. A `FAILED` result explains that no employee rows were imported and the snapshot was not replaced, presents at most 25 safe row rejections, and requires a corrected complete roster with a new request ID. Both outcomes refresh snapshot/history reads. Success clears imported source input from the mounted form; failure retains input for correction. The browser uses only the backend-returned batch ID to mark authority; it does not duplicate Partner validation rules or refresh Customer employment links.

The eligibility-review route loads the bounded pending queue and selected detail only after exact `partner:read` authorization. It stores only review identifiers in TanStack Query keys. Candidate responses expose employee code and active employment facts for this review purpose, but exclude Customer identity evidence, salary, and Salary Advance limit. Query cache uses zero retention after unmount and clears with the shared session boundary.

A read-only actor sees review evidence without decision controls. An actor who also holds exact `partner:manage` may explicitly select one backend-returned eligible employee for approval or choose a controlled rejection reason. The page never selects an employee merely because that candidate appears first. Approval and rejection require a confirmation summary of the safe Partner, employee or reason, and terminal consequence before the command is sent. The page disables unavailable actions from backend `approvalAvailable` and `rejectionAvailable` facts and displays stale/non-reviewable reasons. A `SOURCE_BATCH_REPLACED` reason directs Staff to ask the Customer to re-verify employment against the current snapshot. Refreshing that historical review cannot restore its source authority and never enables its decisions. Exact current verification and approved manual review supersede alternative pending employment attempts; the page labels that historical state without implying a later review must exist. It applies no optimistic state. Confirmed decisions refetch queue and detail. After an unknown result, the page compares the refreshed detail with the attempted employee and reason before reporting that decision as confirmed; a still-pending or different terminal outcome remains distinguishable and never triggers an automatic retry.

---

## 11. Loan Product Administration

Loan Product Administration provides a narrow product-owned surface under `/admin/products`. Editable facts are limited to activation state, minimum amount, and maximum amount.

| Classification | Statement |
|---|---|
| Backend fact | `LoanProduct` owns product code, product type, name, description, active state, minimum amount, and maximum amount. |
| Backend fact | Public `/api/v1/loan-products` reads return active products with Customer-safe policy presentation and remain separate from administration. |
| Backend fact | Protected `GET /api/v1/admin/loan-products` requires exact `loan:product:manage` and returns active and inactive products in backend-owned product-code order. |
| Backend fact | Protected limit and activation `PUT` commands require exact `loan:product:manage` and return backend-confirmed target state; an unchanged target does not create another business change. |
| Frontend decision | Product code and product type remain stable identifiers and do not become casual edit controls. |
| Frontend decision | Name and description remain read-only. The amount form and activation action remain visually distinct and refresh the protected administration query after confirmed success. |
| Frontend decision | Deactivation requires confirmation that the product will leave Customer discovery and reject future submissions while historical lending records remain unchanged. Activation remains a direct target-state command. |
| Frontend decision | Internal Web does not optimistically apply product commands or retry them automatically after an unknown transport result. The operator refreshes authoritative state before an explicit same-target retry. |
| Frontend decision | Returned policy presentation may be shown read-only; its presence does not make policy administratively editable. |

Back-Office Administration does not provide an arbitrary JSON editor. Pricing algorithms, interest-calculation internals, repayment algorithms, exposure rules, settlement rules, overdue rules, allocation order, document-checklist builders, and generic workflow engines remain outside the editable administration boundary.

---

## 12. Internal-User Administration

Internal User Administration provides a narrow Identity-owned surface under `/admin/users` for:

- discovery of internal `STAFF` users;
- creation of internal `STAFF` users with predefined roles and a Staff-owned password setup link;
- inspection of safe user status and assigned roles;
- target-state internal-user status changes among Identity's `ACTIVE`, `SUSPENDED`, and `DISABLED` states;
- assignment and removal of predefined backend-owned roles.

Identity owns User status, roles, permissions, credential state, and authorization facts. The frontend must not create arbitrary permissions, define arbitrary roles, or infer an actor's effective authority from a role label.

| Classification | Statement |
|---|---|
| Backend fact | Protected `GET /api/v1/admin/internal-users` requires exact `identity:user:manage` and returns only Staff Users in deterministic normalized-email and stable-ID order. |
| Backend fact | Protected `GET /api/v1/admin/internal-users/assignable-roles` returns predefined non-Customer roles in deterministic role-code order; it does not expose a role or permission editor. |
| Backend fact | Protected creation accepts email, display name, and at least one assignable Staff role. Identity creates an active Staff User with no Customer association and sends a one-time setup link to Internal Web. The administrator receives no password or raw token. |
| Backend fact | Protected password-setup resend applies only to active, verified Staff Users. The administrator explicitly requests a replacement link; Customer Users cannot be targeted. |
| Backend fact | Status and per-role target-state commands require exact `identity:user:manage`. The browser waits for confirmed target state and does not claim another business change for an unchanged target. |
| Backend fact | A real status or role-assignment change invalidates stale access authority. The protected transport handles `401 INVALID_TOKEN` through the shared refresh/session boundary. |
| Backend fact | Role changes leave active refresh sessions usable so refresh can issue current authority. Suspension or disablement revokes all target-User refresh sessions; reactivation does not restore them. |
| Frontend decision | The page displays only User ID-bound safe facts, status, and backend-returned role codes. Email never enters URL state, query keys, browser persistence, operation recovery, logs, or telemetry. |
| Frontend decision | User and assignable-role queries have zero retention after unmount and clear through the shared session boundary. Route authorization succeeds before either query runs. |
| Frontend decision | Commands never update status or role assignment optimistically and never retry automatically. Confirmed success refreshes the protected User list; an unknown transport result preserves the last confirmed view and refreshes authoritative state before an explicit same-target retry. |
| Frontend decision | The create form uses backend assignable roles and has no password, permission, Customer-link, or User-type field. A confirmed create refreshes the list. Setup resend requires an explicit action and does not retry automatically. |
| Frontend decision | A target status of `SUSPENDED` or `DISABLED` requires confirmation that the User loses access and active refresh sessions are revoked. Reactivation to `ACTIVE` remains direct. Predefined role assignment and removal remain direct target-state actions and do not receive blanket confirmation. |

When the target is the current actor, the normal protected refresh path observes the authorization-version mismatch. A still-active actor may refresh once into current roles and permissions; an inactive actor cannot refresh and the shared session manager clears the session. Route and navigation access then re-evaluate from the replaced actor rather than from the command response.

The anonymous `/set-password` route captures a one-time token from the URL fragment, immediately removes the fragment, and retains the token only in memory until confirmation. It posts the token and a 12–72 character password to Identity's existing reset-confirmation contract. The form requires matching confirmation, clears local token state after success, and directs the Staff member to `/login`. Invalid or expired links direct the Staff member to request an administrator-issued replacement. Token values do not enter browser persistence, query keys, logs, telemetry, or request-correlation UI. `DISABLED` remains deprovisioning; no hard-delete control appears.

Staff creation can succeed durably even when setup-email delivery fails. Refresh the User list before deciding whether another create is appropriate, and use explicit setup-link resend/recovery for the existing User; do not infer a failed creation from mail delivery or an unknown POST response.

---

## 13. Accessibility and Presentation

Administration follows the shared Internal Web operator conventions in [MER-FE-002 Sections 17–20](MER-FE-002-staff-web-blueprint.md#17-visual-language-and-operational-density), including its WCAG 2.2 AA target, route/confirmation focus, keyboard and Sheet navigation, semantic tables, status text, evidence-before-action order, and responsive layouts. These are shared requirements, not a claim of formal certification.

Administrative features retain their business terminology and show only supported filters, paging, and sorting. Company availability reductions, product deactivation, and User suspension/disablement retain the explicit confirmations defined in Sections 10–12. Activation/reactivation and predefined-role changes keep their specific direct target-state interactions; shared confirmation patterns do not add blanket confirmation to them. Every form retains validation, conflict, stale, unknown-result, and backend-confirmed success behavior.

---

## 14. Verification Rules

Back-Office frontend tests prove the boundary rather than a seeded persona:

- each selected exact administration permission grants Back-Office entry;
- prefixes, wildcard-looking permissions, role names, `audit:read`, and `document:upload:staff` do not grant entry;
- administration-only permissions do not grant Staff Web access;
- anonymous requested destinations are preserved only for the matching authorized area;
- Staff remains the default for dual-capability actors;
- each area hides unauthorized navigation and rejects direct unauthorized entry;
- `/admin` and administrative no-access rendering perform no administration data request;
- unimplemented `/admin/*` children and unrelated paths remain safe not-found results;
- shared desktop/mobile chrome, logout, route focus, and accessible labels remain intact.

Each administrative surface requires contract, query, command, error, responsive, and accessibility coverage. Frontend checks remain lint, TypeScript compilation, Vitest, and the production build.

---

## 15. Administration Capability Exclusions

The accepted Administration boundary excludes:

- arbitrary role creation;
- permission creation, editing, deletion, or a permission-matrix builder;
- a generic system configuration center;
- generic audit or compliance exploration and export;
- an advanced Loan Product policy builder;
- Customer CRM or profile administration;
- Customer impersonation;
- editing Customer bank-account ownership;
- force-changing LoanApplication state;
- forced approval or disbursement;
- direct LoanAccount balance or schedule manipulation;
- loan reopening or deletion;
- financial override tools;
- analytics or reporting dashboards;
- OCR execution or OCR-result review.

OCR extraction, review, and application of reviewed values belong to the separate Staff-assisted intake workspace under MER-FE-002. `admin:config` remains an available permission, not evidence that a generic configuration workflow or API exists.

---

## 16. Administrative Surface Acceptance

An administrative surface is complete only when:

1. every list and detail view has a purpose-limited authorized response;
2. every command has an executable use case, exact permission, validation rules, conflict behavior, audit outcome, and safe response;
3. the backend owns business state, calculations, and authoritative selection rules;
4. sensitive fields have explicit display, cache, URL, logging, and disposal rules;
5. route metadata and area navigation use exact permissions;
6. unauthorized routes run no hidden query;
7. loading, empty, forbidden, not-found, validation, conflict, failure, and success behavior is defined;
8. frontend fixtures match current API and source evidence;
9. focused tests and the complete Internal Web verification suite pass.

When a required contract is absent, the capability remains unavailable. It does not replace the backend with browser rules, fake data, or an inferred management API.
