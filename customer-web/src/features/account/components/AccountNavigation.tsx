import { Landmark, UserRound } from 'lucide-react'
import { NavLink } from 'react-router-dom'

import { cn } from '@/lib/cn'

const destinations = [
  { label: 'Profile', href: '/account/profile', icon: UserRound },
  { label: 'Identity verification', href: '/account/identity-verification', icon: UserRound },
  { label: 'Bank accounts', href: '/account/bank-accounts', icon: Landmark },
]

export function AccountNavigation() {
  return (
    <nav aria-label="Account navigation" className="flex min-w-0 flex-wrap gap-x-6 gap-y-2 border-b border-border">
      {destinations.map(({ label, href, icon: Icon }) => (
        <NavLink
          key={href}
          to={href}
          className={({ isActive }) =>
            cn(
              'inline-flex min-h-11 min-w-0 max-w-full items-center gap-2 border-b-2 border-transparent py-3 text-sm leading-5 font-medium text-muted-foreground hover:border-input hover:text-foreground',
              isActive && 'border-primary font-semibold text-foreground',
            )
          }
        >
          <Icon aria-hidden="true" className="size-5 shrink-0" />
          <span className="min-w-0 [overflow-wrap:anywhere]">{label}</span>
        </NavLink>
      ))}
    </nav>
  )
}
