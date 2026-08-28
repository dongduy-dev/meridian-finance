import { useEffect } from 'react'
import { Outlet, useLocation } from 'react-router-dom'

const routeTitles: Record<string, string> = {
  '/': 'Customer Web Foundation',
  '/products': 'Products Foundation',
  '/applications': 'Applications Foundation',
  '/loans': 'Loans Foundation',
  '/account': 'Account Foundation',
  '/foundation/auth': 'Auth Layout Foundation',
  '/foundation/flow': 'Focused Flow Foundation',
  '/foundation/detail': 'Detail Layout Foundation',
}

export function RouteFocusManager() {
  const location = useLocation()

  useEffect(() => {
    document.title = `${routeTitles[location.pathname] ?? 'Page unavailable'} | Meridian`
    document.getElementById('page-heading')?.focus()
  }, [location.pathname])

  return <Outlet />
}
