import type { ReactNode } from 'react'

import { Card, CardContent, CardHeader } from '@/components/ui/card'

export interface AuthCardProps {
  eyebrow: string
  title: string
  description: string
  children: ReactNode
  footer?: ReactNode
}
export function AuthCard({ eyebrow, title, description, children, footer }: AuthCardProps) {
  return (
    <Card>
      <CardHeader className="space-y-3 pb-5">
        <p className="text-xs font-semibold tracking-[0.16em] text-muted-foreground uppercase">
          {eyebrow}
        </p>
        <div className="space-y-2">
          <h1
            id="page-heading"
            tabIndex={-1}
            className="text-3xl leading-tight font-semibold tracking-[-0.025em] text-foreground outline-none"
          >
            {title}
          </h1>
          <p className="text-sm leading-6 text-muted-foreground">{description}</p>
        </div>
      </CardHeader>
      <CardContent>
        {children}
        {footer ? <div className="mt-6 border-t border-border pt-5">{footer}</div> : null}
      </CardContent>
    </Card>
  )
}
