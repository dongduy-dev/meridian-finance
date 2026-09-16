# MER-FE-003 — Back-Office Administration Frontend Blueprint

## 1. Document Information

| Field | Value |
|---|---|
| Project | Meridian |
| Product | Meridian Lending Platform |
| Document Type | Back-Office Administration frontend rulebook and implementation blueprint |
| Version | 1.0 |
| Status | Planning baseline |
| Author | Dong Duy |
| Scope | Back-Office Administration feature area within Internal Web for product, Partner, import, internal-user, role-assignment, and later configuration administration |

---

## 2. Purpose and Authority

This document defines the stable frontend decisions for Back-Office Administration inside Meridian's shared `internal-web` application: feature ownership, source and route boundaries, authentication and capability-aware entry, navigation, state ownership, presentation, API dependencies, and delivery sequence.

It does not define lending rules, Partner eligibility, product-policy invariants, role assignments, permission semantics, audit policy, or HTTP contracts. Those rules remain with their existing authorities.

| Subject | Authority |
|---|---|
| Intended actors, administrative responsibilities, Partner rules, product policies, and business outcomes | [MER-BIZ-001](../business/MER-BIZ-001-business-requirements-and-workflows.md) |
| HTTP methods, paths, requests, responses, authorization, concealment, and errors | [MER-API-001](../api/MER-API-001-endpoints-and-postman-scenarios.md) and generated OpenAPI |
| Executable backend behavior | Backend source, security configuration, Flyway migrations, and tests |
| Bounded-context ownership and dependency direction | [MER-ARCH-001](../architecture/MER-ARCH-001-bounded-contexts.md), [MER-ARCH-003](../architecture/MER-ARCH-003-dependency-rules.md), and [MER-ARCH-006](../architecture/MER-ARCH-006-api-request-flow-and-dependencies.md) |
| Staff lending-operations frontend architecture | [MER-FE-002](MER-FE-002-staff-web-blueprint.md) |
| Deferred backend and frontend work | [MER-TRACK-001](../project/MER-TRACK-001-follow-up-register.md) |
| Back-Office Administration presentation and client implementation rules | This document |

The blueprint distinguishes four kinds of statement:

- **Backend fact** describes verified executable behavior on which Back-Office Administration may rely.
- **Frontend decision** is a rule established by this blueprint.
- **API dependency** is required before the named administration experience can be complete.
- **Deferred decision** is intentionally outside this milestone.

An API dependency does not authorize the browser to query persistence, call a Customer-owned endpoint, reconstruct missing management state, or fabricate a command contract.

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

Staff Web owns lending work:

- application and case operations;
- product verification and document review;
- Customer and Staff correction operations;
- Loan Officer review and recommendation;
- independent Approver decisions;
- contract preparation and readiness;
- disbursement and LoanAccount activation;
- repayment, settlement, and closure.

Back-Office Administration owns administrative work:

- Loan Product administration;
- Partner Company administration;
- Partner Employee administration and imports;
- internal-user administration;
- assignment of predefined backend-owned roles;
- later operational or system configuration with an approved use case.

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
| `admin:config` | A later approved configuration use case |

The client must not use the `BACK_OFFICE_ADMIN` role name, permission prefixes, wildcard-looking strings, `audit:read` alone, or `document:upload:staff` alone as the top-level membership test.

Identity supports composable role assignments, so a user may receive administrative capabilities through more than one role. `audit:read` is also assigned to the Approver role, while a generic audit explorer is deferred. `document:upload:staff` authorizes a Staff correction task rather than administrative data access.

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

### 5.1 Planned Administration Routes

| Route | Purpose | Delivery |
|---|---|---|
| `/admin` | Back-Office landing and capability-scoped entry | Back-Office FE-CP1 |
| `/admin/partners` | Partner Company administration and discovery | Back-Office FE-CP2 |
| `/admin/partners/:partnerCompanyId` | Company detail, employees, import history, and controlled administration | Back-Office FE-CP2 |
| `/admin/products` | Loan Product administration | Back-Office FE-CP3 |
| `/admin/users` | Internal-user administration and predefined role assignment | Back-Office FE-CP4 |

Only `/admin` is executable in FE-CP1. Unimplemented child paths return the normal safe not-found view. The client does not render placeholder pages, fake records, disabled forms, or speculative API contracts for later checkpoints.

### 5.2 Deferred Routes

The milestone does not define executable routes for:

- `/admin/permissions`;
- `/admin/roles`;
- `/admin/configuration`;
- `/admin/audit`.

These paths remain unavailable unless a later approved requirement establishes a supported use case and its backend contract. The existence of `admin:config` does not create a configuration product.

### 5.3 Route Metadata

Each executable administration route declares a path, label, and exact required permissions. Back-Office route metadata remains separate from Staff route metadata so a new administration permission cannot accidentally grant a lending route, and a Staff permission cannot accidentally grant an administration route.

---

## 6. Authentication and Destination Resolution

Staff Web and Back-Office Administration use the same internal login, in-memory access token, rotating HttpOnly refresh cookie, session restoration, and logout lifecycle defined by MER-FE-002.

### 6.1 Default Destination

| Authenticated capability | Default destination |
|---|---|
| At least one Staff operational capability | `/staff` |
| Back-Office capability and no Staff operational capability | `/admin` |
| Both feature-area capability sets | `/staff` |
| Neither supported feature-area capability set | Safe Staff no-access outcome |

The Staff destination keeps precedence for dual-capability actors to preserve established Staff behavior. `/admin` remains directly reachable when the actor has a Back-Office capability.

### 6.2 Requested Destination

An anonymous request for `/staff` or a child path is preserved only for an actor with Staff Web access. An anonymous request for `/admin` or a child path is preserved only for an actor with Back-Office access. The preserved value contains only the router-derived pathname and query.

The destination resolver must reject external URLs, scheme-relative URLs, feature-like prefixes such as `/administrator`, and requests for an area the authenticated actor cannot access. Rejected destinations fall back to the actor's default destination.

Protected routes wait for session restoration. Anonymous actors redirect to `/login`; authenticated actors never use the login form as an area selector.

---

## 7. Shared Internal Web Shell

The authenticated Staff and Back-Office route trees use one Internal Web shell. The shell owns:

- the Meridian identity and shared visual language;
- responsive desktop and mobile navigation mechanics;
- the actor's email and Staff-session label;
- logout;
- the skip-to-main-content target;
- authorized cross-area links.

Each area supplies its own route metadata and accessible navigation label. Staff uses `Staff navigation`; Back-Office Administration uses `Administration navigation`.

An actor who may enter both areas sees lightweight links to **Staff Operations** and **Back-Office Administration**. An actor sees no link to an unauthorized area. These links represent capability unions, not role switching.

The shell must not display invented counts or preload data for another area. Entering `/admin` does not run Partner, Product, Identity-administration, audit, or configuration requests.

---

## 8. Landing and Access States

### 8.1 Back-Office Landing

The `/admin` landing confirms that Internal Web accepted an authorized administrative session and explains that later workspaces remain capability-scoped. It contains no Partner, Product, or User records, metrics, charts, forms, or API-backed summaries.

The landing uses the shared route-heading and focus convention. Its content remains useful before the later administration workspaces exist without pretending those workspaces are executable.

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

Administrative pages must not place raw identity references, employee codes, salary, bank-account data, restricted notes, tokens, or command payloads in URLs, browser persistence, logs, or general telemetry. A later checkpoint must define the minimum authorized projection and cache boundary for every sensitive response it consumes.

---

## 10. Partner Administration Target

### 10.1 Intended Surface

Back-Office FE-CP2 covers:

- Partner Company discovery;
- Partner Company detail;
- Partner status management;
- Partner Employee list;
- employee import history;
- Partner Employee import by effective month.

Partner owns company, employee, import-batch, employment, status, and freshness rules. The browser presents returned states and command outcomes. It does not calculate Salary Advance eligibility or select the latest valid `COMPLETED` batch for the current UTC effective month.

### 10.2 Backend Facts and Dependencies

| Classification | Statement |
|---|---|
| Backend fact | `partner:read` protects Partner Company list/detail, company employee list, and company import-batch history reads. |
| Backend fact | Partner Company states are `ACTIVE`, `INACTIVE`, and `SUSPENDED`; Partner owns their effect on eligibility. |
| Backend fact | Import-batch states are `PENDING`, `PROCESSING`, `COMPLETED`, and `FAILED`; Partner owns batch authority and freshness. |
| API dependency | Partner Company create, edit, and status commands require supported application use cases and HTTP contracts. |
| API dependency | Partner Employee effective-month import requires an upload/import command, validation outcome, replay behavior, and safe error contract. |

The existing employee read includes sensitive employment evidence. FE-CP2 must verify the minimum Back-Office projection, purpose, masking, pagination, and cache treatment before exposing it broadly.

---

## 11. Loan Product Administration Target

Back-Office FE-CP3 provides a narrow administrative product surface. The initial editable facts are limited to backend-supported activation state, minimum amount, and maximum amount after Loan defines their domain invariants and management contract.

| Classification | Statement |
|---|---|
| Backend fact | `LoanProduct` owns product code, product type, name, description, active state, minimum amount, and maximum amount. |
| Backend fact | `LoanProductDto` also returns policy presentation such as terms, pricing presentation, interest and repayment methods, evidence requirements, and eligibility notes. |
| Backend fact | The current Loan Product HTTP controller exposes active public product reads; it is not an administrative management API. |
| Frontend decision | Product code and product type remain stable identifiers and do not become casual edit controls. |
| API dependency | Administrative discovery, activation changes, and min/max changes require a protected management projection and command contract. |
| Deferred decision | Returned policy presentation may be shown read-only; its presence does not make the policy administratively editable. |

Back-Office Administration does not provide an arbitrary JSON editor. Pricing algorithms, interest-calculation internals, repayment algorithms, exposure rules, settlement rules, overdue rules, allocation order, document-checklist builders, and generic workflow engines remain outside FE-CP3.

---

## 12. Internal-User Administration Target

Back-Office FE-CP4 covers:

- discovery of internal `STAFF` users;
- inspection of safe user status and assigned roles;
- supported internal-user status transitions among Identity's `ACTIVE`, `SUSPENDED`, and `DISABLED` states, subject to the management rules established by the FE-CP4 backend contract;
- assignment and removal of predefined backend-owned roles.

Identity owns User status, roles, permissions, credential state, and authorization facts. The frontend must not create arbitrary permissions, define arbitrary roles, or infer an actor's effective authority from a role label.

The current Identity HTTP area contains authentication and Customer registration/recovery operations but no administrative user-management API. FE-CP4 therefore depends on purpose-limited user discovery, safe status commands, predefined role discovery, role-assignment commands, conflict behavior, and audit-safe responses.

`MER-FU-019` tracks the user-management UI and backend dependency. `MER-FU-022` keeps full permission management deferred.

---

## 13. Accessibility and Presentation

Back-Office Administration uses the Internal Web visual language and targets the same WCAG 2.2 AA baseline as Staff Web.

- Route changes move focus to the page heading without removing visible focus.
- Desktop navigation becomes the existing responsive Sheet at narrow widths.
- Navigation groups have distinct accessible labels.
- Permission-denied, empty, loading, error, and not-found states use text and structure rather than color alone.
- Administrative forms appear only after an executable command contract exists and expose every backend validation or conflict outcome needed for recovery.
- Tables use semantic headings, an accessible name, and only backend-supported filters, paging, and sorting.

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

Each later checkpoint adds contract, query, command, error, responsive, and accessibility tests for its own surface. Frontend checks remain lint, TypeScript compilation, Vitest, and the production build.

---

## 15. Delivery Checkpoints

### Back-Office FE-CP1 — Blueprint and Foundation

- establish MER-FE-003;
- make `/admin` an executable capability-guarded landing;
- define the exact administrative-area capability model;
- share Internal Web chrome while keeping Staff and Administration navigation distinct;
- add administrative no-access and cross-area navigation behavior;
- preserve safe requested destinations and Staff-first default routing;
- add boundary tests and align documentation;
- require no administration data API.

### Back-Office FE-CP2 — Partner Administration

- Partner Company list and detail;
- Partner Employee list;
- employee import history;
- controlled company management;
- effective-month employee import after the required backend commands exist.

### Back-Office FE-CP3 — Loan Product Administration

- protected administrative product projection;
- controlled activation and minimum/maximum amount management;
- read-only policy presentation where it helps an authorized administrator;
- required backend management contract and invariant coverage.

### Back-Office FE-CP4 — Internal-User Administration

- internal-user discovery and safe status presentation;
- supported internal-user status management;
- predefined backend-owned role assignment;
- required Identity management use cases and APIs.

The Back-Office milestone stops after FE-CP4. Generic permission, configuration, and audit products and advanced Loan Product policy builders require separate decisions.

---

## 16. Deliberately Deferred Functionality

This milestone does not define or authorize:

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

OCR remains a separate milestone tracked by `MER-FU-033`. `admin:config` remains an available permission, not evidence that any generic configuration workflow or API exists.

---

## 17. Checkpoint Readiness

A later Back-Office checkpoint is ready to implement when:

1. every list and detail view has a purpose-limited authorized response;
2. every command has an executable use case, exact permission, validation rules, conflict behavior, audit outcome, and safe response;
3. the backend owns business state, calculations, and authoritative selection rules;
4. sensitive fields have explicit display, cache, URL, logging, and disposal rules;
5. route metadata and area navigation use exact permissions;
6. unauthorized routes run no hidden query;
7. loading, empty, forbidden, not-found, validation, conflict, failure, and success behavior is defined;
8. frontend fixtures match current API and source evidence;
9. focused tests and the complete Internal Web verification suite pass.

When a required contract is absent, the checkpoint stops at the dependency. It does not replace the backend with browser rules, fake data, or an inferred management API.
