import { describe, expect, it, vi } from 'vitest'

import type { ApiClient, ApiRequestOptions, ProtectedRequestCoordinator } from '@/lib/api'

import { createCorrectionApi } from './correction-api'

const applicationId = '11111111-1111-4111-8111-111111111111'
const taskId = '22222222-2222-4222-8222-222222222222'
const requestId = '33333333-3333-4333-8333-333333333333'
const correctionRequestId = '44444444-4444-4444-8444-444444444444'

const task = {
  correctionTaskId: taskId,
  correctionRequestId,
  status: 'FUTURE_TASK_STATUS',
  scope: 'FUTURE_SCOPE',
  documentType: null,
  checklistItemId: null,
  reasonCode: 'FUTURE_REASON',
  customerInstruction: 'Provide Customer-safe evidence.',
  createdAt: '2026-08-31T08:00:00',
  completedAt: null,
}

describe('Customer correction API boundary', () => {
  it('preserves evolving Customer-safe task values and uses exact command paths and bodies', async () => {
    const request = vi.fn(async (path: string, options?: ApiRequestOptions) => {
      void options
      if (path.endsWith('/tasks')) return [task]
      if (path.endsWith('/complete')) return { ...task, status: 'COMPLETED', completedAt: '2026-08-31T09:00:00' }
      return { correctionRequestId, loanApplicationId: applicationId, loanApplicationStatus: 'SUBMITTED', resubmissionRequestId: requestId, resubmittedAt: '2026-08-31T09:05:00' }
    })
    const coordinator: ProtectedRequestCoordinator = {
      requestProtected: vi.fn((operation) => operation('protected-customer-token')),
    }
    const api = createCorrectionApi(coordinator, { request } as ApiClient)

    expect((await api.getOwnTasks(applicationId))[0]).toMatchObject({ scope: 'FUTURE_SCOPE', reasonCode: 'FUTURE_REASON', checklistItemId: null })
    await api.completeOwnTask(applicationId, taskId, requestId)
    await api.resubmitOwnCorrection(applicationId, requestId)

    expect(request.mock.calls.map(([path]) => path)).toEqual([
      `/loan-applications/${applicationId}/corrections/tasks`,
      `/loan-applications/${applicationId}/corrections/tasks/${taskId}/complete`,
      `/loan-applications/${applicationId}/corrections/resubmit`,
    ])
    expect(request.mock.calls[1]?.[1]).toMatchObject({ method: 'POST', json: { completionRequestId: requestId } })
    expect(request.mock.calls[2]?.[1]).toMatchObject({ method: 'POST', json: { resubmissionRequestId: requestId } })
    expect(new Headers(request.mock.calls[2]?.[1]?.headers).get('Authorization')).toBe('Bearer protected-customer-token')
  })
})
