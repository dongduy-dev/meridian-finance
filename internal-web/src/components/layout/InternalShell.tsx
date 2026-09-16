import { LogOut, Menu, ShieldCheck } from 'lucide-react'
import { useState } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router-dom'
import { ADMIN_HOME_ROUTE, permittedAdminRoutes } from '@/app/router/admin-route-metadata'
import { permittedStaffRoutes } from '@/app/router/staff-route-metadata'
import { MeridianLogo } from '@/components/common/MeridianLogo'
import { Button } from '@/components/ui/button'
import { Separator } from '@/components/ui/separator'
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle, SheetTrigger } from '@/components/ui/sheet'
import { useAuth } from '@/features/auth/model/auth-context'
import { hasBackOfficeAccess, hasStaffWebAccess, type StaffActor } from '@/features/auth/model/access-control'
import { cn } from '@/lib/cn'

type InternalArea = 'staff' | 'admin'
type NavigationRoute = { path: string; label: string }

const AREA_PRESENTATION = {
  staff: { navigationLabel: 'Staff navigation', mobileTitle: 'Staff navigation' },
  admin: { navigationLabel: 'Administration navigation', mobileTitle: 'Administration navigation' },
} as const

function FeatureNavigation({
  label,
  routes,
  onNavigate,
}: {
  label: string
  routes: readonly NavigationRoute[]
  onNavigate?: () => void
}) {
  if (routes.length === 0) return null
  return (
    <nav aria-label={label} className="px-3">
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

function InternalAreaNavigation({ actor, onNavigate }: { actor: StaffActor; onNavigate?: () => void }) {
  const areas = [
    hasStaffWebAccess(actor) ? { path: '/staff', label: 'Staff Operations' } : undefined,
    hasBackOfficeAccess(actor) ? { path: ADMIN_HOME_ROUTE.path, label: 'Back-Office Administration' } : undefined,
  ].filter((area): area is { path: string; label: string } => Boolean(area))

  if (areas.length < 2) return null
  return (
    <nav aria-label="Internal areas" className="px-3 pb-3">
      <p className="px-3 pb-1 text-xs font-semibold tracking-wide text-primary-foreground/50 uppercase">Areas</p>
      {areas.map((area) => (
        <NavLink key={area.path} to={area.path} end onClick={onNavigate} className={({ isActive }) => cn(
          'flex min-h-11 items-center rounded-md px-3 text-sm font-medium text-primary-foreground/75 hover:bg-white/10 hover:text-white',
          isActive && 'bg-white/12 text-white',
        )}>
          {area.label}
        </NavLink>
      ))}
    </nav>
  )
}

export function InternalShell({ area }: { area: InternalArea }) {
  const { manager, state } = useAuth()
  const [open, setOpen] = useState(false)
  const location = useLocation()
  if (state.status !== 'authenticated') return null

  const logout = () => void manager.logout()
  const featureRoutes = area === 'staff' ? permittedStaffRoutes(state.actor) : permittedAdminRoutes(state.actor)
  const presentation = AREA_PRESENTATION[area]
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
  const navigation = (onNavigate?: () => void) => (
    <>
      <InternalAreaNavigation actor={state.actor} onNavigate={onNavigate} />
      <FeatureNavigation label={presentation.navigationLabel} routes={featureRoutes} onNavigate={onNavigate} />
    </>
  )

  return (
    <div className="min-h-screen lg:grid lg:grid-cols-[17rem_1fr]">
      <a href="#main-content" className="sr-only z-[60] bg-white p-3 focus:not-sr-only focus:fixed focus:top-3 focus:left-3">Skip to main content</a>
      <aside className="hidden min-h-screen flex-col bg-primary text-primary-foreground lg:flex">
        <div className="rounded-br-2xl bg-white p-6"><MeridianLogo className="h-8 w-auto" /></div>
        {navigation()}
        <div className="mt-auto">{identity}</div>
      </aside>
      <div className="min-w-0">
        <header className="flex h-16 items-center justify-between border-b bg-card px-4 lg:px-8">
          <div className="flex items-center gap-3 lg:hidden">
            {featureRoutes.length > 0 ? <Sheet open={open} onOpenChange={setOpen}>
              <SheetTrigger asChild><Button size="icon" variant="ghost" aria-label="Open navigation"><Menu /></Button></SheetTrigger>
              <SheetContent key={location.pathname}>
                <SheetHeader><SheetTitle>{presentation.mobileTitle}</SheetTitle><SheetDescription>Meridian internal workspace</SheetDescription></SheetHeader>
                {navigation(() => setOpen(false))}
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
