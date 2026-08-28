import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'

import { AppProviders } from '@/app/providers/AppProviders'

import { createTestRouter } from './router'

function renderRoute(path: string) {
  const router = createTestRouter([path])
  render(<AppProviders router={router} />)
  return router
}

describe('application routing foundation', () => {
  it('bootstraps the Customer shell with the planned navigation vocabulary', async () => {
    renderRoute('/')

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Customer Web foundation' }),
    ).toBeVisible()
    for (const label of ['Dashboard', 'Products', 'Applications', 'Loans', 'Account']) {
      expect(screen.getByRole('link', { name: label })).toBeVisible()
    }
    expect(
      screen.getByRole('button', { name: 'Open customer navigation' }),
    ).toHaveAccessibleName('Open customer navigation')
  })

  it.each([
    ['/foundation/auth', 'A calm start to every secure journey'],
    ['/foundation/flow', 'One clear task at a time'],
    ['/foundation/detail', 'Information with a clear hierarchy'],
  ])('renders the layout demonstration at %s', async (path, heading) => {
    renderRoute(path)

    expect(await screen.findByRole('heading', { level: 1, name: heading })).toBeVisible()
  })

  it('keeps mobile navigation links composed, active, and dismissible', async () => {
    const user = userEvent.setup()
    const router = renderRoute('/')
    const trigger = screen.getByRole('button', { name: 'Open customer navigation' })

    trigger.focus()
    await user.keyboard('{Enter}')

    const dialog = await screen.findByRole('dialog', { name: 'Customer navigation' })
    const productsLink = within(dialog).getByRole('link', { name: 'Products' })

    expect(productsLink).toHaveClass('flex')
    expect(productsLink.className).not.toContain('isActive')

    await user.click(productsLink)

    await waitFor(() => expect(router.state.location.pathname).toBe('/products'))
    await waitFor(() =>
      expect(
        screen.queryByRole('dialog', { name: 'Customer navigation' }),
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
    expect(screen.getByRole('link', { name: 'Return to foundation' })).toBeVisible()
  })
})
