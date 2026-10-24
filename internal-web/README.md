# Meridian Internal Web

`internal-web/` is one React/Vite application hosting Staff Web under `/staff/*` and Back-Office Administration under `/admin/*`. Staff Web covers Staff-assisted UCL and Collateral Loan intake and common lending operations through servicing and closure. Back-Office Administration covers Partner administration and eligibility review, Loan Product administration, and internal users with predefined role assignments.

The two areas share authentication, session management, protected API transport, and Internal Web chrome. Their capability guards, routes, navigation, queries, and commands remain separate. [MER-FE-002](../docs/frontend/MER-FE-002-staff-web-blueprint.md) owns Staff Web client behavior; [MER-FE-003](../docs/frontend/MER-FE-003-back-office-administration-blueprint.md) owns Back-Office Administration client behavior. Backend contracts and business specifications remain authoritative for authentication and lending behavior.

## Requirements

- Node.js 22
- npm 10 or newer
- A running Meridian backend; see the root [local startup guide](../README.md#run-meridian-locally).

## Local setup

Run from `internal-web/`:

```bash
npm ci
# Copy .env.example to .env for the local backend API.
npm run dev
```

The development server uses `http://localhost:5174` with strict port selection. `.env.example` supplies the separate local backend location:

```text
VITE_API_BASE_URL=http://localhost:8080/api/v1
```

Without this setting, requests use relative `/api/v1` on the frontend origin. Change the value when the backend location differs. It is a non-secret API location; never place credentials or keys in Vite environment variables. Local `.env` files must not be committed.

The backend CORS default allows `http://localhost:5173` and `http://localhost:5174`. Wildcard origins remain invalid. Customer email links use `MERIDIAN_FRONTEND_BASE_URL`; Internal Web setup/recovery links use `MERIDIAN_INTERNAL_FRONTEND_BASE_URL`. These are backend settings, not Vite credentials.

For local Staff accounts, follow the [first-admin bootstrap and Staff provisioning guide](../docs/deployment/MER-DEP-001-identity-bootstrap-and-demo-boundary.md). No shared Staff password is provided. Use separate browser profiles for simultaneous Customer and Internal sessions while [MER-FU-049](../docs/project/MER-TRACK-001-follow-up-register.md#mer-fu-049---isolate-customer-web-and-internal-web-browser-sessions) remains deferred.

## Commands

| Command | Purpose |
|---|---|
| `npm run dev` | Start the local Vite server on port 5174 |
| `npm run lint` | Run ESLint |
| `npm run typecheck` | Run strict TypeScript checks |
| `npm test` | Run Vitest and Testing Library tests |
| `npm run test:watch` | Run tests in watch mode |
| `npm run build` | Type-check and create the production bundle |
| `npm run preview` | Preview the production bundle on port 4174 |

## Source organization

- `src/app/` owns application composition, providers, environment, and route metadata.
- `src/features/auth/` owns login, session restoration, actor validation, and capability helpers.
- Other `src/features/` packages own Staff operations and administration workspaces, including their queries and commands.
- `src/components/` contains shared UI primitives, layouts, and operational composites.
- `src/lib/api/` owns protected HTTP transport and safe error handling.
- `src/lib/operation/` contains shared operation identity and recovery support.
- `src/lib/sensitive/` contains sensitive-value lifecycle helpers.

`node_modules/`, `dist/`, local `.env` files, and coverage output are not committed.

## Verification

```bash
npm run lint
npm run typecheck
npm test
npm run build
```

## Internal access boundary

Internal Web accepts only a `STAFF` actor with no Customer association. Frontend capabilities use exact permissions; backend authorization remains authoritative. Access tokens stay in memory, session restoration uses the backend-issued HttpOnly refresh cookie, and logout or actor changes clear protected query and UI state.

Detailed route gates, document-memory rules, command identities, exact replay, and uncertain-result recovery belong to [MER-FE-002](../docs/frontend/MER-FE-002-staff-web-blueprint.md#14-operation-identity-and-uncertain-result-recovery) and [MER-FE-003](../docs/frontend/MER-FE-003-back-office-administration-blueprint.md#9-client-state-and-data-safety).
