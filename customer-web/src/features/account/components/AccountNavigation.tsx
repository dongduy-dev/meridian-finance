import { Landmark, UserRound } from 'lucide-react'
import { NavLink } from 'react-router-dom'

import { cn } from '@/lib/cn'

const destinations = [
  { label: 'Profile', href: '/account/profile', icon: UserRound },
  { label: 'Bank accounts', href: '/account/bank-accounts', icon: Landmark },
]

export function AccountNavigation() {
  return (
    <nav aria-label="Account navigation" className="flex flex-wrap gap-2 border-b border-border pb-4">
      {destinations.map(({ label, href, icon: Icon }) => (
        <NavLink
          key={href}
          to={href}
          className={({ isActive }) =>
            cn(
              'inline-flex min-h-11 items-center gap-2 rounded-md px-4 text-sm font-semibold text-muted-foreground hover:bg-selected hover:text-foreground',
              isActive && 'bg-selected text-foreground',
            )
          }
        >
          <Icon aria-hidden="true" className="size-4" />
          {label}
        </NavLink>
      ))}
    </nav>
  )
}
