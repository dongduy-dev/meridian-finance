# MER-DEP-003 — Portfolio Operations and Recovery

## 1. Operator Boundary

This runbook owns manual release, persistence, recovery, reset, and startup checks for the single-host fictional portfolio. [MER-DEP-002](MER-DEP-002-cloudflare-edge-and-aws-runtime.md) owns topology and provider settings; [MER-DEP-001](MER-DEP-001-identity-bootstrap-and-demo-boundary.md) owns onboarding and credential retirement. Production execution requires independent review and the operator's approval. Repository/local proof does not establish live provider delivery or deployed acceptance.

Use SSM on the existing host. The stopped EC2 instance must stay stopped until the operator schedules deployment. Commands below are operator procedures, not instructions to start or mutate AWS from a coding session. Never paste keys, passwords, inbox contents, reset/verification fragments, or resolved environments into shared evidence. Public demo submissions are private data even when visitors were instructed to use fictional data.

Set these non-secret shell values on the host; the private directory and files already exist under operator control:

```bash
set -eu
umask 077
cd /opt/meridian/source
private=/opt/meridian/private
compose() { docker compose --env-file "$private/runtime.env" -f meridian-platform/deployment/compose.yml "$@"; }
compose config --quiet
```

Use Node 22 for the deployment helpers. Their error messages omit command output that could contain private configuration. Inspect failures privately. Do not run local-development Compose on EC2 or combine it with deployment Compose.

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

The operator creates the Resend account/key, verifies `meridianfinance.dev`, and applies the provider-prescribed SPF/DKIM records through Cloudflare. Review DMARC policy and alignment without inventing DNS values. [Resend's SMTP contract](https://resend.com/docs/send-with-smtp) specifies `smtp.resend.com`, username `resend`, API key as SMTP password, and STARTTLS on 587. Meridian requires TLS upgrade, certificate/hostname validation, auth, and 5-second connection/read/write timeouts. There is no production Mailpit fallback.

Place the credential in `MERIDIAN_SMTP_PASSWORD` in the mode-0600 private environment. Keep its parent mode 0700. Do not use `docker compose config` without `--quiet` against real configuration. Production startup validates the required transport, sender, and frontend destinations; missing/empty credentials fail Compose, and invalid transport settings fail startup. The private credential is never printed by the helper scripts. Maintain ordinary INFO logging and do not enable JavaMail debug or publish provider errors.

Verify TLS from the host privately, without sending an API key in the command:

```bash
openssl s_client -starttls smtp -connect smtp.resend.com:587 \
  -servername smtp.resend.com -verify_hostname smtp.resend.com -verify_return_error </dev/null
```

Require a valid trust chain and hostname. A successful TLS handshake is not proof of SMTP authentication or inbox delivery.

Use three owner-controlled normal external inbox deliveries: Customer verification, Customer reset, and Staff setup. Record delivery time/outcome and link host/scheme only, without token-bearing URLs or message bodies. Customer verification is `https://meridianfinance.dev/verify-email#token=…`; Customer reset is `/reset-password#token=…`; Staff initial setup is `https://internal.meridianfinance.dev/set-password#token=…`. Later Staff reset uses Internal `/reset-password`. Customer digital access uses Customer `/activate-access#verificationToken=…&setupToken=…`.

Follow MER-DEP-001 for temporary first-admin bootstrap and disable it immediately afterward. The private Compose variables allow explicit enablement, email, and display name; no password bypass exists. If delivery fails after account/token commit, disable bootstrap and use supported Forgot password/resend after correcting transport. Do not retry bootstrap to replace an established administrator. Test expired/replaced/used tokens through their existing contracts; verification confirmation has its existing exact replay semantics, while password reset/setup remains single-use. Do not change those rules during acceptance.

## 4. Matched Backup Before Release or Reset

For the two-month portfolio, the operator takes a manual matched backup before each release, reset, or storage cutover; once weekly while actively receiving demo data; and after a demo session whose state should be retained. Verify an off-host encrypted copy after every backup. Retain weekly matched sets for the portfolio period and at least two last-known-good release sets. The operator owns this schedule; no automation or defined production recovery SLA exists.

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

The set contains `database.dump`, `documents.tgz`, `private.tgz`, `release.json`, and a checksum manifest. `release.json` records source, exact image ID, and last successful migration version. CP4 images must match their embedded source label. For the first pre-CP4 image without that label, source attribution is explicitly operator-confirmed; record the inspected running image ID and independently reviewed source association before proceeding. A supplied SHA alone is not artifact proof.

Retain exact current/previous image artifacts as well as their release records:

```bash
docker image save --output "$backup/backend-image.tar" REPLACE_WITH_EXACT_CURRENT_IMAGE_ID REPLACE_WITH_EXACT_PREVIOUS_IMAGE_ID
sha256sum "$backup/backend-image.tar" > "$backup/backend-image.tar.sha256"
chmod 600 "$backup/backend-image.tar" "$backup/backend-image.tar.sha256"
```

Use only the current ID when no previous image exists, and record that limitation. Encrypt the entire matched set plus artifact using an operator-controlled off-host recovery recipient, for example `tar -C "$backup" -czf - . | gpg --encrypt --recipient "$recovery_recipient" --output "$backup.gpg"`. Do not put a passphrase in arguments or shell history. Verify decryption/checksums on a separate trusted machine, transfer the encrypted file to operator-controlled off-host storage, and record access/recovery ownership. Local plaintext is sensitive and requires mode 0700 directories/0600 files and deliberate removal under the operator's retention policy. Do not mistake local archives for completed off-host backup.

## 5. Isolated Restore and Application Readback

Verify checksums before extraction. Restore first into a new `cp4restore-*` project with fresh volumes. The helper refuses production names and any existing restore volume; it publishes no port and initially joins no network. It restores the matching DB, document bytes/modes, and private files. Do not point it at live PostgreSQL or attach a production network. Backups are trusted operator artifacts, not arbitrary uploaded archives.

```bash
restore_project=cp4restore-REPLACE_WITH_NEW_LOWERCASE_NAME
node meridian-platform/deployment/recovery.mjs restore "$backup" "$restore_project"
```

The result is an isolated running PostgreSQL container and restored document volume/private directory. Confirm the recorded migration version. Create a dedicated isolated bridge only for the restored app and DB; disconnect the restored DB from `none` before connecting that bridge (`docker network disconnect none "$restore_project-postgres"`). Run the exact approved backed-up image with retained keys, bootstrap disabled, scanner/Mailpit confined to the isolated rehearsal, and the restored document mount. Override mail only in the rehearsal so it cannot deliver duplicate real messages. Keep external routing disabled. `rehearse.mjs` demonstrates this assembly end to end with synthetic data and generated keys; it does not replay real backup inboxes.

Require readiness, matching DB rows, an authorized document-content read and checksum comparison, and protected encrypted-data readback. Confirm retained historical key IDs against restored snapshot metadata without exposing values. Current local proof covers the active `local` key and Customer/JWT continuity; a live historical-key inventory remains operator evidence. A raw `pg_restore` exit status is insufficient. Record elapsed restore/readback time and any unavailable evidence.

For actual recovery, stop traffic first, review the isolated result, and obtain explicit approval for discarding writes since the snapshot. Preserve the failed current set separately. Restore the approved artifact/config/keys, matching DB and documents, and required ownership before startup/Flyway. If any matching backup, required key, or artifact is unavailable, remain in the no-traffic state; do not substitute new keys or mix unmatched snapshots. Recovery into the production volume names is a separately reviewed operator operation, not an option in the isolated restore helper.

## 6. Non-Root Document Volume Cutover

The image runs as UID/GID `10001:10001`; capabilities are dropped and privilege escalation is disabled. Fresh volumes receive the image's owned storage tree. Existing CP2/CP3 root-owned volumes require one explicit migration after a verified matched backup and before backend cutover. Preserve PostgreSQL, scanner signatures, document bytes, and cryptographic keys.

Validate the expected volume and its Compose labels, stop all writers, and mount only that document volume. This one-shot root helper has no network, a read-only root filesystem, no Docker socket, and only ownership/mode capabilities. It rejects symlinks, hard links, special files, and unexpected top-level paths. It changes directories to 0700, files to 0600, and owner/group to 10001; it never deletes/replaces content. Review the exact command and backup before execution:

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

For the initial CP3 rollback artifact only, a matched backup's `release.json` may explicitly record `sourceAttribution=operator-confirmed-pre-CP4` when that image has no revision label. The helper still compares its exact image ID and requires the reviewed source association; it permits that historical artifact's former root user only as a retained previous image. This exception cannot validate a newly built or running CP4 release. Actual previous-image redeployment still requires proved schema compatibility and operator approval.

The CP3 artifact predates private readiness and required SMTP TLS. Retaining it provides a recovery reference, not permission to reopen traffic without CP4 controls. If recovery selects that artifact, keep ingress stopped until a reviewed forward repair restores the required runtime controls and smoke succeeds. For a subsequent CP4 previous image, verify its own record and the current schema compatibility before normal traffic resumes.

```bash
reviewed_sha=REPLACE_WITH_REVIEWED_40_CHARACTER_SHA
record=/opt/meridian/releases/REPLACE_WITH_NEW_RECORD.json
previous=/opt/meridian/releases/REPLACE_WITH_PREVIOUS_APPROVED_RECORD.json
node meridian-platform/deployment/release.mjs build "$reviewed_sha" "$record" "$previous"
```

Omit the previous argument for the initial release only. Keep source SHAs distinct from image IDs/digests. Copy the record's exact `imageId` into private `MERIDIAN_BACKEND_IMAGE`; retain the previous artifact/record and off-host export. Do not retag or rebuild over the sole rollback reference. Compose must not rebuild implicitly.

After backup, cutover, configuration validation, and independent approval:

```bash
compose config --quiet
compose up -d --no-deps meridian-platform
backend=$(compose ps -q meridian-platform)
node meridian-platform/deployment/release.mjs verify "$record" "$backend"
compose exec -T meridian-platform curl --fail --silent --show-error --max-time 10 http://127.0.0.1:9090/actuator/health/readiness
# Only after smoke acceptance, resume the existing ingress.
compose start cloudflared
```

Flyway runs before ordinary application availability; readinessState remains refusing until startup runners, including any bootstrap, finish. Schema migration failure prevents normal readiness. Git/image rollback never undoes persisted migrations. Do not alter historical migrations or apply `repair`, drop, undo, or manual financial SQL to force startup.

| Failure point | Guarded decision |
|---|---|
| Before migration/startup | Keep ingress stopped; inspect actual Flyway history and artifact/config. Correct configuration or redeploy previous image only after confirming schema is unchanged/compatible |
| During or after migration | Preserve evidence and the failed state; check history/checksums and actual schema privately. Do not assume transactional DDL means every release side effect rolled back |
| Application-only regression with proved schema compatibility | Set `MERIDIAN_BACKEND_IMAGE` to the retained previous exact ID, recreate backend, verify its release identity/readiness and preserved data, repeat smoke before traffic |
| Compatibility unknown, failed migration, or incompatible schema | Require reviewed coordinated recovery of matching DB + documents + keys + approved artifact; explicitly approve loss of post-snapshot writes |

Local rehearsal restores V73 into a fresh database and starts the matching artifact without further schema change. No CP4 migration is introduced. Compatibility with future migrations is a release-specific review decision, never a blanket rollback guarantee.

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

Verify old access/refresh/setup/reset tokens and passwords cannot authorize deleted accounts. Check fresh catalog, Staff role/capability separation, new Customer ownership, clean upload, readiness, and disabled bootstrap before ingress resumes. Local synthetic proof exercises coordinated DB/files reset, migrations, first-admin setup, and obsolete access/password/reset rejection. Operator reviewer invitations and real Customer inbox onboarding remain live acceptance work.

## 9. Deterministic Startup Smoke and Acceptance

1. Compare running image ID/source label with the approved release record; inspect exact named mounts, UID/GID, dropped capabilities, no host-published ports, and backend/DB/ClamAV health. Preserve SG/UFW zero inbound rules.
2. Through SSM/Docker, run the private readiness command in section 7. Require HTTP 200 and only `{"status":"UP"}`. A required DB/storage failure returns 503 and `DOWN` without paths/errors. From a sibling container, port 9090 must refuse; the public listener must deny `/actuator/**` even for authenticated actors. OCR never gates readiness.
3. Once private checks pass, resume ingress for a controlled smoke window. Public `curl --fail --silent --show-error https://api.meridianfinance.dev/api/v1/health` must reach liveness. Verify external `/actuator/health/readiness` cannot expose private health.
4. With fictional profile/document data and owner-controlled inboxes, register Customer, require unverified-login rejection, receive/confirm verification, log in, and read own profile. Receive a Customer reset message and verify host/scheme privately. Check refresh/logout through the ordinary secure cookie contract.
5. If first installation/reset, follow the temporary bootstrap procedure, read Internal `/set-password` mail privately, set password, disable bootstrap/recreate, then log into Internal Web and perform an authorized administration read. Subsequent releases use an established Staff account. Customer portal must reject Staff login through its existing separation rules.
6. Upload a benign fictional document through an authenticated supported endpoint (for example Customer identity verification), require a scanned accepted version, and read back/check bytes with the same authorized actor. Repeat an old-document read after restart; confirm DB/keys/mount continuity.

On smoke failure, stop ingress, avoid further financial writes, and use section 7's migration-compatible rollback or coordinated recovery decision. Do not hide a failing dependency by weakening authentication, TLS, readiness, or scan rules. General readiness deliberately omits scanner availability so protected reads/servicing remain diagnosable; per-upload CP3 scanning remains mandatory and fail-closed. Scanner health plus clean upload are mandatory smoke conditions before uploads resume.

Keep CP3 authenticated deployed upload acceptance as a separate gate: near/exact 10 MiB, >10 MiB rejection, benign EICAR-bearing fixture rejection, scanner outage fail-closed, no authoritative/retrievable rejected object, exact successful replay while scanner is down, and cleanup/reconciliation. Scanner disruption needs an approved controlled window and restored health afterward. Direct clamdscan proof does not satisfy that API gate.

## 10. Local Rehearsal and Outstanding Evidence

Build a local test image and run `node meridian-platform/deployment/rehearse.mjs meridian-platform:cp4-local-proof`. The script creates only `cp4test-*`/`cp4restore-*` resources, random synthetic private keys, fake inboxes/Mailpit, and private PostgreSQL/ClamAV; it never publishes service ports or starts Tunnel. Success removes only its validated disposable Docker resources and retains the synthetic matched backup privately for inspection. Failure leaves named disposable resources for diagnosis. This proves local mechanics; it cannot prove Cloudflare, Resend, live volume ownership, external backup access, or deployed smoke.

Real Resend domain/TLS/auth/inbox delivery, live key inventory/off-host restore, production image identity, old-volume cutover, bootstrap/login/cookies, and deployed smoke remain operator-only acceptance. `MER-FU-032` stays Open for broader production storage lifecycle, retention/legal hold, and recovery hardening. CP4 must not be called PASS until independent review and provider/deployed evidence are complete.
