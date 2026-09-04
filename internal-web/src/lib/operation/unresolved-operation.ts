const STORAGE_KEY = 'meridian.staff.unresolved-operations.v1'

export type UnresolvedOperationType = 'DOCUMENT_REVIEW' | 'STAFF_UPLOAD' | 'TASK_COMPLETION' | 'STAFF_RESUBMISSION'
export type UnresolvedOperation = {
  type: UnresolvedOperationType
  resource: string
  operationId: string
  payloadDigest: string
  unresolvedAt: string
}
export type RecoveryBindingIdentity = {
  userId: string
  roles: readonly string[]
  permissions: readonly string[]
}
export type OperationIdentityDecision =
  | { kind: 'NEW'; operationId: string }
  | { kind: 'REUSE_EXISTING'; operationId: string }
  | { kind: 'CONFLICT_WITH_UNRESOLVED'; operation: UnresolvedOperation }

export class UnresolvedOperationConflictError extends Error {
  constructor() {
    super('A previous operation result is still unknown. Refresh authoritative evidence before changing this command.')
    this.name = 'UnresolvedOperationConflictError'
  }
}

type RecoveryEnvelope = {
  binding: string
  operations: UnresolvedOperation[]
}

let activeBinding: string | undefined

function isUnresolvedOperation(value: unknown): value is UnresolvedOperation {
  if (!value || typeof value !== 'object') return false
  const operation = value as Partial<UnresolvedOperation>
  return typeof operation.type === 'string'
    && typeof operation.resource === 'string'
    && typeof operation.operationId === 'string'
    && typeof operation.payloadDigest === 'string'
    && typeof operation.unresolvedAt === 'string'
}

function readEnvelope(): RecoveryEnvelope | undefined {
  try {
    const value = JSON.parse(sessionStorage.getItem(STORAGE_KEY) ?? 'null') as unknown
    if (!value || typeof value !== 'object') return undefined
    const envelope = value as Partial<RecoveryEnvelope>
    if (typeof envelope.binding !== 'string' || !Array.isArray(envelope.operations)) return undefined
    return { binding: envelope.binding, operations: envelope.operations.filter(isUnresolvedOperation) }
  } catch { return undefined }
}

function writeEnvelope(binding: string, operations: UnresolvedOperation[]) {
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify({ binding, operations }))
}

function readAll(): UnresolvedOperation[] {
  const envelope = readEnvelope()
  return activeBinding && envelope?.binding === activeBinding ? envelope.operations : []
}

export async function digestOperationPayload(payload: unknown): Promise<string> {
  const bytes = new TextEncoder().encode(JSON.stringify(payload))
  const hash = await crypto.subtle.digest('SHA-256', bytes)
  return Array.from(new Uint8Array(hash), (value) => value.toString(16).padStart(2, '0')).join('')
}

export async function digestFile(file: File): Promise<string> {
  const hash = await crypto.subtle.digest('SHA-256', await file.arrayBuffer())
  return Array.from(new Uint8Array(hash), (value) => value.toString(16).padStart(2, '0')).join('')
}

export async function bindUnresolvedOperations(identity: RecoveryBindingIdentity): Promise<void> {
  const binding = await digestOperationPayload({
    userId: identity.userId,
    roles: [...new Set(identity.roles)].sort(),
    permissions: [...new Set(identity.permissions)].sort(),
  })
  const envelope = readEnvelope()
  activeBinding = binding
  if (envelope?.binding === binding) {
    writeEnvelope(binding, envelope.operations)
    return
  }
  writeEnvelope(binding, [])
}

export function listUnresolvedOperations(): UnresolvedOperation[] {
  return [...readAll()]
}

export function findUnresolvedOperation(type: UnresolvedOperationType, resource: string) {
  return readAll().find((item) => item.type === type && item.resource === resource)
}

export function decideOperationIdentity(
  type: UnresolvedOperationType,
  resource: string,
  payloadDigest: string,
): OperationIdentityDecision {
  const unresolved = findUnresolvedOperation(type, resource)
  if (!unresolved) return { kind: 'NEW', operationId: crypto.randomUUID() }
  if (unresolved.payloadDigest === payloadDigest) {
    return { kind: 'REUSE_EXISTING', operationId: unresolved.operationId }
  }
  return { kind: 'CONFLICT_WITH_UNRESOLVED', operation: unresolved }
}

export function saveUnresolvedOperation(operation: UnresolvedOperation) {
  if (!activeBinding) throw new Error('Unresolved operation recovery is not bound to an authenticated Staff actor.')
  const next = readAll().filter((item) => item.type !== operation.type || item.resource !== operation.resource)
  writeEnvelope(activeBinding, [...next, operation])
}

export function removeUnresolvedOperation(type: UnresolvedOperationType, resource: string) {
  if (!activeBinding) return
  const next = readAll().filter((item) => item.type !== type || item.resource !== resource)
  writeEnvelope(activeBinding, next)
}

export function clearUnresolvedOperations() {
  activeBinding = undefined
  sessionStorage.removeItem(STORAGE_KEY)
}
