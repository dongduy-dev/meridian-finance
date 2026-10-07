import { useEffect } from 'react'
import { matchPath, useLocation } from 'react-router-dom'

import { ADMIN_ROUTES } from '@/app/router/admin-route-metadata'
import { STAFF_EXECUTABLE_ROUTES } from '@/app/router/staff-route-metadata'

const executableRoutes = [...STAFF_EXECUTABLE_ROUTES, ...ADMIN_ROUTES]

function routeTitle(pathname: string): string {
  if (pathname === '/login') return 'Staff sign in | Meridian'
  if (pathname === '/set-password') return 'Set your Staff password | Meridian'
  const route = executableRoutes.find((candidate) => matchPath({ path: candidate.path, end: true }, pathname))
  if (route) return `${route.documentTitle} | Meridian`
  return 'Page not found | Meridian'
}

export function RouteFocus() {
  const { pathname } = useLocation()
  useEffect(() => {
    document.title = routeTitle(pathname)
    const focusHeading = () => {
      const heading = document.querySelector<HTMLElement>('[data-route-heading]')
      if (!heading) return false
      heading.focus()
      return true
    }
    if (focusHeading()) return
    const observer = new MutationObserver(() => {
      if (focusHeading()) observer.disconnect()
    })
    observer.observe(document.body, { childList: true, subtree: true })
    return () => observer.disconnect()
  }, [pathname])
  return null
}
