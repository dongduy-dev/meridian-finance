import { z } from 'zod'
import { createProtectedApiClient, type ProtectedRequestCoordinator } from '@/lib/api'
import { meridianUuidSchema } from '@/lib/validation/meridian-uuid'

export const identityVerificationSchema = z.object({
  verificationId: meridianUuidSchema, sequence: z.number().int().positive(),
  customerNumber: z.string(), fullName: z.string(), source: z.string(), method: z.string(),
  status: z.string(), rejectionReason: z.string().nullable(), submittedAt: z.string(), completedAt: z.string().nullable(),
  evidence: z.object({ versionId: meridianUuidSchema, versionNumber: z.number().int().positive(), filename: z.string(), mimeType: z.string(), byteSize: z.number(), uploadedAt: z.string() }).nullable(),
})
export type IdentityVerification = z.infer<typeof identityVerificationSchema>
export const identityReasons: Record<string, string> = {
  IDENTITY_REFERENCE_MISMATCH: 'The identity reference did not match your profile.',
  NAME_MISMATCH: 'The name on the evidence did not match your profile.',
  UNREADABLE_EVIDENCE: 'The document could not be read clearly.',
  UNACCEPTABLE_EVIDENCE: 'The document could not be accepted as identity evidence.',
}
export function createIdentityApi(manager: ProtectedRequestCoordinator) {
  const client = createProtectedApiClient(manager)
  return {
    async history() { return identityVerificationSchema.array().parse(await client.request('/customers/me/identity-verifications')) },
    async upload(file: File, requestId: string, baseline?: string) {
      const body = new FormData()
      body.set('file', file); body.set('uploadRequestId', requestId)
      if (baseline) body.set('expectedCurrentVersionId', baseline)
      return identityVerificationSchema.parse(await client.request('/customers/me/identity-verifications', { method: 'POST', body }))
    },
    content(id: string) { return client.request<Blob>(`/customers/me/identity-verifications/${encodeURIComponent(id)}/content`, { responseType: 'blob', cache: 'no-store' }) },
  }
}
