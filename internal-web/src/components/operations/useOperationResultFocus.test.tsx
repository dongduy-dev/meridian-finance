import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it } from 'vitest'

import { OperationStatusPanel, type OperationStatus } from './OperationStatusPanel'
import { useOperationResultFocus } from './useOperationResultFocus'

function Harness() {
  const [status, setStatus] = useState<OperationStatus>('DRAFT')
  const requestResultFocus = useOperationResultFocus(status, 'operation-result')
  return <>
    <button onClick={() => { requestResultFocus(); setStatus('IN_FLIGHT') }}>Submit confirmed command</button>
    <button onClick={() => setStatus('RECONCILING')}>Finish request</button>
    <button onClick={() => setStatus('RESOLVED')}>Finish reconciliation</button>
    <button onClick={() => setStatus('BLOCKED')}>Background refresh</button>
    {status !== 'DRAFT'
      ? <OperationStatusPanel status={status} headingId="operation-result" headingLabel="Command result for APP-1" />
      : null}
  </>
}

describe('useOperationResultFocus', () => {
  it('waits for a settled submitted result and ignores later background status changes', async () => {
    render(<Harness />)
    const user = userEvent.setup()

    const submit = screen.getByRole('button', { name: 'Submit confirmed command' })
    await user.click(submit)
    const result = screen.getByRole('heading', { name: 'Command result for APP-1' })
    expect(result).not.toHaveFocus()

    const reconciling = screen.getByRole('button', { name: 'Finish request' })
    await user.click(reconciling)
    expect(result).not.toHaveFocus()

    await user.click(screen.getByRole('button', { name: 'Finish reconciliation' }))
    expect(result).toHaveFocus()

    const backgroundRefresh = screen.getByRole('button', { name: 'Background refresh' })
    await user.click(backgroundRefresh)
    expect(backgroundRefresh).toHaveFocus()
  })
})
