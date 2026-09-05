# Meridian Internal Web

`internal-web/` is Meridian's shared Internal Web React/Vite application. Its delivered Staff Web feature area under `/staff/*` includes authentication, authorization, session restoration, the responsive operations shell, permission-scoped application discovery, the read-only case Overview and LoanApplication History, document-review and Staff-correction queues, exact-version memory-only document review, authorized Staff upload, correction completion, Staff resubmission, product-specific verification, and Loan Officer review start/current-cycle reconciliation. Loan Officer recommendation, approval, contract, disbursement, servicing, partner administration, user administration, and other later operational workspaces remain deferred.

Future Back-Office Administration belongs to the same application under `/admin/*`; that route area remains reserved and unimplemented. [MER-FE-002](../docs/frontend/MER-FE-002-staff-web-blueprint.md) governs Staff Web architecture and delivery. A future MER-FE-003 may govern Back-Office Administration when that area is separately scheduled. Backend behavior remains authoritative; the frontend never infers authorization from a role name or permission prefix.

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
- Navigation and the no-access decision use an explicit list of supported operational permissions; prefix and wildcard matching are prohibited.
- Product Verification and Loan Officer Review routes require exact `loan:review` and can operate without the broader `loan:read` case capability; their backend reads and services repeat the same Staff-shape and authority checks.
- Verification and review-start commands have no business UUID. Internal Web never automatically retries their POSTs after a network failure; it reconciles through the purpose-limited GET, and Collateral completion also binds confirmation to `expectedVerificationId`.
- Restricted assessment notes remain in component memory, are excluded from query caches and browser storage, and clear after confirmed completion or unmount.
- `/admin/*` is reserved and currently returns the safe not-found page.

The generic `DRAFT`, `IN_FLIGHT`, `RESULT_UNKNOWN`, `RECONCILING`, `RESOLVED`, and `BLOCKED` presentation states are frontend operation-control vocabulary, not Loan domain statuses. CP3 commands create and retain explicit logical operation UUIDs; the API client does not auto-attach mutation identities.
