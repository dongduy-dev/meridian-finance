// Disposable CP4 runtime proof. Never uses production projects, accounts, volumes, or providers.
// Node 22 + local Docker. Run: node deployment/rehearse.mjs IMAGE_ID_OR_LOCAL_TEST_TAG
import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { generateKeyPairSync, randomBytes, randomUUID } from 'node:crypto'
import { mkdtempSync, mkdirSync, writeFileSync, readFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url)), project = `cp4test-${Date.now()}`
const restored = project.replace('cp4test-', 'cp4restore-')
const temp = mkdtempSync(join(tmpdir(), 'meridian-cp4-')), privateDir = join(temp, 'private')
mkdirSync(privateDir, { mode: 0o700 })
const image = process.argv[2]
assert.ok(image && (image.startsWith('sha256:') || image.startsWith('meridian-platform:')), 'Use a local test image')
const resources = [], volumes = [], networks = []
function command(exe, args, input, allowFailure = false) {
  const r = spawnSync(exe, args, { encoding: 'utf8', input, timeout: 300000, maxBuffer: 16 * 1024 * 1024 })
  if (!allowFailure) assert.equal(r.status, 0, `${exe} operation failed; inspect disposable ${project} privately`)
  return r
}
const docker = (args, input) => command('docker', args, input).stdout.trim()
const inspect = name => JSON.parse(docker(['inspect', name]))[0]
const delay = ms => new Promise(r => setTimeout(r, ms))
const envFile = join(privateDir, 'runtime.env')
const pair = generateKeyPairSync('rsa', { modulusLength: 2048,
  privateKeyEncoding: { type: 'pkcs8', format: 'der' }, publicKeyEncoding: { type: 'spki', format: 'der' } })
const key = () => randomBytes(32).toString('base64')
const config = {
  POSTGRES_PASSWORD: key(), MERIDIAN_CUSTOMER_ENCRYPTION_KEY: key(), MERIDIAN_CUSTOMER_FINGERPRINT_KEY: key(),
  MERIDIAN_LOAN_DISBURSEMENT_SNAPSHOT_KEYS_LOCAL: key(), MERIDIAN_LOAN_DISBURSEMENT_SNAPSHOT_ACTIVE_KEY_ID: 'local',
  MERIDIAN_JWT_PRIVATE_KEY: pair.privateKey.toString('base64'), MERIDIAN_JWT_PUBLIC_KEY: pair.publicKey.toString('base64'),
  MERIDIAN_DOCUMENT_STORAGE_ROOT: '/var/lib/meridian/documents', SPRING_PROFILES_ACTIVE: 'deployment',
  MERIDIAN_ADMIN_BOOTSTRAP_EMAIL: 'operator@example.invalid', MERIDIAN_ADMIN_BOOTSTRAP_DISPLAY_NAME: 'Fictional Operator',
  MERIDIAN_SMTP_PASSWORD: 'synthetic-only',
  // Disposable proof overrides only the mail transport; never send to Resend.
  SPRING_APPLICATION_JSON: JSON.stringify({ meridian: { notification: { smtp: { production: false,
    host: `${project}-mail`, port: 1025, username: '', 'authentication-enabled': false,
    'start-tls-enabled': false, 'start-tls-required': false } } },
    spring: { datasource: { hikari: { 'connection-timeout': 2000 } } } }),
}
writeFileSync(envFile, Object.entries(config).map(([k,v]) => `${k}=${v}`).join('\n'), { mode: 0o600 })
writeFileSync(join(privateDir, 'tunnel-token'), 'SYNTHETIC_NOT_A_TUNNEL_TOKEN', { mode: 0o600 })
const payload = '%PDF-1.4\nFictional CP4 evidence only.\n%%EOF\n'
writeFileSync(join(privateDir, 'fixture.pdf'), payload)
const password = randomBytes(24).toString('base64url')
const rawTokens = []
const customer = 'customer@example.invalid', operator = config.MERIDIAN_ADMIN_BOOTSTRAP_EMAIL
function volume(name) { docker(['volume', 'create', '--label', `com.docker.compose.project=${project}`, name]); volumes.push(name) }
function run(name, args) { resources.push(name); return docker(['run', '-d', '--name', name,
  '--label', `com.docker.compose.project=${project}`, ...args]) }
async function waitFor(check, description, seconds = 120) {
  for (let i = 0; i < seconds; i++) { if (check()) return; await delay(1000) }
  throw new Error(`Timed out: ${description}; inspect ${project} privately`)
}
let backend = `${project}-backend`
function startBackend(docVolume, dbHost, bootstrap = false, file = envFile) {
  run(backend, ['--network', project, '--env-file', file,
    '--label', 'com.docker.compose.service=meridian-platform', '--cap-drop', 'ALL', '--security-opt', 'no-new-privileges',
    '-e', `SPRING_DATASOURCE_URL=jdbc:postgresql://${dbHost}:5432/meridian_db`,
    '-e', 'SPRING_DATASOURCE_USERNAME=meridian_user', '-e', `SPRING_DATASOURCE_PASSWORD=${config.POSTGRES_PASSWORD}`,
    '-e', `MERIDIAN_DOCUMENT_SCANNER_HOST=${project}-scanner`,
    '-e', `MERIDIAN_ADMIN_BOOTSTRAP_ENABLED=${bootstrap}`, '-e', 'JAVA_TOOL_OPTIONS=-Xmx768m',
    '--mount', `type=volume,source=${docVolume},target=/var/lib/meridian/documents`,
    '--mount', `type=bind,source=${privateDir},target=/fixtures,readonly`, image])
}
function http(path, method = 'GET', body, token, port = 8080, form = false) {
  if (form) {
    // curl config arrives on stdin so passwords/tokens never appear in process arguments.
    const text = `url = "http://127.0.0.1:8080${path}"\nheader = "Authorization: Bearer ${token}"\nform = "uploadRequestId=${randomUUID()}"\n${typeof form === 'string' ? `form = "expectedCurrentVersionId=${form}"\n` : ''}form = "file=@/fixtures/fixture.pdf;type=application/pdf"\nwrite-out = "\\n%{http_code}"\nsilent\nshow-error\n`
    const r = docker(['exec', '-i', backend, 'curl', '--config', '-'], text)
    const lines = r.split('\n'), status = Number(lines.pop()), data = lines.join('\n')
    return { status, data }
  }
  if (token && body) return httpConfig(path, method, JSON.stringify(body), token, port)
  const r = command('docker', ['exec', '-i', backend, 'curl', '-sS', '--max-time', '10',
    '--request', method, '-H', 'Content-Type: application/json', ...(token ? ['--config', '-'] : []),
    ...(body ? ['--data-binary', '@-'] : []), '-w', '\n%{http_code}', `http://127.0.0.1:${port}${path}`],
    token ? `header = "Authorization: Bearer ${token}"\n` : body ? JSON.stringify(body) : '', true)
  if (r.status !== 0) return { status: 0, data: '' }
  const lines = r.stdout.trim().split('\n'), status = Number(lines.pop())
  return { status, data: lines.join('\n') }
}
function httpConfig(path, method, content, token, port) {
  const configText = `url = "http://127.0.0.1:${port}${path}"\nrequest = "${method}"\nheader = "Authorization: Bearer ${token}"\nheader = "Content-Type: application/json"\ndata = ${JSON.stringify(content)}\nwrite-out = "\\n%{http_code}"\nsilent\nshow-error\n`
  const r = docker(['exec', '-i', backend, 'curl', '--config', '-'], configText)
  const lines = r.split('\n'), status = Number(lines.pop()); return { status, data: lines.join('\n') }
}
const ready = () => http('/actuator/health/readiness', 'GET', undefined, undefined, 9090)
function json(response, status) { assert.ok(response.status === status, `Expected HTTP ${status}, observed ${response.status}`); return response.data ? JSON.parse(response.data) : null }
function mailToken(fragment, recipient) {
  const messages = JSON.parse(docker(['exec', backend, 'curl', '-sS', `http://${project}-mail:8025/api/v1/messages`])).messages
  for (const message of messages) {
    if (!message.To?.some(to => to.Address === recipient)) continue
    const mail = JSON.parse(docker(['exec', backend, 'curl', '-sS', `http://${project}-mail:8025/api/v1/message/${message.ID}`]))
    const match = mail.Text?.match(new RegExp(fragment + '#token=([^\\s]+)'))
    if (match) { const token = decodeURIComponent(match[1]); rawTokens.push(token); return token }
  }
  throw new Error('Expected synthetic email missing')
}
let successful = false
try {
  docker(['network', 'create', project]); networks.push(project)
  volume(`${project}_postgres_data`); volume(`${project}_document_data`); volume(`${project}_fresh`); volume(`${project}_signatures`)
  run(`${project}-postgres`, ['--network', project, '--label', 'com.docker.compose.service=postgres', '--env-file', envFile,
    '-e', 'POSTGRES_USER=meridian_user', '-e', 'POSTGRES_DB=meridian_db', '--mount', `type=volume,source=${project}_postgres_data,target=/var/lib/postgresql/data`, 'postgres:16'])
  run(`${project}-mail`, ['--network', project, 'axllent/mailpit:v1.31.0'])
  run(`${project}-scanner`, ['--network', project, '-e', 'CLAMD_CONF_StreamMaxLength=10M', '-e', 'CLAMD_CONF_AlertExceedsMax=yes',
    '--mount', `type=volume,source=${project}_signatures,target=/var/lib/clamav`, 'clamav/clamav:1.4.6'])
  await waitFor(() => command('docker', ['exec', `${project}-scanner`, 'clamdscan', '--stream', '--no-summary', '/etc/hostname'], undefined, true).status === 0, 'scanner signatures', 360)
  startBackend(`${project}_fresh`, `${project}-postgres`, true)
  assert.notEqual(ready().status, 200, 'Startup must not claim readiness early')
  await waitFor(() => ready().status === 200, 'fresh-volume application readiness')
  assert.equal(docker(['exec', backend, 'id', '-u']), '10001')
  assert.equal(docker(['exec', backend, 'id', '-g']), '10001')
  docker(['exec', backend, 'sh', '-eu', '-c', 'probe=$(mktemp); test -w "$probe"; rm "$probe"'])
  assert.equal(ready().data, '{"status":"UP"}')
  assert.equal(http('/api/v1/health').status, 200)
  assert.notEqual(http('/actuator/health/readiness').status, 200)
  const bridgeProbe = command('docker', ['run', '--rm', '--network', project, '--entrypoint', 'curl', image,
    '--max-time', '2', '-sS', `http://${backend}:9090/actuator/health/readiness`], undefined, true)
  assert.notEqual(bridgeProbe.status, 0, 'Management listener must not accept bridge/Tunnel connections')
  console.log('PASS: fresh-volume non-root startup, private readiness, public liveness, bridge isolation')
  json(http('/api/v1/auth/password-reset/confirm', 'POST', { token: mailToken('/set-password', operator), newPassword: password }), 204)
  let staff = json(http('/api/v1/auth/login', 'POST', { email: operator, password }), 200)
  json(http('/api/v1/admin/internal-users', 'GET', undefined, staff.accessToken), 200)
  // Recreate with bootstrap disabled and the fresh volume intact.
  docker(['rm', '-f', backend]); startBackend(`${project}_fresh`, `${project}-postgres`)
  await waitFor(() => ready().status === 200, 'bootstrap-disabled restart')
  json(http('/api/v1/auth/register', 'POST', { email: customer, password, displayName: 'Fictional Customer' }), 201)
  assert.equal(http('/api/v1/auth/login', 'POST', { email: customer, password }).status, 401)
  json(http('/api/v1/auth/email-verification/confirm', 'POST', { token: mailToken('/verify-email', customer) }), 204)
  let login = json(http('/api/v1/auth/login', 'POST', { email: customer, password }), 200)
  json(http('/api/v1/customers/me/profile', 'PUT', { fullName: 'Fictional Customer', identityReference: 'CP4-FICTIONAL-IDENTITY',
    phoneNumber: '0000000000', residentialAddress: 'Fictional Address', employmentStatus: 'SALARIED', employerName: 'Fictional Employer',
    termsConsentAccepted: true, dataProcessingConsentAccepted: true }, login.accessToken), 200)
  json(http('/api/v1/customers/me/bank-accounts', 'POST', { bankCode: 'DEMO', bankNameSnapshot: 'Fictional Bank',
    accountHolderName: 'Fictional Customer', accountNumber: '0000111122223333' }, login.accessToken), 201)
  const document = json(http('/api/v1/customers/me/identity-verifications', 'POST', undefined, login.accessToken, 8080, true), 201)
  const contentPath = `/api/v1/customers/me/identity-verifications/${document.verificationId}/content`
  assert.equal(http(contentPath, 'GET', undefined, login.accessToken).data.trim(), payload.trim())
  json(http('/api/v1/auth/password-reset/request', 'POST', { email: customer }), 202)
  assert.ok(mailToken('/reset-password', customer))
  console.log('PASS: synthetic mail, Customer verification/login, Staff setup/login, protected reads, scanned upload/readback')
  // Root-owned preserved volume upgrade: copy fictional stored data before cutover.
  docker(['stop', backend])
  docker(['run', '--rm', '--network', 'none', '--mount', `type=volume,source=${project}_fresh,target=/from,readonly`,
    '--mount', `type=volume,source=${project}_document_data,target=/to`, 'postgres:16', 'sh', '-eu', '-c',
    'cp -a /from/. /to/; chown -R 0:0 /to; chmod 700 /to /to/staging /to/objects'])
  const cutover = () => docker(['run', '--rm', '--network', 'none', '--read-only', '--user', '0:0', '--cap-drop', 'ALL',
    '--cap-add', 'CHOWN', '--cap-add', 'FOWNER', '--cap-add', 'DAC_OVERRIDE', '--security-opt', 'no-new-privileges',
    '--mount', `type=volume,source=${project}_document_data,target=/var/lib/meridian/documents`,
    '--mount', `type=bind,source=${here},target=/procedure,readonly`, '--entrypoint', 'sh', image,
    '/procedure/document-volume-cutover.sh', 'CONFIRM-DOCUMENT-OWNERSHIP-10001'])
  cutover()
  // Guard proof uses a separate disposable volume; no unsafe tree is ever migrated.
  const badVolume = `${project}_unsafe`; volume(badVolume)
  docker(['run', '--rm', '--network', 'none', '--mount', `type=volume,source=${badVolume},target=/bad`,
    'postgres:16', 'sh', '-eu', '-c', 'mkdir -p /bad/staging /bad/objects; ln -s /etc/hostname /bad/objects/unsafe'])
  const refused = command('docker', ['run', '--rm', '--network', 'none', '--read-only', '--user', '0:0',
    '--mount', `type=volume,source=${badVolume},target=/var/lib/meridian/documents`,
    '--mount', `type=bind,source=${here},target=/procedure,readonly`, '--entrypoint', 'sh', image,
    '/procedure/document-volume-cutover.sh', 'CONFIRM-DOCUMENT-OWNERSHIP-10001'], undefined, true)
  assert.notEqual(refused.status, 0, 'Symlinked storage must be refused before ownership migration')
  docker(['rm', backend]); startBackend(`${project}_document_data`, `${project}-postgres`)
  await waitFor(() => ready().status === 200, 'old-volume cutover readiness')
  assert.equal(http(contentPath, 'GET', undefined, login.accessToken).data.trim(), payload.trim())
  assert.equal(docker(['exec', backend, 'stat', '-c', '%u:%g:%a', '/var/lib/meridian/documents']), '10001:10001:700')
  const replacement = json(http('/api/v1/customers/me/identity-verifications', 'POST', undefined,
    login.accessToken, 8080, document.evidence.versionId), 201)
  assert.equal(http(`/api/v1/customers/me/identity-verifications/${replacement.verificationId}/content`,
    'GET', undefined, login.accessToken).data.trim(), payload.trim())
  docker(['restart', backend]); await waitFor(() => ready().status === 200, 'restart persistence')
  assert.equal(http(contentPath, 'GET', undefined, login.accessToken).data.trim(), payload.trim())
  // Actual non-root permission failure and DB outage leave liveness independently usable.
  docker(['exec', backend, 'chmod', '500', '/var/lib/meridian/documents/objects'])
  assert.equal(ready().status, 503); assert.equal(ready().data, '{"status":"DOWN"}'); assert.equal(http('/api/v1/health').status, 200)
  docker(['exec', backend, 'chmod', '700', '/var/lib/meridian/documents/objects'])
  docker(['stop', `${project}-postgres`]); assert.equal(ready().status, 503); assert.equal(http('/api/v1/health').status, 200)
  docker(['start', `${project}-postgres`]); await waitFor(() => ready().status === 200, 'dependency recovery')
  console.log('PASS: preserved root-owned volume cutover/read/restart; DB and unwritable-storage NOT READY; liveness independent')
  const logs = docker(['logs', backend])
  assert.ok(logs.length > 0, 'Stdout logging must work')
  for (const secret of [...rawTokens, password, config.MERIDIAN_JWT_PRIVATE_KEY, config.MERIDIAN_CUSTOMER_ENCRYPTION_KEY,
    config.MERIDIAN_CUSTOMER_FINGERPRINT_KEY, config.MERIDIAN_LOAN_DISBURSEMENT_SNAPSHOT_KEYS_LOCAL]) {
    assert.ok(!logs.includes(secret), 'Private synthetic authority must not enter backend logs')
  }
  const backup = join(temp, 'matched-backup')
  const source = inspect(backend).Config.Labels?.['org.opencontainers.image.revision']
    ?? docker(['image', 'inspect', image, '--format', '{{index .Config.Labels "org.opencontainers.image.revision"}}'])
  const sourceSha = /^[a-f0-9]{40}$/.test(source) ? source : command('git', ['rev-parse', 'HEAD']).stdout.trim()
  command(process.execPath, [join(here, 'recovery.mjs'), 'backup', project, backend, `${project}-postgres`, privateDir, backup, sourceSha, `BACKUP-${project}`])
  const started = Date.now()
  command(process.execPath, [join(here, 'recovery.mjs'), 'restore', backup, restored])
  assert.notEqual(command(process.execPath, [join(here, 'recovery.mjs'), 'restore', backup, 'meridian-prod'],
    undefined, true).status, 0, 'Restore must refuse a production target')
  assert.notEqual(command(process.execPath, [join(here, 'recovery.mjs'), 'restore', backup, restored],
    undefined, true).status, 0, 'Restore must refuse preexisting volumes')
  volumes.push(`${restored}_postgres_data`, `${restored}_document_data`); resources.push(`${restored}-postgres`)
  docker(['network', 'disconnect', 'none', `${restored}-postgres`])
  docker(['network', 'connect', project, `${restored}-postgres`])
  backend = `${project}-restored-backend`
  startBackend(`${restored}_document_data`, `${restored}-postgres`, false, join(backup, `${restored}-private/runtime.env`))
  await waitFor(() => ready().status === 200, 'restored application/Flyway readiness')
  // Original signed access token is usable only because restored keys and durable User state match.
  assert.equal(http(contentPath, 'GET', undefined, login.accessToken).data.trim(), payload.trim())
  const banks = json(http('/api/v1/customers/me/bank-accounts', 'GET', undefined, login.accessToken), 200)
  assert.ok(banks.length === 1 && banks[0].accountNumberLastFour === '3333', 'Restored encrypted bank data must read correctly')
  console.log(`PASS: matched checksums, isolated PostgreSQL/files/private-key restore, app/Flyway/document/encrypted readback (${((Date.now()-started)/1000).toFixed(1)}s)`)
  // Coordinated disposable reset: new DB and document volume, retained keys, normal onboarding.
  docker(['rm', '-f', backend]); docker(['rm', '-f', `${restored}-postgres`])
  docker(['volume', 'rm', `${restored}_postgres_data`, `${restored}_document_data`])
  volume(`${restored}_postgres_data`); volume(`${restored}_document_data`)
  run(`${restored}-postgres`, ['--network', project, '--env-file', envFile, '-e', 'POSTGRES_USER=meridian_user', '-e', 'POSTGRES_DB=meridian_db',
    '--mount', `type=volume,source=${restored}_postgres_data,target=/var/lib/postgresql/data`, 'postgres:16'])
  backend = `${project}-reset-backend`; startBackend(`${restored}_document_data`, `${restored}-postgres`, true)
  await waitFor(() => ready().status === 200, 'reset migration/bootstrap')
  assert.equal(http('/api/v1/customers/me', 'GET', undefined, login.accessToken).status, 401)
  assert.equal(http('/api/v1/auth/login', 'POST', { email: customer, password }).status, 401)
  assert.equal(http('/api/v1/auth/password-reset/confirm', 'POST', { token: mailToken('/reset-password', customer), newPassword: password }).status, 401)
  json(http('/api/v1/auth/password-reset/confirm', 'POST', { token: mailToken('/set-password', operator), newPassword: password }), 204)
  staff = json(http('/api/v1/auth/login', 'POST', { email: operator, password }), 200)
  json(http('/api/v1/admin/internal-users', 'GET', undefined, staff.accessToken), 200)
  console.log('PASS: coordinated isolated reset, retained keys, V73 baseline, first-admin setup; obsolete access/password/reset authority rejected')
  successful = true
} finally {
  if (successful) {
    for (const name of [...new Set(resources)].reverse()) {
      const result = command('docker', ['inspect', name], undefined, true)
      if (result.status === 0) {
        const item = JSON.parse(result.stdout)[0]
        assert.ok([project, restored].includes(item.Config.Labels['com.docker.compose.project']))
        docker(['rm', '-f', name])
      }
    }
    for (const name of new Set(volumes)) {
      assert.ok(name.startsWith(project) || name.startsWith(restored)); docker(['volume', 'rm', name])
    }
    for (const name of networks) docker(['network', 'rm', name])
    console.log('PASS: disposable Docker resources removed; synthetic matched backup retained privately for inspection')
  } else console.log(`FAIL: disposable resources retained for diagnosis: ${project}; private synthetic directory ${temp}`)
}
