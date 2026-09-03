import { beforeEach, describe, expect, it } from 'vitest'
import {
  clearUnresolvedOperations,
  digestFile,
  digestOperationPayload,
  findUnresolvedOperation,
  removeUnresolvedOperation,
  saveUnresolvedOperation,
} from './unresolved-operation'

describe('unresolved operation recovery', () => {
  beforeEach(() => sessionStorage.clear())

  it('retains only safe operation metadata and replaces the same resource', () => {
    saveUnresolvedOperation({
      type: 'DOCUMENT_REVIEW', resource: 'application:item:version', operationId: 'first',
      payloadDigest: 'digest-one', unresolvedAt: '2026-09-03T01:00:00Z',
    })
    saveUnresolvedOperation({
      type: 'DOCUMENT_REVIEW', resource: 'application:item:version', operationId: 'second',
      payloadDigest: 'digest-two', unresolvedAt: '2026-09-03T01:01:00Z',
    })

    expect(findUnresolvedOperation('DOCUMENT_REVIEW', 'application:item:version')).toEqual({
      type: 'DOCUMENT_REVIEW', resource: 'application:item:version', operationId: 'second',
      payloadDigest: 'digest-two', unresolvedAt: '2026-09-03T01:01:00Z',
    })
    expect(sessionStorage.getItem('meridian.staff.unresolved-operations.v1')).not.toContain('filename')
  })

  it('removes one operation or clears all recovery state', () => {
    saveUnresolvedOperation({
      type: 'TASK_COMPLETION', resource: 'task-one', operationId: 'one',
      payloadDigest: 'digest-one', unresolvedAt: '2026-09-03T01:00:00Z',
    })
    saveUnresolvedOperation({
      type: 'STAFF_RESUBMISSION', resource: 'application-one', operationId: 'two',
      payloadDigest: 'digest-two', unresolvedAt: '2026-09-03T01:00:00Z',
    })

    removeUnresolvedOperation('TASK_COMPLETION', 'task-one')
    expect(findUnresolvedOperation('TASK_COMPLETION', 'task-one')).toBeUndefined()
    expect(findUnresolvedOperation('STAFF_RESUBMISSION', 'application-one')).toBeDefined()
    clearUnresolvedOperations()
    expect(sessionStorage).toHaveLength(0)
  })

  it('creates stable SHA-256 digests without persisting payloads or file bytes', async () => {
    const payload = { taskId: 'task-one', instruction: 'restricted text' }
    const file = new File(['controlled fixture'], 'proof.pdf', { type: 'application/pdf' })

    await expect(digestOperationPayload(payload)).resolves.toMatch(/^[a-f0-9]{64}$/)
    await expect(digestOperationPayload(payload)).resolves.toBe(await digestOperationPayload(payload))
    await expect(digestFile(file)).resolves.toMatch(/^[a-f0-9]{64}$/)
    expect(sessionStorage).toHaveLength(0)
  })
})
