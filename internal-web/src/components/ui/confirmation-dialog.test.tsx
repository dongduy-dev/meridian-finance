import { useState } from 'react'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { Button } from './button'
import {
  ConfirmationDialog, ConfirmationDialogCancel, ConfirmationDialogDescription,
  ConfirmationDialogFooter, ConfirmationDialogTitle,
} from './confirmation-dialog'

function Harness({ confirm, background }: { confirm: () => void; background: () => void }) {
  const [open, setOpen] = useState(false)
  return <>
    <Button onClick={() => setOpen(true)}>Review action</Button>
    <Button onClick={background}>Background action</Button>
    {open ? <ConfirmationDialog onDismiss={() => setOpen(false)}>
      <ConfirmationDialogTitle>Confirm action</ConfirmationDialogTitle>
      <ConfirmationDialogDescription>Review the exact evidence before recording.</ConfirmationDialogDescription>
      <ConfirmationDialogFooter>
        <ConfirmationDialogCancel asChild><Button variant="outline">Cancel</Button></ConfirmationDialogCancel>
        <Button onClick={() => { setOpen(false); confirm() }}>Record action</Button>
      </ConfirmationDialogFooter>
    </ConfirmationDialog> : null}
  </>
}

describe('Internal confirmation dialog', () => {
  it('contains forward and reverse focus, isolates the background, and dismisses safely', async () => {
    const confirm = vi.fn()
    const background = vi.fn()
    const user = userEvent.setup()
    render(<Harness confirm={confirm} background={background} />)
    const trigger = screen.getByRole('button', { name: 'Review action' })
    const obscured = screen.getByRole('button', { name: 'Background action' })
    await user.click(trigger)
    const dialog = screen.getByRole('dialog', { name: 'Confirm action' })
    expect(dialog).toHaveAccessibleDescription('Review the exact evidence before recording.')
    expect(dialog).toContainElement(document.activeElement as HTMLElement)
    for (let i = 0; i < 6; i++) {
      await user.tab()
      expect(dialog).toContainElement(document.activeElement as HTMLElement)
    }
    for (let i = 0; i < 6; i++) {
      await user.tab({ shift: true })
      expect(dialog).toContainElement(document.activeElement as HTMLElement)
    }
    obscured.focus()
    expect(dialog).toContainElement(document.activeElement as HTMLElement)
    expect(obscured.closest('[aria-hidden="true"]')).not.toBeNull()
    expect(document.body).toHaveStyle({ pointerEvents: 'none' })
    await expect(user.click(obscured)).rejects.toThrow(/pointer-events/)
    await user.click(document.querySelector('[data-state="open"][aria-hidden="true"]')!)
    expect(dialog).toBeInTheDocument()
    expect(background).not.toHaveBeenCalled()
    await user.keyboard('{Escape}')
    await waitFor(() => expect(trigger).toHaveFocus())
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    await user.click(trigger)
    await user.click(screen.getByRole('button', { name: 'Cancel' }))
    await waitFor(() => expect(trigger).toHaveFocus())
    expect(confirm).not.toHaveBeenCalled()
  })

  it('bounds scrolling and stacks actions on narrow viewports; only explicit confirmation runs the action', async () => {
    const confirm = vi.fn()
    const user = userEvent.setup()
    render(<Harness confirm={confirm} background={vi.fn()} />)
    await user.click(screen.getByRole('button', { name: 'Review action' }))
    const dialog = screen.getByRole('dialog')
    expect(dialog).toHaveClass('max-h-[calc(100dvh-2rem)]', 'overflow-y-auto', 'overscroll-contain', 'w-[calc(100%-2rem)]')
    expect(within(dialog).getByRole('button', { name: 'Cancel' }).parentElement).toHaveClass('flex-col', 'sm:flex-row')
    await user.click(within(dialog).getByRole('button', { name: 'Record action' }))
    expect(confirm).toHaveBeenCalledTimes(1)
  })
})
