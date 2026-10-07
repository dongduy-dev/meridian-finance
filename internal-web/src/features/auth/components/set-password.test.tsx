import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { StrictMode, Suspense, useLayoutEffect } from 'react'
import { BrowserRouter, createBrowserRouter, Route, RouterProvider, Routes } from 'react-router-dom'
import { AppProviders } from '@/app/providers/AppProviders'
import { routes } from '@/app/router/router'
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
  return render(<StrictMode><BrowserRouter><Routes>
    <Route path="/set-password" element={<SetPasswordPage />} />
    <Route path="/login" element={<p>Staff login destination</p>} />
  </Routes></BrowserRouter></StrictMode>)
}

describe('Internal Web password setup', () => {
  beforeEach(() => { vi.clearAllMocks(); localStorage.clear(); sessionStorage.clear() })

  it('shows the valid form and scrubs the fragment before the first paint', () => {
    function BeforePaintProbe() {
      useLayoutEffect(() => {
        expect(window.location.hash).toBe('')
        expect(screen.getByLabelText('New password')).toBeVisible()
        expect(screen.queryByText(/setup link is incomplete/i)).not.toBeInTheDocument()
      }, [])
      return null
    }
    window.history.replaceState({}, '', '/set-password#token=before-paint-value')
    render(<StrictMode><BrowserRouter><SetPasswordPage /><BeforePaintProbe /></BrowserRouter></StrictMode>)
  })

  it('retains the token when concurrent initialization suspends before committing', async () => {
    let ready = false
    let resume!: () => void
    const deferred = new Promise<void>((resolve) => { resume = resolve })
    function DeferredSibling() { if (!ready) throw deferred; return null }
    window.history.replaceState({}, '', '/set-password#token=concurrent-value')
    render(<StrictMode><BrowserRouter><Suspense fallback={<p>Loading setup</p>}>
      <SetPasswordPage /><DeferredSibling />
    </Suspense></BrowserRouter></StrictMode>)
    await Promise.resolve()
    ready = true
    resume()
    expect(await screen.findByLabelText('New password')).toBeVisible()
    expect(window.location.hash).toBe('')
  })

  it('retains the setup form through the production lazy router under Strict Mode', async () => {
    window.history.replaceState({}, '', '/set-password#token=production-opaque-value')
    const router = createBrowserRouter(routes)
    const view = render(<StrictMode><AppProviders><RouterProvider router={router} /></AppProviders></StrictMode>)
    try {
      expect(await screen.findByLabelText('New password')).toBeVisible()
      expect(window.location.hash).toBe('')
    } finally { view.unmount(); router.dispose() }
  })

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
    expect(api.apiRequest).toHaveBeenCalledTimes(1)
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

  it('does not restore a token on a genuine later mount at the scrubbed URL', async () => {
    const first = renderSetup('#token=departed-secret')
    expect(screen.getByLabelText('New password')).toBeVisible()
    expect(window.location.hash).toBe('')
    first.unmount()
    renderSetup('')
    expect(screen.getByText(/setup link is incomplete/i)).toBeVisible()
    expect(screen.queryByLabelText('New password')).not.toBeInTheDocument()
    expect(api.apiRequest).not.toHaveBeenCalled()
  })

  it('uses only the fresh link and safely decodes its fragment characters', async () => {
    vi.mocked(api.apiRequest).mockResolvedValue(undefined)
    const first = renderSetup('#token=old-secret')
    first.unmount()
    const token = 'fresh+value/with=encoded &characters%'
    renderSetup(`#token=${encodeURIComponent(token)}`)
    expect(window.location.hash).toBe('')
    const user = userEvent.setup()
    await user.type(screen.getByLabelText('New password'), 'A-Valid-Password-123')
    await user.type(screen.getByLabelText('Confirm new password'), 'A-Valid-Password-123')
    await user.click(screen.getByRole('button', { name: 'Set password' }))
    expect(await screen.findByText('Staff login destination')).toBeVisible()
    expect(api.apiRequest).toHaveBeenCalledExactlyOnceWith('/auth/password-reset/confirm', {
      method: 'POST', body: { token, newPassword: 'A-Valid-Password-123' },
    })
    expect(localStorage.length).toBe(0)
    expect(sessionStorage.length).toBe(0)
  })

  it('offers sign-in or a fresh link after an uncertain result without replaying the password', async () => {
    vi.mocked(api.apiRequest).mockRejectedValue(new api.NetworkError())
    renderSetup()
    const user = userEvent.setup()
    await user.type(screen.getByLabelText('New password'), 'A-Valid-Password-123')
    await user.type(screen.getByLabelText('Confirm new password'), 'A-Valid-Password-123')
    await user.dblClick(screen.getByRole('button', { name: 'Set password' }))
    expect(await screen.findByText(/password result could not be confirmed/i)).toBeVisible()
    expect(screen.queryByLabelText('New password')).not.toBeInTheDocument()
    expect(api.apiRequest).toHaveBeenCalledTimes(1)
    expect(screen.getByRole('link', { name: 'Back to Staff sign in' })).toHaveAttribute('href', '/login')
  })
})
