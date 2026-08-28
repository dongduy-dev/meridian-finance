import { Circle } from 'lucide-react'
import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { EmptyState } from './EmptyState'

describe('EmptyState', () => {
  it('communicates its title and description without depending on the icon', () => {
    render(
      <EmptyState
        icon={Circle}
        title="Nothing here yet"
        description="The description explains the supported next step."
      />,
    )

    expect(screen.getByRole('heading', { level: 2, name: 'Nothing here yet' })).toBeVisible()
    expect(screen.getByText('The description explains the supported next step.')).toBeVisible()
  })
})
