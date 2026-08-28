import { ArrowRight, ShieldCheck } from 'lucide-react'
import { Link } from 'react-router-dom'

import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardFooter, CardHeader } from '@/components/ui/card'

export function AuthFoundationPage() {
  return (
    <Card>
      <CardHeader className="space-y-3">
        <p className="text-xs font-semibold tracking-[0.16em] text-muted-foreground uppercase">
          Auth layout preview
        </p>
        <h1
          id="page-heading"
          tabIndex={-1}
          className="text-3xl leading-tight font-semibold tracking-[-0.025em] outline-none"
        >
          A calm start to every secure journey
        </h1>
        <p className="leading-6 text-muted-foreground">
          This foundation demonstrates branding and layout only. Registration, login, and
          session behavior belong to FE-CP2.
        </p>
      </CardHeader>
      <CardContent>
        <Alert variant="success">
          <ShieldCheck aria-hidden="true" />
          <AlertTitle>Boundary preserved</AlertTitle>
          <AlertDescription>
            No credentials, auth state, or simulated Customer session are present.
          </AlertDescription>
        </Alert>
      </CardContent>
      <CardFooter className="flex-col items-stretch sm:flex-row sm:justify-between">
        <Button variant="secondary" asChild>
          <Link to="/">Back to foundation</Link>
        </Button>
        <Button asChild>
          <Link to="/foundation/flow">
            Review focused flow
            <ArrowRight aria-hidden="true" />
          </Link>
        </Button>
      </CardFooter>
    </Card>
  )
}
