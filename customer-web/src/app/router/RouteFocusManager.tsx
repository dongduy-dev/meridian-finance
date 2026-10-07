import { useEffect } from 'react'
import { Outlet, useLocation } from 'react-router-dom'

import { customerRouteTitle } from './route-titles'

export function RouteFocusManager() {
  const location = useLocation()

  useEffect(() => {
    document.title = `${customerRouteTitle(location.pathname, location.search)} | Meridian`
  }, [location.pathname, location.search])

  useEffect(() => {
    const focusHeading = () => {
      const heading = document.getElementById('page-heading')
      if (!heading) return false
      heading.focus()
      return true
    }
    if (focusHeading()) return undefined
    const observer = new MutationObserver(() => {
      if (focusHeading()) observer.disconnect()
    })
    observer.observe(document.body, { childList: true, subtree: true })
    return () => observer.disconnect()
  }, [location.pathname])

  return <Outlet />
}
