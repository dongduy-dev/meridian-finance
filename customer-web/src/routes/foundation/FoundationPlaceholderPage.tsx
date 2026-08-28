import { Construction } from 'lucide-react'
import { Link } from 'react-router-dom'

import { EmptyState } from '@/components/common/EmptyState'
import { PageHeader } from '@/components/common/PageHeader'
import { Button } from '@/components/ui/button'

export interface FoundationPlaceholderPageProps {
  title: string
}

export function FoundationPlaceholderPage({ title }: FoundationPlaceholderPageProps) {
  return (
    <div className="space-y-8">
      <PageHeader
        eyebrow="Navigation foundation"
        title={title}
        description={`This route confirms the planned ${title.toLowerCase()} navigation destination without simulating API-backed capability.`}
      />
      <EmptyState
        icon={Construction}
        title={`${title} begins in a later checkpoint`}
        description="Meridian will connect this area only when its authoritative backend reads and Customer experience are in scope."
        action={
          <Button variant="secondary" asChild>
            <Link to="/">Return to foundation</Link>
          </Button>
        }
      />
    </div>
  )
}
