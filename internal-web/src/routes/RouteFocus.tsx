import { useEffect } from 'react'
import { useLocation } from 'react-router-dom'

function routeTitle(pathname: string): string {
  if (pathname === '/login') return 'Staff sign in | Meridian'
  if (pathname === '/staff') return 'Internal operations | Meridian'
  if (pathname === '/staff/applications') return 'Applications | Meridian'
  if (pathname.startsWith('/staff/applications/')) return 'Application case | Meridian'
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
