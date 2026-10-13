// Node 22. No provider calls, no implicit deployment, and no secret-bearing diagnostics.
import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { existsSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname, isAbsolute, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..')
function command(executable, args, options = {}) {
  const r = spawnSync(executable, args, { cwd: root, encoding: 'utf8', ...options })
  assert.equal(r.status, 0, `${executable} operation failed; inspect privately`)
  return r.stdout?.trim() ?? ''
}
const image = ref => JSON.parse(command('docker', ['image', 'inspect', ref]))[0]
export function verifyIdentity(metadata, inspectedImage, container) {
  assert.match(metadata.sourceRevision, /^[0-9a-f]{40}$/)
  assert.match(metadata.imageId, /^sha256:[0-9a-f]{64}$/)
  assert.equal(inspectedImage.Id, metadata.imageId, 'Artifact mismatch')
  assert.equal(inspectedImage.Config.Labels['org.opencontainers.image.revision'], metadata.sourceRevision,
    'Source revision mismatch')
  assert.equal(inspectedImage.Config.User, '10001:10001')
  if (container) assert.equal(container.Image, metadata.imageId, 'Running container artifact mismatch')
}

export function verifyPrevious(metadata, inspectedImage) {
  if (metadata.sourceAttribution === 'operator-confirmed-pre-CP4') {
    // Initial CP3 artifact predates labels/non-root. This explicit historical exception
    // preserves rollback evidence; it must never validate a new CP4 deployment.
    assert.match(metadata.sourceRevision, /^[0-9a-f]{40}$/)
    assert.match(metadata.imageId, /^sha256:[0-9a-f]{64}$/)
    assert.equal(inspectedImage.Id, metadata.imageId)
    assert.ok(!inspectedImage.Config.Labels?.['org.opencontainers.image.revision'])
  } else verifyIdentity(metadata, inspectedImage)
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const [action, shaOrRecord, outputOrContainer, previousRecord] = process.argv.slice(2)
  if (action === 'build') {
    assert.match(shaOrRecord ?? '', /^[0-9a-f]{40}$/)
    assert.equal(command('git', ['rev-parse', 'HEAD']), shaOrRecord, 'Build must use reviewed checkout SHA')
    assert.equal(command('git', ['status', '--porcelain', '--untracked-files=all']), '', 'Build requires clean source')
    assert.ok(isAbsolute(outputOrContainer ?? '') && !existsSync(outputOrContainer), 'Use a new absolute record path')
    // Previous metadata remains untouched and the artifact is explicitly retained before building.
    let previous = null
    if (previousRecord) {
      previous = JSON.parse(readFileSync(previousRecord, 'utf8'))
      verifyPrevious(previous, image(previous.imageId))
      const rollbackTag = `meridian-platform:rollback-${previous.sourceRevision}`
      const retained = spawnSync('docker', ['image', 'inspect', rollbackTag], { encoding: 'utf8' })
      if (retained.status === 0) assert.equal(JSON.parse(retained.stdout)[0].Id, previous.imageId,
        'Existing rollback reference must not be overwritten')
      else command('docker', ['tag', previous.imageId, rollbackTag])
    }
    const tag = `meridian-platform:git-${shaOrRecord}`
    const existing = spawnSync('docker', ['image', 'inspect', tag], { encoding: 'utf8' })
    assert.notEqual(existing.status, 0, 'Release tag already exists; reuse its recorded ID, do not overwrite')
    command('docker', ['build', '--build-arg', `SOURCE_REVISION=${shaOrRecord}`, '--tag', tag, 'meridian-platform'],
      { stdio: ['ignore', 'ignore', 'ignore'] })
    const built = image(tag)
    const record = { sourceRevision: shaOrRecord, imageId: built.Id, previous }
    verifyIdentity(record, built)
    writeFileSync(outputOrContainer, JSON.stringify(record, null, 2) + '\n', { flag: 'wx', mode: 0o600 })
    console.log(`Release recorded: ${shaOrRecord} ${built.Id}`)
  } else if (action === 'verify') {
    const record = JSON.parse(readFileSync(shaOrRecord, 'utf8'))
    const container = JSON.parse(command('docker', ['container', 'inspect', outputOrContainer]))[0]
    verifyIdentity(record, image(record.imageId), container)
    console.log(`PASS: running source ${record.sourceRevision}, image ${record.imageId}`)
  } else {
    throw new Error('Usage: release.mjs build SHA NEW_ABSOLUTE_RECORD [PREVIOUS_RECORD] | verify RECORD CONTAINER')
  }
}
