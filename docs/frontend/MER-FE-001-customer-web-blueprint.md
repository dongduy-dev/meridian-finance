# MER-FE-001 — Customer Web Frontend Blueprint

## 1. Document Information

| Field | Value |
|---|---|
| Project | Meridian |
| Product | Meridian Lending Platform |
| Document Type | Customer Web frontend rulebook and implementation blueprint |
| Version | 1.0 |
| Status | Planning baseline |
| Author | Dong Duy |
| Scope | Responsive Customer Web for public identity flows and authenticated Customer self-service |

---

## 2. Purpose and Authority

This document defines the stable frontend decisions that Customer Web implementations share: technology, source organization, state ownership, browser authentication, visual language, components, layouts, navigation, page composition, responsive behavior, accessibility, and delivery sequence.

It does not define lending rules, lifecycle transitions, permissions, financial calculations, eligibility, document readiness, authentication semantics, or the HTTP contract. Those rules remain with their existing authorities.

| Subject | Authority |
|---|---|
| Intended actors, products, workflows, statuses, policies, and business outcomes | [MER-BIZ-001](../business/MER-BIZ-001-business-requirements-and-workflows.md) |
| HTTP methods, paths, request and response shapes, authorization, ownership concealment, and idempotency | [MER-API-001](../api/MER-API-001-endpoints-and-postman-scenarios.md) and generated OpenAPI |
| Executable behavior | Backend source, security configuration, migrations, and tests |
| Error identifiers, statuses, safe messages, and caller resolutions | [MER-ARCH-004](../architecture/MER-ARCH-004-api-error-catalog.md) |
| Deliberately deferred backend and projection work | [MER-TRACK-001](../project/MER-TRACK-001-follow-up-register.md) |
| Customer Web presentation and client implementation rules | This document |

The blueprint distinguishes four kinds of statement:

- **Backend fact** describes verified v1 behavior on which the frontend may rely.
- **Frontend decision** is a rule established by this blueprint.
- **API dependency** is required before the named Customer experience can be complete.
- **Deferred decision** is intentionally outside the Customer Web MVP foundation.

An API dependency does not authorize the frontend to copy business configuration, synthesize missing resources, or infer workflow actions.

---

## 3. Customer Web Goals

Customer Web has five goals:

1. Make complex lending workflows understandable without weakening backend controls.
2. Deliver features quickly through a small, reusable component and layout vocabulary.
3. Present one calm, credible visual language across identity, account, application, contract, and servicing journeys.
4. Keep server state, form state, authentication state, and navigation state separate.
5. Make a new page implementable without inventing tokens, controls, data-access patterns, or lifecycle rules.

The implementation follows this reuse order:

```text
shared UI primitive
        ↓
Meridian domain component
        ↓
feature component
        ↓
page composition
```

New pages must reuse an existing layer before adding a new abstraction. A visually distinct box on one page is not sufficient reason to create a shared component.

### 3.1 Delivery Character

Backend delivery remains correctness-heavy because it owns financial and workflow invariants. Customer Web delivery is component-driven, visually reviewed, and optimized for short vertical increments. Speed does not permit the frontend to become a second business-rule engine.

### 3.2 Non-Goals

This blueprint does not define:

- Staff or Back-Office screens;
- React scaffolding or package installation;
- backend contract changes;
- a mobile application;
- deployment or hosting;
- a reusable cross-product design-system package;
- pixel-perfect screen specifications;
- production analytics, experimentation, or behavioral tracking.

---

## 4. Backend and Frontend Responsibility Boundary

| Backend and business specifications own | Customer Web owns |
|---|---|
| Valid lifecycle states and transitions | Friendly labels and faithful status presentation |
| Eligibility, limits, blocking applications, and outstanding-debt rules | Explanations, calls to action, and form flow around returned facts |
| Authentication and session semantics | Browser credential handling and route experience |
| Permissions, Customer ownership, and concealment | Navigation visibility and authenticated page composition |
| Product configuration and financial calculations | Formatting and presentation of returned values |
| Required evidence, document readiness, and correction validity | Upload interaction and task presentation |
| Contract and offer validity | Review, acknowledgment, acceptance, and decline interactions |
| Request validation required for correctness | Immediate input feedback for obvious format errors |
| Error codes and safe disclosure | Consistent error placement and friendly action text |

### 4.1 Authority Rules

- Customer Web must render the state returned by the backend. It must not predict a transition, mark a command complete before success, or infer an unsupported next action from a partial status projection.
- Client validation improves input quality. Backend validation remains authoritative.
- Customer Web must display financial values returned by the backend. It must not recalculate interest, fees, installments, available limit, outstanding balance, settlement amount, or allocation.
- A hidden or disabled action is not an authorization control. The backend must still authorize every request.
- A lifecycle stepper may explain progress, but its steps must map to documented backend states. It must not create a new workflow state.
- A form wizard step is client navigation, not a `LoanApplication` status.
- Browser storage must not become an application index, LoanAccount index, document register, or recovery source when the API lacks the corresponding query.
- Customer Web must not expose Staff-only notes, evidence, actions, identifiers, or endpoints.

### 4.2 Status Presentation

Backend enum values are mapped through one feature-owned label map. For example, `RETURNED_FOR_REVISION` may render as “Action required,” but the raw value remains available to application logic and diagnostics. Status presentation must include text or an icon in addition to color.

Unknown enum values must render a neutral “Status unavailable” treatment and retain the raw value only in non-sensitive diagnostic context. They must not crash a page or be converted into an assumed lifecycle position.

| Semantic treatment | Representative meaning |
|---|---|
| Neutral | Draft, cancelled, closed, superseded, or unavailable |
| Information | Submitted, under review, active, or progressing normally |
| Warning | Customer action required, documents pending, expiring, or awaiting a decision |
| Success | Verified, accepted, disbursed, settled, paid, or completed |
| Danger | Failed, rejected, overdue, invalid, or destructive confirmation |

The owning feature maps each backend value deliberately. The representative meanings above do not authorize mapping by string pattern or lifecycle inference.

---

## 5. Technology Stack

The Customer Web foundation uses the following stack unless a later approved decision replaces it.

| Technology | Responsibility | Rule |
|---|---|---|
| React | UI composition | Pages remain compositions of feature and shared components. |
| Vite | Development and build tooling | Customer Web is a client-rendered application; no server-rendering framework is introduced for the MVP. |
| TypeScript | Frontend type safety | Strict mode is required. Avoid `any` at API and domain-component boundaries. |
| React Router | Routing and layout nesting | Routes own URL state and compose the four layout templates in Section 12. |
| TanStack Query | Server-state queries, caching, mutations, and invalidation | API-backed data must not be copied into arbitrary global state. |
| React Hook Form | Form state and submission lifecycle | Each form uses one consistent field, validation, error, and focus pattern. |
| Zod | Client input validation and narrow boundary parsing | Zod improves UX; it does not reproduce lending policy. |
| Tailwind CSS | Token-driven layout and styling | Shared semantic tokens replace page-specific colors and spacing. |
| shadcn/ui | Locally owned accessible primitives | Generic controls begin with shadcn/ui rather than custom reimplementation. |
| Lucide | Icon vocabulary | Icons use consistent size and stroke treatment and do not replace required text. |

Vitest, React Testing Library, and the Vite ESLint baseline belong in the foundation checkpoint as delivery tooling. Tests should verify route behavior, component states, form behavior, and API-boundary handling rather than implementation details.

The MVP foundation does not include Redux, Zustand, MobX, GraphQL, Next.js, microfrontends, Storybook, a custom CSS framework, offline persistence, or a frontend hexagonal-architecture copy. These tools require a demonstrated need before adoption.

---

## 6. Frontend Architecture and Source Structure

### 6.1 Source Structure

```text
customer-web/
└── src/
    ├── app/
    │   ├── router/
    │   ├── providers/
    │   └── styles/
    ├── routes/
    │   ├── auth/
    │   ├── dashboard/
    │   ├── products/
    │   ├── applications/
    │   ├── loans/
    │   └── account/
    ├── components/
    │   ├── ui/
    │   ├── common/
    │   └── layout/
    ├── features/
    │   ├── auth/
    │   ├── customer/
    │   ├── bank-accounts/
    │   ├── loan-products/
    │   ├── applications/
    │   ├── documents/
    │   ├── corrections/
    │   ├── offers/
    │   ├── contracts/
    │   └── loans/
    ├── lib/
    │   ├── api/
    │   ├── auth/
    │   ├── errors/
    │   ├── format/
    │   └── ids/
    └── test/
```

This tree is a placement guide, not a requirement to create empty directories. A feature contains only the API functions, query options, forms, components, types, and tests it needs.

### 6.2 Placement Rules

| Concern | Location |
|---|---|
| Router creation, provider composition, global styles, and theme variables | `app/` |
| Route-level loading boundaries and page composition | `routes/` |
| shadcn/ui source owned by Meridian | `components/ui/` |
| Generic application components such as `PageHeader` and `EmptyState` | `components/common/` |
| Shells and reusable page templates | `components/layout/` |
| Domain-oriented API calls, query keys, forms, and components | `features/<feature>/` |
| HTTP transport, session coordination, error parsing, formatting, and UUID helpers | `lib/` |

A feature may import shared components and `lib` utilities. Features must not reach into another feature's private files. A cross-feature component moves to `components/common/` only after its generic responsibility is clear; a cross-feature business presentation component remains in the owning feature and exposes a narrow public module file.

### 6.3 State Ownership

| State | Owner | Examples |
|---|---|---|
| Server state | TanStack Query | profile, products, readiness, application, offer, contract, LoanAccount, repayment history |
| Authentication state | Auth provider plus private in-memory credential module | bootstrap status, actor facts, access token |
| Form state | React Hook Form | profile edits, login, application inputs, file upload |
| Shareable navigation state | URL path and search parameters | resource ID, tab, repayment-history page |
| Ephemeral UI state | Local component state | open dialog, expanded schedule row, mobile navigation |

The frontend does not introduce a general global store. If a later feature demonstrates shared non-server state that cannot be represented by URL, form, auth context, or local state, that decision must name the state and its lifetime before adding a library.

### 6.4 Environment and Configuration

The foundation defines one non-secret API base URL setting. No credentials, token values, Customer data, or policy values belong in Vite environment files. Production may use a same-origin API path; local development may use the configured backend origin or a Vite proxy. CORS remains a backend deployment contract.

---

## 7. API and Server-State Rules

### 7.1 HTTP Access

- Pages and components must not call raw `fetch()` directly.
- `lib/api` owns transport behavior: base URL, JSON parsing, multipart handling, Bearer injection, credential mode, request correlation, timeout/abort behavior, and error-envelope parsing.
- Feature API modules own paths, request/response types, and query or mutation options.
- API response types describe the HTTP contract; domain components receive smaller view props where that improves reuse.
- Generated OpenAPI client adoption is deferred. The first implementation uses narrow handwritten API modules checked against generated OpenAPI and `MER-API-001`.

### 7.2 Query Rules

- Query keys use feature-owned factories, for example `applicationKeys.detail(id)` and `loanKeys.repayments(id, page, size)`.
- Keys contain every input that changes the response.
- Queries do not copy data into context or local state for reuse.
- Route loaders may warm the Query cache, but the Query cache remains the server-state owner.
- Read queries retry at most once for a transient network or `5xx` failure. They do not retry `4xx` outcomes.
- Mutations do not retry automatically except for the one authenticated replay described in Section 8.4 after a definitive authentication rejection.
- Mutation success invalidates only affected keys. Profile mutation invalidates the own-profile query; bank mutation invalidates the bank-account list and profile readiness; offer response invalidates the offer and application; correction resubmission invalidates tasks and application.

### 7.3 Operation Identity and Uncertain Results

For an operation with `requestId`, `uploadRequestId`, `completionRequestId`, `resubmissionRequestId`, `acknowledgmentRequestId`, or another operation-specific UUID:

1. Generate the UUID when the user starts one logical submission.
2. Keep it stable while that submission is pending.
3. Reuse it when the client retries the same logical content after a transport timeout or uncertain result.
4. Generate a new UUID only after the user changes the logical content or starts a distinct action.

The request UUID does not belong in the URL, user-visible copy, analytics, or logs. `X-Request-ID` is transport correlation and does not replace operation identity.

### 7.4 Error Mapping

The HTTP client parses the Meridian error envelope and exposes status, `errorCode`, safe message, path, `X-Request-ID`, and `Retry-After` when present.

| Error category | Presentation |
|---|---|
| Client field format known locally | Inline field message and error summary |
| `400 VALIDATION_FAILED` without field metadata | Form-level alert; retain entered values |
| `401` session failure | Section 8.4 |
| `403` | Access message without offering a Staff-only workaround |
| Concealed `404` | Generic unavailable-resource state |
| `409` stale/state/idempotency conflict | Explain that state changed, refetch the affected resource, and require a deliberate retry |
| `422` business rejection | Persistent in-page alert with the mapped safe next action |
| `429 RATE_LIMIT_EXCEEDED` | Persistent rate-limit message and countdown from `Retry-After` |
| `503` | Retry option without exposing storage or infrastructure details |
| Unknown failure | Generic fallback plus readable `X-Request-ID` for support |

Error-code label maps live in `lib/errors` for cross-cutting codes or in the owning feature for domain-specific codes. Customer Web must not show raw exception text, stack traces, internal identifiers, or restricted evidence.

### 7.5 Money, Dates, and Enums

- One `formatMoney` utility formats VND with `Intl.NumberFormat`; financial components use tabular numerals.
- Amount inputs keep a normalized digit value and submit whole VND. JavaScript floating-point arithmetic must not calculate authoritative lending results.
- One date utility formats date-only values without timezone conversion.
- One timestamp utility parses the API's UTC timestamp contract and displays it in the selected Customer locale. Timestamp parsing stays centralized because the current Java DTO surface contains offset-free `LocalDateTime` values produced from a UTC clock.
- Known backend enums use centralized friendly labels. Raw enum text must not be scattered through JSX.
- The frontend must not derive financial or lifecycle meaning from string ordering, color, or display label.

---

## 8. Authentication and Session Model

### 8.1 Verified Backend Facts

| Fact | Customer Web consequence |
|---|---|
| Login and refresh return an RS256 Bearer access JWT in JSON with a one-hour expiry. | The client stores the access token in memory and uses `expiresAt` to avoid knowingly sending an expired token. |
| Login creates an opaque rotating refresh token in an HttpOnly cookie. | JavaScript never reads or persists the refresh token. |
| The refresh cookie uses `/api/v1/auth`, `SameSite=Strict`, configurable `Secure`, and the configured lifetime. | Login, refresh, logout, and reset confirmation use credentialed requests from an allowed origin. |
| Security is stateless; HTTP Basic, form login, server sessions, Spring logout, and a browser CSRF-token contract are absent from v1. | Customer Web uses Bearer access plus the defined auth endpoints and must not invent a session or CSRF-token protocol. |
| Refresh rotates the cookie; reuse revokes the active token family. | The client allows only one refresh request at a time. |
| Current-session logout revokes the presented refresh family and the presented valid access token, then clears the cookie. | The client sends both credentials when available and clears local auth state regardless of the response. |
| Registration issues no credentials and requires email verification. | Registration succeeds into a verification-pending page, not the app shell. |
| Correct credentials for an unverified User return `EMAIL_VERIFICATION_REQUIRED`. | Login routes to the verification-pending experience. |
| Unknown email, wrong password, and active temporary lock return the same `INVALID_CREDENTIALS`. | The UI must not claim that an account is locked or expose an attempt count. |
| Password reset revokes every refresh family but does not enumerate and revoke existing access JWTs. | Reset success clears this tab's auth state and requires login; the UI must not promise immediate global access-token revocation. |
| Verification and reset email links carry the opaque token in the URL fragment. | The browser reads the fragment, submits the token in JSON, and removes the fragment from the address bar. |
| CORS permits explicit origins, credentials, required methods/headers, and exposes `X-Request-ID` and `Retry-After`. | Browser requests must use the configured origin and the client may display correlation and rate-limit recovery information. |

Customer business `verificationStatus` is not Identity email verification. Customer Web must not label the profile field as an email-verification result.

`SameSite=Strict` cookie rules still apply after CORS grants an origin. Localhost ports are same-site, and deployment must keep Customer Web and the API in a compatible same-site arrangement unless a separately reviewed backend cookie policy changes. CORS alone cannot make the refresh cookie available to a cross-site deployment.

### 8.2 Frontend Auth State

The auth provider exposes only:

```text
checking      app bootstrap or refresh is unresolved
authenticated Customer actor facts and access expiry are available
anonymous     no usable Customer session exists
```

Actor facts include `userId`, `email`, `userType`, `customerId`, roles, and permissions returned by authentication. The private auth module holds the access token in memory. It must not write the token to `localStorage`, `sessionStorage`, IndexedDB, cookies, URLs, logs, or error reporting.

The Customer Web shell requires `userType = CUSTOMER` and a non-null `customerId`. A Staff authentication result must not enter Customer routes or expose Staff navigation.

### 8.3 Bootstrap, Login, and Refresh

1. App startup enters `checking`.
2. The auth provider calls `POST /api/v1/auth/refresh` once with credentials.
3. Success stores the returned access token in memory and enters `authenticated`.
4. `INVALID_REFRESH_TOKEN` enters `anonymous` without an error page.
5. A transport or server failure presents a retryable session-check state; it must not be mislabeled as signed out.

Login uses a credentialed request so the browser accepts the refresh cookie. On success, Customer Web stores the access token in memory, caches actor facts only in auth state, and navigates to the previously requested Customer route or Dashboard.

### 8.4 Protected Request Recovery

The HTTP client attaches the in-memory Bearer token to protected requests. When a protected request receives a session-related `401`:

1. One shared refresh promise performs a credentialed refresh.
2. Other rejected requests wait for that same promise.
3. Refresh success replays each rejected request once.
4. Refresh failure clears auth state and routes to Login with the intended destination preserved.
5. A second `401` after replay clears auth state and stops; the client must not loop.

Network failures, `403`, `404`, `409`, and `422` do not trigger refresh. Login's `EMAIL_VERIFICATION_REQUIRED` routes to verification pending rather than entering the generic refresh path.

### 8.5 Logout and Password Reset

Logout sends the current Bearer token and credentialed refresh cookie when available. The client then clears its in-memory token, actor facts, and private Customer Query cache even if the request fails. It must not retain private server data on an anonymous screen.

Password-reset confirmation is credentialed because success clears the refresh cookie. Success also clears this tab's access state and private Query cache, then navigates to Login with a persistent success message. Existing access tokens in other clients remain governed by backend expiry and separate logout behavior.

### 8.6 Email and Reset Token Handling

- Read `token` only from `window.location.hash` on the dedicated confirmation route.
- Remove the fragment with `history.replaceState` immediately after capturing it in memory.
- Never copy the token into search parameters, route state persisted to storage, logs, analytics, or error messages.
- Confirmation pages provide distinct missing-token, submitting, success, invalid/expired, and unexpected-failure states.
- Invalid verification directs the Customer to request another verification email.
- Invalid reset directs the Customer to request another reset email.

---

## 9. Visual Design Principles

Customer Web uses a **clean, calm, trustworthy fintech** direction.

The interface is:

- modern without using trading-dashboard or crypto aesthetics;
- financially credible without dense enterprise-admin styling;
- clear about status, money, and required actions;
- polished through typography, spacing, and alignment rather than decoration;
- restrained in its use of cards, gradients, shadows, and motion.

One strong primary color, neutral surfaces, and semantic status colors carry the visual system. Cards group meaningful content; they do not wrap every heading, field, or row. Gradients, glassmorphism, neon effects, decorative charts, and continuous animation are outside the starting direction.

---

## 10. Starting Design Tokens

These tokens are the starting implementation direction. The first real Auth and shell pages may tune exact values through visual review, but pages must consume semantic tokens rather than introducing local replacements.

### 10.1 Color

| Token group | Starting direction | Use |
|---|---|---|
| Brand primary | Deep trustworthy blue; start near `#1D4ED8` | Primary actions, active navigation, links, focused brand moments |
| Primary hover | Darker blue; start near `#1E40AF` | Hover and pressed primary controls |
| Primary subtle | Pale blue; start near `#EFF6FF` | Selected and informational brand surfaces |
| Page background | Cool near-white; start near `#F7F9FC` | Main app background |
| Surface | White | Forms, grouped details, navigation |
| Foreground | Deep slate; start near `#172033` | Primary text |
| Muted foreground | Slate gray; start near `#667085` | Supporting text |
| Border | Cool light gray; start near `#E4E7EC` | Fields, separators, grouped regions |
| Success | Deep green with pale green surface | Completed and healthy states |
| Warning | Amber/brown with pale amber surface | Pending, expiring, attention states |
| Danger | Deep red with pale red surface | Destructive action, rejection, failure |
| Information | Blue with pale blue surface | Neutral workflow information |

Status components must use the semantic token and a label or icon. Product identity must not create three unrelated page themes.

### 10.2 Typography

The starting typeface is a system sans stack. Inter may replace it only if the foundation deliberately bundles or loads it with an acceptable privacy and performance decision.

| Role | Starting size and line height |
|---|---|
| Display | `32 / 40` |
| Page title | `28 / 36` |
| Section title | `20 / 28` |
| Body | `16 / 24` |
| Supporting text | `14 / 20` |
| Caption | `12 / 16` |

Headings use semibold weight. Body copy uses regular weight. Money, account summaries, and aligned schedule values use tabular numerals.

### 10.3 Shape, Spacing, and Elevation

| Token | Rule |
|---|---|
| Spacing | 4 px base rhythm; common gaps are 8, 12, 16, 24, 32, and 48 px |
| Radius | 10 px starting control radius; 12 px grouped-surface radius; full pills only for badges and compact filters |
| Border | 1 px semantic border; do not use contrast-heavy boxes for every section |
| Shadow | One restrained low elevation for overlays and rare raised surfaces; normal cards may use border only |
| Focus ring | 2 px primary ring with visible offset |
| Motion | 150–200 ms for state changes; honor reduced-motion preference |

### 10.4 Width and Breakpoints

- Main app content: maximum 1280 px.
- Detail content: maximum 1120 px.
- Focused forms and readable text: maximum 720 px.
- Page gutters: 16 px on small screens, 24 px on medium screens, and 32 px on large screens.
- Tailwind's default `sm`, `md`, `lg`, and `xl` breakpoints are sufficient until browser review proves otherwise.

### 10.5 Action Hierarchy

| Variant | Use |
|---|---|
| Primary | The one preferred forward action in a page or action region |
| Secondary | A safe alternative such as Back, review another section, or defer |
| Ghost | Low-emphasis navigation and compact contextual controls |
| Destructive | Decline, deactivate, cancel, or another action with a lasting negative outcome |
| Link | Inline navigation within explanatory copy |

An action region normally has one primary button. Destructive actions require explicit wording and a confirmation when the result is terminal or difficult to reverse. Button color must come from the shared variant; pages must not create product-colored button styles.

### 10.6 Icons

Lucide icons normally render at 18 or 20 px with consistent stroke weight. Icons accompany text for important actions and status. An icon-only button requires an accessible name and tooltip; common primary actions keep visible text.

---

## 11. UI Component System

### 11.1 Generic Primitives

| Group | Components |
|---|---|
| Actions | `Button`, `DropdownMenu`, `Dialog`, `Sheet`, `Tooltip` |
| Inputs | `Input`, `Textarea`, `Select`, `Checkbox`, `RadioGroup`, `FormField` |
| Structure | `Card`, `Tabs`, `Table`, `Separator`, `PageHeader` |
| Feedback | `Badge`, `Alert`, `Toast`, `Progress`, `Skeleton`, `Spinner`, `EmptyState` |
| Navigation | `Breadcrumb`, `Pagination`, `Stepper` |

Generic controls begin with shadcn/ui where a matching primitive exists. `PageHeader`, `EmptyState`, and the application Stepper are small Meridian-owned composites built from primitives.

`Spinner` is reserved for compact controls and indeterminate inline work. `Skeleton` is preferred for initial page and section loading. Toasts confirm transient outcomes; important business outcomes remain visible in page content.

### 11.2 Meridian Domain Components

| Component | Responsibility | Reuse threshold |
|---|---|---|
| `MoneyDisplay` | Consistent VND formatting, emphasis, and tabular numerals | Every financial summary |
| `StatusBadge` | Maps known backend status to semantic label, icon, and color | Every status surface |
| `LoanProductCard` | Product name, description, amount range, and supported action | Product catalogue and Dashboard |
| `ReadinessSummary` | Presents returned readiness facts and blocker-code actions | Salary Advance and Customer readiness surfaces |
| `RequiredActionCard` | One persistent action with reason and destination | Dashboard and application detail |
| `ApplicationSummary` | Application number, product, requested terms, status, and submitted time | List and detail headers |
| `ApplicationTimeline` | Maps returned application state to documented lifecycle milestones | Application detail after the read projection can support it |
| `ApplicationStepper` | Shows form-wizard progress only | Multi-step application and correction forms |
| `AmountInput` | Whole-VND entry, display formatting, and accessible raw-value handling | Every Customer money input |
| `DocumentUpload` | File selection, constraints, upload progress, replacement baseline, and retry identity | Evidence and correction flows |
| `DocumentStatus` | Current filename/version and backend review/readiness state | After a Customer document projection exists |
| `OfferSummary` | Approved principal, term, interest, fee, total, expiry, and provisional items | Approved-offer page |
| `ContractSummary` | Version, status, accepted terms, masked destination, and acknowledgment | Contract page |
| `LoanAccountCard` | Account status and high-level paid/outstanding facts | Loan list and Dashboard |
| `RepaymentSummary` | Paid and outstanding component totals | Loan detail |
| `InstallmentRow` | Due date, contractual components, servicing components, and status | Responsive schedule |

`ApplicationTimeline` must not be built from the current compact application status response as though it were a complete history. It may launch only when the backend supplies the facts required for a faithful projection.

### 11.3 Composition Rule

A page may contain feature-local layout markup. It creates a shared component only when the component has a stable responsibility, stable inputs, and a second credible reuse. Page components must not clone a generic primitive merely to apply different colors or spacing.

---

## 12. Page Layout Templates

| Layout | Intent | Composition | Responsive behavior |
|---|---|---|---|
| `AuthLayout` | Public identity tasks with low distraction | Meridian wordmark, concise context, one narrow form surface, support link area | Single column at all sizes; visual panel may appear only on wide screens |
| `CustomerAppLayout` | Normal authenticated navigation and browsing | Desktop sidebar, compact top bar, account access, main content container | Sidebar becomes a top bar plus Sheet navigation below `lg` |
| `FocusedFlowLayout` | Application, evidence, and correction flows | Narrow content, form Stepper, Back/Continue region, leave-flow warning | Single column; action region may become a sticky bottom bar without covering content |
| `DetailLayout` | Application, offer, contract, and LoanAccount detail | Page header, primary content, optional summary/action rail | Two columns at `xl`; summary stacks before secondary detail on smaller screens |

Dialogs handle short confirmations. Sheets handle mobile navigation and compact supporting tasks. A full form, document workflow, or financial review must not be forced into a modal.

---

## 13. Customer Information Architecture

### 13.1 Planned Routes

```text
Public
├── /login
├── /register
├── /verify-email
├── /verify-email/pending
├── /forgot-password
└── /reset-password

Authenticated Customer
├── /                         Dashboard
├── /products                 Product catalogue
│   ├── /products/salary-advance
│   ├── /products/unsecured-consumer-loan
│   └── /products/collateral-loan
├── /applications
│   ├── /applications/:loanApplicationId
│   ├── /applications/:loanApplicationId/documents
│   ├── /applications/:loanApplicationId/corrections
│   ├── /applications/:loanApplicationId/offer
│   └── /applications/:loanApplicationId/contract
├── /loans
│   └── /loans/:loanApplicationId
└── /account
    ├── /account/profile
    └── /account/bank-accounts
```

Product application forms use nested focused-flow routes or route state under the product route. Exact step URLs are chosen during feature implementation; they must remain browser-back-safe and must not expose sensitive form values.

Documents are not a top-level navigation destination in the MVP. Evidence is meaningful in the context of an application or correction, and the v1 API exposes no Customer-wide document index. Required actions appear on Dashboard and Application Detail after the required projections exist.

### 13.2 Verified Capability Baseline and Dependencies

| Customer capability | Verified v1 support | Blueprint decision or dependency |
|---|---|---|
| Register, verify email, login, refresh, logout, request/confirm reset | Complete endpoint surface | Implement in the Auth checkpoint. |
| Own profile and bank accounts | Own reads and mutations exist | Implement after Auth. Treat protected identity reference as “on file” after completion. |
| Product catalogue | Public list/detail expose name, description, active state, and amount range | **API dependency:** allowed terms, pricing display, repayment method, evidence requirements, and eligibility notes must come from an enriched product contract before complete product detail/application UX. |
| Dashboard readiness | Profile, bank-account list, catalogue, and Salary Advance readiness can be queried | **API dependency:** active-application, active-loan, and required-action aggregation. Do not reconstruct them from browser storage. |
| Salary Advance readiness | Customer readiness returns limit facts, reusable link ID, and blockers | Usable for an already linked Customer. |
| First-time Salary Advance employee verification | Verification command requires `partnerCompanyId` and employee code | **API dependency:** Customer-safe Partner selection/lookup. Staff Partner Company reads must not be reused. |
| Salary Advance submission | Authenticated submit endpoint exists | **API dependency:** allowed terms must be returned by product policy; do not hardcode them as frontend authority. |
| UCL submission | Authenticated amount/term submission creates three checklist items | **API dependency:** submission response or Customer checklist query must expose the required checklist item IDs and current evidence state. |
| Collateral submission | Authenticated submission returns ownership-evidence item ID | Initial upload can follow submission. **API dependency:** checklist/version query is still required for reconnect and resume. |
| Application tracking | Owned application read exists by known ID | **API dependency:** Customer application index and richer action/history projection for Dashboard, list, faithful timeline, and resume. |
| Correction tasks | Owned task list, completion, resubmission, and narrow Salary Advance/UCL cancellation exist by application ID | Build after application index and document projection. Never show cancellation for Collateral or another state. |
| Document upload/status | Upload and content reads exist when checklist/version IDs are known | **API dependency:** Customer checklist/current-version/readiness query. |
| Approved offer | Owned read, accept, and decline exist by application ID | Build after application navigation can discover pending offers. |
| Contract | Owned current-contract read and acknowledgment exist | Customer sees and acknowledges; readiness confirmation and destination reveal remain Staff-only. |
| LoanAccount and repayment history | Owned reads exist by application ID | **API dependency:** Customer LoanAccount index. Customer servicing remains read-only. |

`MER-FU-037` already tracks richer lending projections. Frontend-enabling endpoint work should extend that item rather than creating browser-side substitutes.

---

## 14. Customer Page Inventory and Composition

Each page below defines its intended composition. “Dependency” means the page may be scaffolded only as an honest unavailable state until the named backend contract exists; it must not ship a simulated data path.

### 14.1 Public Identity Pages

| Page | Purpose and primary goal | Backend capability | Composition and primary actions | Important states and success destination |
|---|---|---|---|---|
| Login | Enter Customer Web with an existing verified account | `POST /api/v1/auth/login` | `AuthLayout`, email/password fields, Login, Forgot password, Register | Invalid credentials remain generic; verification-required links to pending verification; rate limit uses countdown; success goes to intended route or Dashboard |
| Register | Create an unverified Customer and Identity User | `POST /api/v1/auth/register` | `AuthLayout`, display name, email, password, password guidance, consent to account creation copy, Register | Duplicate email offers Login/recovery; success goes to Verification Pending; no app session is assumed |
| Verification Pending | Explain the required email step and allow enumeration-safe resend | `POST /api/v1/auth/email-verification/request` | `AuthLayout`, persistent instructions, email field when not retained in memory, Resend, Login | Accepted response always uses neutral copy; rate-limited state is persistent; success remains on the page |
| Verify Email | Consume a fragment token and confirm email | `POST /api/v1/auth/email-verification/confirm` | `AuthLayout`, automatic confirmation state, Retry request link | Missing, submitting, success, invalid/expired, unexpected error; success goes to Login |
| Forgot Password | Request enumeration-safe recovery | `POST /api/v1/auth/password-reset/request` | `AuthLayout`, email field, Send reset link, Login | Success and unknown/ineligible account share the same confirmation; rate limit uses countdown; success remains on confirmation state |
| Reset Password | Replace password using a fragment token | `POST /api/v1/auth/password-reset/confirm` | `AuthLayout`, new password, confirmation field, Reset | Missing, submitting, success, invalid/expired, unexpected error; success clears local auth and goes to Login |

Password confirmation is a client-only matching check. The backend remains authoritative for the new-password policy.

### 14.2 Dashboard and Account Pages

| Page | Purpose and primary goal | Backend capability | Composition and primary actions | Important states and success destination |
|---|---|---|---|---|
| Dashboard | Show account readiness and the next meaningful Customer work | Profile, bank accounts, products, Salary Advance readiness; portfolio projections are missing | `CustomerAppLayout`, `PageHeader`, readiness card, required actions, active application summary, active loan summary, product cards | Profile and product portions can load independently; application/loan/action areas depend on frontend-enabling projections; actions navigate to the owning page |
| Profile | Complete or maintain the Customer profile | `GET /customers/me`, `PUT /customers/me/profile` | Profile readiness header, editable fields, consent checkboxes, Save | Initial create requires identity reference; after completion show protected identity as “On file” and omit it from ordinary update payload; success stays and refreshes Dashboard readiness |
| Bank Accounts | Maintain masked Customer-owned destination sources | Bank-account list/add/make-primary/deactivate endpoints | Account list, primary badge, Add account form, make-primary and deactivate confirmations | Empty state explains application readiness; mutation conflicts refetch; success stays and refreshes profile/readiness queries |

Bank-account UI never displays or stores a full account number after the add request completes. The response and all later views use the backend mask.

### 14.3 Product Catalogue and Origination Pages

| Page | Purpose and primary goal | Backend capability | Composition and primary actions | Important states and success destination |
|---|---|---|---|---|
| Product Catalogue | Compare active Meridian products | Public product list | `CustomerAppLayout`, `PageHeader`, `LoanProductCard` grid, profile-readiness prompt | Empty catalogue is a real empty state; details are limited to returned fields until product metadata is enriched |
| Salary Advance Product | Understand readiness, verify employment when required, and begin an application | Product detail, Salary Advance readiness, employee verification, submission | Product summary, `ReadinessSummary`, limit summary, blocker actions, verification form, Apply | First-time Partner selection depends on a Customer-safe API; stale evidence offers re-verification; successful verification refetches readiness; submission goes to Application Detail |
| Salary Advance Application | Submit requested amount and term against returned readiness | Salary Advance submission | `FocusedFlowLayout`, `ApplicationStepper`, amount, term, readiness summary, review, Submit | No server draft exists; leaving warns about unsaved input; amount/term policy options depend on enriched product metadata; success goes to Application Detail |
| UCL Product | Understand the streamlined evidence-based product and begin | Product detail and UCL submission | Product summary, eligibility notes, required-evidence preview, Apply | Full policy and evidence copy depends on product metadata; Apply enters focused flow |
| UCL Application | Submit requested amount and term, then provide required evidence | UCL submission and document upload | Amount/term form, review, evidence workspace for income proof, bank statement, employment proof | The request must not invent income/employment fields absent from v1; checklist item discovery is an API dependency; success goes to Documents, then Application Detail |
| Collateral Product | Understand the one-asset manual-assessment workflow and begin | Product detail and Collateral submission | Product summary, ownership-evidence explanation, supported Collateral fact summary, Apply | Product policy metadata remains an API dependency; Apply enters focused flow |
| Collateral Application | Submit terms and one structured Collateral fact, then ownership evidence | Collateral submission and document upload | Amount/term, Collateral type, description, estimated value, ownership status, condition note, review, ownership-evidence upload | Estimated value is not an LTV calculation; submitted facts become immutable; success goes to Documents using returned evidence ID, then Application Detail |

Application forms must not offer “Save draft” until a backend draft contract exists. Local unsaved form state is not a Meridian `DRAFT` application.

### 14.4 Application, Evidence, Correction, Offer, and Contract Pages

| Page | Purpose and primary goal | Backend capability | Composition and primary actions | Important states and success destination |
|---|---|---|---|---|
| Applications | Find owned applications and resume work | Customer application index is missing | `CustomerAppLayout`, filters limited to backend-supported query fields, `ApplicationSummary` list | **API dependency.** Do not construct the list from remembered submission IDs. |
| Application Detail | Understand durable state and find supported Customer actions | Owned application-by-ID read; richer projection is missing | `DetailLayout`, `ApplicationSummary`, status explanation, required actions, product facts, documents, offer/contract/loan links | Basic state works for a known ID; faithful timeline and action discovery depend on richer projection; concealed `404` uses generic unavailable state |
| Documents | Upload or replace evidence and understand its current state | Upload/content endpoints; Customer checklist/current-version query is missing | `FocusedFlowLayout` or application detail section, checklist rows, `DocumentUpload`, `DocumentStatus` | Initial Collateral upload can use submission response; reconnect, UCL, version status, and review state require the document projection |
| Corrections | Complete owned tasks and resubmit or abandon an eligible application | Owned correction task, completion, resubmission, and cancellation endpoints | Instruction alert, task list, document actions, completion state, Resubmit, eligible Cancel confirmation | Task proof must come from backend; mixed/Staff work is read-only context if exposed; Cancel appears only for Salary Advance/UCL in `RETURNED_FOR_REVISION`; success returns to Application Detail |
| Offer | Review immutable approved terms and accept or decline | Approved-offer read/respond endpoints | `DetailLayout`, expiry banner, `OfferSummary`, provisional items, Accept, Decline confirmation | Loading, pending, accepted, declined, expired, action conflict; accept goes to Contract waiting/detail, decline goes to Application Detail |
| Contract | Review the current operational contract and acknowledge its exact version | Current-contract read and acknowledgment | `DetailLayout`, version/status, accepted terms, masked destination, repayment preview, acknowledgment confirmation | Superseded/stale version refetches; acknowledgment success stays with persistent confirmation; Customer does not confirm readiness or reveal destination |

The operational contract page must state that acknowledgment is operational evidence, not an electronic signature or generated legal agreement.

### 14.5 LoanAccount and Servicing Pages

| Page | Purpose and primary goal | Backend capability | Composition and primary actions | Important states and success destination |
|---|---|---|---|---|
| Loans | Find activated owned LoanAccounts | Customer LoanAccount index is missing | `CustomerAppLayout`, `LoanAccountCard` list, status filter if supported | **API dependency.** Empty and filtered states must come from an authoritative query. |
| Loan Detail | Understand account status, outstanding balance, destination mask, and final schedule | Owned LoanAccount read by application ID | `DetailLayout`, status, `RepaymentSummary`, masked destination, responsive schedule of `InstallmentRow` | `ACTIVE`, `OVERDUE`, `SETTLED`, `CLOSED`; concealed unavailable state; no Customer repayment or settlement action |
| Repayment History | Review immutable recorded payment outcomes | Owned paged repayment history | Tab within Loan Detail, history rows/cards, pagination, allocation detail disclosure | Empty history, loading next page, page error, and successful history; pagination remains in URL search state |

Customer Web does not expose repayment entry, Administrative Full-Balance Settlement, account closure, readiness confirmation, disbursement destination reveal, or manual disbursement. Those are Staff operations.

---

## 15. Forms and Validation

### 15.1 Common Form Contract

Every form defines:

- initial values and how they are loaded;
- client format validation;
- submitting and disabled behavior;
- backend validation and business-rejection placement;
- success invalidation and navigation;
- field labels, descriptions, and associated error messages;
- focus movement to the first invalid field or the form-level error summary.

React Hook Form owns field and submission state. Zod owns obvious client input shape. Server errors remain separate from Zod errors so a backend business rejection is not presented as a syntax mistake.

### 15.2 Appropriate Client Validation

- required email and email syntax;
- password length and matching confirmation;
- required text and known maximum length;
- required consent selection;
- positive whole-VND input shape;
- supported file extension, declared MIME type, and 10 MiB size warning before upload;
- required Collateral fields and known text limits;
- canonical UUID presence when the route or operation requires one.

Client file checks improve feedback only. The backend still validates file signature, size, media type, ownership, checklist state, and replacement baseline.

### 15.3 Backend-Owned Validation

Customer Web must not duplicate:

- product activation, amount limits, or allowed-term authority;
- Salary Advance employment eligibility, freshness, or available limit;
- blocking-application or outstanding-debt rules;
- Collateral loan-to-value decisions, because no automated LTV rule exists;
- checklist upload completeness, review acceptance, processing readiness, or task proof;
- offer validity or action eligibility;
- contract readiness;
- lending calculations, schedule allocation, repayment balances, or status transitions.

### 15.4 Sensitive Form Rules

- Password fields never repopulate after failure.
- Full bank-account input is cleared from component state after a successful add.
- Identity reference is required on first profile completion but is not returned by the API. Once the profile is complete, the UI displays an “On file” protected state and omits the value from routine updates.
- Document file objects stay in component memory only for the active upload.
- Opaque email-verification and reset tokens follow Section 8.6.
- Collateral description and evidence content must not enter analytics or client logs.

---

## 16. Loading, Empty, Error, and Success States

### 16.1 Query Pages

Every data-driven region defines four states:

| State | Rule |
|---|---|
| Loading | Use a shape-matched Skeleton; retain stable surrounding layout. |
| Success | Render returned data without a second local copy. |
| Empty | Explain what is empty and offer only a supported action. |
| Error | Keep page context, show safe recovery, and expose `X-Request-ID` when useful. |

A page with several independent queries may render successful regions while another region loads or fails. A full-page spinner is reserved for initial auth bootstrap when no safe shell decision can yet be made.

### 16.2 Mutations

Every mutation defines:

| State | Rule |
|---|---|
| Idle | Primary action is available when obvious format requirements are satisfied. |
| Submitting | Disable duplicate submission, retain values, and show progress in the action. |
| Success | Update persistent page state, invalidate exact queries, then navigate when the page contract says so. |
| Business rejection | Keep the form or page visible and show the backend-owned recovery action. |
| Unexpected failure | Preserve input, retain the logical request UUID for an uncertain retry, and show correlation when available. |

Optimistic updates are not used for offer response, contract acknowledgment, correction completion/resubmission, document replacement, application submission, or bank-account primary/deactivation. These actions depend on authoritative state and return quickly enough to wait for confirmation.

### 16.3 Notifications

Toasts may confirm a saved profile, added account, copied reference, or successful resend. Registration, verification, password reset, application submission, correction outcome, offer response, contract acknowledgment, and servicing status must also remain visible in page content or the destination page.

---

## 17. Responsive Design

- At `lg` and above, `CustomerAppLayout` uses a persistent sidebar. Below `lg`, it uses a compact top bar and a shadcn Sheet for navigation.
- Primary navigation order is Dashboard, Products, Applications, Loans, Account. Missing API-dependent destinations remain out of production navigation until their authoritative queries exist.
- Tables convert to labeled cards or stacked rows below `md`. Horizontal scrolling is reserved for information that cannot be understood when split, such as a wide financial allocation detail.
- Detail pages stack the action/summary rail before secondary detail on small screens.
- Forms use one column by default. Two-column fields are allowed only for short, closely related values on `md` and above.
- Dialogs become near-full-width with safe margins. Multi-step forms and evidence workflows remain pages, not full-screen dialogs.
- The application Stepper shows compact step number and current label on small screens; completed and future labels may collapse into an accessible progress summary.
- Touch targets are at least 44 by 44 px.
- Long financial values use tabular numerals, controlled wrapping, and no ellipsis that hides the amount.
- Long status labels wrap without changing their semantic color or losing their icon/text pairing.
- Sticky mobile action bars reserve bottom padding so content and errors remain visible.

Customer Web is one responsive web application. It does not maintain separate desktop and mobile feature implementations.

---

## 18. Accessibility Baseline

The MVP baseline requires:

- semantic landmarks, headings, forms, lists, tables, and buttons;
- keyboard access for every action;
- visible focus states from the shared token system;
- persistent labels for fields; placeholders are not labels;
- field errors connected through accessible descriptions;
- an error summary for multi-field submission failures;
- sufficient foreground, border, status, and focus contrast;
- status communicated by text or icon as well as color;
- accessible dialog, menu, Select, Tabs, and Sheet behavior from component primitives;
- announced mutation progress and outcome where focus does not naturally move;
- reduced-motion support;
- meaningful page titles and route-change focus on the page heading.

This is a pragmatic implementation baseline, not a claim of formal WCAG certification.

---

## 19. Code-First Visual Iteration

Customer Web uses code-first design:

```text
MER-FE-001 blueprint
        ↓
theme, primitives, and layouts
        ↓
first real Auth pages
        ↓
browser review at mobile and desktop widths
        ↓
tune semantic tokens and shared components
        ↓
reuse the established system in later features
```

The foundation and Auth checkpoint act as the live design prototype. Token changes occur centrally. A page-specific override must not become the unreviewed start of another design system.

Each feature review includes:

1. keyboard and focus pass;
2. narrow-mobile, tablet, and desktop browser pass;
3. loading, empty, business-rejection, and unexpected-error pass;
4. long-label and long-financial-value pass;
5. confirmation that Staff actions and restricted data are absent.

Figma may support later brand exploration, but implementation does not wait for pixel-perfect mockups.

---

## 20. Delivery Checkpoints

The sequence below keeps review boundaries small and places missing API authority before dependent UI.

### Planning CP — MER-FE-001 Customer Web Blueprint

- Establish this frontend rulebook.
- No frontend source, package, or backend behavior change.

### #71 — Frontend Foundation

- Vite, React, and TypeScript application;
- React Router and provider composition;
- Tailwind and shadcn/ui foundation;
- semantic tokens and base typography;
- TanStack Query and typed HTTP/error boundary;
- auth, main app, focused flow, and detail layouts;
- responsive navigation shell;
- lint, unit/component test, build, and CI baseline;
- no large business feature.

### #72 — Customer Authentication Experience

- registration and verification pending;
- fragment-token email confirmation;
- login, bootstrap refresh, deduplicated refresh, and logout;
- forgot/reset password and local session clearing;
- protected Customer routes and session-error handling;
- rate-limit and request-correlation presentation.

### #73 — Customer Account

- own-profile read and completion/update;
- protected identity-reference treatment;
- bank-account list, add, make-primary, and deactivate;
- account readiness presentation.

### #74 — Frontend-Enabling Backend Read Contracts

Complete and test the smallest backend contracts required by the next Customer Web slices:

- product policy presentation fields required for term, rate, repayment, evidence, and eligibility UI;
- Customer-safe Partner selection for Salary Advance verification;
- Customer application and LoanAccount indexes;
- Customer document checklist/current-version/readiness projection;
- richer Customer application action/status projection or another narrow aggregation sufficient for Dashboard and resume.

This checkpoint extends existing backend ownership and `MER-FU-037`; it must not create frontend-owned policy or expose restricted evidence.

### #75 — Dashboard and Product Catalogue

- readiness and required-action composition;
- active application and LoanAccount summaries;
- product catalogue and product detail pages;
- full loading, empty, error, and responsive states.

### #76 — Salary Advance Customer Flow

- readiness and blocker actions;
- first-time/re-verification experience;
- limit presentation;
- amount/term submission;
- application destination and resume.

### #77 — UCL and Collateral Origination and Evidence

- UCL amount/term submission and three-item evidence flow;
- Collateral one-asset fact form and ownership evidence;
- document upload, replacement baseline, reconnect, and status;
- explicit product-specific copy without client-side pricing or LTV logic.

### #78 — Application Tracking and Corrections

- application list and detail;
- faithful status/action projection;
- Customer correction tasks, proof, completion, and resubmission;
- narrow Salary Advance/UCL correction cancellation;
- no Staff task execution.

### #79 — Offers and Contracts

- approved-offer review, expiry, accept, and decline;
- current operational contract and exact-version acknowledgment;
- masked destination and provisional repayment presentation.

### #80 — LoanAccount and Servicing Reads

- LoanAccount list and detail;
- paid/outstanding summary;
- immutable final schedule and installment servicing state;
- paged repayment history;
- no Customer payment-entry or Staff servicing commands.

Staff/Admin frontend, OCR review UI, deployment, and production hosting remain separate later work.

---

## 21. Deferred Frontend Concerns

The Customer Web MVP foundation deliberately defers:

- final logo, illustration system, and immutable brand color approval;
- dark mode;
- formal Figma component libraries;
- Storybook or a separate design-system package;
- SSR, Next.js, microfrontends, and native mobile delivery;
- PWA installation, offline mutations, and persisted Query caches;
- Redux or another general state store;
- OpenAPI client generation;
- advanced animation and charting;
- analytics, experimentation, personalization, and marketing tracking;
- Customer notifications beyond the existing email-driven identity flows;
- saved LoanApplication drafts until the backend exposes a draft contract;
- Customer payment initiation or payment-provider integration;
- OCR execution and Customer OCR-result presentation;
- localization infrastructure until the initial UI language and copy authority are selected.

The starting palette, system typography, desktop sidebar, mobile Sheet navigation, and English UI copy are implementation defaults. Deni's visual/product review should confirm the public-facing language, final brand mark, color tuning, and Customer copy tone during #71–#72. Those decisions tune the centralized system; they do not change the component, layout, state, or authority rules in this blueprint.

---

## 22. Implementation Readiness Rules

A future page checkpoint is ready to implement when:

1. every displayed business fact has an authoritative backend response;
2. every action maps to an executable Customer endpoint and permission;
3. the route uses one of the four layout templates;
4. generic controls come from the shared primitive layer;
5. repeated lending presentation uses an existing Meridian domain component or names a justified new one;
6. query keys, invalidation, request identity, and auth recovery are defined;
7. loading, empty, error, business-rejection, success, and responsive states are specified;
8. sensitive fields and Staff-only data remain outside logs, storage, and display;
9. financial values are formatted, not recalculated;
10. keyboard, focus, labels, status semantics, and contrast satisfy Section 18.

If an API is missing, the implementation stops at the dependency or ships an honest unavailable state. It must not hardcode changing product policy, retain private resource IDs as a substitute index, or invent a lifecycle transition.
