import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  bindUnresolvedOperations,
  clearUnresolvedOperations,
  decideOperationIdentity,
  digestFile,
  digestOperationPayload,
  findUnresolvedOperation,
  listUnresolvedOperations,
  removeUnresolvedOperation,
  saveUnresolvedOperation,
} from './unresolved-operation'

const actor = {
  userId: '11111111-1111-4111-8111-111111111111',
  roles: ['LOAN_OFFICER'],
  permissions: ['document:review', 'loan:correction:staff'],
}

describe('unresolved operation recovery', () => {
  beforeEach(async () => {
    sessionStorage.clear()
    await bindUnresolvedOperations(actor)
  })

  it('retains only safe operation metadata and replaces the same resource', () => {
    saveUnresolvedOperation({
      type: 'DOCUMENT_REVIEW', resource: 'application:item', operationId: 'first',
      payloadDigest: 'digest-one', unresolvedAt: '2026-09-03T01:00:00Z',
    })
    saveUnresolvedOperation({
      type: 'DOCUMENT_REVIEW', resource: 'application:item', operationId: 'second',
      payloadDigest: 'digest-two', unresolvedAt: '2026-09-03T01:01:00Z',
    })

    expect(findUnresolvedOperation('DOCUMENT_REVIEW', 'application:item')).toEqual({
      type: 'DOCUMENT_REVIEW', resource: 'application:item', operationId: 'second',
      payloadDigest: 'digest-two', unresolvedAt: '2026-09-03T01:01:00Z',
    })
    const persisted = sessionStorage.getItem('meridian.staff.unresolved-operations.v1') ?? ''
    expect(persisted).not.toContain('filename')
    expect(persisted).not.toContain(actor.roles[0])
    expect(persisted).not.toContain(actor.permissions[0])
  })

  it('removes one operation or clears all recovery state', () => {
    saveUnresolvedOperation({
      type: 'TASK_COMPLETION', resource: 'task-one', operationId: 'one',
      payloadDigest: 'digest-one', unresolvedAt: '2026-09-03T01:00:00Z',
    })
    saveUnresolvedOperation({
      type: 'STAFF_RESUBMISSION', resource: 'resubmit:application-one', operationId: 'two',
      payloadDigest: 'digest-two', unresolvedAt: '2026-09-03T01:00:00Z',
    })

    removeUnresolvedOperation('TASK_COMPLETION', 'task-one')
    expect(findUnresolvedOperation('TASK_COMPLETION', 'task-one')).toBeUndefined()
    expect(findUnresolvedOperation('STAFF_RESUBMISSION', 'resubmit:application-one')).toBeDefined()
    clearUnresolvedOperations()
    expect(sessionStorage).toHaveLength(0)
  })

  it('creates stable SHA-256 digests without persisting payloads or file bytes', async () => {
    const payload = { taskId: 'task-one', instruction: 'restricted text' }
    const file = new File(['controlled fixture'], 'proof.pdf', { type: 'application/pdf' })

    await expect(digestOperationPayload(payload)).resolves.toMatch(/^[a-f0-9]{64}$/)
    await expect(digestOperationPayload(payload)).resolves.toBe(await digestOperationPayload(payload))
    await expect(digestFile(file)).resolves.toMatch(/^[a-f0-9]{64}$/)
    const persisted = sessionStorage.getItem('meridian.staff.unresolved-operations.v1') ?? ''
    expect(persisted).not.toContain('restricted text')
    expect(persisted).not.toContain('controlled fixture')
    expect(persisted).not.toContain('proof.pdf')
  })

  it('reuses the operation ID for the same digest and blocks a changed digest', () => {
    saveUnresolvedOperation({
      type: 'DOCUMENT_REVIEW', resource: 'application:item', operationId: 'retained-id',
      payloadDigest: 'same-digest', unresolvedAt: '2026-09-03T01:00:00Z',
    })

    expect(decideOperationIdentity('DOCUMENT_REVIEW', 'application:item', 'same-digest')).toEqual({
      kind: 'REUSE_EXISTING', operationId: 'retained-id',
    })
    expect(decideOperationIdentity('DOCUMENT_REVIEW', 'application:item', 'changed-digest')).toMatchObject({
      kind: 'CONFLICT_WITH_UNRESOLVED', operation: { operationId: 'retained-id' },
    })
  })

  it('creates a new operation ID only when the resource has no unresolved result', () => {
    vi.spyOn(crypto, 'randomUUID').mockReturnValue('22222222-2222-4222-8222-222222222222')

    expect(decideOperationIdentity('TASK_COMPLETION', 'complete:task-two', 'digest')).toEqual({
      kind: 'NEW', operationId: '22222222-2222-4222-8222-222222222222',
    })
  })

  it('keeps Staff resubmission recovery isolated by application resource', () => {
    saveUnresolvedOperation({
      type: 'STAFF_RESUBMISSION', resource: 'resubmit:application-a', operationId: 'application-a-id',
      payloadDigest: 'digest-a', unresolvedAt: '2026-09-03T01:00:00Z',
    })

    expect(decideOperationIdentity('STAFF_RESUBMISSION', 'resubmit:application-a', 'digest-a'))
      .toMatchObject({ kind: 'REUSE_EXISTING', operationId: 'application-a-id' })
    expect(decideOperationIdentity('STAFF_RESUBMISSION', 'resubmit:application-b', 'digest-b').kind)
      .toBe('NEW')
    expect(listUnresolvedOperations()).toHaveLength(1)
  })
})
