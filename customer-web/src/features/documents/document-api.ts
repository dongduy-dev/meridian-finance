import { z } from 'zod'

import {
  createProtectedApiClient,
  type ApiClient,
  type ProtectedRequestCoordinator,
} from '@/lib/api'

const nonEmptyString = z.string().min(1)
const javaUuid = z.string().guid()

export const documentVersionSchema = z.object({
  documentVersionId: javaUuid,
  checklistItemId: javaUuid,
  versionNumber: z.number().int().positive(),
  originalFilename: nonEmptyString,
  mimeType: nonEmptyString,
  byteSize: z.number().int().nonnegative(),
  uploadedAt: nonEmptyString,
})

export const customerDocumentChecklistSchema = z.object({
  checklistId: javaUuid,
  loanApplicationId: javaUuid,
  stage: nonEmptyString,
  uploadComplete: z.boolean(),
  processingReady: z.boolean(),
  items: z.array(z.object({
    checklistItemId: javaUuid,
    documentType: nonEmptyString,
    requirementStatus: nonEmptyString,
    customerStatus: nonEmptyString,
    uploadComplete: z.boolean(),
    processingReady: z.boolean(),
    currentVersion: documentVersionSchema.nullable(),
  })),
})

export type DocumentVersion = z.infer<typeof documentVersionSchema>
export type CustomerDocumentChecklist = z.infer<typeof customerDocumentChecklistSchema>
export type CustomerDocumentChecklistItem = CustomerDocumentChecklist['items'][number]

export interface UploadDocumentInput {
  loanApplicationId: string
  checklistItemId: string
  uploadRequestId: string
  expectedCurrentVersionId?: string
  file: File
}

export function createDocumentApi(
  coordinator: ProtectedRequestCoordinator,
  client?: ApiClient,
) {
  const protectedClient = createProtectedApiClient(coordinator, client)
  return {
    async getChecklist(loanApplicationId: string) {
      return customerDocumentChecklistSchema.parse(
        await protectedClient.request(
          `/loan-applications/${encodeURIComponent(loanApplicationId)}/documents`,
        ),
      )
    },
    async uploadDocument(input: UploadDocumentInput) {
      javaUuid.parse(input.loanApplicationId)
      javaUuid.parse(input.checklistItemId)
      javaUuid.parse(input.uploadRequestId)
      if (input.expectedCurrentVersionId) javaUuid.parse(input.expectedCurrentVersionId)
      const form = new FormData()
      form.set('uploadRequestId', input.uploadRequestId)
      if (input.expectedCurrentVersionId) {
        form.set('expectedCurrentVersionId', input.expectedCurrentVersionId)
      }
      form.set('file', input.file)
      return documentVersionSchema.parse(
        await protectedClient.request(
          `/loan-applications/${encodeURIComponent(input.loanApplicationId)}/documents/${encodeURIComponent(input.checklistItemId)}/versions`,
          { method: 'POST', body: form },
        ),
      )
    },
  }
}
