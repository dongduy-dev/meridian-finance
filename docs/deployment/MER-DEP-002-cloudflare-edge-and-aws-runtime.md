# MER-DEP-002 — Cloudflare Edge and AWS Runtime

## 1. Scope and Ownership

This document defines the single-host portfolio runtime, public routing, frontend hosting, and authentication client-address boundary. [MER-DEP-001](MER-DEP-001-identity-bootstrap-and-demo-boundary.md) owns the public demo policy, historical credential retirement, and Identity bootstrap. Repository configuration does not prove that provider settings or live smoke checks have been completed.

The executable runtime contract is [`meridian-platform/deployment/compose.yml`](../../meridian-platform/deployment/compose.yml). It is independent of the local-development `meridian-platform/docker-compose.yml`; never combine the two as Compose overrides. The local stack retains its published ports and Mailpit workflow.

## 2. Public Hosts and Runtime Boundary

| Public URL | Destination | Authorization |
|---|---|---|
| `https://meridianfinance.dev` | Customer Web on Cloudflare Pages | Public entry; Customer authentication for protected operations |
| `https://internal.meridianfinance.dev` | Internal Web on Cloudflare Pages | Meridian Staff authentication and exact capability/RBAC checks |
| `https://api.meridianfinance.dev` | Cloudflare Tunnel → `http://meridian-platform:8080` | Existing Meridian API authentication, ownership, and authorization |

```text
Cloudflare Pages: Customer Web + Internal Web
                         │ HTTPS API requests from the browser
Cloudflare API edge ──────┘
        │ authenticated Tunnel connection initiated outbound
AWS EC2 │ ap-southeast-1
        └── cloudflared ── tunnel Docker network ── Meridian
                                                    │
                                       internal database network
                                                    │
                                                PostgreSQL
```

The AWS baseline is `meridian-prod`, Ubuntu 24.04 x86_64, `t3.small` (2 vCPU, 2 GiB RAM), 2 GiB persistent swap, and 30 GiB encrypted gp3 EBS. IMDSv2, termination protection, Standard CPU credits, and operator-IP-only SSH are host prerequisites. No IAM instance role or Elastic IP is required. The operator maintains the SSH allowlist in the Security Group and UFW without recording transient addresses in Git.

No service publishes a host port. No host listener or inbound rule for 80, 443, 8080, or 5432 belongs to this deployment. Dockerfile `EXPOSE 8080` is image metadata, not a host publication. UFW alone is insufficient protection for Docker-published ports: retain both the no-publication Compose contract and the AWS inbound boundary.

PostgreSQL joins only the `internal: true` database network. Meridian joins that network and the outbound-capable tunnel network; `cloudflared` joins only the tunnel network. Meridian retains outbound connectivity for later SMTP. Docker/host administrators are trusted operators and can reach container networks; do not attach unrelated workloads, host proxies, or a Docker socket mount. There is no alternative public backend route, host-network mode, or frontend server on EC2.

The fixed Compose project name produces `meridian-prod_postgres_data` and `meridian-prod_document_data`. Their local Docker storage must reside on the encrypted EBS filesystem; verify Docker's data root and volume mountpoints on the host. Restart, `stop`, and ordinary `down` preserve these volumes. Do not use `down --volumes` or volume pruning for routine deployment. These volumes provide persistence, not a backup.

All three services use `restart: unless-stopped` and bounded Docker JSON logs. Meridian starts after PostgreSQL's `pg_isready` check. Tunnel can start before Java finishes startup, so temporary edge failures are possible. `/api/v1/health` is liveness; it does not prove database/storage readiness. The Java heap is bounded at 768 MiB with a 128 MiB initial heap. Native JVM memory, PostgreSQL, Docker, and the OS also consume RAM; measure `docker stats`, swap use, and EBS capacity on the actual host.

## 3. Runtime Configuration

The deployment [environment example](../../meridian-platform/deployment/.env.example) is an inventory of placeholders, not usable credentials. Store actual configuration outside the checkout, for example `/opt/meridian/private/runtime.env`, and the Tunnel token in `/opt/meridian/private/tunnel-token`. Keep the directory mode `0700` and the environment file mode `0600`. The token file must be readable by the image's unprivileged `cloudflared` user through its read-only secret mount; mode `0444` inside the host's operator-only directory satisfies that requirement. Do not make the parent directory public. Standalone Compose secrets are file mounts, not an encrypted secret-management service.

| Setting | Classification and required value |
|---|---|
| `POSTGRES_PASSWORD` | Required private database password; no development fallback |
| `MERIDIAN_CUSTOMER_ENCRYPTION_KEY` | Required private Base64 32-byte Customer encryption key |
| `MERIDIAN_CUSTOMER_FINGERPRINT_KEY` | Required separate private Base64 32-byte Customer fingerprint key |
| `MERIDIAN_LOAN_DISBURSEMENT_SNAPSHOT_KEYS_LOCAL` | Required separate private Base64 32-byte Loan snapshot key |
| `MERIDIAN_JWT_PRIVATE_KEY` | Required private Base64 PKCS#8 RSA signing key |
| `MERIDIAN_JWT_PUBLIC_KEY` | Required matching Base64 X.509 RSA public key; public material, operator-owned configuration |
| `MERIDIAN_TUNNEL_TOKEN_FILE` | Required non-secret absolute path to the private remotely managed Tunnel token file |

Compose rejects absent or empty required values before starting containers. Meridian's existing crypto/key configuration validates key material at application startup. Do not reuse checked-in test keys, local-development keys, or placeholder values. Do not print resolved `docker compose config`, container environments, keys, or token contents into shared logs. Use `config --quiet` with real configuration; use the placeholder-only verification script for structural evidence.

The existing Loan key-map entry is named `local`; this identifier does not select a development mode or supply key material. The deployment binds it to an operator-generated key and sets the active key ID to `local`. Preserve this key across deployments. Historical-key retention and rotation procedures belong to CP4; do not change the active key or replace the retained key merely to redeploy.

The following non-secret values are fixed in deployment Compose so a copied local `.env` cannot override the public contract:

| Setting | Deployment value |
|---|---|
| `MERIDIAN_FRONTEND_ALLOWED_ORIGINS` | `https://meridianfinance.dev,https://internal.meridianfinance.dev` |
| `MERIDIAN_FRONTEND_BASE_URL` | `https://meridianfinance.dev` |
| `MERIDIAN_INTERNAL_FRONTEND_BASE_URL` | `https://internal.meridianfinance.dev` |
| `MERIDIAN_REFRESH_TOKEN_COOKIE_SECURE` | `true` |
| `MERIDIAN_AUTH_CLIENT_ADDRESS_MODE` | `CLOUDFLARE` |
| `SERVER_FORWARD_HEADERS_STRATEGY` | `none` |
| `MERIDIAN_DOCUMENT_STORAGE_ROOT` | `/var/lib/meridian/documents` |
| `MERIDIAN_ADMIN_BOOTSTRAP_ENABLED` | `false` until SMTP/onboarding is deliberately activated in CP4 |

CORS permits credentialed requests from the two exact HTTPS frontend origins. Localhost, Pages preview hosts, wildcard origins, and other subdomains are not allowed. CORS is a browser boundary and does not replace API authorization.

Refresh cookies retain `HttpOnly`, `SameSite=Strict`, `/api/v1/auth`, the existing lifetime, and host-only scope on the API. `Secure=true` is explicit even though Tunnel reaches the origin over HTTP. The three HTTPS production hosts are same-site; their different origins still require credentialed CORS. Do not relax SameSite for `pages.dev` previews. Customer verification/reset and Staff setup emails derive their destinations from the two configured frontend base URLs when email delivery is activated.

OCR execution is disabled by omitting its worker, provider credentials, and result-encryption key. Existing OCR APIs are not removed; an authorized job request can remain pending without a worker. Use the manual evidence workflow and do not present OCR as operational. Mailpit and ClamAV are absent. SMTP points to unused container loopback, so CP2 does not deliver email or provide usable account onboarding. It does not disable registration or uploads. CP4 must activate provider SMTP and the existing bootstrap procedure before onboarding/live session proof.

## 4. Authentication Client Address

`AuthenticationClientAddressResolver` belongs to Identity's HTTP infrastructure and supplies only authentication rate-limit keys. It does not change the servlet remote address, request scheme, audit payloads, or domain behavior.

| Mode | Rate-limit identity | Invalid/missing metadata |
|---|---|---|
| `REMOTE_ADDR` (default) | Servlet remote address; all Cloudflare and forwarding headers ignored | No Cloudflare metadata required |
| `CLOUDFLARE` (explicit deployment setting) | Exactly one `CF-Connecting-IP` containing an IPv4 or IPv6 literal | HTTP 400 `VALIDATION_ERROR`, safe fixed message, no authentication command and no limiter bucket consumed |

Cloudflare mode rejects duplicate headers, comma-separated lists, hostnames, ports, brackets, zone IDs, whitespace, and malformed addresses. Java's literal-only parser performs no DNS lookup; normalized IPv6 and mapped IPv4 forms share a bucket. Neither mode uses `X-Forwarded-For`, `Forwarded`, `X-Real-IP`, or `CF-Connecting-IPv6`. An unknown mode fails startup rather than silently falling back.

The interceptor applies this boundary only to POST login, refresh, registration, email-verification request, and password-reset request. Their independent fixed-window policies and bounded in-memory storage remain unchanged. Preflight and logout remain outside throttling. Missing trusted metadata rejects an auth request rather than collapsing visitors into the connector's bucket or accepting a caller-selected fallback.

Trust comes from the isolated origin and the authenticated Tunnel, not the header name or a private source IP. Enable Cloudflare mode only when the topology in section 2 holds. Anyone with direct origin access could supply that header; host/Docker/Tunnel administration therefore remains inside the trust boundary. Keep Spring forwarding-header processing disabled.

Cloudflare must preserve its visitor header: leave **Remove visitor IP headers** disabled and **Pseudo IPv4** off. Do not add same-zone Workers, Snippets, or transforms that rewrite client identity in the API path. Cloudflare documents different identity semantics for Worker subrequests; cross-zone Worker traffic may share Cloudflare's fixed Worker address and is not a claim of unique human identity. A future proxy path requires a new trust review. See [Cloudflare's client-header contract](https://developers.cloudflare.com/fundamentals/reference/http-headers/).

## 5. Cloudflare Pages

Create two ordinary Pages projects using the repository's Git integration. Authorize the provider integration separately; no GitHub/provider mutation is performed by repository validation. Use these production settings:

| Setting | Customer project | Internal project |
|---|---|---|
| Project name | `meridian-customer-web` | `meridian-internal-web` |
| Root directory | `customer-web` | `internal-web` |
| Build command | `npm ci && npm run build` | `npm ci && npm run build` |
| Output directory | `dist` | `dist` |
| Production branch | `main` | `main` |
| Build variable `NODE_VERSION` | `22` | `22` |
| Build variable `VITE_API_BASE_URL` | `https://api.meridianfinance.dev/api/v1` | `https://api.meridianfinance.dev/api/v1` |
| Custom domain | `meridianfinance.dev` | `internal.meridianfinance.dev` |

`VITE_API_BASE_URL` is public build configuration, not a secret. Vite substitutes it when building; changing backend environment variables cannot change an already-built frontend. Add custom domains through each Pages project's **Custom domains** screen and let Pages establish the zone records/certificates. Do not point either frontend at EC2. Disable automatic production/preview deployments while checkpoints are under review; deploy the approved revision deliberately. Preview URLs do not receive production CORS authorization.

Pages' built-in SPA fallback serves `index.html` for deep links when no top-level `404.html` exists. Keep that condition; no Functions, Worker, custom server, or catch-all redirect is required. Meridian's own router continues to handle unsupported application routes. See [Pages routing and caching](https://developers.cloudflare.com/pages/configuration/serving-pages/) and [monorepo build configuration](https://developers.cloudflare.com/pages/configuration/monorepos/).

Each app's `public/_headers` is copied by Vite into `dist/_headers`. Pages applies it to static responses, including SPA navigation. JavaScript is restricted to same-origin external scripts. Connections permit same-origin asset preloading and the exact API origin. Framing is denied and base URL changes are prohibited. Styles permit inline values because React progress indicators and Radix primitives inject them. Fonts are system/local; brand images are bundled locally and small images may use Vite data URLs. No external font or analytics host is required.

Internal Web additionally permits `blob:` images and PDF objects/frames for its authenticated document viewer. Customer Web downloads blobs through anchors and does not embed documents. Neither CSP enables inline scripts or `unsafe-eval`. Both applications set `nosniff`, `no-referrer` (including token-bearing setup/reset URLs), and disable unused camera, microphone, location, payment, and USB features. Disable Cloudflare HTML/script injection features such as Rocket Loader or automatic Web Analytics unless separately reviewed against this CSP. See [Pages custom headers](https://developers.cloudflare.com/pages/configuration/headers/).

Hashed Vite `/assets/*` resources receive one-year immutable browser caching. HTML and SPA navigation retain Pages' default revalidation/ETag behavior. Do not add a zone-wide Cache Everything rule or a browser cache override that makes HTML stale. Future unhashed public files must not be placed under `/assets/` with mutable contents.

## 6. Tunnel and Edge Provider Settings

After the reviewed code is available to the operator:

1. Create a remotely managed Cloudflare Tunnel for Meridian. Store its token privately as described in section 3. The pinned `cloudflared` image supports `--token-file`; do not paste a dashboard command containing the token into repository files, shared terminals, or logs. See [Tunnel run parameters](https://developers.cloudflare.com/cloudflare-one/networks/connectors/cloudflare-tunnel/configure-tunnels/run-parameters/).
2. Add only the public API route `api.meridianfinance.dev` to `http://meridian-platform:8080`. Keep the unmatched-route catch-all at HTTP 404. Do not add database, Docker, host-management, readiness, or alternate application routes. The resulting proxied DNS record targets the Tunnel UUID under `cfargotunnel.com`; never create an API A/AAAA record to the EC2 address. No Cloudflare Access application is required.
3. Permit the connector's required outbound connectivity (Cloudflare Tunnel TCP/UDP 7844 and DNS; package/image retrieval also requires outbound HTTPS). Do not open an inbound application port. Retain the supplied Security Group and UFW SSH boundary.
4. Set **Always Use HTTPS** for the zone and verify edge certificates on all three hosts. Keep TLS minimum 1.2 and Full (strict) zone mode. Tunnel's explicitly configured local HTTP service uses its authenticated connector path; it does not require a public origin TLS listener.
5. After verifying HTTPS on each host, enable edge HSTS with a six-month maximum age. Leave `includeSubDomains` and preload submission disabled unless all future subdomains are deliberately covered. Verify `Strict-Transport-Security` on both Pages hosts and the API. Do not rely on the HTTP backend to emit HSTS. See [Cloudflare HSTS](https://developers.cloudflare.com/ssl/edge-certificates/additional-options/http-strict-transport-security/).
6. Create a Cache Rule named `Meridian API bypass` with expression `(http.host eq "api.meridianfinance.dev")` and **Cache eligibility → Bypass cache**. Ensure no later matching rule overrides it. Preserve backend `Cache-Control`, document headers, and `Set-Cookie`; do not add response transformations that weaken them. The rule covers all API paths, including paths resembling static file extensions. See [Cache Rule setup](https://developers.cloudflare.com/cache/how-to/cache-rules/create-dashboard/).
7. Apply the client-header conditions from section 4. Verify actual visitor identity through the public hostname before treating throttling as operational.

## 7. Operator Start, Stop, and Update

Use a reviewed source checkout at `/opt/meridian/source` and private configuration outside it. These commands run on the existing Ubuntu host; they do not provision AWS or Cloudflare. Do not upload local `.env`, test keys, or generated build directories with the source.

```bash
cd /opt/meridian/source
docker compose --env-file /opt/meridian/private/runtime.env -f meridian-platform/deployment/compose.yml config --quiet
docker compose --env-file /opt/meridian/private/runtime.env -f meridian-platform/deployment/compose.yml pull postgres cloudflared
docker compose --env-file /opt/meridian/private/runtime.env -f meridian-platform/deployment/compose.yml build meridian-platform
docker compose --env-file /opt/meridian/private/runtime.env -f meridian-platform/deployment/compose.yml up -d
docker compose --env-file /opt/meridian/private/runtime.env -f meridian-platform/deployment/compose.yml ps
```

On the small host, watch memory/disk during Maven image builds and avoid concurrent heavyweight work. Tunnel may briefly return an origin error during startup; inspect bounded service logs privately and repeat the liveness check after Flyway/application startup completes. Never publish environment dumps or authentication/evidence content as diagnostic artifacts.

For a deliberate outage, use the same command prefix with `stop`; resume with `up -d`. For recreation with changed configuration, use `up -d --force-recreate meridian-platform` (and `cloudflared` when its token mount/configuration changes). `restart` alone does not load changed environment values. Ordinary `down` removes containers/networks while preserving volumes; avoid it for a simple restart.

For an update, prepare the reviewed source revision, validate configuration, build the backend image, and run `up -d --no-deps meridian-platform`; repeat section 8. Review migration compatibility and preserve state before applying a version with migrations. An older image is not an automatic schema rollback. CP4 owns the complete backup/restore, release-identity, migration recovery, and reset procedures; this start/update sequence does not replace them.

## 8. Verification and Evidence

### 8.1 Repository checks

Run backend focused tests for the resolver, configuration, rate-limit interceptor, CORS/security, and refresh cookies, then `./mvnw -B verify` with Java 25 and isolated PostgreSQL 16. For each frontend run `npm run lint`, `npm run typecheck`, `npm test`, and a build with the production API variable. Example in each app directory:

```bash
VITE_API_BASE_URL=https://api.meridianfinance.dev/api/v1 npm run build
```

From the repository root, `node meridian-platform/deployment/verify-contract.mjs --frontends` renders Compose with placeholders only, checks the service/network/volume/port and required-value contracts, and inspects both `dist` directories for headers, SPA fallback compatibility, API targets, and known secret markers. It starts no containers and never reads the operator's `.env`. Static marker checks supplement review; they are not a general proof that arbitrary secrets cannot exist in a bundle. Inspect the complete diff and run `git diff --check`.

### 8.2 Host and provider checks

| Evidence | Required observation |
|---|---|
| Pages roots and deep links | HTTPS loads Customer `/login`, `/demo-privacy`, `/applications`, and Internal `/login`, `/staff`, `/admin/users` directly and on refresh; protected destinations retain normal login/capability behavior |
| Resource/CSP behavior | No blocked required JS/CSS/brand assets, forms, dialogs, document downloads, or authenticated Internal PDF/image previews; no unexpected third-party requests |
| Frontend response headers | CSP, DENY framing, nosniff, no-referrer, Permissions-Policy, and edge HSTS present on roots and deep links |
| Static caching | HTML revalidates; hashed assets have immutable caching; an updated HTML deployment selects the new hashed assets |
| API liveness | `https://api.meridianfinance.dev/api/v1/health` reaches Meridian through the configured Tunnel |
| CORS | OPTIONS with each configured Origin allows that exact Origin and credentials; `https://untrusted.example` and localhost return no allowed-origin header/403 |
| Session | With an ordinary usable test identity, login sets Secure + HttpOnly + SameSite=Strict at `/api/v1/auth`; refresh rotates; logout clears the same cookie and revokes the session |
| Client identity | Independent public network addresses get independent auth capacity; changing spoofed `CF-Connecting-IP`, `X-Forwarded-For`, and `Forwarded` through Cloudflare does not change the visitor bucket |
| API caching | Repeated health/public/protected requests and a static-looking API path never return a cache HIT or cached personalized body; inspect rule matching and retained backend no-store headers |
| Origin isolation | Compose/container `PortBindings` empty; host has no 80/443/8080/5432 listeners; SG/UFW still permit only operator SSH; external probes to those EC2 ports fail; API DNS points to Tunnel, not EC2 |
| Persistence | Docker volume mountpoints/data root are EBS-backed; container recreation preserves the same database/document volumes |
| URL configuration | Effective Customer and Internal frontend bases match section 3; actual verification/reset/setup email links checked when CP4 activates SMTP |

Safe unauthenticated probes include:

```bash
curl --fail --silent --show-error https://api.meridianfinance.dev/api/v1/health
curl -sSI https://meridianfinance.dev/demo-privacy
curl -sSI https://internal.meridianfinance.dev/staff
curl -sS -D - -o /dev/null -X OPTIONS https://api.meridianfinance.dev/api/v1/auth/refresh \
  -H 'Origin: https://meridianfinance.dev' -H 'Access-Control-Request-Method: POST'
```

Repeat the preflight for Internal and unrelated origins. For the throttle probe, use POST refresh with no cookie from two separate public networks; unauthenticated attempts should reach the normal auth error until the configured limit, then 429. Change spoofed forwarding headers on the exhausted network: it must remain 429 while the other network retains capacity. Wait for the configured window to expire between runs. Do not use a real account's failed login attempts or print session tokens/cookies into shared evidence.

On EC2 inspect `docker compose ... ps`, container `NetworkSettings.Ports`/`HostConfig.PortBindings`, `ss -lnt`, `docker volume inspect meridian-prod_postgres_data meridian-prod_document_data`, and `docker info --format '{{.DockerRootDir}}'`. The operator supplies the transient EC2 address privately for external port probes. Do not publish it in the runbook.

Record the reviewed revision, provider settings, observed results, and any missing evidence in the checkpoint review. If SMTP/account onboarding is unavailable, explicitly leave live login/cookie/logout and email-link delivery evidence pending. Automated cookie/CORS/configuration proof does not authorize a temporary password or historical-user reactivation.

## 9. Checkpoint Boundaries

CP2 repository checks establish the runtime/edge contract; they do not establish live Cloudflare/AWS success or final public-demo readiness. Provider setup and section 8 evidence remain operator work after review.

CP3 owns multipart 10 MiB alignment, private ClamAV, fail-closed malware scanning, and upload quarantine/promotion. CP4 owns Resend/TLS/SMTP activation, account onboarding, private dependency readiness, non-root backend execution, immutable release identity, key preservation/recovery, backup/restore, and reset operations. Do not declare the public upload deployment ready before those approved checkpoints and the final deployed smoke test. `MER-FU-032` remains open for its broader storage lifecycle. OCR activation and real financial production remain outside this runtime contract.
