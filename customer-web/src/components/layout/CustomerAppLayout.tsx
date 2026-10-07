import {
  CircleUserRound,
  Files,
  Landmark,
  LayoutDashboard,
  LogOut,
  Menu,
  Shapes,
} from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router-dom'
import { useNavigate } from 'react-router-dom'

import { MeridianLogo } from '@/components/common/MeridianLogo'
import { Button } from '@/components/ui/button'
import { Separator } from '@/components/ui/separator'
import {
  Sheet,
  SheetClose,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from '@/components/ui/sheet'
import { cn } from '@/lib/cn'
import { useAuth } from '@/features/auth/auth-context'

interface NavigationItem {
  label: string
  href: string
  icon: LucideIcon
  end?: boolean
}

const navigation: NavigationItem[] = [
  { label: 'Home', href: '/', icon: LayoutDashboard, end: true },
  { label: 'Products', href: '/products', icon: Shapes },
  { label: 'Applications', href: '/applications', icon: Files },
  { label: 'Loans', href: '/loans', icon: Landmark },
  { label: 'Account', href: '/account', icon: CircleUserRound },
]

function NavigationLinks({ mobile = false }: { mobile?: boolean }) {
  const { pathname } = useLocation()

  return (
    <nav aria-label="Main navigation" className={mobile ? 'space-y-2' : 'flex flex-wrap items-center gap-2'}>
      {navigation.map(({ label, href, icon: Icon, end }) => {
        const isActive = end
          ? pathname === href
          : pathname === href || pathname.startsWith(`${href}/`)
        const link = (
          <NavLink
            key={href}
            to={href}
            end={end}
            className={cn(
              'main-nav-link flex min-h-11 items-center gap-3 px-4 py-3 text-sm leading-5 font-medium',
              isActive && 'font-semibold',
            )}
          >
            {mobile ? <Icon aria-hidden="true" className="size-5 shrink-0" /> : null}
            <span>{label}</span>
          </NavLink>
        )

        return mobile ? (
          <SheetClose asChild key={href}>
            {link}
          </SheetClose>
        ) : (
          link
        )
      })}
    </nav>
  )
}

export function CustomerAppLayout() {
  const { manager } = useAuth()
  const navigate = useNavigate()
  const [isLoggingOut, setIsLoggingOut] = useState(false)
  const [menuOpen, setMenuOpen] = useState(false)
  const headerRef = useRef<HTMLElement>(null)

  useLayoutEffect(() => {
    const header = headerRef.current
    if (!header || typeof ResizeObserver === 'undefined') return
    const updateClearance = () => document.documentElement.style.setProperty('--header-clearance', `${header.getBoundingClientRect().height}px`)
    updateClearance()
    const observer = new ResizeObserver(updateClearance)
    observer.observe(header)
    return () => {
      observer.disconnect()
      document.documentElement.style.removeProperty('--header-clearance')
    }
  }, [])

  useEffect(() => {
    if (typeof matchMedia === 'undefined') return
    const desktop = matchMedia('(min-width: 1280px)')
    const closeOnDesktop = () => { if (desktop.matches) setMenuOpen(false) }
    desktop.addEventListener('change', closeOnDesktop)
    return () => desktop.removeEventListener('change', closeOnDesktop)
  }, [])

  const logout = async () => {
    setIsLoggingOut(true)
    try {
      await manager.logout()
    } catch {
      // Local session clearing is guaranteed by the session manager.
    } finally {
      navigate('/login', { replace: true })
    }
  }

  return (
    <div className="min-h-svh bg-background">
      <header ref={headerRef} className="on-navy sticky top-0 z-20">
        <div className="page-container flex min-h-16 flex-wrap items-center justify-between gap-x-4 gap-y-2 py-2 xl:min-h-20">
          <MeridianLogo variant="wordmark" className="w-36 shrink-0 xl:w-44" />
          <div className="hidden xl:block"><NavigationLinks /></div>
          <div className="flex items-center gap-2" aria-label="Account area">
            <div className="xl:hidden">
              <Sheet open={menuOpen} onOpenChange={setMenuOpen}>
                <SheetTrigger asChild>
                  <Button variant="ghost" size="icon" className="text-primary-foreground" aria-label="Open menu" title="Open menu">
                    <Menu aria-hidden="true" />
                  </Button>
                </SheetTrigger>
                <SheetContent className="on-navy">
                  <SheetHeader>
                    <SheetTitle className="text-primary-foreground">Menu</SheetTitle>
                    <SheetDescription className="text-primary-foreground">Choose where you want to go.</SheetDescription>
                  </SheetHeader>
                  <div className="px-4 pb-6 sm:px-6">
                    <MeridianLogo variant="wordmark" className="mb-6 w-36" />
                    <Separator className="mb-6 bg-input" />
                    <NavigationLinks mobile />
                  </div>
                </SheetContent>
              </Sheet>
            </div>
            <Button variant="ghost" size="sm" className="text-primary-foreground"
              aria-label="Log out" title="Log out" disabled={isLoggingOut} onClick={() => void logout()}>
              <LogOut aria-hidden="true" />
              <span className="hidden sm:inline">{isLoggingOut ? 'Logging out…' : 'Log out'}</span>
            </Button>
          </div>
        </div>
      </header>
      <main className="page-container py-8 md:py-12"><Outlet /></main>
    </div>
  )
}
