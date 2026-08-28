import {
  CircleUserRound,
  Files,
  Landmark,
  LayoutDashboard,
  Menu,
  Shapes,
} from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import { NavLink, Outlet, useLocation } from 'react-router-dom'

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

interface NavigationItem {
  label: string
  href: string
  icon: LucideIcon
  end?: boolean
}

const navigation: NavigationItem[] = [
  { label: 'Dashboard', href: '/', icon: LayoutDashboard, end: true },
  { label: 'Products', href: '/products', icon: Shapes },
  { label: 'Applications', href: '/applications', icon: Files },
  { label: 'Loans', href: '/loans', icon: Landmark },
  { label: 'Account', href: '/account', icon: CircleUserRound },
]

function NavigationLinks({ mobile = false }: { mobile?: boolean }) {
  const { pathname } = useLocation()

  return (
    <nav aria-label="Customer navigation" className="space-y-1">
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
              'flex min-h-11 items-center gap-3 rounded-md px-3 text-sm font-medium text-primary-foreground/72 hover:bg-white/8 hover:text-primary-foreground',
              isActive && 'bg-white/12 text-primary-foreground shadow-inner',
            )}
          >
            <Icon aria-hidden="true" className="size-5" />
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
  return (
    <div className="min-h-svh bg-background lg:grid lg:grid-cols-[17rem_minmax(0,1fr)]">
      <aside className="fixed inset-y-0 left-0 hidden w-68 flex-col bg-primary px-4 py-5 text-primary-foreground lg:flex">
        <div className="flex min-h-28 items-center justify-center px-2">
          <MeridianLogo variant="primary" className="w-40" />
        </div>
        <Separator className="my-5 bg-white/14" />
        <NavigationLinks />
      </aside>

      <div className="min-w-0 lg:col-start-2">
        <header className="sticky top-0 z-40 flex min-h-18 items-center justify-between border-b border-border bg-background/95 px-4 backdrop-blur-sm sm:px-6 lg:px-8">
          <div className="flex items-center gap-3 lg:hidden">
            <Sheet>
              <SheetTrigger asChild>
                <Button variant="ghost" size="icon" aria-label="Open customer navigation">
                  <Menu aria-hidden="true" />
                </Button>
              </SheetTrigger>
              <SheetContent className="bg-primary text-primary-foreground">
                <SheetHeader>
                  <SheetTitle className="text-primary-foreground">Customer navigation</SheetTitle>
                  <SheetDescription className="text-primary-foreground/65">
                    Choose a Customer Web destination.
                  </SheetDescription>
                </SheetHeader>
                <div className="px-4 pb-6">
                  <MeridianLogo variant="primary" className="mb-5 w-32" />
                  <Separator className="mb-5 bg-white/14" />
                  <NavigationLinks mobile />
                </div>
              </SheetContent>
            </Sheet>
            <MeridianLogo variant="mark" decorative className="size-8" />
            <span className="text-sm font-semibold text-foreground">Meridian</span>
          </div>

          <div className="hidden lg:block">
            <p className="text-xs font-semibold tracking-[0.14em] text-muted-foreground uppercase">
              Customer Web
            </p>
          </div>

          <div className="flex items-center gap-3" aria-label="Account area">
            <div className="flex size-10 items-center justify-center rounded-full border border-border bg-card text-foreground">
              <CircleUserRound aria-hidden="true" className="size-5" />
            </div>
          </div>
        </header>

        <main className="mx-auto w-full max-w-7xl px-4 py-8 sm:px-6 sm:py-10 lg:px-8">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
