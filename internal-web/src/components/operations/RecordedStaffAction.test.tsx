import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { RecordedStaffAction } from './RecordedStaffAction'

const actor = { displayName: 'Deni Loan Officer', email: 'officer@meridian.local' }

describe('Recorded Staff action presentation', () => {
  it('keeps a full uninterrupted reason in a shrinkable, wrapping panel', () => {
    const reason = 'UninterruptedReason'.repeat(100)
    render(<RecordedStaffAction outcome="Recommend rejection" recordedAt="2026-09-06T08:20:00Z" recordedBy={actor}>
      <p>Reason: {reason}</p>
    </RecordedStaffAction>)

    const panel = screen.getByRole('alert')
    expect(panel).toHaveClass('min-w-0', 'grid-cols-[auto_minmax(0,1fr)]', '[overflow-wrap:anywhere]')
    expect(within(panel).getByText(`Reason: ${reason}`)).toBeVisible()
    expect(within(panel).getByText(`Reason: ${reason}`).textContent).toBe(`Reason: ${reason}`)
    expect(within(panel).getByText(actor.displayName)).toBeVisible()
    expect(within(panel).getByText(actor.email)).toBeVisible()
    expect(panel.querySelector('time')).toHaveAttribute('dateTime', '2026-09-06T08:20:00Z')
  })

  it('emphasizes the unchanged business outcome over metadata and supporting context', () => {
    render(<RecordedStaffAction outcome="Approve" recordedAt="2026-09-06T08:20:00Z" recordedBy={actor}>
      <p>Reason: The required evidence is complete.</p>
    </RecordedStaffAction>)

    expect(screen.getByRole('heading', { name: 'Approve' })).toHaveClass('text-base', 'font-bold')
    expect(screen.getByText(actor.displayName)).toHaveClass('font-medium')
    expect(screen.getByText('Recorded')).toHaveClass('text-xs', 'font-medium')
    expect(screen.getByText('Recorded by')).toHaveClass('text-xs', 'font-medium')
    expect(screen.getByText('Reason: The required evidence is complete.').parentElement)
      .toHaveClass('text-sm', 'font-normal')
  })
})
