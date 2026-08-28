# Meridian Customer Web

`customer-web/` is Meridian's independent React/Vite application for responsive Customer experiences. [MER-FE-001](../docs/frontend/MER-FE-001-customer-web-blueprint.md) defines its frontend architecture, state ownership, visual language, accessibility baseline, and delivery sequence. Backend contracts and business specifications remain authoritative for authentication and lending behavior.

## Requirements

- Node.js 22
- npm 10 or newer

## Local setup

```bash
npm ci
```

Copy `.env.example` to `.env` when the backend runs outside the frontend origin. Customer Web reads one non-secret API location setting:

```text
VITE_API_BASE_URL=http://localhost:8080/api/v1
```

This value is an API location, not a secret. The backend permits the default Vite development origin at `http://localhost:5173`.

## Commands

| Command | Purpose |
|---|---|
| `npm run dev` | Start the local Vite server |
| `npm run lint` | Run ESLint |
| `npm run typecheck` | Run strict TypeScript checks |
| `npm test` | Run focused Vitest and Testing Library tests |
| `npm run test:watch` | Run tests in watch mode |
| `npm run build` | Type-check and create the production bundle |
| `npm run preview` | Preview the production bundle locally |

## Branding assets

The frontend copies the approved SVG variants it uses byte-for-byte from `docs/branding/`:

| Frontend asset | Authoritative source |
|---|---|
| `src/assets/brand/meridian-logo.svg` | `docs/branding/Meridian Logo.svg` |
| `src/assets/brand/meridian-logo-expanded.svg` | `docs/branding/Meridian Logo Expand.svg` |
| `src/assets/brand/meridian-logo-mark.svg` | `docs/branding/Meridian Logo Mark.svg` |

`MeridianLogo` is the application boundary for selecting among these variants. The wordmark and retained PNG variants remain available from `docs/branding/`; Customer Web does not copy unused variants into its bundle.

## Source organization

- `src/app/` owns router, providers, environment, and central theme.
- `src/components/ui/` contains locally owned shadcn-style primitives.
- `src/components/common/` contains stable Meridian composites.
- `src/components/layout/` contains the four shared layout templates.
- `src/lib/api/` owns typed HTTP and safe error-envelope handling.
- `src/routes/foundation/` contains neutral visual-review routes with no fake Customer data.

`node_modules/`, `dist/`, local `.env` files, and coverage output are not committed.
