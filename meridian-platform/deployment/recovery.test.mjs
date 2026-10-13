import test from 'node:test'
import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { spawnSync } from 'node:child_process'
import { mkdtempSync, mkdirSync, readFileSync, rmSync, truncateSync, unlinkSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { basename, dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { hash, verifyBackup } from './recovery.mjs'

const here = dirname(fileURLToPath(import.meta.url))
function fixture(t) {
  const directory = mkdtempSync(join(tmpdir(), 'meridian-cp4-checksum-'))
  t.after(() => {
    assert.equal(dirname(directory), resolve(tmpdir()))
    assert.ok(basename(directory).startsWith('meridian-cp4-checksum-'))
    rmSync(directory, { recursive: true, force: true })
  })
  const contents = {
    'database.dump': 'fictional database dump',
    'documents.tgz': 'fictional document archive',
    'private.tgz': 'fictional private configuration archive',
    'release.json': '{"sourceRevision":"fictional"}',
  }
  const checksums = {}
  for (const [name, content] of Object.entries(contents)) {
    writeFileSync(join(directory, name), content)
    checksums[name] = createHash('sha256').update(content).digest('hex')
  }
  const manifest = { project: 'cp4test-checksum', createdAt: '2026-10-13T00:00:00Z', checksums }
  writeFileSync(join(directory, 'matched-set.json'), JSON.stringify(manifest))
  return { directory, manifest }
}

test('streamed SHA-256 preserves known and multi-chunk digests', async t => {
  const { directory } = fixture(t)
  const file = join(directory, 'digest-fixture')
  writeFileSync(file, 'abc')
  assert.equal(await hash(file), 'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad')
  writeFileSync(file, '')
  assert.equal(await hash(file), 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855')
  const content = Buffer.alloc(128 * 1024 + 1, 0x61)
  writeFileSync(file, content)
  assert.equal(await hash(file), createHash('sha256').update(content).digest('hex'))
})

test('intact matched backup verifies all four existing checksums without changing the manifest', async t => {
  const { directory, manifest } = fixture(t)
  const before = readFileSync(join(directory, 'matched-set.json'), 'utf8')
  assert.deepEqual(await verifyBackup(directory), manifest)
  assert.equal(readFileSync(join(directory, 'matched-set.json'), 'utf8'), before)
})

for (const artifact of ['database.dump', 'documents.tgz', 'private.tgz', 'release.json']) {
  test(`modified ${artifact} is rejected`, async t => {
    const { directory } = fixture(t)
    writeFileSync(join(directory, artifact), 'modified fictional bytes')
    await assert.rejects(verifyBackup(directory), /Backup checksum verification failed/)
  })
}

test('truncated dump and mismatched manifest checksum are rejected', async t => {
  const { directory, manifest } = fixture(t)
  truncateSync(join(directory, 'database.dump'), 3)
  await assert.rejects(verifyBackup(directory), /Backup checksum verification failed/)
  manifest.checksums['database.dump'] = await hash(join(directory, 'database.dump'))
  manifest.checksums['documents.tgz'] = '0'.repeat(64)
  writeFileSync(join(directory, 'matched-set.json'), JSON.stringify(manifest))
  await assert.rejects(verifyBackup(directory), /Backup checksum verification failed/)
})

test('missing and unreadable artifacts fail without leaking their private paths', async t => {
  const { directory } = fixture(t)
  const file = join(directory, 'private.tgz')
  unlinkSync(file)
  const safeFailure = error => {
    assert.equal(error.message, 'Backup checksum could not be calculated; inspect privately')
    assert.ok(!String(error).includes(directory))
    return true
  }
  await assert.rejects(verifyBackup(directory), /Backup checksum verification failed/)
  mkdirSync(file)
  await assert.rejects(hash(file), safeFailure)
})

test('CLI awaits intact verification and rejects corruption before restore creates resources', t => {
  const { directory } = fixture(t)
  const run = (...args) => spawnSync(process.execPath, [join(here, 'recovery.mjs'), ...args], { encoding: 'utf8' })
  const valid = run('verify', directory)
  assert.equal(valid.status, 0)
  assert.match(valid.stdout, /PASS: matched backup checksums/)
  unlinkSync(join(directory, 'documents.tgz'))
  for (const action of ['verify', 'restore']) {
    const invalid = run(action, directory, 'cp4restore-must-not-start')
    assert.notEqual(invalid.status, 0)
    assert.ok(!invalid.stdout.includes('PASS'))
    assert.match(invalid.stderr, /Backup checksum verification failed/)
    assert.ok(!invalid.stderr.includes(directory))
  }
})

test('missing or malformed manifest fails without exposing private paths or content', async t => {
  const { directory } = fixture(t)
  const manifest = join(directory, 'matched-set.json')
  unlinkSync(manifest)
  await assert.rejects(verifyBackup(directory), /Backup checksum verification failed/)
  writeFileSync(manifest, 'PRIVATE_MANIFEST_MARKER')
  const invalid = spawnSync(process.execPath, [join(here, 'recovery.mjs'), 'verify', directory], { encoding: 'utf8' })
  assert.notEqual(invalid.status, 0)
  assert.match(invalid.stderr, /Backup checksum verification failed/)
  assert.ok(!invalid.stderr.includes(directory) && !invalid.stderr.includes('PRIVATE_MANIFEST_MARKER'))
})
