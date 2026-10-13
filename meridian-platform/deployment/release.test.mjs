import test from 'node:test'
import assert from 'node:assert/strict'
import { verifyIdentity, verifyPrevious } from './release.mjs'

const record = { sourceRevision: 'a'.repeat(40), imageId: `sha256:${'b'.repeat(64)}` }
const image = { Id: record.imageId, Config: { User: '10001:10001',
  Labels: { 'org.opencontainers.image.revision': record.sourceRevision } } }
test('exact source/image/container identities pass', () => {
  verifyIdentity(record, image, { Image: record.imageId })
})
test('source, artifact, running-image, and root-user mismatches fail', () => {
  assert.throws(() => verifyIdentity({ ...record, sourceRevision: 'c'.repeat(40) }, image))
  assert.throws(() => verifyIdentity({ ...record, imageId: `sha256:${'d'.repeat(64)}` }, image))
  assert.throws(() => verifyIdentity(record, image, { Image: `sha256:${'e'.repeat(64)}` }))
  assert.throws(() => verifyIdentity(record, { ...image, Config: { ...image.Config, User: '0' } }))
})
test('only explicitly attributed pre-CP4 artifacts may use the historical rollback exception', () => {
  const legacy = { Id: record.imageId, Config: { User: '', Labels: {} } }
  const previous = { ...record, sourceAttribution: 'operator-confirmed-pre-CP4' }
  verifyPrevious(previous, legacy)
  assert.throws(() => verifyIdentity(previous, legacy))
  assert.throws(() => verifyPrevious(previous, image))
  assert.throws(() => verifyPrevious({ ...previous, imageId: `sha256:${'c'.repeat(64)}` }, legacy))
})
