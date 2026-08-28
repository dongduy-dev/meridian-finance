import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { Button } from '@/components/ui/button'

import { PageHeader } from './PageHeader'

describe('PageHeader', () => {
  it('renders a focusable page heading, supporting copy, and actions', () => {
    render(
      <PageHeader
        eyebrow="Foundation"
        title="Customer Web"
        description="Reusable page structure"
        actions={<Button>Continue</Button>}
      />,
    )

    expect(screen.getByRole('heading', { level: 1, name: 'Customer Web' })).toHaveAttribute(
      'tabindex',
      '-1',
    )
    expect(screen.getByText('Reusable page structure')).toBeVisible()
    expect(screen.getByRole('button', { name: 'Continue' })).toBeEnabled()
  })
})
