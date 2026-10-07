import type { ReactNode } from 'react'

import { PageHeader } from '@/components/common/PageHeader'

export interface AuthCardProps {
  eyebrow: string
  title: string
  description: string
  children: ReactNode
  footer?: ReactNode
}

export function AuthCard({ eyebrow, title, description, children, footer }: AuthCardProps) {
  return (
    <div className="min-w-0">
      <PageHeader headingRole="transactional" eyebrow={eyebrow} title={title} description={description} />
      <div className="mt-8">{children}</div>
      {footer ? <div className="mt-8 border-t border-border pt-6">{footer}</div> : null}
    </div>
  )
}
