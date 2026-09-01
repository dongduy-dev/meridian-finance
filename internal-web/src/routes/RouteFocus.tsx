import { useEffect } from 'react'
import { useLocation } from 'react-router-dom'

const titles: Record<string, string> = { '/login': 'Staff sign in | Meridian', '/staff': 'Internal operations | Meridian' }

export function RouteFocus() {
  const { pathname } = useLocation()
  useEffect(() => {
    document.title = titles[pathname] ?? 'Page not found | Meridian'
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
