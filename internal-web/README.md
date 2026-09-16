# Meridian Internal Web

`internal-web/` is Meridian's shared Internal Web React/Vite application. Its delivered Staff Web feature area under `/staff/*` includes authentication, authorization, session restoration, the responsive Internal Web shell, permission-scoped application discovery, the read-only case Overview and LoanApplication History, document-review and Staff-correction queues, exact-version memory-only document review, authorized Staff upload, correction completion, Staff resubmission, product-specific verification, Loan Officer review and recommendation, authoritative Approver discovery, independent decision recovery, Accounting-owned contract preparation and readiness operations, external-transfer confirmation with durable LoanAccount activation reconciliation, server-owned `ACTIVE` / `OVERDUE` servicing discovery, authoritative LoanAccount schedule/progress/history presentation, ordinary repayment recording, Approver-owned Administrative Full-Balance Settlement, and Accounting-owned administrative closure.

Back-Office Administration provides an executable `/admin` landing, a `partner:read`-guarded Partner workspace under `/admin/partners/*`, and a `loan:product:manage`-guarded Loan Product workspace under `/admin/products`. The Partner workspace supports authorized company discovery/detail, employee evidence and import-history reads, controlled `partner:manage` company commands, and memory-only exact replay for effective-month employee imports. Loan Product Administration lists active and inactive products and provides backend-confirmed amount-limit and activation commands; product code, type, name, description, and broader policy remain read-only. Internal-user administration remains unavailable. [MER-FE-002](../docs/frontend/MER-FE-002-staff-web-blueprint.md) governs Staff Web; [MER-FE-003](../docs/frontend/MER-FE-003-back-office-administration-blueprint.md) governs Back-Office Administration. Backend behavior remains authoritative, and frontend capability exposure uses exact permissions rather than role names, prefixes, or wildcards.

## Local development

Requirements: Node.js 22 and a Meridian backend at `http://localhost:8080`.

```bash
cd internal-web
npm ci
npm run dev
```

The development server uses `http://localhost:5174` with strict port selection. Copy `.env.example` to `.env` only when the API base differs from `http://localhost:8080/api/v1`. `VITE_API_BASE_URL` is a non-secret origin/base-path setting; never place credentials or keys in Vite environment variables.

The backend CORS default explicitly allows the local Customer Web (`http://localhost:5173`) and Internal Web (`http://localhost:5174`) origins. Wildcard origins remain invalid. `MERIDIAN_FRONTEND_BASE_URL` remains the Customer Web base used for customer email links and is not changed by this package.

## Verification

```bash
npm run lint
npm run typecheck
npm test
npm run build
```

## Security model

- Only an authentication response with `userType` equal to `STAFF` and `customerId` equal to `null` is accepted.
- Customer responses are rejected, best-effort logged out, and cleared from memory during both login and refresh restoration.
- The bearer access token exists only in module memory. Session restoration uses the backend's HttpOnly refresh cookie.
- Concurrent refresh calls share one promise. A protected request may replay only once after a definitive session failure.
- TanStack Query data and ephemeral sensitive UI state are cleared on logout and session/actor changes.
- Staff and Back-Office navigation and no-access decisions use separate explicit permission lists; role-name, prefix, and wildcard matching are prohibited.
- Product Verification and Loan Officer Review routes require exact `loan:review` and can operate without the broader `loan:read` case capability; their backend reads and services repeat the same Staff-shape and authority checks.
- Verification and review-start commands have no business UUID. Internal Web never automatically retries their POSTs after a network failure; it reconciles through the purpose-limited GET, and Collateral completion also binds confirmation to `expectedVerificationId`.
- Contract preparation and readiness confirmation retain one UUID only for an exact semantic payload. A lost response triggers a purpose-limited GET without inferring the request result or automatically replaying the POST; an operator may explicitly retry only the retained UUID and unchanged version/reason payload.
- Repayment recording retains one UUID for the canonical application/reference/amount/date payload. The raw external payment reference remains in component memory only; persisted recovery contains only actor-bound operation metadata and a SHA-256 digest. Account or history changes never prove the unknown request succeeded, so only explicit exact replay resolves request identity.
- Administrative Full-Balance Settlement follows the same protected-reference boundary and adds the Approver role requirement. Reload recovery reconstructs safe immutable amount/date evidence through the settlement-evidence read, requires the operator to re-enter a digest-matching reference, and preserves exact replay after the account reaches `SETTLED` or `CLOSED`.
- Administrative closure requires the Accounting Officer role, has no financial inputs, and persists only actor-bound operation metadata, its stable UUID, and a semantic digest. A `CLOSED` read does not prove the unresolved request identity; explicit exact replay remains required.
- Restricted assessment notes remain in component memory, are excluded from query caches and browser storage, and clear after confirmed completion or unmount.
- `/admin/partners` and `/admin/partners/:partnerCompanyId` require exact `partner:read`; `partner:manage` alone does not grant route access. Sensitive employee reads use company ID-only query keys, zero cache retention after unmount, and no browser persistence. Import unknown-result recovery retains raw rows only in mounted page memory and explicitly replays the same request UUID.
- `/admin/products` requires exact `loan:product:manage`. It uses the protected administration projection rather than the public catalogue, never applies optimistic product state, and never automatically retries a mutation after an unknown result.
- Unimplemented User, configuration, and audit children return the safe not-found page and load no administration data.

The generic `DRAFT`, `IN_FLIGHT`, `RESULT_UNKNOWN`, `RECONCILING`, `RESOLVED`, and `BLOCKED` presentation states are frontend operation-control vocabulary, not Loan domain statuses. Commands that define durable replay create and retain explicit logical operation UUIDs; target-state Loan Product commands do not. The API client does not auto-attach mutation identities.
