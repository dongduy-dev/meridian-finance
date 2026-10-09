import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { StrictMode } from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { queryClient } from '@/app/providers/query-client'
import { AppProviders } from '@/app/providers/AppProviders'
import type { AuthApi } from '@/features/auth/auth-api'
import { AuthSessionManager } from '@/features/auth/auth-session'
import { ApiError, NetworkError } from '@/lib/api'
import { createAuthApiMock } from '@/test/auth'

import { createTestRouter } from './router'

function invalidRefresh() {
  return new ApiError({
    status: 401,
    errorCode: 'INVALID_REFRESH_TOKEN',
    message: 'Refresh authentication failed',
  })
}

function renderAuthRoute(path: string, api: AuthApi, strict = false) {
  const manager = new AuthSessionManager(api, vi.fn())
  const router = createTestRouter([path])
  const application = <AppProviders router={router} authManager={manager} />
  const view = render(strict ? <StrictMode>{application}</StrictMode> : application)
  return { manager, router, unmount: view.unmount }
}

function anonymousApi() {
  const api = createAuthApiMock()
  vi.mocked(api.refresh).mockRejectedValue(invalidRefresh())
  return api
}

async function completeLogin(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText('Email'), 'customer@example.com')
  await user.type(screen.getByLabelText('Password'), 'not-retained-password')
  await user.click(screen.getByRole('button', { name: 'Log in' }))
}

afterEach(() => {
  window.history.replaceState(null, '', '/')
  queryClient.clear()
  vi.unstubAllGlobals()
})

describe('Customer authentication routes', () => {
  it.each([
    { path: '/login', labels: ['Password'], autocomplete: 'current-password', command: 'login' as const },
    { path: '/register', labels: ['Password'], autocomplete: 'new-password', command: 'register' as const },
    { path: '/reset-password#token=visibility-reset', labels: ['New password', 'Confirm new password'], autocomplete: 'new-password', command: 'confirmPasswordReset' as const },
    { path: '/activate-access#verificationToken=visibility-email&setupToken=visibility-setup', labels: ['New password', 'Confirm new password'], autocomplete: 'new-password', command: 'confirmPasswordReset' as const },
  ])('toggles only the primary password on $path without submitting or revealing confirmation', async ({ path, labels, autocomplete, command }) => {
    const user = userEvent.setup()
    const api = anonymousApi()
    window.history.replaceState(null, '', path)
    renderAuthRoute(path, api)
    const first = await screen.findByLabelText(labels[0]!)
    const fields = labels.map((label) => screen.getByLabelText(label))
    expect(screen.getAllByRole('button', { name: 'Show password' })).toHaveLength(1)
    const confirmation = fields[1]
    if (confirmation) expect(within(confirmation.parentElement!).queryByRole('button')).not.toBeInTheDocument()
    if (path === '/register') {
      expect(screen.getByText('Create your account, then confirm your email to sign in.')).toBeVisible()
      await user.type(screen.getByLabelText('Display name'), 'Meridian Customer')
    }
    if (screen.queryByLabelText('Email')) await user.type(screen.getByLabelText('Email'), 'customer@example.com')
    for (const field of fields) {
      expect(field).toHaveAttribute('type', 'password')
      expect(field).toHaveAttribute('autocomplete', autocomplete)
      await user.type(field, 'visibility-password-value')
    }
    for (const field of [first]) {
      const controls = within(field.parentElement!)
      const describedBy = field.getAttribute('aria-describedby')
      const show = controls.getByRole('button', { name: 'Show password' })
      expect(show).toHaveAttribute('type', 'button')
      expect(show).toHaveAttribute('aria-controls', field.id)
      await user.click(show)
      expect(field).toHaveAttribute('type', 'text')
      expect(field).toHaveValue('visibility-password-value')
      expect(field).toHaveAttribute('aria-invalid', 'false')
      expect(field.getAttribute('aria-describedby')).toBe(describedBy)
      if (confirmation) {
        expect(confirmation).toHaveAttribute('type', 'password')
        expect(confirmation).toHaveValue('visibility-password-value')
        expect(confirmation).toHaveAttribute('autocomplete', 'new-password')
        expect(within(confirmation.parentElement!).queryByRole('button')).not.toBeInTheDocument()
      }
      await user.click(controls.getByRole('button', { name: 'Hide password' }))
      expect(field).toHaveAttribute('type', 'password')
      expect(field).toHaveValue('visibility-password-value')
    }
    if (confirmation) expect(confirmation).toHaveAttribute('type', 'password')
    expect(api.login).not.toHaveBeenCalled()
    expect(api.register).not.toHaveBeenCalled()
    expect(api.confirmPasswordReset).not.toHaveBeenCalled()
    // Native Enter submission also works while the input is revealed.
    await user.click(within(first.parentElement!).getByRole('button', { name: 'Show password' }))
    await user.click(first)
    await user.keyboard('{Enter}')
    await waitFor(() => expect(api[command]).toHaveBeenCalledTimes(1))
  })

  it('establishes login and restores the intended protected destination', async () => {
    const user = userEvent.setup()
    const api = anonymousApi()
    const { router } = renderAuthRoute('/loans', api)
    await screen.findByRole('heading', { name: 'Welcome back' })

    await completeLogin(user)

    await waitFor(() => expect(router.state.location.pathname).toBe('/loans'))
    expect(await screen.findByRole('heading', { name: 'Your loans' })).toBeVisible()
  })

  it('keeps invalid credentials generic and never calls refresh recovery', async () => {
    const user = userEvent.setup()
    const api = anonymousApi()
    vi.mocked(api.login).mockRejectedValue(
      new ApiError({
        status: 401,
        errorCode: 'INVALID_CREDENTIALS',
        message: 'Invalid credentials.',
      }),
    )
    renderAuthRoute('/login', api)
    await screen.findByRole('heading', { name: 'Welcome back' })
    vi.mocked(api.refresh).mockClear()

    await completeLogin(user)

    expect(await screen.findByText('The email or password could not be verified.')).toBeVisible()
    expect(screen.queryByText(/locked|attempt/i)).not.toBeInTheDocument()
    expect(api.refresh).not.toHaveBeenCalled()
    expect(screen.getByLabelText('Password')).toHaveValue('')
  })

  it('routes EMAIL_VERIFICATION_REQUIRED to the pending page with safe email state', async () => {
    const user = userEvent.setup()
    const api = anonymousApi()
    vi.mocked(api.login).mockRejectedValue(
      new ApiError({
        status: 401,
        errorCode: 'EMAIL_VERIFICATION_REQUIRED',
        message: 'Email verification required.',
      }),
    )
    const { router } = renderAuthRoute('/login', api)
    await screen.findByRole('heading', { name: 'Welcome back' })

    await completeLogin(user)

    await waitFor(() => expect(router.state.location.pathname).toBe('/verify-email/pending'))
    expect(await screen.findByLabelText('Email')).toHaveValue('customer@example.com')
  })

  it('moves registration to verification pending without creating a session', async () => {
    const user = userEvent.setup()
    const api = anonymousApi()
    const { manager, router } = renderAuthRoute('/register', api)

    await user.type(await screen.findByLabelText('Display name'), 'Meridian Customer')
    await user.type(screen.getByLabelText('Email'), 'new@example.com')
    await user.type(screen.getByLabelText('Password'), 'twelve-characters')
    await user.click(screen.getByRole('button', { name: 'Create account' }))

    await waitFor(() => expect(router.state.location.pathname).toBe('/verify-email/pending'))
    expect(manager.getSnapshot()).toEqual({ status: 'anonymous' })
    expect(screen.getByLabelText('Email')).toHaveValue('new@example.com')
  })

  it('keeps resend and forgot-password success copy enumeration-safe', async () => {
    const user = userEvent.setup()
    const verificationApi = anonymousApi()
    const verification = renderAuthRoute('/verify-email/pending', verificationApi)
    await user.type(await screen.findByLabelText('Email'), 'unknown@example.com')
    await user.click(screen.getByRole('button', { name: 'Resend verification email' }))
    expect(await screen.findByText(/If this email is eligible/)).toBeVisible()
    verification.unmount()
    verification.router.dispose()

    const resetApi = anonymousApi()
    renderAuthRoute('/forgot-password', resetApi)
    await user.type(await screen.findByLabelText('Email'), 'unknown@example.com')
    await user.click(screen.getByRole('button', { name: 'Send reset link' }))
    expect(await screen.findByText(/If this email is eligible/)).toBeVisible()
  })

  it('captures and removes a verification fragment and confirms only once in Strict Mode', async () => {
    const api = anonymousApi()
    window.history.replaceState(null, '', '/verify-email#token=opaque-email-token')
    renderAuthRoute('/verify-email#token=opaque-email-token', api, true)

    expect(await screen.findByText('Your email is confirmed. If you already have a password, you can log in. If you still need to set your first password, use Forgot password on the login page.')).toBeVisible()
    expect(api.confirmEmailVerification).toHaveBeenCalledOnce()
    expect(api.confirmEmailVerification).toHaveBeenCalledWith('opaque-email-token')
    expect(window.location.hash).toBe('')
  })

  it('handles missing and invalid verification tokens with resend recovery', async () => {
    const missingApi = anonymousApi()
    const missing = renderAuthRoute('/verify-email', missingApi)
    expect(await screen.findByText('This confirmation link is incomplete. Request another verification email.')).toBeVisible()
    expect(screen.getByRole('link', { name: 'Request another email' })).toHaveAttribute('href', '/verify-email/pending')
    missing.unmount()
    missing.router.dispose()

    const invalidApi = anonymousApi()
    vi.mocked(invalidApi.confirmEmailVerification).mockRejectedValue(
      new ApiError({
        status: 401,
        errorCode: 'INVALID_EMAIL_VERIFICATION_TOKEN',
        message: 'Email verification token is invalid or expired.',
      }),
    )
    window.history.replaceState(null, '', '/verify-email#token=invalid-email-token')
    renderAuthRoute('/verify-email#token=invalid-email-token', invalidApi)
    expect(await screen.findByText('This link is invalid or has expired. Request another verification email.')).toBeVisible()
  })

  it('validates password confirmation and clears local session after reset success', async () => {
    const user = userEvent.setup()
    const api = anonymousApi()
    window.history.replaceState(null, '', '/reset-password#token=opaque-reset-token')
    const { manager, router } = renderAuthRoute('/reset-password#token=opaque-reset-token', api)
    await screen.findByRole('heading', { name: 'Choose a new password' })

    await user.type(screen.getByLabelText('New password'), 'new-password-value')
    await user.type(screen.getByLabelText('Confirm new password'), 'different-value')
    await user.click(screen.getByRole('button', { name: 'Update password' }))
    expect(await screen.findByText('Passwords must match.')).toBeVisible()
    expect(api.confirmPasswordReset).not.toHaveBeenCalled()

    await user.clear(screen.getByLabelText('Confirm new password'))
    await user.type(screen.getByLabelText('Confirm new password'), 'new-password-value')
    await user.click(screen.getByRole('button', { name: 'Update password' }))

    await waitFor(() => expect(router.state.location.pathname).toBe('/login'))
    expect(await screen.findByText('Log in with your new password to continue.')).toBeVisible()
    expect(manager.getSnapshot()).toEqual({ status: 'anonymous' })
  })

  it('offers forgot-password recovery for an invalid reset token', async () => {
    const user = userEvent.setup()
    const api = anonymousApi()
    vi.mocked(api.confirmPasswordReset).mockRejectedValue(
      new ApiError({
        status: 401,
        errorCode: 'INVALID_PASSWORD_RESET_TOKEN',
        message: 'Password reset token is invalid or expired.',
      }),
    )
    window.history.replaceState(null, '', '/reset-password#token=invalid-reset-token')
    renderAuthRoute('/reset-password#token=invalid-reset-token', api)

    await user.type(await screen.findByLabelText('New password'), 'new-password-value')
    await user.type(screen.getByLabelText('Confirm new password'), 'new-password-value')
    await user.click(screen.getByRole('button', { name: 'Update password' }))

    expect(await screen.findByText('This password-reset link is invalid or has expired.')).toBeVisible()
    expect(screen.getByRole('link', { name: 'Request another reset link' })).toHaveAttribute(
      'href',
      '/forgot-password',
    )
  })

  it('shows request correlation on an unexpected login failure and clears the password', async () => {
    const user = userEvent.setup()
    const api = anonymousApi()
    vi.mocked(api.login).mockRejectedValue(
      new ApiError({
        status: 503,
        errorCode: 'SERVICE_TEMPORARILY_UNAVAILABLE',
        message: 'Unavailable',
        requestId: '33333333-3333-4333-8333-333333333333',
      }),
    )
    renderAuthRoute('/login', api)
    await screen.findByRole('heading', { name: 'Welcome back' })

    await completeLogin(user)

    expect(await screen.findByText(/Support reference: 33333333/)).toBeVisible()
    expect(screen.getByLabelText('Password')).toHaveValue('')
  })

  it('clears locally and returns to Login even when logout transport fails', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({ errorCode: 'SERVICE_TEMPORARILY_UNAVAILABLE', message: 'Unavailable' }), { status: 503, headers: { 'Content-Type': 'application/json' } })))
    const user = userEvent.setup()
    const api = createAuthApiMock()
    vi.mocked(api.logout).mockRejectedValue(new NetworkError())
    const { manager, router } = renderAuthRoute('/', api)
    await screen.findByRole('heading', { name: 'Home' })

    await user.click(screen.getByRole('button', { name: 'Log out' }))

    await waitFor(() => expect(router.state.location.pathname).toBe('/login'))
    expect(manager.getSnapshot()).toEqual({ status: 'anonymous' })
  })
})
