import { useEffect, useState } from 'react'
import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { RouteFocus } from './RouteFocus'

function DeferredHeading() {
  const [ready, setReady] = useState(false)
  useEffect(() => { queueMicrotask(() => setReady(true)) }, [])
  return ready ? <h1 data-route-heading tabIndex={-1}>Deferred workspace</h1> : null
}

describe('route focus', () => {
  it('focuses a route heading that mounts after the route frame', async () => {
    render(<MemoryRouter initialEntries={['/staff']}><RouteFocus /><DeferredHeading /></MemoryRouter>)
    await waitFor(() => expect(screen.getByRole('heading', { name: 'Deferred workspace' })).toHaveFocus())
    expect(document.title).toBe('Internal operations | Meridian')
  })

  it.each([
    ['/staff/work/documents', 'Document review | Meridian'],
    ['/staff/work/corrections', 'Staff corrections | Meridian'],
    ['/staff/applications/11111111-1111-4111-8111-111111111111/documents', 'Application documents | Meridian'],
    ['/staff/applications/11111111-1111-4111-8111-111111111111/corrections', 'Application corrections | Meridian'],
  ])('publishes an accurate title for %s', async (path, title) => {
    render(<MemoryRouter initialEntries={[path]}><RouteFocus /><DeferredHeading /></MemoryRouter>)
    await waitFor(() => expect(document.title).toBe(title))
  })
})
