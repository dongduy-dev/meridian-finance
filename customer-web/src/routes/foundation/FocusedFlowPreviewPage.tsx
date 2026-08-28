import { ArrowLeft, ArrowRight, Layers3 } from 'lucide-react'
import { Link } from 'react-router-dom'

import { FocusedFlowLayout } from '@/components/layout/FocusedFlowLayout'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'

export function FocusedFlowPreviewPage() {
  return (
    <FocusedFlowLayout
      title="One clear task at a time"
      description="The focused template establishes progress location, readable content width, and a durable action region without introducing a business workflow."
      currentStep={1}
      totalSteps={3}
      backAction={
        <Button variant="secondary" asChild>
          <Link to="/">
            <ArrowLeft aria-hidden="true" />
            Back
          </Link>
        </Button>
      }
      continueAction={
        <Button asChild>
          <Link to="/foundation/detail">
            Continue
            <ArrowRight aria-hidden="true" />
          </Link>
        </Button>
      }
    >
      <Card>
        <CardHeader>
          <CardTitle>Focused content region</CardTitle>
          <CardDescription>
            Real forms will use React Hook Form and Zod when FE-CP2 exercises the shared
            form contract.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-5">
          <div className="flex items-start gap-4 rounded-md bg-muted p-4">
            <div className="flex size-10 shrink-0 items-center justify-center rounded-md bg-card text-primary">
              <Layers3 aria-hidden="true" className="size-5" />
            </div>
            <div>
              <p className="font-semibold">Layout responsibility only</p>
              <p className="mt-1 text-sm leading-5 text-muted-foreground">
                Business steps, validation, and transition authority remain outside this
                component.
              </p>
            </div>
          </div>
          <Alert>
            <Layers3 aria-hidden="true" />
            <AlertTitle>Mobile-safe action region</AlertTitle>
            <AlertDescription>
              Reserved bottom space keeps content and errors visible above the sticky actions.
            </AlertDescription>
          </Alert>
        </CardContent>
      </Card>
    </FocusedFlowLayout>
  )
}
