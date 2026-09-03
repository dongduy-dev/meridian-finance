const STORAGE_KEY = 'meridian.staff.unresolved-operations.v1'

export type UnresolvedOperationType = 'DOCUMENT_REVIEW' | 'STAFF_UPLOAD' | 'TASK_COMPLETION' | 'STAFF_RESUBMISSION'
export type UnresolvedOperation = {
  type: UnresolvedOperationType
  resource: string
  operationId: string
  payloadDigest: string
  unresolvedAt: string
}

function readAll(): UnresolvedOperation[] {
  try {
    const value = JSON.parse(sessionStorage.getItem(STORAGE_KEY) ?? '[]') as unknown
    return Array.isArray(value) ? value.filter((item): item is UnresolvedOperation => Boolean(
      item && typeof item === 'object' && 'operationId' in item && 'payloadDigest' in item,
    )) : []
  } catch { return [] }
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

export function findUnresolvedOperation(type: UnresolvedOperationType, resource: string) {
  return readAll().find((item) => item.type === type && item.resource === resource)
}

export function saveUnresolvedOperation(operation: UnresolvedOperation) {
  const next = readAll().filter((item) => item.type !== operation.type || item.resource !== operation.resource)
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify([...next, operation]))
}

export function removeUnresolvedOperation(type: UnresolvedOperationType, resource: string) {
  const next = readAll().filter((item) => item.type !== type || item.resource !== resource)
  if (next.length === 0) sessionStorage.removeItem(STORAGE_KEY)
  else sessionStorage.setItem(STORAGE_KEY, JSON.stringify(next))
}

export function clearUnresolvedOperations() {
  sessionStorage.removeItem(STORAGE_KEY)
}
