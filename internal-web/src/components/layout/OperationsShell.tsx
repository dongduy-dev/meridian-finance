import { Menu, ShieldCheck, LogOut } from 'lucide-react'
import { useState } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router-dom'
import { MeridianLogo } from '@/components/common/MeridianLogo'
import { Button } from '@/components/ui/button'
import { Separator } from '@/components/ui/separator'
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle, SheetTrigger } from '@/components/ui/sheet'
import { useAuth } from '@/features/auth/model/auth-context'
import { cn } from '@/lib/cn'
import { permittedStaffRoutes, type StaffRouteDefinition } from '@/app/router/staff-route-metadata'

function Navigation({ routes, onNavigate }: { routes: readonly StaffRouteDefinition[]; onNavigate?: () => void }) {
  if (routes.length === 0) return null
  return (
    <nav aria-label="Staff navigation" className="px-3">
      {routes.map((route) => (
        <NavLink key={route.path} to={route.path} end onClick={onNavigate} className={({ isActive }) => cn(
          'flex min-h-11 items-center gap-3 rounded-md px-3 text-sm font-medium text-primary-foreground/75 hover:bg-white/10 hover:text-white',
          isActive && 'bg-white/12 text-white',
        )}>
          <ShieldCheck aria-hidden="true" className="size-5" /> {route.label}
        </NavLink>
      ))}
    </nav>
  )
}

export function OperationsShell() {
  const { manager, state } = useAuth()
  const [open, setOpen] = useState(false)
  const location = useLocation()
  if (state.status !== 'authenticated') return null

  const logout = () => void manager.logout()
  const navigationRoutes = permittedStaffRoutes(state.actor)
  const identity = (
    <div className="space-y-3 px-4 pb-5">
      <Separator className="bg-white/15" />
      <div className="min-w-0">
        <p className="truncate text-sm font-medium text-white">{state.actor.email}</p>
        <p className="text-xs text-primary-foreground/60">Staff session</p>
      </div>
      <Button className="w-full justify-start border-white/20 text-white hover:bg-white/10" variant="outline" onClick={logout}>
        <LogOut aria-hidden="true" /> Sign out
      </Button>
    </div>
  )

  return (
    <div className="min-h-screen lg:grid lg:grid-cols-[17rem_1fr]">
      <a href="#main-content" className="sr-only z-[60] bg-white p-3 focus:not-sr-only focus:fixed focus:top-3 focus:left-3">Skip to main content</a>
      <aside className="hidden min-h-screen flex-col bg-primary text-primary-foreground lg:flex">
        <div className="rounded-br-2xl bg-white p-6"><MeridianLogo className="h-8 w-auto" /></div>
        <Navigation routes={navigationRoutes} />
        <div className="mt-auto">{identity}</div>
      </aside>
      <div className="min-w-0">
        <header className="flex h-16 items-center justify-between border-b bg-card px-4 lg:px-8">
          <div className="flex items-center gap-3 lg:hidden">
            {navigationRoutes.length > 0 ? <Sheet open={open} onOpenChange={setOpen}>
              <SheetTrigger asChild><Button size="icon" variant="ghost" aria-label="Open navigation"><Menu /></Button></SheetTrigger>
              <SheetContent key={location.pathname}>
                <SheetHeader><SheetTitle>Staff navigation</SheetTitle><SheetDescription>Meridian internal workspace</SheetDescription></SheetHeader>
                <Navigation routes={navigationRoutes} onNavigate={() => setOpen(false)} />
                <div className="mt-auto">{identity}</div>
              </SheetContent>
            </Sheet> : null}
            <MeridianLogo className="h-7 w-auto" />
          </div>
          <p className="hidden text-sm font-medium lg:block">Internal workspace</p>
          <p className="max-w-48 truncate text-sm text-muted-foreground lg:max-w-xs">{state.actor.email}</p>
        </header>
        <main id="main-content" className="min-w-0 overflow-x-hidden p-4 sm:p-6 lg:p-8"><Outlet /></main>
      </div>
    </div>
  )
}
