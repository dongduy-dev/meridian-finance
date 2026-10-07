import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { RouteErrorPage } from './RouteErrorPage'

describe('route failure recovery', () => {
  it('keeps the outcome of an interrupted command open and names the recovery action', () => {
    render(<RouteErrorPage />)
    expect(screen.getByText(/review its result and recovery guidance before repeating it/i)).toBeVisible()
    expect(screen.getByRole('button', { name: 'Reload workspace' })).toBeVisible()
    expect(screen.queryByText(/No data was changed/i)).not.toBeInTheDocument()
  })
})
