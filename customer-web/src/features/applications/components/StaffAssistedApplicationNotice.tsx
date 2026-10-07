import { Info } from 'lucide-react'

import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'

export function StaffAssistedApplicationNotice({ children }: { children?: React.ReactNode }) {
  return (
    <Alert variant="information">
      <Info aria-hidden="true" />
      <AlertTitle>Application handled with Meridian staff</AlertTitle>
      <AlertDescription>{children ?? 'You can view this application online. Contact your Loan Officer if you need to update it or arrange the next step. Meridian staff will coordinate any documents or responses needed from you.'}</AlertDescription>
    </Alert>
  )
}
