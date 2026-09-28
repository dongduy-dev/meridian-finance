import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as api from '@/lib/api'
import { ApiError } from '@/lib/api'
import { SetPasswordPage } from './SetPasswordPage'

vi.mock('@/lib/api', async () => {
  const actual = await vi.importActual<typeof import('@/lib/api')>('@/lib/api')
  return { ...actual, apiRequest: vi.fn() }
})

function renderSetup(hash = '#token=opaque-value') {
  window.history.replaceState({}, '', `/set-password${hash}`)
  render(<MemoryRouter initialEntries={[`/set-password${hash}`]}><Routes>
    <Route path="/set-password" element={<SetPasswordPage />} />
    <Route path="/login" element={<p>Staff login destination</p>} />
  </Routes></MemoryRouter>)
}

describe('Internal Web password setup', () => {
  beforeEach(() => { vi.clearAllMocks(); localStorage.clear(); sessionStorage.clear() })

  it('scrubs a fragment token, validates password and confirmation, then returns to login', async () => {
    vi.mocked(api.apiRequest).mockResolvedValue(undefined)
    renderSetup()
    expect(window.location.hash).toBe('')
    const user = userEvent.setup()
    await user.type(screen.getByLabelText('New password'), 'short')
    await user.type(screen.getByLabelText('Confirm new password'), 'short')
    await user.click(screen.getByRole('button', { name: 'Set password' }))
    expect(screen.getByRole('alert')).toHaveTextContent('Use 12 to 72 characters.')
    expect(api.apiRequest).not.toHaveBeenCalled()
    await user.clear(screen.getByLabelText('New password'))
    await user.clear(screen.getByLabelText('Confirm new password'))
    await user.type(screen.getByLabelText('New password'), 'A-Valid-Password-123')
    await user.type(screen.getByLabelText('Confirm new password'), 'different-password')
    await user.click(screen.getByRole('button', { name: 'Set password' }))
    expect(screen.getByText('Passwords must match.')).toBeVisible()
    await user.clear(screen.getByLabelText('Confirm new password'))
    await user.type(screen.getByLabelText('Confirm new password'), 'A-Valid-Password-123')
    await user.click(screen.getByRole('button', { name: 'Set password' }))
    expect(await screen.findByText('Staff login destination')).toBeVisible()
    expect(api.apiRequest).toHaveBeenCalledWith('/auth/password-reset/confirm', {
      method: 'POST', body: { token: 'opaque-value', newPassword: 'A-Valid-Password-123' },
    })
    expect(localStorage.length).toBe(0)
    expect(sessionStorage.length).toBe(0)
  })

  it('shows safe missing and invalid token recovery without automatic retry', async () => {
    renderSetup('')
    expect(screen.getByText(/setup link is incomplete/i)).toBeVisible()
    expect(api.apiRequest).not.toHaveBeenCalled()
  })

  it('does not resubmit an invalid token', async () => {
    vi.mocked(api.apiRequest).mockRejectedValue(new ApiError(401, 'INVALID_PASSWORD_RESET_TOKEN',
      'Invalid token', '/auth/password-reset/confirm', 'now'))
    renderSetup('#token=expired')
    const user = userEvent.setup()
    await user.type(screen.getByLabelText('New password'), 'A-Valid-Password-123')
    await user.type(screen.getByLabelText('Confirm new password'), 'A-Valid-Password-123')
    await user.click(screen.getByRole('button', { name: 'Set password' }))
    await waitFor(() => expect(screen.getByText(/ask an administrator to send a new link/i)).toBeVisible())
    expect(screen.queryByRole('button', { name: 'Set password' })).not.toBeInTheDocument()
    expect(api.apiRequest).toHaveBeenCalledTimes(1)
  })
})
