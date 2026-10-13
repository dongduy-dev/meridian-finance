// Run with Node 22 and Docker Compose. Uses placeholders only; starts no containers.
import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { existsSync, mkdtempSync, readFileSync, readdirSync, rmSync, rmdirSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
const root = resolve(here, '../..')
const temporary = mkdtempSync(join(tmpdir(), 'meridian-cp2-contract-'))
const emptyEnv = join(temporary, 'empty.env')
const token = join(temporary, 'tunnel-token')
writeFileSync(emptyEnv, '')
writeFileSync(token, 'PLACEHOLDER_NOT_A_TUNNEL_TOKEN')
const required = {
  POSTGRES_PASSWORD: 'PLACEHOLDER_NOT_A_PASSWORD',
  MERIDIAN_CUSTOMER_ENCRYPTION_KEY: 'PLACEHOLDER_NOT_AN_ENCRYPTION_KEY',
  MERIDIAN_CUSTOMER_FINGERPRINT_KEY: 'PLACEHOLDER_NOT_A_FINGERPRINT_KEY',
  MERIDIAN_LOAN_DISBURSEMENT_SNAPSHOT_KEYS_LOCAL: 'PLACEHOLDER_NOT_A_SNAPSHOT_KEY',
  MERIDIAN_JWT_PRIVATE_KEY: 'PLACEHOLDER_NOT_A_PRIVATE_KEY',
  MERIDIAN_JWT_PUBLIC_KEY: 'PLACEHOLDER_NOT_A_PUBLIC_KEY',
  MERIDIAN_TUNNEL_TOKEN_FILE: token,
  MERIDIAN_SMTP_PASSWORD: 'PLACEHOLDER_NOT_A_SMTP_CREDENTIAL',
  MERIDIAN_BACKEND_IMAGE: `sha256:${'a'.repeat(64)}`,
}
const baseEnv = Object.fromEntries(Object.entries(process.env).filter(([key]) =>
  !/^(MERIDIAN_|POSTGRES_|SPRING_|COMPOSE_)/i.test(key)))
const compose = (values) => spawnSync('docker', [
  'compose', '--env-file', emptyEnv, '-f', join(here, 'compose.yml'), 'config', '--format', 'json',
], { env: { ...baseEnv, ...values }, encoding: 'utf8' })

try {
  const rendered = compose(required)
  assert.equal(rendered.status, 0, rendered.error?.message ?? rendered.stderr)
  const config = JSON.parse(rendered.stdout)
  assert.equal(config.name, 'meridian-prod')
  assert.deepEqual(Object.keys(config.services).sort(), ['clamav', 'cloudflared', 'meridian-platform', 'postgres'])
  for (const service of Object.values(config.services)) {
    assert.ok(!service.ports?.length, 'No service may publish a host port')
    assert.ok(!service.network_mode && !service.privileged, 'No host network or privileged bypass')
    assert.equal(service.restart, 'unless-stopped')
  }
  assert.equal(config.networks.database.internal, true)
  assert.ok(!config.networks.tunnel.internal, 'Backend and Tunnel need outbound access')
  assert.deepEqual(Object.keys(config.services.postgres.networks), ['database'])
  assert.deepEqual(Object.keys(config.services.cloudflared.networks), ['tunnel'])
  assert.deepEqual(Object.keys(config.services['meridian-platform'].networks).sort(), ['database', 'scanning', 'tunnel'])
  assert.equal(config.services.postgres.volumes[0].type, 'volume')
  assert.equal(config.services.postgres.volumes[0].target, '/var/lib/postgresql/data')
  assert.equal(config.services['meridian-platform'].volumes[0].type, 'volume')
  assert.equal(config.services['meridian-platform'].volumes[0].target, '/var/lib/meridian/documents')
  assert.deepEqual(Object.keys(config.volumes).sort(), ['clamav_signatures', 'document_data', 'postgres_data'])
  assert.equal(config.networks.scanning.internal, true)
  assert.ok(!config.networks.signature_updates.internal)
  assert.deepEqual(Object.keys(config.services.clamav.networks).sort(), ['scanning', 'signature_updates'])
  assert.equal(config.services.clamav.image, 'clamav/clamav:1.4.6')
  assert.equal(config.services.clamav.volumes[0].target, '/var/lib/clamav')
  assert.equal(config.services.clamav.environment.CLAMAV_NO_FRESHCLAMD, 'false')
  assert.equal(config.services.clamav.environment.CLAMD_CONF_AlertExceedsMax, 'yes')
  assert.ok(config.services.clamav.healthcheck.test.join(' ').includes('clamdscan --stream'))
  assert.equal(config.services['meridian-platform'].depends_on.clamav.condition, 'service_healthy')
  const env = config.services['meridian-platform'].environment
  const backend = config.services['meridian-platform']
  assert.equal(backend.image, required.MERIDIAN_BACKEND_IMAGE)
  assert.ok(!backend.build, 'Production must deploy an inspected image, not rebuild implicitly')
  assert.equal(backend.user, '10001:10001')
  assert.deepEqual(backend.cap_drop, ['ALL'])
  assert.ok(backend.security_opt.includes('no-new-privileges:true'))
  assert.ok(backend.healthcheck.test.includes('http://127.0.0.1:9090/actuator/health/readiness'))
  assert.equal(config.services.cloudflared.depends_on['meridian-platform'].condition, 'service_healthy')
  assert.equal(env.SPRING_PROFILES_ACTIVE, 'deployment')
  assert.equal(env.MERIDIAN_SMTP_HOST, 'smtp.resend.com')
  assert.equal(env.MERIDIAN_SMTP_PORT, '587')
  assert.equal(env.MERIDIAN_SMTP_USERNAME, 'resend')
  assert.equal(env.MERIDIAN_SMTP_PASSWORD, required.MERIDIAN_SMTP_PASSWORD)
  assert.equal(env.MERIDIAN_SMTP_AUTH, 'true')
  assert.equal(env.MERIDIAN_SMTP_STARTTLS, 'true')
  assert.equal(env.MERIDIAN_SMTP_STARTTLS_REQUIRED, 'true')
  const properties = readFileSync(join(root, 'meridian-platform/src/main/resources/application-deployment.properties'), 'utf8')
  assert.ok(properties.includes('management.server.address=127.0.0.1'))
  assert.ok(properties.includes('management.server.port=9090'))
  assert.ok(properties.includes('readinessState,database,documentStorage'))
  assert.equal(env.MERIDIAN_FRONTEND_ALLOWED_ORIGINS, 'https://meridianfinance.dev,https://internal.meridianfinance.dev')
  assert.equal(env.MERIDIAN_FRONTEND_BASE_URL, 'https://meridianfinance.dev')
  assert.equal(env.MERIDIAN_INTERNAL_FRONTEND_BASE_URL, 'https://internal.meridianfinance.dev')
  assert.equal(env.MERIDIAN_REFRESH_TOKEN_COOKIE_SECURE, 'true')
  assert.equal(env.MERIDIAN_AUTH_CLIENT_ADDRESS_MODE, 'CLOUDFLARE')
  assert.equal(env.SERVER_FORWARD_HEADERS_STRATEGY, 'none')
  assert.equal(env.MERIDIAN_DOCUMENT_SCANNER_HOST, 'clamav')
  assert.equal(env.MERIDIAN_DOCUMENT_SCANNER_PORT, '3310')
  assert.equal(env.MERIDIAN_DOCUMENT_SCANNER_OPERATION_TIMEOUT_MS, '15000')
  assert.equal(env.MERIDIAN_OCR_RESULT_ENCRYPTION_KEY, '')
  assert.equal(env.MERIDIAN_ADMIN_BOOTSTRAP_ENABLED, 'false')
  const bootstrap = JSON.parse(compose({ ...required, MERIDIAN_ADMIN_BOOTSTRAP_ENABLED: 'true',
    MERIDIAN_ADMIN_BOOTSTRAP_EMAIL: 'operator@example.invalid',
    MERIDIAN_ADMIN_BOOTSTRAP_DISPLAY_NAME: 'Fictional Operator' }).stdout)
  assert.equal(bootstrap.services['meridian-platform'].environment.MERIDIAN_ADMIN_BOOTSTRAP_ENABLED, 'true')
  assert.ok(config.services.cloudflared.command.includes('--token-file'))
  assert.ok(!config.services.cloudflared.environment?.TUNNEL_TOKEN)
  for (const key of Object.keys(required)) {
    const missing = { ...required, [key]: '' }
    assert.notEqual(compose(missing).status, 0, `Missing ${key} must fail Compose validation`)
  }
  console.log('PASS: runtime services, isolation, persistence, production settings, and required configuration')

  if (process.argv.includes('--frontends')) {
    for (const app of ['customer-web', 'internal-web']) {
      const dist = join(root, app, 'dist')
      assert.ok(existsSync(join(dist, 'index.html')), `${app}: build first`)
      assert.ok(!existsSync(join(dist, '404.html')), `${app}: preserve Pages SPA fallback`)
      const headers = readFileSync(join(dist, '_headers'), 'utf8')
      assert.equal(headers, readFileSync(join(root, app, 'public/_headers'), 'utf8'))
      for (const name of ['Content-Security-Policy', 'X-Frame-Options', 'X-Content-Type-Options', 'Referrer-Policy', 'Permissions-Policy']) {
        assert.ok(headers.includes(`${name}:`), `${app}: missing ${name}`)
      }
      const assets = readdirSync(join(dist, 'assets')).filter(name => name.endsWith('.js'))
      const scripts = assets.map(name => readFileSync(join(dist, 'assets', name), 'utf8')).join('\n')
      assert.ok(scripts.includes('https://api.meridianfinance.dev/api/v1'), `${app}: wrong build-time API target`)
      assert.ok(!/https?:\/\/localhost:\d+/.test(scripts), `${app}: localhost in production bundle`)
      assert.ok(!/MERIDIAN_(JWT_PRIVATE_KEY|CUSTOMER_ENCRYPTION_KEY)|TUNNEL_TOKEN|BEGIN (RSA )?PRIVATE KEY|PLACEHOLDER_NOT_/.test(scripts))
      console.log(`PASS: ${app} production API target and static headers; no tested secret markers`)
    }
  }
} finally {
  rmSync(emptyEnv, { force: true })
  rmSync(token, { force: true })
  rmdirSync(temporary)
}
