import { z } from 'zod'

import {
  createProtectedApiClient,
  type ApiClient,
  type ProtectedRequestCoordinator,
} from '@/lib/api'

const javaUuid = z.string().guid()
const nonEmptyString = z.string().min(1)

export const customerCorrectionTaskSchema = z.object({
  correctionTaskId: javaUuid,
  correctionRequestId: javaUuid,
  status: nonEmptyString,
  scope: nonEmptyString,
  documentType: nonEmptyString.nullable(),
  checklistItemId: javaUuid.nullable(),
  reasonCode: nonEmptyString,
  customerInstruction: nonEmptyString,
  createdAt: nonEmptyString,
  completedAt: nonEmptyString.nullable(),
})

const customerCorrectionTasksSchema = z.array(customerCorrectionTaskSchema)

export const correctionResubmissionSchema = z.object({
  correctionRequestId: javaUuid,
  loanApplicationId: javaUuid,
  loanApplicationStatus: nonEmptyString,
  resubmissionRequestId: javaUuid,
  resubmittedAt: nonEmptyString,
})

export type CustomerCorrectionTask = z.infer<typeof customerCorrectionTaskSchema>
export type CorrectionResubmission = z.infer<typeof correctionResubmissionSchema>

export interface CorrectionApi {
  getOwnTasks(loanApplicationId: string): Promise<CustomerCorrectionTask[]>
  completeOwnTask(loanApplicationId: string, taskId: string, completionRequestId: string): Promise<CustomerCorrectionTask>
  resubmitOwnCorrection(loanApplicationId: string, resubmissionRequestId: string): Promise<CorrectionResubmission>
}

export function createCorrectionApi(
  coordinator: ProtectedRequestCoordinator,
  client?: ApiClient,
): CorrectionApi {
  const protectedClient = createProtectedApiClient(coordinator, client)
  return {
    async getOwnTasks(loanApplicationId) {
      javaUuid.parse(loanApplicationId)
      return customerCorrectionTasksSchema.parse(
        await protectedClient.request(
          `/loan-applications/${encodeURIComponent(loanApplicationId)}/corrections/tasks`,
        ),
      )
    },
    async completeOwnTask(loanApplicationId, taskId, completionRequestId) {
      javaUuid.parse(loanApplicationId)
      javaUuid.parse(taskId)
      javaUuid.parse(completionRequestId)
      return customerCorrectionTaskSchema.parse(
        await protectedClient.request(
          `/loan-applications/${encodeURIComponent(loanApplicationId)}/corrections/tasks/${encodeURIComponent(taskId)}/complete`,
          { method: 'POST', json: { completionRequestId } },
        ),
      )
    },
    async resubmitOwnCorrection(loanApplicationId, resubmissionRequestId) {
      javaUuid.parse(loanApplicationId)
      javaUuid.parse(resubmissionRequestId)
      return correctionResubmissionSchema.parse(
        await protectedClient.request(
          `/loan-applications/${encodeURIComponent(loanApplicationId)}/corrections/resubmit`,
          { method: 'POST', json: { resubmissionRequestId } },
        ),
      )
    },
  }
}
