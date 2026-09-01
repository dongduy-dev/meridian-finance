# MER-FE-002 — Staff Web Frontend Blueprint

## 1. Document Information

| Field | Value |
|---|---|
| Project | Meridian |
| Product | Meridian Lending Platform |
| Document Type | Staff Web frontend rulebook and implementation blueprint |
| Version | 1.0 |
| Status | Planning baseline |
| Author | Dong Duy |
| Scope | Responsive internal Staff Web for lending review, approval, correction, contract, disbursement, repayment, settlement, and closure operations |

---

## 2. Purpose and Authority

This document defines the stable frontend decisions for Meridian's internal lending operations experience: application topology, source organization, authentication, permission-aware navigation, state ownership, work queues, case workspaces, command recovery, maker-checker presentation, sensitive-data handling, visual language, layouts, route inventory, API dependencies, and delivery sequence.

It does not define lending rules, lifecycle transitions, role assignments, financial calculations, document validity, settlement authority, or the HTTP contract. Those rules remain with their existing authorities.

| Subject | Authority |
|---|---|
| Intended actors, products, workflows, statuses, policies, and business outcomes | [MER-BIZ-001](../business/MER-BIZ-001-business-requirements-and-workflows.md) |
| HTTP methods, paths, request and response shapes, authorization, concealment, and idempotency | [MER-API-001](../api/MER-API-001-endpoints-and-postman-scenarios.md) and generated OpenAPI |
| Executable behavior | Backend source, security configuration, Flyway migrations, and tests |
| Context ownership and runtime coordination | [MER-ARCH-001](../architecture/MER-ARCH-001-bounded-contexts.md), [MER-ARCH-003](../architecture/MER-ARCH-003-dependency-rules.md), and [MER-ARCH-006](../architecture/MER-ARCH-006-api-request-flow-and-dependencies.md) |
| Error identifiers, statuses, safe messages, and caller resolutions | [MER-ARCH-004](../architecture/MER-ARCH-004-api-error-catalog.md), subject to the executable inconsistencies tracked by `MER-FU-043` |
| Deliberately deferred operational queries and product work | [MER-TRACK-001](../project/MER-TRACK-001-follow-up-register.md) |
| Shared Customer Web frontend foundations | [MER-FE-001](MER-FE-001-customer-web-blueprint.md) |
| Staff Web presentation and client implementation rules | This document |

The blueprint distinguishes four kinds of statement:

- **Backend fact** describes verified v1 behavior on which Staff Web may rely.
- **Frontend decision** is a rule established by this blueprint.
- **API dependency** is required before the named operational experience can be complete.
- **Deferred decision** is intentionally outside this blueprint's delivery boundary.

An API dependency does not authorize Staff Web to query persistence directly, combine restricted endpoints into a browser-owned projection, hardcode business policy, or infer missing workflow evidence.

### 2.1 Research Baseline

This planning baseline reflects the current executable Salary Advance, Unsecured Consumer Loan, and Collateral Loan lifecycles through servicing and closure, together with the current security, persistence, and test evidence.

Target-state wording in a business, architecture, or roadmap document is not treated as an executable Staff surface unless a controller, security rule, application service, persistence path, and relevant test evidence support it.

---

## 3. Goals and Non-Goals

Staff Web has six goals:

1. Make pending operational work discoverable without inventing browser-owned queues.
2. Keep each application or LoanAccount decision anchored to one authoritative case workspace.
3. Present role separation, expected versions, and maker-checker constraints before consequential actions.
4. Recover safely from timeouts, refreshes, stale evidence, and concurrent work.
5. Reuse a compact visual and component vocabulary across review, approval, and servicing.
6. Make missing backend read contracts explicit so delivery checkpoints start with the dependencies they need.

The implementation follows this reuse order:

```text
shared UI primitive
        ↓
internal operations component
        ↓
feature component
        ↓
workspace or queue composition
```

New pages must reuse an existing layer before adding a new abstraction. A product-specific rule is not a reason to fork the whole workspace; it is a reason for a feature-owned panel driven by authoritative product data.

### 3.1 Delivery Character

Staff Web is an operational client for durable workflow and financial commands. Delivery may remain component-driven and incremental, but completion means more than rendering a form: each checkpoint must define authority, evidence freshness, operation identity, success reconciliation, uncertain-result recovery, error treatment, and permission coverage.

### 3.2 Non-Goals

This blueprint does not define or authorize:

- Staff Web source code, React scaffolding, package installation, or deployment;
- Back-Office product, Partner, user, role, permission, or configuration screens;
- backend endpoint or schema changes;
- OCR execution or OCR-result screens;
- automated credit, collateral valuation, loan-to-value, or risk decisions;
- Customer Web screens;
- bank, payment-provider, payroll, reconciliation, ledger, or collections integrations;
- a generic workflow builder or configurable approval engine;
- a separate enterprise design-system package;
- pixel-perfect screen specifications;
- analytics or monitoring dashboards unsupported by an operational projection.

---

## 4. Staff and Back-Office Boundary

Meridian uses `STAFF` as the internal authentication type. Business roles and permissions then distinguish lending operations from platform administration.

| Internal area | Included in this blueprint | Excluded for MER-FE-003 or later work |
|---|---|---|
| Lending case operations | Loan Officer review and verification, Approver decision and settlement, Accounting contract/disbursement/servicing, Staff correction tasks, document review | Product and policy administration |
| Partner data | Case-owned safe facts only when a future case projection requires them | Partner Company maintenance, employee imports, and Partner administration |
| Identity | Staff login, refresh, logout, session and permission-aware navigation | User creation, status changes, role assignment, and permission management |
| Audit | Case-safe history when an authorized API exists | Generic audit exploration, compliance search, and export |
| Configuration | None | Operational and system configuration |

Back-Office permissions exist in seeded roles, but most corresponding management use cases and HTTP endpoints do not. `MER-FU-019` and `MER-FU-022` explicitly defer user and permission management UI until backend support exists.

One narrow current seam must remain visible in planning: `document:upload:staff` is seeded to the Back-Office Admin role, while Staff correction upload tasks belong to the lending correction workflow. Staff Web may present the task and its missing proof, but an upload control must appear only for an authenticated actor who actually has `document:upload:staff`. This blueprint does not silently move the permission to Loan Officer or design the Back-Office administration experience.

---

## 5. Backend and Frontend Responsibility Boundary

| Backend and business specifications own | Staff Web owns |
|---|---|
| Valid states, transitions, eligibility, and product verification | Faithful status labels, evidence organization, and action placement |
| Roles, permissions, maker-checker, ownership, and concealment | Permission-aware navigation and explanatory unavailable states |
| Required documents, current versions, readiness, and correction proof | Evidence viewers, task presentation, and safe file interaction |
| Approved terms, balances, allocations, and schedules | Formatting returned values and arranging review summaries |
| Idempotency, serialization, expected versions, and durable replay | Stable browser operation identity and explicit uncertain-result recovery |
| Transaction, audit, history, and cross-module coordination | Pending, success, conflict, and reconciliation experience |
| Safe response projections and restricted-field exclusion | Query caching boundaries and prevention of browser-side disclosure |
| Error status, code, and safe message | Consistent placement, correlation display, and next-step copy |

### 5.1 Authority Rules

- Staff Web must render authoritative server state. It must not predict a transition or optimistically mark a durable command complete.
- Client validation improves input quality. Backend validation and role checks remain authoritative.
- Staff Web formats financial values; it must not calculate pricing, readiness, balances, allocations, payoff, settlement amounts, schedules, or product exposure.
- A hidden or disabled action is not authorization. The backend must authorize every request.
- The client must not interpret a permission description as proof that an endpoint exists. For example, `loan:read` describes work-queue access, but no general Staff application queue or search endpoint currently exists.
- Browser storage must not become an application index, approval ledger, document register, or command-outcome database.
- A case timeline must be based on returned immutable lifecycle evidence. It must not be reconstructed from the current status and local action history.
- The client must not expose Customer-only endpoints through a Staff route merely because a Staff user knows a resource identifier.
- Restricted notes, external references, full bank-account numbers, document content, and internal operation identities must remain outside URLs, analytics, logs, breadcrumbs, and general-purpose caches.

### 5.2 Status Presentation

Backend enum values map through feature-owned label and semantic-treatment maps. Raw values remain available to application logic and safe diagnostics. Text or an icon accompanies color.

Unknown enum values render a neutral “Status unavailable” treatment and a safe refresh path. They do not crash the workspace, become an assumed lifecycle stage, or enable an action.

The same backend value must use the same label within one internal application unless a clearly named context changes its meaning. Queue labels may add operational context, such as “Awaiting document review,” without replacing the durable status.

---

## 6. Actors, Permissions, and Separation of Duties

### 6.1 Operational Actors

| Actor | Current operational responsibility | Representative executable permissions |
|---|---|---|
| Loan Officer | UCL and Collateral verification, LoanApplication review, recommendation, document review, and authorized correction initiation | `loan:read`, `loan:review`, `approval:recommend`, `document:review`, `loan:correction:staff`; waiver additionally requires `document:waive` |
| Approver | Independent decision and payment-backed Administrative Full-Balance Settlement | `loan:read`, `approval:decide`, `document:read`, `audit:read`, `loan:settlement:approve`; settlement also requires the Approver role |
| Accounting Officer | Contract preparation/readiness, destination reveal, manual disbursement, repayment, and administrative closure | `loan:read`, `loan:contract:prepare`, `loan:contract:read`, `loan:disbursement:prepare`, `loan:disburse`, `repayment:update`, `loan:account:close`; closure also requires the Accounting Officer role |
| Back-Office Admin | Platform administration outside this blueprint; currently also holds the narrow Staff document-upload permission | `loan:product:manage`, `partner:read`, `partner:manage`, `identity:user:manage`, `admin:config`, `audit:read`, `document:upload:staff` |

The permission sets above describe the current seeded roles; they are not a frontend role template. Identity supports composable role assignments, and authentication returns sets of roles and permissions. Staff Web therefore gates capabilities from permission and user-type facts, then lets the backend enforce both permission and any stricter business-role rule.

### 6.2 Permission-Gate Rules

- The authenticated session must have `userType = STAFF` and `customerId = null` before entering the internal shell.
- Top-level navigation is capability-driven. A user with several roles sees the union of authorized areas, not a persona switcher.
- Route guards improve navigation and prevent accidental rendering. They do not replace endpoint authorization.
- Query functions must not run for a capability the session lacks. The application must not preload hidden administrative or sensitive case data.
- A `403` after an action was shown is handled as current authority truth: keep the case context, remove or disable the action after session refresh, and explain that authorization changed or the role rule was not satisfied.
- Staff Web must not infer a role from a permission when the command also enforces an explicit role. Settlement and closure are the current examples.
- Permission-denied and maker-checker-denied are different operator explanations even when both use HTTP `403`.

### 6.3 Maker-Checker Presentation

Maker-checker is a backend invariant and a visible operational constraint:

- the Approver must differ from the Loan Officer who submitted the applicable recommendation;
- the Staff member who created a correction request cannot complete its Staff tasks;
- the UI may explain a known separation before submission only when the relevant actor evidence is authoritative;
- if actor evidence is not queryable, the UI must not claim that an action is eligible merely because permissions match;
- a maker-checker rejection preserves the workspace and entered non-sensitive rationale for editing, but does not silently reroute or impersonate another actor;
- restricted actor identifiers from command responses are not placed in URLs or generalized telemetry.

---

## 7. Verified Staff HTTP Surface

The current backend provides a broad set of direct commands and application-scoped reads, but only two narrow operational queues. The distinction determines what can ship safely.

### 7.1 Executable Reads

| Capability | Endpoint | Authority | Current limit |
|---|---|---|---|
| Safe application status | `GET /api/v1/loan-applications/{loanApplicationId}` | Staff `loan:read` | Minimal durable summary; not a case projection |
| Document-review queue | `GET /api/v1/document-review-items?status=AWAITING_REVIEW&page=0&size=20` | `document:review` | Current versions awaiting review; list has no total or page metadata |
| Review-authorized content | `GET /api/v1/staff/loan-applications/{loanApplicationId}/documents/{checklistItemId}/versions/{documentVersionId}/content` | `document:review` | Exact known version only; `no-store`, private attachment |
| Staff correction queue | `GET /api/v1/staff-corrections/tasks?status=OPEN&page=0&size=20` | `loan:correction:staff` | Open tasks only; list has no total or page metadata |
| Current contract | `GET /api/v1/loan-applications/{loanApplicationId}/contracts/current` | `loan:contract:read` | Known application only; masked destination |
| Advisory readiness | `GET /api/v1/loan-applications/{loanApplicationId}/contracts/current/readiness` | `loan:contract:read` | Point-in-time result; optional expected version |
| LoanAccount detail | `GET /api/v1/loan-applications/{loanApplicationId}/loan-account` | `loan:read` | Known application only; safe terms, schedule, and servicing state |
| Repayment history | `GET /api/v1/loan-applications/{loanApplicationId}/repayments?page=0&size=20` | `loan:read` | Known application only; immutable paged outcomes, no external references |

### 7.2 Executable Commands

| Workspace | Commands available now |
|---|---|
| Verification | Start and complete UCL verification; start and complete exact numbered Collateral verification |
| Review and approval | Start review; submit recommendation; submit independent decision |
| Documents | Review the exact current version; waive with added permission; request replacement; stream known content; upload for an open Staff task with `document:upload:staff` |
| Corrections | Complete an eligible Staff task and resubmit an eligible Staff-only or mixed correction |
| Contract | Prepare or regenerate a contract; recompute advisory readiness; confirm readiness |
| Disbursement | Reveal the exact ready-contract destination; confirm an externally completed transfer and activate the LoanAccount |
| Servicing | Record repayment; apply exact payment-backed Administrative Full-Balance Settlement; administratively close an eligible settled account |

### 7.3 Existing Foundations That Are Not Staff Screens

- Customer-owned application and LoanAccount indexes do not authorize Staff indexing.
- Customer-owned document checklist reads do not provide a Staff checklist or document-history projection.
- Customer approved-offer reads do not provide a Staff approval-evidence view.
- `audit:read` has no generic audit-query controller.
- `customer:read` does not currently expose a Staff Customer-review endpoint.
- overdue evaluation is a scheduler-owned backend operation, not a manual Staff command.
- Partner read endpoints are administration-oriented and do not form a safe lending case snapshot.

---

## 8. Operational Read Dependencies

`MER-FU-037` remains the durable home for incomplete Staff work queues, application search, consolidated lifecycle/history, dashboard aggregation, and richer product projections.

### 8.1 Queue Inventory

| Operational need | Current state | Blueprint decision |
|---|---|---|
| Document review | Executable narrow queue | May ship first, with honest pagination limits and direct case linking |
| Staff correction tasks | Executable narrow queue | May ship first; show proof and maker-checker constraints |
| Applications awaiting verification | API dependency | Do not create from saved IDs, browser history, or status polling |
| Applications awaiting Loan Officer review | API dependency | Requires authoritative filterable operational index |
| Applications awaiting recommendation | API dependency | Requires current review-cycle and recommendation eligibility facts |
| Applications awaiting approval | API dependency | Requires latest recommendation evidence and maker-checker-safe case facts |
| Contracts awaiting preparation or readiness | API dependency | Direct known-ID reads are insufficient for a queue |
| Disbursements awaiting transfer confirmation | API dependency | Requires ready-contract index with exact safe financial snapshot |
| Active or overdue LoanAccounts | API dependency | Customer index cannot be reused; Staff servicing index is missing |
| Accounts eligible for settlement or closure | API dependency | Requires authoritative state and reconciliation facts, not client filtering |
| Cross-product application search | API dependency | No Staff application search/filter endpoint exists |

There is no assignment model in the current backend. Labels such as “My work,” “Assigned to me,” ownership SLA, reassignment, and workload balancing must not appear until assignment semantics and an authoritative projection exist. Initial queues are capability-based pending work.

### 8.2 Case Workspace Projection

A production Staff case workspace needs one authorized, PII-minimized read contract that can compose context-owned facts without exposing persistence internals. At minimum it should return, when applicable:

- safe LoanApplication identity, number, product, requested terms, durable status, and submission time;
- safe Customer readiness or identity-review summary deliberately approved for Staff use;
- product-specific verification summary and exact current cycle identity where an action requires it;
- document checklist/readiness summary, current version identities, review outcomes, and permissible content links;
- current correction request, responsibilities, proof state, and safe instructions;
- current review cycle and action eligibility;
- latest recommendation and decision evidence required for independent review;
- current offer/contract summary needed for contract operations without using Customer-only reads;
- current LoanAccount and servicing summary when activated;
- server-derived available actions or blockers where the backend can prove them;
- safe ordered lifecycle/history evidence when a timeline is shown.

The projection must retain bounded-context ownership. Loan does not query Customer, Approval, or Document persistence directly merely to build a convenient DTO; the backend must compose through narrow public contracts or purpose-owned query adapters.

### 8.3 Queue Contract Quality

Operational indexes should provide stable ordering, opaque cursor or documented page semantics, total/continuation evidence, permitted filters, and an `asOf` or equivalent freshness indicator when needed. Queue rows should carry only data required to triage. Restricted notes, raw identity evidence, document content, bank-account numbers, and external payment references belong behind an explicit case action.

The frontend must not implement an aggregated queue by polling every known application. That produces incomplete results, leaks browser history into business state, and creates uncontrolled backend load.

---

## 9. Internal Frontend Application Topology

### 9.1 Decision

Build one internal React application for Staff and Back-Office users when implementation begins, with strict feature and route boundaries inside it. A conceptual repository root is `internal-web/`; this document does not create it.

Customer Web remains a separate application because its public/Customer trust boundary, ownership model, navigation, content exposure, and delivery character differ materially from internal operations.

### 9.2 Why One Internal Application Is Required Now

| Evidence | Consequence |
|---|---|
| All internal actors authenticate as `STAFF` through the same login, access token, refresh cookie, and logout contract | One session and auth foundation is sufficient |
| Users may have multiple roles and permissions | One permission-aware shell handles the union without duplicated login or app switching |
| Staff and administration need the same dense tables, filters, status treatments, forms, errors, and correlation support | Shared internal primitives avoid premature package extraction |
| Backend RBAC and service checks are the real security boundary | Separate frontend deployments would not create authorization by themselves |
| No current evidence requires independent release teams, availability targets, identity providers, or regulated workstation policies | Two deployments would add operational cost without a proven boundary |
| Back-Office HTTP use cases are mostly absent | A second application would initially be a shell without executable work |

### 9.3 Shared Concerns

The internal application shares authentication/session handling, protected transport, error and correlation parsing, permission helpers, route focus, formatting, Meridian tokens, accessible primitives, queue composition, case identity, operation-recovery presentation, and test infrastructure. These are technical or presentation concerns; they do not merge Loan, Approval, Document, Identity, Partner, or Back-Office business ownership.

### 9.4 Separated Feature Boundaries

One bundle does not mean one undifferentiated feature tree.

```text
internal-web/                         # conceptual; not created by this document
└── src/
    ├── app/                          # providers, router, internal shell, session
    ├── routes/
    │   ├── staff/                    # lending operations route compositions
    │   └── admin/                    # reserved for MER-FE-003
    ├── features/
    │   ├── applications/
    │   ├── verification/
    │   ├── documents/
    │   ├── corrections/
    │   ├── review/
    │   ├── approval/
    │   ├── contracts/
    │   ├── disbursement/
    │   └── servicing/
    ├── components/
    │   ├── ui/                       # local primitives
    │   └── operations/               # shared internal domain presentation
    └── lib/                          # API client, auth, errors, formatting, IDs
```

Rules:

- Staff features must not import future Admin feature internals, or vice versa.
- Shared code contains technical primitives and genuinely repeated presentation, not permission-specific business policy.
- Navigation groups are permission-aware, but direct routes remain guarded.
- The client does not eagerly fetch all data for all visible role groups.
- MER-FE-003 owns Admin pages, Admin route inventory, and Admin delivery checkpoints.

### 9.5 Security Implications

One internal application does not grant one internal authority level. Login establishes only an internal session; every navigation item, query, reveal, and command remains permission-scoped, and the backend remains authoritative for permission, business role, state, maker-checker, and evidence checks.

Code delivered in the browser cannot be treated as confidential authorization policy. Lazy Staff/Admin route chunks reduce loading and accidental data access but are not a security boundary. Sensitive resources are fetched only after the current actor has the route capability and explicitly enters the relevant workspace.

### 9.6 Required Now, Useful Later, and Unnecessary Complexity Today

| Timing | Topology capability |
|---|---|
| Required now | One internal auth/session, route-level capability metadata, Staff/Admin feature boundaries, lazy route loading, permission-scoped queries, separate navigation groups, cache clearing on session change |
| Useful later | Workspace package only after real duplication, independent route build chunks, feature ownership checks, shared visual regression harness, per-area telemetry budgets |
| Unnecessary today | Microfrontends, module federation, separate Staff and Admin deployments, separate identity clients, shared Customer/Internal design-system package, monorepo orchestration beyond actual need |

### 9.7 Reconsideration Triggers

Reconsider separate Staff and Admin applications only when repository or operating evidence establishes one or more of these boundaries:

- different identity providers, session policies, device trust, or network zones;
- independent release ownership and materially different deployment cadence;
- distinct availability, incident isolation, or compliance obligations;
- Admin functionality large enough to create unacceptable bundle, testing, or change-coupling cost after route splitting;
- contractual or regulatory rules that prohibit co-resident code or shared runtime origin.

A theoretical future team split is not sufficient evidence.

---

## 10. Technology and Dependency Policy

Staff Web should use the proven Customer Web foundation unless implementation evidence justifies a change.

| Concern | Decision |
|---|---|
| Application | React with Vite and strict TypeScript |
| Routing | React Router with route objects and lazy feature boundaries |
| Server state | TanStack Query |
| Forms | React Hook Form with Zod schemas at browser input and API response boundaries |
| Styling | Tailwind CSS with centralized Meridian tokens |
| Primitives | Local shadcn/ui-style primitives whose source remains in the repository |
| Icons | Lucide React |
| Unit and component tests | Vitest and React Testing Library |
| Tables | Semantic HTML table plus local composition first; add a grid library only after executable queue needs prove it |
| Dates and money | Centralized `Intl` formatting with explicit locale and VND rules |

No Redux or another general state store is planned. TanStack Query, the auth provider, form state, URL state, and local component state cover the verified needs.

### 10.1 Dependency Rules

- Add a dependency only when a current checkpoint has a concrete use that local composition cannot meet safely.
- Prefer accessible native controls and repository-owned primitives over wrapper stacks.
- Do not introduce a data-grid package for a two-filter list. Reconsider when queues require verified column pinning, virtualization, server sorting, and large-data accessibility.
- Do not generate an API client until generated output ownership, regeneration, response validation, and review noise are resolved.
- Do not extract Customer Web code into a shared package before Staff implementation proves stable duplication. Reusing decisions is required; sharing code is evidence-driven.
- Keep feature-specific enums and DTO schemas with the feature. Do not create a global catalogue that silently couples unrelated backend contexts.

---

## 11. Authentication and Session

### 11.1 Browser Credential Model

Staff Web uses the existing Identity contract:

- password login returns actor facts and an access token;
- the access token remains in memory;
- the rotating refresh token remains in the backend-issued HttpOnly cookie;
- authenticated API requests use the Bearer access token;
- refresh and logout send credentials so the restricted refresh cookie is included;
- the client allows only one refresh request at a time and replays an eligible failed request once;
- logout clears the in-memory token, actor facts, pending operation state, private Query cache, and sensitive local component state.

The internal app must reject a successful authentication response unless `userType = STAFF` and `customerId = null`. It must not transform a Customer session into a Staff session or expose the Customer application shell.

### 11.2 Session Provider Responsibilities

The session provider owns only:

- access token and authenticated Staff actor facts;
- login, refresh, and logout coordination;
- the single in-flight refresh promise;
- session restoration state;
- permission and role membership helpers;
- session epoch used to prevent results from a previous actor entering a new cache.

It does not own case data, forms, queue filters, selected application, or business operation results.

### 11.3 Route Behavior

- Public `/login` redirects an active Staff session to the first permitted operational destination.
- Protected routes wait for session restoration before rendering or redirecting.
- A session with no Staff Web capabilities receives a dedicated “No operational access” page with logout, not an empty dashboard.
- Expired access initiates one refresh and one replay. A second `401`, a failed refresh, or invalid actor facts ends the session.
- Account or permission changes discovered through refresh cause route and query capability re-evaluation.
- Browser back navigation must not reveal a cached sensitive panel after logout.

### 11.4 Login Safety

Login uses the common safe credential-error copy. It does not distinguish unknown user, wrong password, unverified account, inactive account, or active lock beyond the safe backend response. `429` displays the returned safe message and `Retry-After` when exposed.

Passwords, Bearer tokens, refresh cookies, and login request bodies never enter client logs, error-reporting payloads, URLs, or analytics.

---

## 12. State Ownership

| State | Owner | Examples |
|---|---|---|
| Server state | TanStack Query | queues, case projection, contract, readiness, LoanAccount, repayment history |
| Authentication state | Session provider | actor, roles, permissions, access token, refresh lifecycle |
| Form state | React Hook Form | rationale, controlled codes, expected amount confirmation, external reference, value dates |
| URL state | Router/search parameters | queue filters, page/cursor, workspace section, known application route |
| Operation state | Feature hook with narrow session persistence where allowed | logical request UUID, submitted payload digest, uncertain-result status |
| Ephemeral sensitive state | Local component memory only | revealed destination, document blob URL, restricted note draft |
| Pure presentation state | Local component state | expanded panel, column visibility, confirmation dialog |

### 12.1 Server-State Rules

- Query keys include resource identity, authoritative filters, page/cursor, and relevant expected version.
- Query data is never copied into a general store.
- Reads may retry once for a network failure or server `5xx` when the endpoint is side-effect free. `401`, `403`, `404`, `409`, `422`, and `429` do not use generic retry.
- Mutations do not use automatic TanStack Query retry. Auth refresh may replay an eligible request once under the API client's rules.
- Mutation success invalidates the narrow affected queue, case, contract, readiness, account, and history keys. Financial commands then refetch authoritative state before presenting the workspace as reconciled.
- Critical transition and financial mutations do not use optimistic updates.
- Query cache is memory-only for the MVP. It is not persisted to IndexedDB, local storage, or a service worker.

### 12.2 URL-State Rules

URLs may contain safe resource identities, workspace section, queue status, filter values approved for the query contract, and pagination state. They must not contain:

- rationale or restricted notes;
- raw Customer identity facts;
- document filenames when avoidable;
- external transfer or payment references;
- bank-account data;
- operation request UUIDs;
- revealed sensitive data;
- internal actor, audit, or persistence evidence IDs not designed as route identifiers.

### 12.3 Form-Draft Rules

Non-sensitive filter preferences may use session storage. Consequential command forms default to memory-only drafts. Restricted notes, external references, full account numbers, document content, and financial command payloads are not stored persistently.

If a page refresh discards an unsent sensitive draft, the UI should warn before an intentional navigation when practical. Preventing browser crashes or tab closure is not a reason to persist restricted content.

---

## 13. API Client and Response Boundaries

### 13.1 Client Modules

Use one protected transport client and feature-owned endpoint modules:

```text
lib/api/                  # base URL, headers, auth replay, error parsing
features/applications/api
features/verification/api
features/documents/api
features/corrections/api
features/review/api
features/approval/api
features/contracts/api
features/disbursement/api
features/servicing/api
```

Feature modules expose typed application-facing functions, not raw `fetch` responses.

### 13.2 Response Validation

- Validate response boundaries with feature-owned schemas where malformed or expanded data could affect a decision.
- Reject missing required monetary, state, version, or identity fields instead of supplying business defaults.
- Preserve unknown enum values for safe neutral rendering; do not coerce them into a known action.
- Treat unexpected restricted fields as a contract-review signal. Do not automatically render arbitrary response properties.
- Convert monetary JSON numbers to the application's selected exact representation without floating-point arithmetic. Staff Web only formats and compares exact values supplied for confirmation; it does not calculate them.

### 13.3 Request Correlation

Generate a fresh `X-Request-ID` for each HTTP attempt unless the transport replay implementation deliberately preserves it for diagnosing one attempt chain. Display the response correlation value on blocking errors and copy it only through an explicit control.

`X-Request-ID` is transport correlation. It is never the logical business operation identity.

---

## 14. Operation Identity and Uncertain-Result Recovery

### 14.1 Identity Classes

| Class | Examples | Client rule |
|---|---|---|
| Exact business request UUID | document upload/review, correction completion/resubmission, contract preparation/readiness confirmation, disbursement, repayment, settlement, closure | Keep one stable UUID for one logical payload until the result is reconciled |
| Expected evidence identity | Collateral `expectedVerificationId`, review `expectedReviewCycleId`, contract `expectedCurrentContractVersion` or `expectedContractVersion`, document `documentVersionId` or replacement baseline | Refetch and require operator review after stale conflict; never treat as idempotency |
| No client business UUID | verification start/complete, review start, recommendation, decision, destination reveal | No automatic command retry after an uncertain result; reconcile from an authoritative read |
| Transport correlation | `X-Request-ID` | One HTTP-attempt diagnostic; never reused as business identity |

### 14.2 Current Exact-Identity Fields

| Command | Field |
|---|---|
| Staff document upload | `uploadRequestId` |
| Document review | `reviewRequestId` |
| Staff correction completion | `completionRequestId` |
| Staff correction resubmission | `resubmissionRequestId` |
| Contract preparation or regeneration | `preparationRequestId` |
| Readiness confirmation | `confirmationRequestId` |
| Manual disbursement | `requestId` |
| Repayment | `requestId` |
| Administrative Full-Balance Settlement | `requestId` |
| Administrative closure | `requestId` |

### 14.3 Logical-Operation Rules

- Create the UUID when the operator begins the final submission attempt, not on every render.
- Keep the same UUID and exactly the same logical payload for a retry after timeout, lost response, refresh, or reconnect.
- A changed amount, reference, value date, outcome, version, reason, task selection, or other semantic input is a new logical operation and receives a new UUID.
- Where backend replay includes actor identity, retry uses the same authenticated actor. Another Staff member does not inherit the operation identity.
- Disable duplicate submits while a request is in flight, but do not confuse button disabling with durable idempotency.
- Persist an unresolved operation identity only in narrowly scoped session storage when survival across refresh is necessary. Store the minimum safe payload digest and resource identity, never the external reference, notes, full financial payload, or revealed data.
- After refresh, a digest-only record can support “Check result” but cannot reconstruct a sensitive payload. Retry is allowed only after the operator re-enters or reselects the exact inputs and the client verifies that their digest matches the unresolved operation. Files must be reselected; the browser must not persist their bytes.
- Remove resolved operation records after authoritative reconciliation or an explicit operator abandonment of an unsubmitted draft.
- `409 IDEMPOTENCY_KEY_REUSED` is not a prompt to generate a new UUID automatically. Preserve evidence, show the conflict, and require reconciliation.
- When the backend defines exact replay after later state changes, recovery remains available even if the current state would reject a new operation. Staff Web labels this as replay of prior evidence and never substitutes a new UUID to bypass current state.

### 14.4 Recovery State Machine

```text
DRAFT
  ↓ submit
IN_FLIGHT
  ├─ confirmed response ──> RECONCILING ──> RESOLVED
  ├─ definite rejection ──> DRAFT or BLOCKED
  └─ timeout / lost response ──> RESULT_UNKNOWN
                                  ├─ exact UUID replay allowed ──> replay same payload
                                  └─ no UUID ──> refetch authoritative evidence
```

For commands with an exact business UUID, the recovery panel offers “Check result” before “Retry same operation.” A retry uses the identical UUID and payload.

For commands without a business UUID, an uncertain result is more restrictive:

- refetch the case, current cycle, and relevant queue;
- if the projection proves the command outcome, reconcile and continue;
- if the projection proves it did not occur and state is still eligible, permit a fresh operator-confirmed attempt;
- if the projection cannot prove either outcome, label the result unresolved and block a contradictory command.

Recommendation, decision, and UCL verification currently lack sufficient read/history projections for robust browser reconciliation. Those workspaces are API dependencies before production command enablement, even though the POST endpoints exist.

### 14.5 Destination Reveal Exception

Destination reveal has no business UUID because it is a sensitive read action rather than a durable workflow command. A lost response is not replayed automatically. The operator explicitly reveals again after the client revalidates current contract version and eligible state.

---

## 15. Concurrency, Stale Evidence, and Reconciliation

The backend owns advisory locks, row locks, transaction boundaries, expected-version validation, request serialization, and maker-checker. Staff Web owns a legible conflict experience.

### 15.1 Conflict Rules

- On `409`, preserve the workspace and safe unsent inputs, stop automatic retry, and refetch authoritative state.
- Highlight which evidence changed: review cycle, verification cycle, document version, contract version, application state, account state, or business request identity.
- Do not silently replace the expected version and resubmit. The operator must review the updated evidence and confirm again.
- If a queue row disappears after another Staff member acts, show “Work no longer pending” and link to the refreshed case when a case read exists.
- If a case becomes inaccessible, discard its private cache and display the safe `403` or `404` outcome without revealing whether permission, concealment, or deletion caused it.
- Command buttons use the latest fetched eligibility only as presentation. The backend may still reject because another transaction won the race.

### 15.2 Reconciliation After Success

| Command group | Required post-success reads |
|---|---|
| Document review or correction | queue, case/document state, correction tasks |
| Verification or review | case verification/review projection and applicable queue |
| Recommendation or decision | approval evidence, case status, applicable queue |
| Contract preparation/readiness | current contract, readiness, case status |
| Disbursement | case status, LoanAccount, final schedule, disbursement queue |
| Repayment | LoanAccount, repayment history, servicing queue |
| Settlement | LoanAccount, repayment history, settlement/closure queues |
| Closure | LoanAccount and closure queue |

Where a required read contract does not exist, the command checkpoint remains blocked or must ship with a deliberately designed, backend-supported command-result query. A transient POST response alone is not a durable operational workspace.

### 15.3 Time

- Display backend timestamps in one explicit business locale and time zone selected by product policy; do not let browser locale silently change business meaning.
- Send date-only value dates exactly as `YYYY-MM-DD` from a date control, without UTC conversion.
- Display the server's evaluation date and value-date constraints; do not calculate “today” as a financial authority.
- Offer or contract timing shown to Staff must come from Staff-authorized projections, not Customer-only APIs or the browser clock.

---

## 16. Sensitive Data and Evidence

### 16.1 Data-Minimization Rules

Staff Web must not expose or log raw:

- salary, identity references, employee codes, or protected verification evidence;
- bank-account numbers except in the explicit destination-reveal surface;
- document binary content, OCR text, or extracted identity evidence;
- correction contents or internal reviewer notes outside the authorized workspace;
- canonical external transfer/payment references after submission;
- request UUIDs, internal operation IDs, actor IDs, audit IDs, encryption details, fingerprints, storage keys, or provider paths unless an API deliberately exposes a safe operational identifier.

Queue rows are triage surfaces, not evidence dumps.

### 16.2 Document Content

- Fetch an immutable version only after an explicit operator action in a document-review workspace.
- Respect `Cache-Control: no-store, private` and `X-Content-Type-Options: nosniff`.
- Use a memory-only object URL and revoke it when the viewer closes, the version changes, the route leaves, or the session ends.
- Do not put content in TanStack Query, browser cache helpers, service workers, thumbnails persisted across sessions, or client logs.
- Keep the version identity visible so the operator understands exactly what is being reviewed.
- Do not imply document history from a current queue item. Historical-version discovery requires an API projection.

### 16.3 Disbursement-Destination Reveal

The reveal panel is a protected local-memory surface:

- display the masked contract destination first;
- require an explicit “Reveal destination” action and explain the operational purpose;
- revalidate exact current contract version before reveal;
- hold the full account number in local component memory only;
- never place the reveal response in TanStack Query or persistent storage;
- hide and clear it on route change, tab background timeout, explicit hide, contract change, or logout;
- do not provide automatic clipboard copy; if a future copy control is approved, it must be explicit, audited where required, and visibly confirm clipboard risk;
- never include the full value in confirmation summaries, toast messages, print styles, screenshots generated by the app, or telemetry.

The disbursement form submits only the external transfer evidence requested by the backend. It does not echo the full destination into the command body.

### 16.4 Restricted Notes and External References

Restricted notes use a clearly labeled field with an audience warning. They are not reused as Customer-visible instructions. External transfer/payment references are write-only evidence from the browser's perspective after successful submission because command responses intentionally exclude them.

After success, replace the raw field with a neutral “Reference recorded” indicator. Recovery of an uncertain exact-identity command uses the stored logical operation identity and safe digest, not redisplay of the reference from logs or a general cache.

---

## 17. Visual Language and Operational Density

Staff Web shares Meridian Navy, Gold, and Ivory anchors, typography, spacing scale, radii, focus treatment, status semantics, and control primitives with Customer Web. It uses them differently: internal pages are information-dense, desktop-first work surfaces rather than marketing or guided self-service journeys.

### 17.1 Staff Character

The interface should feel calm, exact, and accountable:

- Navy establishes shell, headings, and primary control hierarchy.
- Gold is a restrained accent for selected navigation, focus support, and high-value emphasis; it is not a general warning color.
- Ivory and neutral surfaces separate dense evidence groups without excessive cards.
- Status uses semantic color plus icon and text.
- Consequential actions reserve strong color and are visually separated from ordinary navigation.
- Tables favor readable row rhythm and stable alignment over maximum row count.
- Financial values use tabular numerals and right alignment.

### 17.2 Density Rules

- Use a table for repeated comparable queue facts.
- Use definition lists or compact key-value groups for one case.
- Use cards only for meaningful grouping, not every field.
- Keep the current case identity, product, durable status, and primary blocker visible while moving between workspace sections.
- Place evidence and its decision form close enough to review together, while keeping irreversible actions in a dedicated action rail or full-page step.
- Do not hide required evidence in tooltips.
- Truncation must provide an accessible full value when the value is safe to display. Restricted text is not made hover-visible across the page.

### 17.3 Status and Risk Treatments

| Treatment | Use |
|---|---|
| Neutral | Historical, closed, cancelled, superseded, or unavailable |
| Information | Pending work, active servicing, or point-in-time readiness |
| Success | Verified, accepted, ready, settled, or completed evidence |
| Warning | Action required, overdue, stale evidence, or unresolved result |
| Danger | Rejection, failed verification, destructive outcome, or blocking inconsistency |

“Approved,” “settled,” and “closed” are distinct labels. “Ready for disbursement” must never look like “disbursed.” “Settlement” must never be presented as a discount or write-off.

---

## 18. Layouts, Navigation, and Responsive Behavior

### 18.1 Layout Templates

| Template | Purpose | Representative routes |
|---|---|---|
| Auth layout | Focused credential entry and session errors | `/login` |
| Queue layout | Title, saved-in-URL filters, table/list, count/continuation, empty and stale states | document review, Staff corrections, future verification/approval/servicing queues |
| Case workspace | Persistent safe case header, section navigation, evidence canvas, contextual action rail | application verification, review, decision, contract |
| Financial operation | Full-width authoritative summary, value-date/reference form, confirmation step, reconciliation result | disbursement, repayment, settlement, closure |
| Access state | No capability, forbidden, missing case, unresolved result | protected fallbacks |

### 18.2 Internal Shell

Desktop shell:

- left navigation grouped into Work, Lending, Servicing, and Administration;
- Administration group appears only when future Admin routes and permissions exist;
- top bar contains current Staff identity summary, environment label when non-production, session menu, and optional global application search only after its API exists;
- main content has a bounded readable width for forms and a wider mode for queues and side-by-side evidence.

The shell does not display decorative dashboard metrics without an authoritative aggregation endpoint.

### 18.3 Queue Navigation

Queue filters and pagination live in the URL so a safe view is refreshable and shareable among authorized Staff. The current document and correction endpoints permit only their documented status and page/size parameters; the client must not show unsupported product, owner, date, sort, or free-text filters.

No local “saved views” are planned until server-side filter contracts stabilize. A saved view must never serialize sensitive case data.

### 18.4 Case Workspace Navigation

Sections use nested routes or a safe `section` path segment rather than client-only tabs when deep linking materially helps handoff. The case header remains consistent across sections and shows only facts from the authorized case projection.

Workspace sections are capability- and state-aware, but historical evidence stays inspectable when authorized even when no action remains. Hiding a completed section must not erase audit context.

### 18.5 Responsive Rules

Staff operations are designed primarily for laptops and desktop monitors because evidence comparison, schedules, and queues benefit from width. The application remains usable at narrow widths:

- navigation becomes a Sheet;
- queue tables become prioritized stacked rows or horizontally scroll within a labeled region;
- side-by-side evidence becomes an ordered single column;
- the action rail moves after evidence, never before facts required for the decision;
- sticky headers and action bars must not consume most of a small viewport;
- full financial schedules retain columns through controlled horizontal scrolling rather than dropping amounts;
- dialogs that contain complex forms become full-page routes or full-screen Sheets.

Viewport width alone does not authorize or prohibit a command. A consequential action on a narrow device must still present every required fact, warning, and confirmation in a coherent sequence. Operational rollout may recommend a wider workstation based on usability testing, but the client must not silently omit evidence on mobile.

---

## 19. Accessibility and Interaction Baseline

Staff Web targets WCAG 2.2 AA for implemented screens.

### 19.1 Required Behavior

- All controls are keyboard reachable and have visible focus.
- Queue tables use proper headers, captions or accessible names, and announced sort state only when server sorting exists.
- Status never relies on color alone.
- Forms have persistent labels, descriptions, inline errors, and an error summary for consequential submissions.
- Confirmation dialogs return focus to the invoking control when cancelled and to the reconciled result on success.
- Route changes move focus to the page heading through the shared route-focus manager.
- Loading regions expose meaningful status without trapping focus.
- Toasts supplement, but never replace, persistent command outcomes.
- Document viewers provide filename, type, version, download/open behavior, and a non-visual fallback; visual evidence alone is not made accessible by an empty `alt` value.
- Reduced-motion preferences disable nonessential movement.
- Touch targets and pointer alternatives remain usable on tablets.

### 19.2 Decision Accessibility

Maker-checker, stale-version, financial, and destructive warnings appear in programmatically associated text before the final button. Disabled actions must have an adjacent readable reason; a disabled button with only a tooltip is insufficient.

When a command changes durable state, success focus moves to a heading that names the outcome and includes the safe resource reference. The operator must be able to distinguish “request sent,” “result unknown,” and “result confirmed” without relying on animation or color.

---

## 20. Action Taxonomy and Confirmation

| Class | Examples | Presentation and confirmation |
|---|---|---|
| Read and navigation | open case, filter queue, inspect schedule | Immediate; no confirmation |
| Sensitive read | stream document, reveal disbursement destination | Explicit purpose action, local-memory display, easy hide, no background prefetch |
| Evidence mutation | upload version, review document, waive, request replacement, complete correction task | Exact evidence summary and controlled outcome; explicit submit |
| Workflow transition | start/complete verification, start review, recommend, decide, prepare contract, confirm readiness, resubmit correction | Current state/cycle summary, resulting transition language, explicit confirmation |
| Financial operation | confirm disbursement, record repayment, settle full balance | Dedicated page, authoritative amount/date summary, double-action confirmation, no optimistic result |
| Terminal administrative action | close settled LoanAccount | Dedicated confirmation naming that closure is administrative and non-financial |

### 20.1 General Rules

- One primary command per action surface.
- Buttons name the business action: “Record repayment,” not “Submit.”
- Consequential confirmations repeat safe case identity, product, current state, expected version, amount, and value date returned or entered for that command.
- Confirmation copy describes the real outcome. Contract readiness does not transfer funds; settlement records a full payment; closure does not alter balances.
- Reject, failed verification, replacement request, and return actions require their controlled reason and instruction inputs before confirmation.
- The client never invents reason codes. Options come from an explicit stable contract or a versioned frontend map backed by the API specification for that checkpoint.
- Successful commands end on a persistent result panel after authoritative refetch, not a toast-only message.

### 20.2 Financial Confirmation

Disbursement, repayment, and settlement use two steps:

1. enter and validate the operator-supplied evidence;
2. review a read-only summary and execute the exact operation.

The review step shows the normalized display value locally but must not claim how the backend will canonicalize an external reference. After submission, the raw reference is removed from display.

Settlement confirmation must show the locked/current expected full outstanding amount obtained from an authoritative Staff read and explain that no discount, waiver, or write-off is supported. Closure confirmation must show `SETTLED` and explain that it records `CLOSED` without another payment.

### 20.3 Major Command Classification

| Command | Classification | Consequence for Staff Web |
|---|---|---|
| Start verification or review | Workflow transition | Exact current evidence summary; no automatic retry after uncertainty |
| Complete verification | Workflow transition; failed outcome receives destructive emphasis | Outcome-specific confirmation and authoritative cycle reconciliation |
| Upload or review document, complete correction task | Evidence mutation within workflow | Exact version/task, stable UUID where supported, proof refetch |
| Request correction or resubmit | Workflow transition | Structured plan/task summary, cycle identity, post-command case refetch |
| Recommend approval/rejection | Workflow transition | Clearly labeled as recommendation, never final approval |
| Approve or return | Workflow transition | Strong independent-decision review and maker-checker context |
| Reject | Destructive terminal decision | Explicit adverse-outcome confirmation with required reason |
| Prepare/regenerate contract or confirm readiness | Workflow transition | Exact contract version and stable UUID; distinguish readiness from transfer |
| Reveal destination | Sensitive-data access | Explicit purpose, local memory, no automatic retry or cache |
| Confirm disbursement | Financially consequential action | Dedicated two-step page, exact UUID, reconciliation |
| Record repayment | Financially consequential action | Dedicated two-step page, no allocation preview calculation |
| Apply Administrative Full-Balance Settlement | Financially consequential action | Approver-only full-outstanding confirmation, exact UUID |
| Close LoanAccount | Destructive administrative action | Accounting-only terminal confirmation, exact UUID, no financial fields |

No current major Staff lending command is treated as an ordinary reversible server update. Harmless local interactions such as filters, panel expansion, and column visibility may update immediately because they do not claim backend state.

---

## 21. Work Queues

### 21.1 Queue Row Contract

Every queue row should answer:

- what case or task is this;
- why is it in this queue;
- what product and current durable state apply;
- what evidence freshness or age is safe to show;
- what action is available to this actor;
- whether a blocker requires opening the case.

Rows do not contain action forms. The operator opens the case or dedicated action route before a durable command.

### 21.2 Document-Review Queue

This is executable now for `AWAITING_REVIEW` current versions.

| Element | Decision |
|---|---|
| Row identity | Checklist item and current version, linked to safe application route |
| Visible facts | Application ID, document type, upload time, uploader actor type, review status |
| Filters | Only status/page/size supported by the endpoint |
| Empty state | “No documents awaiting review” without implying all lending work is complete |
| Action | Open exact version in document workspace |
| Refresh | Manual plus bounded background refetch while visible; no content prefetch |

The endpoint returns a list rather than a page envelope. “Next” may be shown only when the returned row count equals the requested size, and the UI must label continuation as best-effort rather than displaying an invented total.

### 21.3 Staff-Correction Queue

This is executable now for `OPEN` Staff tasks.

Rows may show task ID, application ID, scope, document type, status, safe instruction, creation time, and baseline version identity where returned. They must not infer that proof exists from a local upload or document view; completion asks the backend to validate persisted proof.

The queue must explain the maker-checker possibility. Because the task response does not provide every creator fact needed for pre-validation, the final backend result remains authoritative.

### 21.4 Future Queues

Verification, review, recommendation, approval, contract, disbursement, servicing, settlement, and closure queues require backend projections before UI implementation. Each must be independently permissioned and paged. A single mega-queue is not the initial design because the actors, urgency, evidence, and actions differ.

An optional “All permitted work” landing may aggregate counts only after the backend exposes a purpose-built summary. It must not fetch every queue merely to count rows.

---

## 22. Case Workspace

### 22.1 Persistent Case Header

The case header should contain only authoritative safe facts:

- application number and safe ID copy control;
- product code/type;
- requested amount and term;
- durable application status;
- submitted time;
- current operational stage or blocker when returned by the case projection.

Customer identity details, Partner facts, income, collateral, document readiness, offer, contract, and LoanAccount state appear only in their authorized sections. The minimal existing application read cannot populate a production case workspace by itself.

### 22.2 Workspace Sections

| Section | Purpose | Required authority |
|---|---|---|
| Overview | Safe application and Customer readiness summary, current stage, available actions | New case projection |
| Product verification | Salary Advance snapshot summary or UCL/Collateral manual cycle evidence | New Staff verification read |
| Documents | Checklist, current versions, readiness, reviews, content actions | New Staff checklist/history read plus existing queue/content actions |
| Corrections | Active request, Customer/Staff tasks, proof, completion and resubmission | Expanded correction projection plus current queue/commands |
| Review | Current review cycle, Loan Officer evidence and recommendation | New review/recommendation read plus current commands |
| Decision | Latest recommendation, separation-of-duties evidence, decision form/history | New approval-evidence read plus current decision command |
| Contract | Current masked contract, versions, acknowledgment/readiness, preparation | Existing direct contract/readiness reads plus discovery/case projection |
| Disbursement | Ready-contract facts, controlled reveal, transfer confirmation | Existing direct commands plus discovery/case projection |
| LoanAccount | Final schedule, balances, installment progress, repayments | Existing direct account/history reads |
| History | Ordered safe lifecycle and action evidence | New consolidated lifecycle/history projection |

### 22.3 Evidence Freshness

Each action section shows:

- last successful fetch time;
- exact current evidence identity or version where applicable;
- a refresh action;
- a stale warning after reconnect, tab resume, or mutation in another section;
- the confirmation's evidence snapshot.

Opening a confirmation freezes only the displayed review snapshot. Submission still sends the expected evidence identity and the backend decides whether it is current.

---

## 23. Verification, Documents, and Corrections

### 23.1 Product Verification

| Product | Current backend fact | Staff Web implication |
|---|---|---|
| Salary Advance | Product verification is created during submission from Partner/employment evidence; there is no separate Staff verification command | Show a Staff-safe immutable summary only when a new case projection exposes it; do not manufacture a verification task |
| UCL | Loan Officer can start and complete manual verification with `VERIFIED`, `FAILED`, or `REQUIRES_MORE_INFORMATION` | Needs current-cycle/history read before production UI because start/complete have no client UUID and the response is too narrow for robust recovery |
| Collateral | Loan Officer starts numbered verification, receives restricted Collateral facts, and completes the exact `expectedVerificationId` | Keep the assessment snapshot and cycle ID together; stale ID requires refetch and review; a queryable latest cycle is still needed for recovery |

Verification outcomes are not generic approval decisions. The UI keeps product verification, Loan Officer recommendation, and Approver decision visibly separate.

Assessment notes and controlled reasons are sensitive Staff evidence. `REQUIRES_MORE_INFORMATION` composes a structured correction plan under current product restrictions; the browser must not allow arbitrary task types or financial-term changes.

### 23.2 Document Review

Document review workspace composition:

1. safe case/document header;
2. exact immutable version identity and metadata;
3. explicit content viewer;
4. outcome choices `ACCEPT_DOCUMENT`, `WAIVE_DOCUMENT`, or `REQUEST_REPLACEMENT` as permitted;
5. controlled waiver/replacement reason inputs;
6. final exact-version confirmation;
7. persistent reconciled result.

`WAIVE_DOCUMENT` appears only with `document:waive`. Replacement requires the controlled replacement reason and Customer-visible instruction. Restricted Staff notes are labeled separately. Reviewing a stale version never switches to the new version automatically.

Manual review is the current process. `MER-FU-033` tracks OCR; no OCR confidence, extracted text, retry, or override controls belong in this blueprint.

### 23.3 Staff Correction Tasks

The task workspace shows responsible party, scope, required document/item, baseline version, safe Staff instruction, proof status when authoritative, and maker-checker notice.

- `SUPPORTING_DOCUMENT_UPLOAD` exposes upload only with `document:upload:staff` and only for the open task.
- `DOCUMENT_REVIEW` links to the exact review workspace.
- Completion uses a stable `completionRequestId` and never marks proof complete locally.
- Staff-only or mixed resubmission uses a stable `resubmissionRequestId` only after authoritative tasks are complete.
- Mixed corrections remain blocked until both Customer and Staff work is complete.
- Requested amount and term remain immutable through current correction flows.
- UCL and Collateral product restrictions are rendered from the supported command contract, not generalized into arbitrary task construction.

---

## 24. Review, Recommendation, and Independent Decision

### 24.1 Start Review

The Loan Officer sees authoritative verification, document readiness, active-correction absence, and current status before “Start review.” The command has no body or business UUID. A production UI therefore requires a queryable active review-cycle projection for lost-response reconciliation.

The same Loan Officer may complete UCL or Collateral verification and start review. This does not weaken the later Approver maker-checker rule.

### 24.2 Recommendation

Supported actions remain distinct:

- `RECOMMEND_APPROVAL`;
- `RECOMMEND_REJECTION`;
- `RETURN_TO_CUSTOMER_REVISION`;
- `REQUEST_STAFF_CORRECTION`.

The recommendation page presents the current review cycle, verification result, document evidence, and controlled action-specific fields. Rejection requires a reason. Revision/correction requires exact current review cycle, controlled reason code, and structured tasks within the product rules.

Recommendation POST has no client business UUID and no later read endpoint. Production enablement is blocked until the case projection exposes the latest recommendation or a command-status resource supports reconciliation.

### 24.3 Independent Decision

Supported actions remain distinct:

- `APPROVE`;
- `REJECT`;
- `RETURN_TO_LOAN_OFFICER_REVIEW`;
- `REQUEST_CUSTOMER_OR_STAFF_CORRECTION`.

The decision page must show the exact latest recommendation, recommending Loan Officer evidence needed for separation, product verification state, review cycle, documents/readiness, and any action-specific correction plan. It must not rely on the transient recommendation response from another browser session.

Approval POST has no client business UUID and no decision read/history endpoint. The page is an API dependency until the current recommendation and resulting decision are durably queryable.

Approval may atomically create an immutable Customer offer. Staff Web must report only the Staff-authorized decision outcome; it must not call the Customer-only approved-offer endpoint to inspect it.

### 24.4 Approval Integrity

- A Staff user with both recommendation and decision permissions still cannot approve their own applicable recommendation.
- UI warnings never promise eligibility when actor evidence is incomplete.
- `MAKER_CHECKER_VIOLATION` remains a durable blocked result, not a generic form error.
- A stale review cycle or changed verification forces case refetch and a new confirmation.
- Internal notes are restricted and never copied into Customer-facing correction instructions.

---

## 25. Contracts, Readiness, and Disbursement

### 25.1 Contract Workspace

Accounting operations use the current masked contract as their authoritative starting point.

The workspace presents:

- safe contract reference, ID, version, and status;
- accepted immutable terms and provisional repayment items returned by the contract;
- safe bank and account-holder facts with masked account number;
- Customer acknowledgment state;
- point-in-time readiness and blocker codes;
- version history only when a future authorized projection exposes it;
- current action eligibility from authoritative state.

Version 1 preparation uses `expectedCurrentContractVersion = 0`. Regeneration requires the exact current version and supported `DISBURSEMENT_ACCOUNT_REFRESH` reason. The UI explains that regeneration preserves accepted financial terms, supersedes the prior operational version, refreshes the captured destination, and requires fresh Customer acknowledgment.

Preparation uses a stable `preparationRequestId`. It must not accept browser-entered pricing, principal, term, schedule, product verification, Customer ID, or destination fields.

### 25.2 Readiness

Advisory readiness is visibly labeled point-in-time. A green advisory result is not a durable confirmation and is not disbursement.

Readiness confirmation:

- uses a stable `confirmationRequestId` and exact `expectedContractVersion`;
- displays the latest blocker codes before confirmation;
- explains that the backend recomputes readiness in the command transaction;
- on success reconciles contract and application state to `READY_FOR_DISBURSEMENT` / `DISBURSEMENT_PENDING` as returned;
- on stale or new blocker response preserves the workspace and refetches.

The browser does not reimplement document, Customer, reservation, product-verification, or captured-account readiness rules.

### 25.3 Disbursement

Disbursement is manual confirmation of an external transfer, not transfer initiation.

The dedicated page contains:

1. current ready contract and exact version;
2. safe amount and schedule summary returned by the backend;
3. explicit local-memory destination reveal;
4. external transfer reference, disbursement value date, and first repayment date inputs;
5. confirmation summary;
6. exact request operation state;
7. reconciled activation result and final schedule.

The command body must not acquire Customer, product, destination, amount, pricing, term, limit, account, or schedule fields. Those remain backend-derived from the ready contract.

After success, Staff Web shows the safe returned disbursement/account identifiers, amount, value dates, activation time, and final schedule. It does not show the raw external transfer reference or claim Salary Advance exposure effects for UCL or Collateral.

### 25.4 Discovery Dependency

Direct contract and disbursement endpoints are executable for a known application ID. Production queues for contracts needing preparation, acknowledgment/readiness blockers, and disbursements awaiting confirmation remain API dependencies. The browser must not derive them by scanning application statuses.

---

## 26. LoanAccount, Repayment, Settlement, and Closure

### 26.1 LoanAccount Workspace

The existing application-scoped read can present:

- account identity, status, and activation;
- originated principal, approved term, interest, fee, and total repayment;
- authoritative component and total paid/outstanding balances;
- evaluation and last-payment dates;
- permanently masked destination;
- immutable final schedule and installment servicing progress.

Staff Web never decrypts the destination from the account read, reconstructs balances from history, or recalculates installment status.

### 26.2 Repayment

Accounting Officer repayment entry is a financial operation.

- Load the latest account and require a serviceable server state.
- Capture only `externalPaymentReference`, `amount`, and `paymentValueDate` plus stable `requestId`.
- Display amount as an entered value; do not preview allocation, payoff, principal release, or resulting balance by calculating in the browser.
- Confirmation explains that the backend allocates oldest installment first and applies its product policy.
- After success, render returned allocations and outcomes, then refetch LoanAccount and repayment history.
- Exact replay may return the original result even after later servicing state changes; label replay using the response's `idempotentReplay` fact.
- Overpayment, invalid value date, non-serviceable state, duplicate reference, and evidence inconsistency are backend outcomes, not client-calculated guards.

### 26.3 Administrative Full-Balance Settlement

Settlement belongs to an Approver with `loan:settlement:approve`, not the Accounting Officer repayment role.

The settlement page shows the authoritative current total outstanding, current account state, payment value date, and a clear statement:

> Administrative Full-Balance Settlement records an actual payment for exactly the full outstanding balance. It is not a discount, concession, waiver, forgiveness, or write-off.

The form sends a stable `requestId`, `expectedSettlementAmount`, `paymentValueDate`, and external payment reference. The expected amount is copied from the authoritative read and displayed for confirmation; the browser does not calculate it. After success, reconcile the account and immutable repayment history.

### 26.4 Administrative Closure

Closure belongs to an Accounting Officer with `loan:account:close` and requires an eligible fully reconciled `SETTLED` account.

The page explains that closure:

- records the separate `SETTLED -> CLOSED` administrative outcome;
- does not record another payment;
- does not change allocations, balances, schedule, installment progress, product exposure, or LoanApplication state;
- uses a stable `requestId` and supports exact replay.

The final confirmation names the account and current `SETTLED` state. It does not ask for financial inputs.

### 26.5 Servicing Discovery Dependency

There is no Staff LoanAccount index, overdue queue, repayment queue, settlement queue, or closure queue. Customer `/api/v1/loan-accounts` is ownership-scoped and cannot be reused. Staff servicing delivery therefore begins with an authorized index/search projection.

Overdue evaluation is backend-scheduled. Staff Web displays resulting `OVERDUE` and cure state but provides no manual “evaluate overdue” control.

---

## 27. Forms and Controlled Input

### 27.1 Form Rules

- One form represents one backend command.
- Use React Hook Form for interaction state and Zod for obvious shape validation.
- Initialize expected cycle/version values from the authoritative query and show them in the review step.
- Do not silently rewrite user-entered rationale or references.
- Trim only where the API contract defines normalization; otherwise send the operator-confirmed value and let the backend validate.
- Preserve safe fields after a definite validation rejection. Clear secrets and revealed data.
- On client validation, focus the first invalid field and link it from the form error summary.
- Restricted note forms do not autosave to persistent storage.
- Changing semantic input after an uncertain exact-identity submission requires explicit abandonment and a new operation identity.
- Prevent double click and Enter-key duplicate submission while in flight.

### 27.2 Reason and Instruction Fields

Reason codes are controlled enums. Human rationale, restricted internal notes, Customer-visible instructions, and Staff instructions are different fields and must retain distinct labels and audiences.

The UI must not copy an internal note into a Customer instruction by default. Character limits, required rules, and available codes follow the executable API contract for the selected action.

### 27.3 Money and Dates

- Use one centralized VND input/format strategy.
- Preserve exact whole-VND values; do not use binary floating-point calculations.
- Right-align money in tables and show the currency label in summaries.
- Date-only inputs remain date-only strings.
- Timestamps display an explicit time-zone label where operational ordering matters.
- Browser-side min/max hints improve input but never replace backend value-date validation.

### 27.4 File Upload

The Staff upload form accepts only the documented PDF, JPEG, and PNG types and 10 MiB limit as immediate feedback. It still sends the file for authoritative signature/type/size validation. Replacement includes the exact `expectedCurrentVersionId` when required and a stable `uploadRequestId`.

The file object and preview remain memory-only. A successful upload refetches the task/document state before completion is enabled.

---

## 28. Route Map

Routes are conceptual implementation targets. “API dependency” means the route must not ship as a fabricated experience.

| Route | Capability | Availability |
|---|---|---|
| `/login` | Internal authentication | Executable foundation |
| `/staff` | Redirect to first permitted work area | Executable after shell exists; no invented dashboard |
| `/staff/work/documents` | Document-review queue | Executable narrow queue |
| `/staff/work/corrections` | Staff-correction queue | Executable narrow queue |
| `/staff/work/verifications` | Pending UCL/Collateral verification | API dependency |
| `/staff/work/reviews` | Pending Loan Officer review/recommendation | API dependency |
| `/staff/work/approvals` | Pending independent decisions | API dependency |
| `/staff/work/contracts` | Contract preparation/readiness | API dependency |
| `/staff/work/disbursements` | Ready external-transfer confirmations | API dependency |
| `/staff/work/servicing` | Active/overdue/settled operational accounts | API dependency |
| `/staff/applications` | Staff application search/filter | API dependency |
| `/staff/applications/:loanApplicationId` | Case overview | API dependency beyond minimal status read |
| `/staff/applications/:loanApplicationId/verification` | Product verification | API dependency for authoritative read/recovery |
| `/staff/applications/:loanApplicationId/documents` | Document evidence/review | Partially executable; Staff checklist/history dependency |
| `/staff/applications/:loanApplicationId/corrections` | Correction tasks/proof/resubmission | Partially executable; expanded case dependency |
| `/staff/applications/:loanApplicationId/review` | Review and recommendation | API dependency for durable read/recovery |
| `/staff/applications/:loanApplicationId/decision` | Independent decision | API dependency for recommendation/decision evidence |
| `/staff/applications/:loanApplicationId/contract` | Current contract/readiness | Direct known-ID reads executable; discovery/case dependency |
| `/staff/applications/:loanApplicationId/disbursement` | Reveal and disbursement | Commands executable; discovery/case dependency |
| `/staff/applications/:loanApplicationId/loan-account` | Account/schedule/history | Direct known-ID reads executable; Staff index dependency |
| `/staff/applications/:loanApplicationId/repayments/new` | Record repayment | Command executable; discovery dependency |
| `/staff/applications/:loanApplicationId/settlement` | Exact full-balance settlement | Command executable; Approver and discovery dependency |
| `/staff/applications/:loanApplicationId/closure` | Administrative closure | Command executable; Accounting and discovery dependency |
| `/admin/*` | Back-Office feature boundary | Reserved for MER-FE-003; no pages defined here |

The current servicing APIs are application-scoped, so routes retain `loanApplicationId`. Do not pretend the path parameter is a LoanAccount ID. A future LoanAccount search may introduce a canonical account route with an explicit redirect strategy.

### 28.1 Route Ownership and State

| Route family | Intended actor/capability | Backend authority | Primary task | Important states and action ownership |
|---|---|---|---|---|
| Document work | Loan Officer with `document:review`; waiver also needs `document:waive` | Document review queue, content, and review endpoints | Inspect and decide the exact current version | `AWAITING_REVIEW`; Loan Officer owns review, Back-Office Admin may own Staff-task upload only with `document:upload:staff` |
| Correction work | Loan Officer with `loan:correction:staff`; uploader also needs `document:upload:staff` | Staff correction queue, task completion, upload, and resubmission endpoints | Satisfy Staff proof and return an eligible request to workflow | `OPEN`, proof incomplete/complete, mixed work incomplete, resubmitted; backend owns maker-checker |
| Verification and review | Loan Officer with `loan:review` and `approval:recommend` | Product-verification, review-start, and recommendation endpoints plus required new reads | Verify product evidence, start review, submit recommendation | Submitted/pending verification, verified/failed/more information, under review; Loan Officer acts, Approver does not verify |
| Approval | Approver with `approval:decide` | Decision endpoint plus required recommendation/decision reads | Independently approve, reject, return, or request correction | Awaiting decision, customer acceptance pending, rejected, returned; Approver owns final decision, backend owns separation |
| Contract/readiness | Accounting Officer with `loan:contract:prepare`, `loan:contract:read`, and `loan:disbursement:prepare` | Contract and readiness endpoints | Prepare/regenerate and confirm an eligible contract | Contract pending, acknowledgment missing, ready/not ready, disbursement pending; Accounting owns operations, Customer owns acknowledgment |
| Disbursement | Accounting Officer with `loan:disburse` | Reveal and disbursement endpoints | Verify destination and record an external transfer | `DISBURSEMENT_PENDING`, ready contract, activated/disbursed; Accounting owns confirmation, external bank remains outside Meridian |
| Repayment | Accounting Officer with `repayment:update`; account reads need `loan:read` | LoanAccount, repayment command, and history endpoints | Record externally received payment and inspect outcome | `ACTIVE`, `OVERDUE`, `SETTLED`; Accounting records ordinary repayment, backend allocates |
| Settlement | Approver role plus `loan:settlement:approve` | Settlement endpoint and Staff LoanAccount read | Record exact full-balance settlement | `ACTIVE` or `OVERDUE` to `SETTLED`; Approver owns settlement, no concession path |
| Closure | Accounting Officer role plus `loan:account:close` | Closure endpoint and Staff LoanAccount read | Record administrative terminal closure | `SETTLED` to `CLOSED`; Accounting owns closure, no financial mutation |
| Admin | Future Back-Office capabilities | Future administration APIs and MER-FE-003 | Platform administration | No Staff lending action is moved here merely because one actor has an Admin role |

### 28.2 Route Metadata

Each protected route declares:

- required `STAFF` user type;
- one or more navigation permissions;
- stronger command permission checked at the action;
- query keys to clear when capability changes;
- route title and focus target;
- sensitive-state cleanup callback;
- feature error boundary.

---

## 29. Page Inventory

| Page | Delivery class | Primary data | Primary action | Required exceptional states |
|---|---|---|---|---|
| Login | Executable now | Session status | Authenticate | invalid credentials, throttled, unverified/inactive safe response, session restore |
| Document queue | Executable now | Awaiting-review list | Open exact version | empty, best-effort continuation, row no longer pending |
| Document review | Foundation exists but projection missing | Exact version metadata/content and safe case facts | Record review outcome | content failure, stale version, waiver forbidden, result unknown |
| Staff correction queue | Executable now | Open Staff tasks | Open task | empty, task completed elsewhere, pagination uncertainty |
| Staff correction task | Foundation exists but projection missing | Task, baseline, proof, correction state | Upload/review/complete | maker-checker, proof missing, stale version, mixed work incomplete |
| Application search | API dependency | Staff operational index | Open case | API unavailable, no results, invalid filters |
| Case overview | API dependency | Consolidated safe case projection | Navigate to eligible work | partial dependency, stale case, forbidden/not found |
| Verification | Foundation exists but projection missing | Current product cycle and evidence | Complete exact outcome | readiness blocker, stale cycle, unresolved no-UUID command |
| Review/recommendation | Foundation exists but projection missing | Review cycle and evidence | Submit recommendation | stale cycle, invalid task plan, unresolved command |
| Decision | Foundation exists but projection missing | Latest recommendation and separation evidence | Submit decision | maker-checker, stale cycle, unresolved command |
| Contract | Foundation exists but discovery projection missing | Current masked contract and readiness | Prepare/regenerate/confirm | stale version, acknowledgment or readiness blockers, replay conflict |
| Disbursement | Foundation exists but discovery projection missing | Ready contract, local reveal, transfer evidence | Confirm disbursement | reveal unavailable, duplicate reference, invalid dates, result unknown/replay |
| LoanAccount | Foundation exists but Staff index missing | Account, balances, schedule, history | Open servicing action | unavailable account, history paging failure, inconsistent state |
| Repayment | Foundation exists but discovery projection missing | Authoritative account summary and entered payment | Record repayment | overpayment, invalid date, duplicate reference, replay/result unknown |
| Settlement | Foundation exists but discovery projection missing | Locked/current outstanding and entered payment evidence | Apply full-balance settlement | wrong role, changed amount, non-serviceable state, replay/result unknown |
| Closure | Foundation exists but discovery projection missing | Reconciled settled account | Close account | wrong role, not settled, competing operation, replay/result unknown |
| Generic audit page | Deferred | No authorized query exists | None | do not synthesize audit evidence |
| Back-Office pages | Deferred to MER-FE-003 | Future administration projections | Future Admin actions | no placeholder management forms |
| No access | Executable now | Session capability facts | Logout | permission change |

No page derives a missing index from browser history or local storage.

---

## 30. Component Vocabulary

### 30.1 Shared Primitives

Button, link, input, textarea, select, checkbox, radio group, date input, dialog, Sheet, tabs, table, pagination, badge, alert, skeleton, spinner, tooltip, toast, separator, and visually hidden text.

### 30.2 Internal Operations Components

| Component | Responsibility |
|---|---|
| `OperationsShell` | Permission-aware internal navigation and session chrome |
| `CapabilityGate` | UX gating with explicit unavailable fallback; never backend authorization |
| `QueueLayout` | Filters, continuation, refresh, table/list, empty/error state |
| `QueueAge` | Safe relative plus exact timestamp presentation |
| `CaseHeader` | Stable safe application identity and durable status |
| `WorkspaceNavigation` | Capability/state-aware case sections |
| `CustomerSummary` | Purpose-limited Staff-safe Customer readiness facts from a future case projection |
| `ApplicationFacts` | Requested terms, product, submission, and immutable application evidence |
| `EvidencePanel` | Labeled facts with source/freshness context |
| `StatusBadge` | Centralized enum label, icon, and semantic treatment |
| `MoneyValue` and `DateTimeValue` | Exact centralized formatting |
| `ExpectedEvidenceNotice` | Cycle/version identity and stale-conflict explanation |
| `MakerCheckerNotice` | Separation-of-duties explanation and blocked result |
| `OperationStatusPanel` | In-flight, result unknown, replay, reconciling, resolved |
| `RequestCorrelation` | Safe explicit copy of response `X-Request-ID` |
| `ActionReview` | Read-only final confirmation summary |
| `FinancialOperationSummary` | Authoritative amount/date/state presentation |
| `DocumentVersionViewer` | Memory-only exact-version content lifecycle |
| `ContractReadinessPanel` | Advisory blockers and confirmation distinction |
| `RecommendationSummary` | Immutable latest Loan Officer recommendation and safe maker evidence |
| `DecisionEvidence` | Independent-decision context and resulting durable outcome |
| `RepaymentScheduleTable` | Immutable schedule and returned servicing progress |
| `LifecycleHistory` | Ordered backend-returned evidence only |

Components do not own endpoint calls unless they are feature-level containers. Generic primitives never encode loan transitions, permissions, or product policy.

---

## 31. Loading, Empty, Error, and Success States

### 31.1 Loading

- Preserve the existing queue or case while a background refetch runs; show a subtle freshness indicator.
- Use skeletons only when their shape is stable and does not imply facts.
- A financial confirmation blocks while authoritative evidence refreshes.
- Document binary loading is isolated from case metadata loading.
- Do not show a blank full shell during token refresh.

### 31.2 Empty

Empty states name the scope: “No Staff correction tasks are open” rather than “No work.” A filter-empty state offers filter reset; a globally empty result does not invent completion counts.

### 31.3 Errors

| Outcome | Frontend treatment |
|---|---|
| `400` | Field/summary validation when safe details map; otherwise form banner |
| `401` | One refresh/replay, then session end |
| `403` | Permission or business-role/maker-checker explanation; retain safe context |
| `404` | Neutral resource unavailable; do not infer existence |
| `409` | Dedicated conflict/reconciliation panel; no automatic mutation retry |
| `422` | Business-rule blocker near the action with current case retained |
| `429` | Rate-limit message and safe retry timing; no request storm |
| `503` | Protected capability unavailable; preserve non-sensitive draft when safe |
| Other `5xx` | Unexpected failure banner, result-unknown treatment for mutations, and safe support correlation |

Parse backend status, code, safe message, details, and correlation defensively. `MER-FU-043` records that some executable identifiers do not yet have one canonical status/message. Staff Web must not hardcode a universal status from an identifier until that backend conformance work is complete.

Staff pages may show an operational resolution returned by the backend, but “internal” does not authorize raw exception detail or undisclosed state. Copy remains bounded by the safe error envelope.

Unknown errors show a safe generic message and response correlation. Raw exception text, stack traces, request bodies, tokens, notes, references, or account data are never displayed or logged.

### 31.4 Success

- Read-only success is the loaded authoritative view.
- Durable command success remains visible in the page until the operator leaves or starts a new operation.
- A toast may announce success but is not the only record.
- `idempotentReplay = true` receives a neutral “Previously recorded result” label, not a duplicate success celebration.
- If the POST succeeds but reconciliation read fails, show “Command confirmed; refreshed state unavailable” with safe result data and correlation. Do not label the operation failed.

---

## 32. Audit and Evidence Presentation

Meridian records PII-safe business audit evidence, but no generic audit-query controller currently exists. Staff Web must not label browser event history or current-status inference as an audit log.

When a future authorized case history is exposed:

- preserve backend ordering and immutable timestamps;
- distinguish application transition, verification, document review, recommendation, decision, contract, disbursement, payment, settlement, and closure concepts;
- show actor display facts only when the API intentionally exposes them;
- do not reveal internal operation, audit, persistence, encryption, or external-reference identifiers;
- provide a safe correlation link only when it represents transport support, not business evidence;
- keep restricted notes in their purpose-specific section rather than copying them into a broad timeline.

An `OperationStatusPanel` is client recovery state, not audit evidence.

---

## 33. Consolidated API Dependency Inventory

### 33.1 Blocking Dependencies

| Dependency | Experiences blocked | Required safe outcome |
|---|---|---|
| Staff application index/search | discovery, direct case access, all broad queues | Authorized paging/filtering across products and durable states |
| Consolidated Staff case projection | overview and evidence context for every action | PII-minimized current facts composed across context-owned contracts |
| Product verification read/history | UCL/Collateral recovery and evidence review; Salary Advance snapshot | Current exact cycle, immutable outcome, permitted restricted facts |
| Review-cycle and recommendation read | review start/recommendation recovery; Approver evidence | Current cycle, latest immutable recommendation, actor separation evidence |
| Approval decision read/history | decision recovery and case history | Latest durable decision and safe outcome facts |
| Staff document checklist/version projection | document workspace and correction proof | Current checklist/readiness, current version discovery, authorized review evidence |
| Expanded correction projection | mixed-task workspace and resubmission readiness | Current request, all responsible parties, proof and completion state |
| Contract/disbursement operational indexes | Accounting discovery | Current version/status/readiness blockers and ready cases |
| Staff LoanAccount/servicing index | repayment, overdue, settlement, closure discovery | Authorized balances/states with server paging/filtering |
| Consolidated lifecycle/history | case timeline and reliable no-UUID reconciliation | Ordered safe immutable workflow evidence |

### 33.2 Useful but Non-Blocking Enhancements

- page envelopes with totals or cursors for document and correction queues;
- server sort and product/date filters based on observed operations;
- server-returned available actions and blocker summaries;
- purpose-built queue-count summary for a landing page;
- direct safe lookup by application number or account number;
- command-status resource where a natural durable read cannot reconcile a no-UUID command.

### 33.3 Explicitly Deferred

- OCR job/result UI (`MER-FU-033`);
- generic audit search;
- assignment, reassignment, workload, and SLA tracking;
- configurable approval workflow;
- automated risk or collateral valuation;
- discounted settlement, reversal/refund, suspense, write-off, or reconciliation;
- analytics, reporting, and executive dashboards;
- Back-Office administration owned by MER-FE-003.

### 33.4 Do Not Solve Missing Reads in the Browser

The client must not compensate by:

- retaining every opened application ID;
- scanning application IDs or calling known-detail endpoints in bulk;
- reusing Customer-owned indexes or offers;
- joining Partner, Customer, Approval, Document, and Loan endpoints into a hidden browser database;
- inferring histories from current status;
- using a POST response as the only durable recommendation or decision ledger;
- calculating queue membership from copied workflow rules;
- polling every case to construct dashboard counts.

Missing operational reads are backend API dependencies. They remain visible in delivery planning rather than being disguised as frontend state.

---

## 34. Testing and Verification Strategy

Each future checkpoint runs lint, TypeScript checking, unit/component tests, and a production build. Tests should cover behavior rather than component internals.

### 34.1 Foundation Tests

- Staff-only session acceptance and Customer-session rejection;
- single refresh promise and one-request replay;
- logout/private-cache cleanup;
- permission-aware navigation and direct-route guards;
- response schema rejection and unknown-enum fallback;
- request correlation display without sensitive payloads;
- route focus and keyboard navigation.

### 34.2 Operational Tests

- exact request UUID stability across timeout, refresh, and retry;
- new UUID only after semantic payload change;
- no automatic mutation retry;
- `409` stale-cycle/version reconciliation;
- maker-checker and role-specific `403` explanations;
- content/reveal cleanup on navigation and logout;
- no Query-cache storage of document or full destination content;
- no optimistic financial/account state;
- financial confirmation and post-success refetch;
- empty queue scope wording and best-effort continuation;
- narrow/mobile evidence order and full keyboard operation.

### 34.3 Contract Tests

Mocked frontend fixtures must come from current OpenAPI/API examples and remain PII-safe. Contract tests should prove that Staff Web fails closed when required versions, balances, actor evidence, or state fields are absent.

Consequential checkpoints should add browser-level flows against a controlled test backend once the project selects and installs an end-to-end tool. That choice is deferred until the internal app exists; this blueprint does not add a dependency.

---

## 35. Delivery Checkpoints

Each checkpoint is a complete vertical frontend increment with its required backend read dependency, permission coverage, recovery behavior, responsive states, accessibility, and tests. A checkpoint does not ship command forms against a knowingly unreconcilable API.

### Staff FE-CP1 — Internal Foundation

- create the single internal application and preserve Staff/Admin feature boundaries;
- strict TypeScript, routing, Query, forms, validation, tokens, primitives, formatting, and testing foundation;
- Staff-only login, in-memory access token, rotating refresh cookie, single refresh, logout, and private-cache cleanup;
- permission-aware shell, route metadata, access states, error/correlation model, and operation-status foundation;
- no Back-Office pages.

### Staff FE-CP2 — Operational Discovery and Case Read Foundation

- deliver the backend Staff application index/search and consolidated case projection first;
- add permission-scoped queue and case query schemas;
- implement application search, safe case header, overview, workspace navigation, evidence freshness, and lifecycle/history only where returned;
- keep assignment and aggregate dashboards deferred.

### Staff FE-CP3 — Documents and Staff Corrections

- document-review and Staff-correction queues;
- exact-version memory-only document viewer;
- accept, waive, replacement, Staff upload when authorized, task completion, and resubmission;
- exact operation identities, stale-version handling, proof reconciliation, and correction maker-checker;
- complete the Staff checklist/correction projection dependencies needed by the workspace.

### Staff FE-CP4 — Product Verification and Loan Officer Review

- Salary Advance immutable verification summary;
- queryable UCL and Collateral current/history cycles;
- start/complete verification and safe lost-response reconciliation;
- review start and current review-cycle evidence;
- product-specific evidence panels without client pricing, LTV, or eligibility rules.

### Staff FE-CP5 — Recommendation and Independent Decision

- durable latest recommendation and decision read contracts;
- Loan Officer recommendation actions and structured correction plans;
- Approver queue and decision workspace;
- visible maker-checker, current-cycle, verification, document, and restricted-note boundaries;
- safe no-UUID command reconciliation and atomic-outcome presentation.

### Staff FE-CP6 — Contract and Readiness Operations

- contract operational queue;
- masked current contract and accepted-term presentation;
- prepare/regenerate with exact version and stable request identity;
- advisory readiness, blockers, confirmation, and post-command reconciliation;
- clear separation between ready, confirmed, and disbursed.

### Staff FE-CP7 — Disbursement and Activation

- ready-disbursement queue;
- local-memory destination reveal and cleanup;
- external-transfer evidence form, financial confirmation, exact replay, and uncertain-result recovery;
- activated LoanAccount and final-schedule reconciliation.

### Staff FE-CP8 — LoanAccount and Repayment Servicing

- Staff LoanAccount index/search and serviceable-state filters;
- account, immutable schedule, installment progress, and paged repayment history;
- Accounting repayment operation, returned allocation outcomes, replay, and refetch;
- overdue state presentation without a manual evaluation command.

### Staff FE-CP9 — Settlement and Administrative Closure

- Approver settlement eligibility queue and exact full-balance operation;
- Accounting closure eligibility queue and non-financial terminal confirmation;
- explicit role boundaries, competing-operation conflicts, replay after closure, and reconciled histories;
- no concessions, write-off, reversal, suspense, or reconciliation UI.

### 35.1 Checkpoint Sizing

- Split by one coherent operator outcome, not by arbitrary page count.
- Include the read model before the command UI that depends on it.
- Finish success, validation, authority, stale state, unknown result, replay, empty, loading, responsive, accessibility, and test behavior inside the checkpoint.
- Do not combine FE-CP5–FE-CP9 merely because their POST endpoints already exist.
- A checkpoint may be split further when backend projections require independent review, but must not leave an enabled financial or workflow command without recovery.

---

## 36. Deferred Staff Concerns

The Staff Web planning baseline deliberately defers:

- Back-Office product, Partner, Identity, role, permission, and configuration screens;
- OCR-assisted review and OCR operations;
- assignment, reassignment, SLA, escalation, and workload management;
- global dashboards, analytics, reporting, exports, and generic audit search;
- saved operational views until query contracts stabilize;
- notifications and real-time queue updates;
- generalized Customer profile review until a purpose-limited API exists;
- bulk actions;
- repayment reversal/refund, suspense/unapplied cash, waiver/forgiveness/write-off, and discounted settlement;
- external bank/payment/payroll integrations and reconciliation;
- ledger/accounting posting UI;
- collections;
- multi-level configurable approval;
- dark mode, formal design-system package, Storybook, microfrontends, and native mobile delivery;
- offline support, persisted Query caches, and background financial mutations;
- advanced charts or data-grid infrastructure without proven use.

---

## 37. Implementation Readiness Rules

A future Staff page checkpoint is ready to implement when:

1. every displayed business fact has an authorized Staff response;
2. every queue has authoritative server membership, paging, ordering, and supported filters;
3. every action maps to an executable endpoint, permission, business role, state, and exact evidence requirement;
4. recommendation, decision, verification, or other no-UUID commands have a durable reconciliation read;
5. exact business request UUID lifecycle and uncertain-result recovery are defined;
6. the page does not call Customer-only endpoints or reconstruct missing projections;
7. the route uses a defined layout and permission metadata;
8. shared controls and internal operations components are selected before new abstractions;
9. query keys, invalidation, post-success reconciliation, and session cleanup are defined;
10. sensitive fields have explicit fetch, cache, display, navigation, and disposal rules;
11. financial values are returned and formatted, not recalculated;
12. loading, empty, forbidden, concealed, conflict, business rejection, unknown result, replay, success, and responsive states are specified;
13. keyboard, focus, labels, status semantics, contrast, and narrow-layout evidence order meet Section 19;
14. frontend contract fixtures match current API/source evidence and remain PII-safe;
15. the checkpoint has focused tests and passes lint, type checking, tests, and production build.

If an operational read is missing, implementation stops at the dependency or ships an honest unavailable state. It must not use local resource history, Customer-owned queries, copied backend rules, or transient command responses as a substitute authority.
