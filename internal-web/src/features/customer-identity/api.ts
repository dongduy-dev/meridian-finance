import { z } from 'zod'
import type { AuthSessionManager } from '@/features/auth/model/auth-session'
import type { ApiBinaryResponse } from '@/lib/api'
import { meridianUuidSchema } from '@/lib/validation/meridian-uuid'

export const verificationSchema = z.object({
  verificationId: meridianUuidSchema, sequence: z.number().int().positive(), customerNumber: z.string(), fullName: z.string(),
  source: z.string(), method: z.string(), status: z.string(), rejectionReason: z.string().nullable(),
  submittedAt: z.string(), completedAt: z.string().nullable(),
  evidence: z.object({ versionId: meridianUuidSchema, versionNumber: z.number().int().positive(), filename: z.string(), mimeType: z.string(), byteSize: z.number(), uploadedAt: z.string() }).nullable(),
})
export type IdentityVerification = z.infer<typeof verificationSchema>
export const rejectionReasons: Record<string, string> = {
  IDENTITY_REFERENCE_MISMATCH: 'Identity reference mismatch', NAME_MISMATCH: 'Name mismatch',
  UNREADABLE_EVIDENCE: 'Unreadable evidence', UNACCEPTABLE_EVIDENCE: 'Unacceptable identity evidence',
}
const base = '/staff/customer-identity-verifications'
export async function identityQueue(manager: AuthSessionManager, page: number) { return verificationSchema.array().parse(await manager.protectedRequest(`${base}?page=${page}&size=25`)) }
export async function identityDetail(manager: AuthSessionManager, id: string) { return verificationSchema.parse(await manager.protectedRequest(`${base}/${encodeURIComponent(id)}`)) }
export async function submitIntakeIdentity(manager: AuthSessionManager, caseId: string, versionId: string) { return verificationSchema.parse(await manager.protectedRequest(`/staff/assisted-originations/${encodeURIComponent(caseId)}/identity-verifications`, { method: 'POST', body: { documentVersionId: versionId } })) }
export async function decideIdentity(manager: AuthSessionManager, id: string, verify: boolean, versionId: string, reference: string, reason: string) {
  return verificationSchema.parse(await manager.protectedRequest(`${base}/${encodeURIComponent(id)}/${verify ? 'verify' : 'reject'}`, {
    method: 'POST', body: { requestId: crypto.randomUUID(), documentVersionId: versionId,
      ...(verify ? { presentedIdentityReference: reference } : { rejectionReason: reason }) },
  }, { replayAfterSessionRefresh: false }))
}
export function identityContent(manager: AuthSessionManager, id: string) { return manager.protectedRequest<ApiBinaryResponse>(`${base}/${encodeURIComponent(id)}/content`, { responseType: 'blob', cache: 'no-store' }) }
