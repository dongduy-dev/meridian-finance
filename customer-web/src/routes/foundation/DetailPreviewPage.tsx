import { ArrowLeft, ListTree } from 'lucide-react'
import { Link } from 'react-router-dom'

import { PageHeader } from '@/components/common/PageHeader'
import { DetailLayout } from '@/components/layout/DetailLayout'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Separator } from '@/components/ui/separator'

export function DetailPreviewPage() {
  return (
    <DetailLayout
      header={
        <PageHeader
          eyebrow="Detail layout preview"
          title="Information with a clear hierarchy"
          description="The template separates primary detail from optional summary or actions, then stacks them deliberately on smaller screens."
          actions={
            <Button variant="secondary" asChild>
              <Link to="/">
                <ArrowLeft aria-hidden="true" />
                Foundation
              </Link>
            </Button>
          }
        />
      }
      rail={
        <Card>
          <CardHeader>
            <CardTitle>Summary rail</CardTitle>
            <CardDescription>Reserved for authoritative facts and supported actions.</CardDescription>
          </CardHeader>
          <CardContent>
            <Alert variant="warning">
              <ListTree aria-hidden="true" />
              <AlertTitle>No resource loaded</AlertTitle>
              <AlertDescription>This preview contains no Customer or lending facts.</AlertDescription>
            </Alert>
          </CardContent>
        </Card>
      }
    >
      <Card>
        <CardHeader>
          <CardTitle>Primary detail region</CardTitle>
          <CardDescription>
            Future pages compose returned data here without moving server state into layout
            components.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-5">
          {['Overview section', 'Supporting section', 'History section'].map((label) => (
            <div key={label}>
              <p className="font-semibold">{label}</p>
              <p className="mt-1 text-sm leading-6 text-muted-foreground">
                Purposeful space for future API-backed content.
              </p>
              <Separator className="mt-5" />
            </div>
          ))}
        </CardContent>
      </Card>
    </DetailLayout>
  )
}
