import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { StrictMode } from 'react'
import { afterEach, expect, it, vi } from 'vitest'

import { AppProviders } from '@/app/providers/AppProviders'
import { queryClient } from '@/app/providers/query-client'
import { AuthSessionManager } from '@/features/auth/auth-session'
import { ApiError, NetworkError } from '@/lib/api'
import { createAuthApiMock } from '@/test/auth'
import { createTestRouter } from './router'

const fragment = '#verificationToken=verification-secret&setupToken=setup-secret'

function setup(hash = fragment) {
  const api = createAuthApiMock()
  vi.mocked(api.refresh).mockRejectedValue(new ApiError({ status: 401, errorCode: 'INVALID_REFRESH_TOKEN', message: 'Invalid' }))
  const clearPrivate = vi.fn()
  const manager = new AuthSessionManager(api, clearPrivate)
  window.history.replaceState(null, '', `/activate-access${hash}`)
  const router = createTestRouter([`/activate-access${hash}`])
  return { api, manager, router, clearPrivate,
    mount: () => render(<StrictMode><AppProviders router={router} authManager={manager} /></StrictMode>) }
}

afterEach(() => {
  window.history.replaceState(null, '', '/')
  queryClient.clear()
  vi.restoreAllMocks()
})

async function fillPassword(password = 'Customer-password-123', confirmation = password) {
  const user = userEvent.setup()
  await user.type(await screen.findByLabelText('New password'), password)
  await user.type(screen.getByLabelText('Confirm new password'), confirmation)
  await user.click(screen.getByRole('button', { name: 'Set password' }))
}

it('scrubs both secrets immediately, verifies once, then sets the first password without a recovery email or login', async () => {
  const local = vi.spyOn(Storage.prototype, 'setItem')
  const context = setup()
  let confirm!: () => void
  vi.mocked(context.api.confirmEmailVerification).mockReturnValue(new Promise<void>((resolve) => { confirm = resolve }))
  context.mount()
  expect(window.location.hash).toBe('')
  expect(screen.queryByLabelText('New password')).not.toBeInTheDocument()
  expect(context.api.confirmPasswordReset).not.toHaveBeenCalled()
  expect(context.api.confirmEmailVerification).toHaveBeenCalledExactlyOnceWith('verification-secret')
  confirm()
  await screen.findByRole('heading', { name: 'Set your password' })
  expect(await screen.findByLabelText('New password')).toHaveFocus()
  expect(screen.queryByRole('link', { name: /recover|forgot/i })).not.toBeInTheDocument()
  await fillPassword()
  await waitFor(() => expect(context.router.state.location.pathname).toBe('/login'))
  expect(await screen.findByText('Password set. Sign in to continue.')).toBeVisible()
  expect(context.api.confirmPasswordReset).toHaveBeenCalledExactlyOnceWith('setup-secret', 'Customer-password-123')
  expect(context.api.requestPasswordReset).not.toHaveBeenCalled()
  expect(context.api.login).not.toHaveBeenCalled()
  expect(context.manager.getSnapshot()).toEqual({ status: 'anonymous' })
  expect(context.clearPrivate).toHaveBeenCalled()
  expect(local).not.toHaveBeenCalled()
  expect(JSON.stringify(queryClient.getQueryCache().getAll())).not.toMatch(/verification-secret|setup-secret/)
})

it.each([
  ['short', 'short', 'Password must contain at least 12 characters.'],
  ['x'.repeat(73), 'x'.repeat(73), 'Password must contain no more than 72 characters.'],
  ['Customer-password-123', 'different-password', 'Passwords must match.'],
])('validates the existing password policy and confirmation (%s)', async (password, confirmation, message) => {
  const context = setup(); context.mount()
  await fillPassword(password, confirmation)
  expect(await screen.findByText(message)).toBeVisible()
  expect(context.api.confirmPasswordReset).not.toHaveBeenCalled()
})

it.each(['', '#verificationToken=only', '#setupToken=only'])('fails closed when activation information is missing (%s)', async (hash) => {
  const context = setup(hash); context.mount()
  expect(await screen.findByText('Activation information missing')).toBeVisible()
  expect(window.location.hash).toBe('')
  expect(context.api.confirmEmailVerification).not.toHaveBeenCalled()
  expect(context.api.confirmPasswordReset).not.toHaveBeenCalled()
  expect(screen.queryByLabelText('New password')).not.toBeInTheDocument()
})

it.each([false, true])('blocks setup on invalid or uncertain verification and offers recovery (network=%s)', async (network) => {
  const context = setup()
  vi.mocked(context.api.confirmEmailVerification).mockRejectedValue(network ? new NetworkError() : new ApiError({
    status: 401, errorCode: 'INVALID_EMAIL_VERIFICATION_TOKEN', message: 'Invalid',
  }))
  context.mount()
  expect(await screen.findByRole('link', { name: 'Request verification email' })).toHaveAttribute('href', '/verify-email/pending')
  expect(screen.queryByLabelText('New password')).not.toBeInTheDocument()
  expect(context.api.confirmPasswordReset).not.toHaveBeenCalled()
  expect(context.api.confirmEmailVerification).toHaveBeenCalledOnce()
})

it.each([false, true])('offers recovery without replay on invalid or uncertain setup (network=%s)', async (network) => {
  const context = setup()
  vi.mocked(context.api.confirmPasswordReset).mockRejectedValue(network ? new NetworkError() : new ApiError({
    status: 401, errorCode: 'INVALID_PASSWORD_RESET_TOKEN', message: 'Invalid',
  }))
  context.mount()
  await fillPassword()
  expect(await screen.findByRole('link', { name: 'Recover password setup' })).toHaveAttribute('href', '/forgot-password')
  expect(screen.queryByLabelText('New password')).not.toBeInTheDocument()
  expect(context.api.confirmPasswordReset).toHaveBeenCalledOnce()
  expect(context.api.requestPasswordReset).not.toHaveBeenCalled()
  if (network) expect(screen.getByText(/Try signing in with the password you chose/)).toBeVisible()
})

it('clears transient activation state on unmount and cannot restore it from the scrubbed URL', async () => {
  const context = setup()
  const view = context.mount()
  await screen.findByRole('heading', { name: 'Set your password' })
  view.unmount(); context.router.dispose()
  await Promise.resolve()
  const reloaded = setup(''); reloaded.mount()
  expect(await screen.findByText('Activation information missing')).toBeVisible()
  expect(reloaded.api.confirmEmailVerification).not.toHaveBeenCalled()
  expect(reloaded.api.confirmPasswordReset).not.toHaveBeenCalled()
})

it('does not reuse a previous confirmation when another activation mounts under the same router key', async () => {
  const context = setup()
  const view = context.mount()
  await screen.findByRole('heading', { name: 'Set your password' })
  view.unmount(); context.router.dispose()
  await Promise.resolve()
  vi.mocked(context.api.confirmEmailVerification).mockRejectedValue(new ApiError({
    status: 401, errorCode: 'INVALID_EMAIL_VERIFICATION_TOKEN', message: 'Invalid',
  }))
  window.history.replaceState(null, '', `/activate-access${fragment}`)
  const secondRouter = createTestRouter([`/activate-access${fragment}`])
  render(<StrictMode><AppProviders router={secondRouter} authManager={context.manager} /></StrictMode>)
  expect(await screen.findByText('Verification link unavailable')).toBeVisible()
  expect(context.api.confirmEmailVerification).toHaveBeenCalledTimes(2)
  expect(screen.queryByLabelText('New password')).not.toBeInTheDocument()
})
