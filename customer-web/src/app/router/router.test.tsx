import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'

import { AppProviders } from '@/app/providers/AppProviders'
import { createTestAuthManager } from '@/test/auth'

import { createTestRouter } from './router'

function renderRoute(path: string) {
  const router = createTestRouter([path])
  render(<AppProviders router={router} authManager={createTestAuthManager()} />)
  return router
}

describe('application routing and shell', () => {
  it('bootstraps the Customer shell with the planned navigation vocabulary', async () => {
    renderRoute('/')

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Dashboard' }),
    ).toBeVisible()
    for (const label of ['Dashboard', 'Products', 'Applications', 'Loans', 'Account']) {
      expect(screen.getByRole('link', { name: label })).toBeVisible()
    }
    expect(within(screen.getByRole('complementary')).queryByText(/FE-CP|checkpoint/i)).not.toBeInTheDocument()
    const navigationTrigger = screen.getByRole('button', { name: 'Open menu' })
    const shellBanner = navigationTrigger.closest('header') as HTMLElement

    expect(within(shellBanner).queryByText(/FE-CP|checkpoint/i)).not.toBeInTheDocument()
    expect(navigationTrigger).toHaveAccessibleName('Open menu')
  })

  it.each([
    '/foundation/flow',
    '/foundation/detail',
  ])('does not expose the foundation preview route at %s', async (path) => {
    renderRoute(path)

    expect(await screen.findByText('This page is not available')).toBeVisible()
    expect(screen.getByRole('link', { name: 'Return to dashboard' })).toBeVisible()
  })

  it('keeps mobile navigation links composed, active, and dismissible', async () => {
    const user = userEvent.setup()
    const router = renderRoute('/')
    const trigger = await screen.findByRole('button', { name: 'Open menu' })

    trigger.focus()
    await user.keyboard('{Enter}')

    const dialog = await screen.findByRole('dialog', { name: 'Menu' })
    const productsLink = within(dialog).getByRole('link', { name: 'Products' })

    expect(within(dialog).getByText('Choose where you want to go.')).toBeVisible()
    expect(within(dialog).queryByText(/FE-CP|checkpoint/i)).not.toBeInTheDocument()
    expect(productsLink).toHaveClass('flex')
    expect(productsLink.className).not.toContain('isActive')

    await user.click(productsLink)

    await waitFor(() => expect(router.state.location.pathname).toBe('/products'))
    await waitFor(() =>
      expect(
        screen.queryByRole('dialog', { name: 'Menu' }),
      ).not.toBeInTheDocument(),
    )
    expect(screen.getByRole('link', { name: 'Products' })).toHaveAttribute(
      'aria-current',
      'page',
    )
  })

  it('renders a safe not-found boundary', async () => {
    renderRoute('/not-a-meridian-route')

    expect(await screen.findByText('This page is not available')).toBeVisible()
    expect(screen.getByRole('link', { name: 'Return to dashboard' })).toBeVisible()
  })
})
