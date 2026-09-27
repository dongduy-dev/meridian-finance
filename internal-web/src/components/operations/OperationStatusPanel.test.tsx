import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { createOperationIdentity } from '@/lib/operation/operation-identity'
import { OperationStatusPanel, operationStatuses } from './OperationStatusPanel'

describe('operation foundation', () => {
  it('provides distinct operator guidance for every non-domain operation state', () => {
    const copy = new Set<string>()
    for (const status of operationStatuses) {
      const { unmount } = render(<OperationStatusPanel status={status} />)
      const alert = screen.getByRole('alert')
      expect(alert).toBeVisible()
      copy.add(alert.textContent ?? '')
      unmount()
    }
    expect(copy.size).toBe(operationStatuses.length)
    expect([...copy].join(' ')).not.toMatch(/authoritative state|durable result|current authority/i)
  })

  it('creates a UUID only when explicitly requested', () => {
    const randomUUID = vi.spyOn(crypto, 'randomUUID').mockReturnValue('11111111-1111-4111-8111-111111111111')
    expect(createOperationIdentity()).toBe('11111111-1111-4111-8111-111111111111')
    expect(randomUUID).toHaveBeenCalledTimes(1)
  })
})
