# MER-DEP-003 — Portfolio Operations and Recovery

## 1. Operator Boundary

This runbook owns manual release, persistence, recovery, reset, and startup checks for the single-host fictional portfolio. [MER-DEP-002](MER-DEP-002-cloudflare-edge-and-aws-runtime.md) owns topology and provider settings; [MER-DEP-001](MER-DEP-001-identity-bootstrap-and-demo-boundary.md) owns onboarding and credential retirement. Production execution requires independent review and the operator's approval. Repository/local proof does not establish live provider delivery or deployed acceptance.

Use SSM on the existing host for authorized operations. Starting, stopping, redeploying, or resetting production requires explicit operator authorization. Commands below are operator procedures, not instructions to mutate AWS from an ordinary coding or documentation session. Never paste keys, passwords, inbox contents, reset/verification fragments, or resolved environments into shared evidence. Public demo submissions are private data even when visitors were instructed to use fictional data.

### 1.1 One Privileged Operator Shell

The procedures require an `ubuntu`-owned checkout at `/opt/meridian/source`, approved `sudo` access for Docker, and the verified Node 22 runtime from section 1.2. After the operator authorizes host access, use one temporary root Bash shell for the procedures in this document. Docker access is inherently privileged; do not add the SSM user or `ubuntu` to the Docker group. A shell alias or function for `sudo docker` does not apply to the helpers' Node subprocesses.

From the authorized SSM session, check the caller and approved sudo access, then enter the operator shell. If sudo policy refuses the shell, stop and obtain the operator's approved administration access; do not change permissions to bypass it.

```bash
id
command -v sudo
sudo -n true
sudo -n -H /bin/bash --noprofile --norc
```

Run all subsequent blocks in that same shell. Confirm the exact checkout path, owner, and directory permissions before trusting its Git configuration/hooks. The checkout and `.git` must be controlled by the trusted `ubuntu` operator, without write access for unrelated users. Do not change repository ownership. The exported Git command-scope settings reset inherited safe-directory exceptions and trust only this checkout, including Git launched by `release.mjs`. They do not modify global/system Git configuration. See [Git's safe-directory and runtime configuration rules](https://git-scm.com/docs/git-config).

```bash
set -euo pipefail
umask 077
test "$(id -u)" = 0
test "$(uname -m)" = x86_64
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
unset SUDO_UID SUDO_GID SUDO_USER NODE_OPTIONS NODE_PATH
test "$(readlink -f /opt/meridian/source)" = /opt/meridian/source
test "$(stat -c %U /opt/meridian/source)" = ubuntu
stat -c '%U:%G %a %n' /opt/meridian /opt/meridian/source /opt/meridian/source/.git
export GIT_CONFIG_COUNT=2
export GIT_CONFIG_KEY_0=safe.directory GIT_CONFIG_VALUE_0=
export GIT_CONFIG_KEY_1=safe.directory GIT_CONFIG_VALUE_1=/opt/meridian/source
export GIT_OPTIONAL_LOCKS=0
cd /opt/meridian/source
```

### 1.2 Obtain and Verify Node 22

Choose and record an exact maintained Node 22 patch from the [official Node 22 archive](https://nodejs.org/en/download/archive/v22). Replace the version placeholder below. Download the Linux x64 binary and its SHA-256 listing from that exact official HTTPS release directory; do not execute downloaded installation scripts or use an unverified third-party package. The new mode-0700 temporary directory supplies Node only for this operator session; no package/service installation or persistent PATH change is required.

```bash
for tool in curl tar sha256sum awk git docker; do command -v "$tool"; done
node_version=v22.REPLACE_WITH_REVIEWED_PATCH
[[ "$node_version" =~ ^v22\.[0-9]+\.[0-9]+$ ]]
node_archive="node-$node_version-linux-x64.tar.gz"
node_tools=$(mktemp -d /tmp/meridian-node22.XXXXXX)
node_release="https://nodejs.org/dist/$node_version"
curl --proto '=https' --proto-redir '=https' --tlsv1.2 --fail --silent --show-error --location \
  --output "$node_tools/$node_archive" "$node_release/$node_archive"
curl --proto '=https' --proto-redir '=https' --tlsv1.2 --fail --silent --show-error --location \
  --output "$node_tools/SHASUMS256.txt" "$node_release/SHASUMS256.txt"
awk -v archive="$node_archive" '$2 == archive { print; found++ } END { if (found != 1) exit 1 }' \
  "$node_tools/SHASUMS256.txt" > "$node_tools/selected.sha256"
(cd "$node_tools" && sha256sum --check --strict selected.sha256)
tar -xzf "$node_tools/$node_archive" -C "$node_tools"
export PATH="$node_tools/node-$node_version-linux-x64/bin:$PATH"
test "$(node --version)" = "$node_version"
node -e 'if (process.versions.node.split(".")[0] !== "22") process.exit(1)'
```

Require a successful checksum comparison before extraction/execution. HTTPS authenticates the official checksum source; the selected checksum verifies the downloaded archive. Keep the verified directory and PATH available until every helper completes, including backup/recovery verification. Do not assume Node from a user's interactive PATH survives sudo.

### 1.3 Read-Only Prerequisite Checks

These checks read versions, checkout state, and command/file access. They do not start containers, change provider settings, or print private configuration. Node checks exercise the same inherited PATH/Git trust and direct Docker permissions used by all three helpers. Stop on any failed check before release, backup, restore, or reset.

```bash
command -v node git docker
node --version
git --version
test "$(git rev-parse --show-toplevel)" = /opt/meridian/source
git --no-optional-locks status --short --branch
git rev-parse HEAD
docker version --format '{{.Server.Version}}'
docker compose version
node --input-type=module <<'NODE'
import { spawnSync } from 'node:child_process'
for (const [tool, args] of [
  ['git', ['rev-parse', '--show-toplevel']],
  ['docker', ['version', '--format', '{{.Server.Version}}']],
]) {
  if (spawnSync(tool, args, { stdio: 'inherit' }).status !== 0) process.exit(1)
}
NODE
private=/opt/meridian/private
test -r "$private/runtime.env" && test -r "$private/tunnel-token"
stat -c '%a %n' "$private" "$private/runtime.env"
compose() { docker compose --env-file "$private/runtime.env" -f meridian-platform/deployment/compose.yml "$@"; }
compose config --quiet
```

Require the existing private directory/file modes 0700/0600 and the reviewed clean source revision. All later `node`, `docker`, and `compose()` commands run without another sudo prefix inside this shell; Node-spawned Docker/Git commands inherit the same authority and environment. Do not source or display `runtime.env`, enable shell tracing, or render resolved Compose output. Exit the privileged shell when the approved operation ends. Repeat the EC2 tooling/permission checks during each authorized operator session; local proof does not validate live host prerequisites.

The deployment helpers omit command output that could contain private configuration. Inspect failures privately. Do not run local-development Compose on EC2 or combine it with deployment Compose.

## 2. Persistent State and Recovery Inventory

| Resource | Continuity and backup requirement |
|---|---|
| `meridian-prod_postgres_data` → `/var/lib/postgresql/data` | Business records, Identity/password/token state, audit/history, document metadata, and Flyway history; consistent PostgreSQL dump |
| `meridian-prod_document_data` → `/var/lib/meridian/documents` | Exact stored bytes with staging/object layout and ownership; archive from the same quiescent point as the DB |
| JWT private/public pair | Preserve both for signing/verification continuity; image and DB do not contain the private configuration |
| Customer encryption key | Required to read restored protected identity and bank data |
| Customer fingerprint key | Required for consistent protected matching/duplicate checks after restore |
| Loan snapshot active key ID, active key, all retained historical key IDs/values | Every historical snapshot must remain decryptable; inventory IDs privately and confirm each corresponding key is backed up |
| Private Tunnel credential and runtime configuration | Required to restore the same private routing and SMTP/bootstrap configuration; keep outside source control |
| `meridian-prod_clamav_signatures` → `/var/lib/clamav` | Preserve normal restart/redeploy continuity. Re-creatable by FreshClam; missing signatures block uploads until initialization/scan proof |
| Exact approved current and previous backend images and release records | Retain image IDs and source SHAs; export artifacts outside EC2 for host-loss recovery |

Container writable layers, temporary readiness probes, bounded Docker logs, and downloaded base images are recreatable. Docker volumes on EBS are persistent storage, not backup. No ordinary operation uses volume pruning or `down -v`. Keep the fixed project name and mounts. Stop/start/recreation must retain keys and key IDs. A DB dump alone cannot recover document bytes or external cryptographic keys; an image alone cannot recover any business state.

The initial Loan key ID is `local`; it is an identifier, not a development key. The supplied Compose contract retains that entry. Before any separately approved key rotation, extend the private configuration and inventory to include every retained entry; do not replace the old entry as part of release. OCR is disabled and its key is not required here. Later OCR activation requires retention of the OCR result key for any encrypted OCR evidence.

Privately compare `SELECT DISTINCT protection_key_id FROM loan_contracts ORDER BY protection_key_id` with the configured active/retained key-map IDs and off-host inventory. These are identifiers only; never select protected bytes or key values into the transcript. For each identifier, confirm the corresponding valid 32-byte key exists in both runtime and recovery material, and test a restored authorized snapshot read through the contractual destination contract. A missing historical key blocks recovery sign-off even if startup accepts the active key.

## 3. Resend and Identity Acceptance

Configure production Resend transport, domain authentication, credential protection, and the host TLS probe through [MER-DEP-002 §3.1](MER-DEP-002-cloudflare-edge-and-aws-runtime.md#31-smtp-provider-configuration). Require that configuration before onboarding acceptance; a successful TLS handshake alone does not prove SMTP authentication or inbox delivery. Do not expose credentials, token-bearing links, or message bodies in acceptance evidence.

After a Java certificate-trust change, require a separately authorized Java-side STARTTLS handshake to `smtp.resend.com:587` using the approved image's effective trust store and hostname verification, without credentials or mail delivery. The host OpenSSL probe does not verify Java trust. Keep the delivery checks below as separate acceptance evidence; never restore a distrusted root or disable validation to make delivery succeed.

Use three owner-controlled normal external inbox deliveries: Customer verification, Customer reset, and Staff setup. Record delivery time/outcome and link host/scheme only, without token-bearing URLs or message bodies. Customer verification is `https://meridianfinance.dev/verify-email#token=…`; Customer reset is `/reset-password#token=…`; Staff initial setup is `https://internal.meridianfinance.dev/set-password#token=…`. Later Staff reset uses Internal `/reset-password`. Customer digital access uses Customer `/activate-access#verificationToken=…&setupToken=…`.

Follow [MER-DEP-001 §3](MER-DEP-001-identity-bootstrap-and-demo-boundary.md#3-establish-the-first-back-office-admin) for temporary first-admin bootstrap and disable it immediately afterward. If delivery fails after account/token commit, the account remains created: disable bootstrap, correct transport, restart normally, and use supported Forgot password; an authorized administrator can resend setup. Do not retry bootstrap to replace an established administrator. Test expired/replaced/used tokens through their existing contracts; verification confirmation has its existing exact replay semantics, while password reset/setup remains single-use. Do not change those rules during acceptance.

## 4. Matched Backup Before Release or Reset

For the portfolio runtime, the operator takes a manual matched backup before each release, reset, or storage cutover; once weekly while actively receiving demo data; and after a demo session whose state should be retained. Verify an off-host encrypted copy after every backup. Retain weekly matched sets for the portfolio period and at least two last-known-good release sets. The operator owns this schedule; no automation or defined production recovery SLA exists.

Inventory all key IDs and private files first. The helper archives the complete private directory, including `runtime.env`, `tunnel-token`, and separately retained key/configuration files. Refuse missing files, symlinks, hard-linked private files, or an existing backup destination. Use a new absolute destination outside the source and private directories. The helper validates project/service labels and exact volume identities, stops ingress then backend, checks that no running document writer remains, and leaves traffic stopped even on failure. OCR/other writers must not exist in this topology. PostgreSQL stays running for `pg_dump --format=custom`. Do not allow another operator or process to write to the database during the consistency cut.

```bash
backend=$(compose ps -q meridian-platform)
database=$(compose ps -q postgres)
test -n "$backend" && test -n "$database"
backup=/opt/meridian/backups/REPLACE_WITH_NEW_MATCHED_SET_NAME
source_sha=REPLACE_WITH_EXACT_RUNNING_SOURCE_SHA
node meridian-platform/deployment/recovery.mjs backup meridian-prod \
  "$backend" "$database" "$private" "$backup" "$source_sha" BACKUP-meridian-prod
node meridian-platform/deployment/recovery.mjs verify "$backup"
```

The set contains `database.dump`, `documents.tgz`, `private.tgz`, `release.json`, and the `matched-set.json` checksum manifest. `release.json` records source, exact image ID, and last successful migration version. Labeled releases must match their embedded source revision. For a legacy image without that label, source attribution is explicitly operator-confirmed; record the inspected running image ID and independently reviewed source association before proceeding. Section 7 defines the retained-legacy-artifact exception. A supplied SHA alone is not artifact proof.

Retain exact current/previous image artifacts as well as their release records:

```bash
docker image save --output "$backup/backend-image.tar" REPLACE_WITH_EXACT_CURRENT_IMAGE_ID REPLACE_WITH_EXACT_PREVIOUS_IMAGE_ID
sha256sum "$backup/backend-image.tar" > "$backup/backend-image.tar.sha256"
chmod 600 "$backup/backend-image.tar" "$backup/backend-image.tar.sha256"
```

Use only the current ID when no previous image exists, and record that limitation. Encrypt the entire matched set plus artifact using an operator-controlled off-host recovery recipient, for example `tar -C "$backup" -czf - . | gpg --encrypt --recipient "$recovery_recipient" --output "$backup.gpg"`. Do not put a passphrase in arguments or shell history. Verify decryption/checksums on a separate trusted machine, transfer the encrypted file to operator-controlled off-host storage, and record access/recovery ownership. Local plaintext is sensitive and requires mode 0700 directories/0600 files and deliberate removal under the operator's retention policy. Do not mistake local archives for completed off-host backup.

## 5. Isolated Restore and Application Readback

Verify checksums before extraction. Restore first into a new `cp4restore-*` project with fresh volumes. This legacy helper prefix is required by `recovery.mjs`. The helper refuses production names and any existing restore volume; it publishes no port and initially joins no network. It restores the matching DB, document bytes/modes, and private files. Do not point it at live PostgreSQL or attach a production network. Backups are trusted operator artifacts, not arbitrary uploaded archives.

```bash
restore_project=cp4restore-REPLACE_WITH_NEW_LOWERCASE_NAME
node meridian-platform/deployment/recovery.mjs restore "$backup" "$restore_project"
```

The result is an isolated running PostgreSQL container and restored document volume/private directory. Confirm the recorded migration version. Create a dedicated isolated bridge only for the restored app and DB; disconnect the restored DB from `none` before connecting that bridge (`docker network disconnect none "$restore_project-postgres"`). Run the exact approved backed-up image with retained keys, bootstrap disabled, scanner/Mailpit confined to the isolated rehearsal, and the restored document mount. Override mail only in the rehearsal so it cannot deliver duplicate real messages. Keep external routing disabled. `rehearse.mjs` demonstrates this assembly end to end with synthetic data and generated keys; it does not replay real backup inboxes.

Require readiness, matching DB rows, an authorized document-content read and checksum comparison, and protected encrypted-data readback. Confirm retained historical key IDs against restored snapshot metadata without exposing values. Local rehearsal covers the active `local` key and Customer/JWT continuity; historical-key inventory and readback for a real backup require operator evidence from that restored set. A raw `pg_restore` exit status is insufficient. Record elapsed restore/readback time and any unavailable evidence.

For actual recovery, stop traffic first, review the isolated result, and obtain explicit approval for discarding writes since the snapshot. Preserve the failed current set separately. Restore the approved artifact/config/keys, matching DB and documents, and required ownership before startup/Flyway. If any matching backup, required key, or artifact is unavailable, remain in the no-traffic state; do not substitute new keys or mix unmatched snapshots. Recovery into the production volume names is a separately reviewed operator operation, not an option in the isolated restore helper.

## 6. Non-Root Document Volume Cutover

The image runs as UID/GID `10001:10001`; capabilities are dropped and privilege escalation is disabled. Fresh volumes receive the image's owned storage tree. Existing root-owned volumes from a legacy image require one explicit ownership cutover after a verified matched backup and off-host copy and before backend startup. Preserve PostgreSQL, scanner signatures, document bytes, and cryptographic keys.

After explicit operator authorization, validate the expected volume and its Compose labels, stop ingress and all writers, and mount only that document volume. This one-shot root helper has no network, a read-only root filesystem, no Docker socket, and only ownership/mode capabilities. It rejects symlinks, hard links, special files, and unexpected top-level paths. It changes directories to 0700, files to 0600, and owner/group to 10001; it never deletes/replaces content. Review the exact command and backup before execution:

```bash
node meridian-platform/deployment/recovery.mjs verify "$backup"
test "$(docker volume inspect meridian-prod_document_data --format '{{index .Labels "com.docker.compose.project"}}')" = meridian-prod
test "$(docker volume inspect meridian-prod_document_data --format '{{index .Labels "com.docker.compose.volume"}}')" = document_data
test -z "$(docker ps -q --filter volume=meridian-prod_document_data)"
approved_image=REPLACE_WITH_APPROVED_SHA256_IMAGE_ID
docker run --rm --network none --read-only --user 0:0 --cap-drop ALL \
  --cap-add CHOWN --cap-add FOWNER --cap-add DAC_OVERRIDE --security-opt no-new-privileges \
  --mount type=volume,source=meridian-prod_document_data,target=/var/lib/meridian/documents \
  --mount type=bind,source=/opt/meridian/source/meridian-platform/deployment,target=/procedure,readonly \
  --entrypoint sh "$approved_image" /procedure/document-volume-cutover.sh CONFIRM-DOCUMENT-OWNERSHIP-10001
```

Before normal traffic, verify nonzero UID/GID, readiness, authorized old-document readback, a new clean scanned upload/readback, writable `/tmp`, stdout logs, and restart continuity. If validation finds an unexpected path/link, stop and investigate without widening permissions. The backend never runs permanently as root, privileged, or mode 0777. The live cutover remains operator-only after backup.

Local development shares this Dockerfile and keeps Mailpit. A preserved local document volume created by an older root image also needs the reviewed ownership helper after backup and stopped writers. Inspect that local Compose project's exact document volume identity before substituting its name in the helper command; do not copy production volume names into local operations. Fresh local volumes already receive the owned image tree. No local SMTP setting or database/scanner volume ownership changes are required.

## 7. Exact Release and Forward-Only Migrations

Build from a clean reviewed commit. `release.mjs` rejects a supplied SHA that differs from checkout HEAD, dirty source, an existing release tag, and overwritten metadata. It verifies the embedded OCI source label and exact Docker image ID. The optional previous record is validated and its image retained with a dedicated rollback reference before building. No command builds or deploys `latest`.

The Docker build applies the [Oracle eMudhra root-removal mitigation](https://ops.java/emudhra) to Maven's Java trust store before artifact retrieval and to the runtime trust store after package installation. `deployment/JavaTrust.java` resolves the four affected roots by full X.500 identity, preserves unaffected certificate bytes, and verifies the effective default JSSE anchors. The final image repeats verification as UID/GID `10001:10001`. Record the actual Java distribution/version, base digests, image ID, and trust-check results with release acceptance; distinguish a mitigated image from a vendor-fixed build. A version string alone does not prove remediation. A custom `javax.net.ssl.trustStore` or `jssecacerts` requires separate review and verification; the default-store check rejects these overrides. Do not modify a live container's trust store as the release mechanism.

For the retained initial pre-revision-label rollback artifact only, a matched backup's `release.json` may explicitly record `sourceAttribution=operator-confirmed-pre-CP4`. This exact attribution value is an executable compatibility identifier in `release.mjs` and `recovery.mjs`. The helper still compares its exact image ID; the operator must independently review its source association. The exception permits the historical artifact's former root user only as a retained previous image; it cannot validate a newly built or running approved release. Actual previous-image redeployment still requires proved schema compatibility and operator approval.

The legacy artifact predates private readiness and required SMTP TLS. Retaining it provides a recovery reference, not permission to reopen traffic without the required runtime controls. If recovery selects that artifact, keep ingress stopped until a reviewed forward repair restores those controls and smoke succeeds. For a retained labeled/non-root previous image, verify its own record and the current schema compatibility before normal traffic resumes.

```bash
reviewed_sha=REPLACE_WITH_REVIEWED_40_CHARACTER_SHA
record=/opt/meridian/releases/REPLACE_WITH_NEW_RECORD.json
previous=/opt/meridian/releases/REPLACE_WITH_PREVIOUS_APPROVED_RECORD.json
node meridian-platform/deployment/release.mjs build "$reviewed_sha" "$record" "$previous"
```

Omit the previous argument for the initial release only. Keep source SHAs distinct from image IDs/digests. Copy the record's exact `imageId` into private `MERIDIAN_BACKEND_IMAGE`; retain the previous artifact/record and off-host export. Do not retag or rebuild over the sole rollback reference. Compose must not rebuild implicitly.

For existing state, require the verified matched backup and off-host encrypted copy before replacement. Retain previous artifacts/records and complete any ownership cutover. First installation has no prior state to back up; confirm that the intended fresh volumes contain no state to preserve. After configuration validation and independent operator approval, keep ingress stopped and start dependencies and the approved backend:

```bash
compose config --quiet
compose stop cloudflared
compose up -d postgres clamav
compose ps
```

Require PostgreSQL and ClamAV health before starting the backend; `--no-deps` does not wait for them. After both service health checks pass:

```bash
compose up -d --no-deps meridian-platform
backend=$(compose ps -q meridian-platform)
node meridian-platform/deployment/release.mjs verify "$record" "$backend"
```

Wait for backend health; startup may still be completing immediately after `up -d`. Require a successful private readiness response before proceeding:

```bash
compose exec -T meridian-platform curl --fail --silent --show-error --max-time 10 http://127.0.0.1:9090/actuator/health/readiness
```

Keep ingress stopped on failure. Complete private acceptance first, then follow section 9 to start ingress only for the controlled public smoke window. Keep normal traffic closed until smoke succeeds; stop ingress again on failure.

Flyway runs before ordinary application availability; readinessState remains refusing until startup runners, including any bootstrap, finish. Schema migration failure prevents normal readiness. Git/image rollback never undoes persisted migrations. Do not alter historical migrations or apply `repair`, drop, undo, or manual financial SQL to force startup.

| Failure point | Guarded decision |
|---|---|
| Before migration/startup | Keep ingress stopped; inspect actual Flyway history and artifact/config. Correct configuration or redeploy previous image only after confirming schema is unchanged/compatible |
| During or after migration | Preserve evidence and the failed state; check history/checksums and actual schema privately. Do not assume transactional DDL means every release side effect rolled back |
| Application-only regression with proved schema compatibility | Set `MERIDIAN_BACKEND_IMAGE` to the retained previous exact ID, recreate backend, verify its release identity/readiness and preserved data, repeat smoke before traffic |
| Compatibility unknown, failed migration, or incompatible schema | Require reviewed coordinated recovery of matching DB + documents + keys + approved artifact; explicitly approve loss of post-snapshot writes |

Local rehearsal restores the V73 schema into a fresh database and starts the matching artifact without further schema change. It does not prove backward compatibility with another image or schema. Compatibility with future migrations is a release-specific review decision, never a blanket rollback guarantee.

## 8. Deliberate Full Demo Reset and Supported Reseed

Reset is manual, coordinated, and destructive. It removes all DB state (users, Customers, applications, accounts, transactions, audit/history, token/session state, Flyway history) and document bytes together. It retains private JWT/Customer/Loan keys, active/historical key IDs, Tunnel/SMTP configuration, approved artifacts, and scanner signatures. Do not hand-truncate selected financial tables or preserve orphaned files as an apparent valid reset.

Schedule downtime, take/verify a matched backup and its off-host copy, and explicitly approve the scope and data loss. If no recoverable backup exists, obtain a specific operator-approved data-loss decision before proceeding; this is not routine release rollback. Inspect the exact volume labels/names and all writers. The following deliberately requires an interactive affirmative phrase and never uses `down -v`:

```bash
node meridian-platform/deployment/recovery.mjs verify "$backup"
printf 'Type RESET-meridian-prod-ALL-DEMO-DATA: '
read -r confirmation
test "$confirmation" = RESET-meridian-prod-ALL-DEMO-DATA
compose stop cloudflared meridian-platform postgres
for volume in meridian-prod_postgres_data meridian-prod_document_data; do
  test "$(docker volume inspect "$volume" --format '{{index .Labels "com.docker.compose.project"}}')" = meridian-prod
  test -z "$(docker ps -q --filter volume="$volume")"
done
# Remove only the stopped application/database containers so the two named volumes can be removed.
compose rm -f meridian-platform postgres
docker volume rm meridian-prod_postgres_data meridian-prod_document_data
compose up -d postgres clamav
```

Recreate backend from the approved image with temporary first-admin bootstrap through MER-DEP-001, then disable it and remove bootstrap identity configuration. Flyway seeds fictional catalogs/configuration and retires historical V13 users through V73. Use normal `/admin/users` Staff invitations to create Loan Officer, Approver, and Accounting Officer reviewers with private inbox/password setup. Use normal Customer registration and email verification. Product/Partner baseline adjustments use their authorized administration contracts. No direct SQL provisioning, historical-user reactivation, shared published password, or scheduled deletion is permitted.

Verify old access/refresh/setup/reset tokens and passwords cannot authorize deleted accounts. Check fresh catalog, Staff role/capability separation, new Customer ownership, clean upload, readiness, and disabled bootstrap before ingress resumes. Local synthetic proof exercises coordinated DB/files reset, migrations, first-admin setup, and obsolete access/password/reset rejection. After a reset, verify operator reviewer invitations and real Customer inbox onboarding through live acceptance.

## 9. Deterministic Startup Smoke and Acceptance

Private `GET /actuator/health/readiness` checks completed startup/Flyway through `readinessState`, a small PostgreSQL query, and reversible document-storage stage/move/read/delete operations. It excludes scanner availability and disabled OCR. The public `/api/v1/health` route is liveness and cannot replace this dependency acceptance.

1. Compare running image ID/source label with the approved release record; inspect exact named mounts, UID/GID, dropped capabilities, no host-published ports, and backend/DB/ClamAV health. Preserve SG/UFW zero inbound rules.
2. Through SSM/Docker, run the private readiness command in section 7. Require HTTP 200 and only `{"status":"UP"}`. A required DB/storage failure returns 503 and `DOWN` without paths/errors. From a sibling container, port 9090 must refuse; the public listener must deny `/actuator/**` even for authenticated actors. OCR never gates readiness.
3. Once private checks pass, use `compose start cloudflared` to resume existing ingress for a controlled smoke window. On first installation, create it with `compose up -d --no-deps cloudflared` only after those same checks pass. Public `curl --fail --silent --show-error https://api.meridianfinance.dev/api/v1/health` must reach liveness. Verify external `/actuator/health/readiness` cannot expose private health.
4. With fictional profile/document data and owner-controlled inboxes, register Customer, require unverified-login rejection, receive/confirm verification, log in, and read own profile. Receive a Customer reset message and verify host/scheme privately. Check refresh/logout through the ordinary secure cookie contract.
5. If first installation/reset, follow the temporary bootstrap procedure, read Internal `/set-password` mail privately, set password, disable bootstrap/recreate, then log into Internal Web and perform an authorized administration read. Subsequent releases use an established Staff account. Customer portal must reject Staff login through its existing separation rules.
6. Upload a benign fictional document through an authenticated supported endpoint (for example Customer identity verification), require a scanned accepted version, and read back/check bytes with the same authorized actor. Repeat an old-document read after restart; confirm DB/keys/mount continuity.

On smoke failure, stop ingress, avoid further financial writes, and use section 7's migration-compatible rollback or coordinated recovery decision. Do not hide a failing dependency by weakening authentication, TLS, readiness, or scan rules. General readiness deliberately omits scanner availability so protected reads/servicing remain diagnosable; per-upload scanning remains mandatory and fail-closed. Scanner health plus clean upload are mandatory smoke conditions before uploads resume.

Authenticated deployed upload acceptance is a separate gate: require clean acceptance near and at 10 MiB (10,485,760 bytes), >10 MiB rejection, EICAR test-fixture rejection, scanner outage fail-closed, no authoritative/retrievable rejected object, exact successful replay while the scanner is down without another scan/promotion, and cleanup/reconciliation. Scanner disruption needs an explicitly approved controlled window and restored health afterward. Direct `clamdscan` proof does not satisfy that authenticated API gate.

## 10. Local Rehearsal and Operator Acceptance

Build a local test image and run `node meridian-platform/deployment/rehearse.mjs meridian-platform:cp4-local-proof`. The script creates only `cp4test-*`/`cp4restore-*` resources, random synthetic private keys, fake inboxes/Mailpit, and private PostgreSQL/ClamAV; it never publishes service ports or starts Tunnel. Success removes only its validated disposable Docker resources and retains the synthetic matched backup privately for inspection. Failure leaves named disposable resources for diagnosis. This proves local mechanics; it cannot prove Cloudflare, Resend, live volume ownership, external backup access, or deployed smoke.

`cp4test-*` and `cp4restore-*` are legacy helper namespaces enforced/generated by the rehearsal and recovery scripts. `meridian-platform:cp4-local-proof` is the retained local test-image tag supplied to the helper, not a production release identity. Preserve these command identifiers when using the documented rehearsal.

Real Resend domain/TLS/auth/inbox delivery, live key inventory/off-host restore, production image identity, old-volume cutover, bootstrap/login/cookies, and deployed smoke require operator evidence and independent review. Record observed results and unavailable evidence in the release/acceptance record. These portfolio recovery procedures do not define production-grade storage lifecycle, retention, legal hold, or key governance. Local rehearsal alone cannot establish deployed acceptance.
