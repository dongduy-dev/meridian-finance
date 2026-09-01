import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { createOperationIdentity } from '@/lib/operation/operation-identity'
import { OperationStatusPanel, operationStatuses } from './OperationStatusPanel'

describe('operation foundation', () => {
  it('provides every non-domain operation state', () => {
    for (const status of operationStatuses) {
      const { unmount } = render(<OperationStatusPanel status={status} />)
      expect(screen.getByRole('alert')).toBeVisible()
      unmount()
    }
  })

  it('creates a UUID only when explicitly requested', () => {
    const randomUUID = vi.spyOn(crypto, 'randomUUID').mockReturnValue('11111111-1111-4111-8111-111111111111')
    expect(createOperationIdentity()).toBe('11111111-1111-4111-8111-111111111111')
    expect(randomUUID).toHaveBeenCalledTimes(1)
  })
})
