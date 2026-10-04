import { Info } from 'lucide-react'

import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'

export function StaffAssistedApplicationNotice({ children }: { children?: React.ReactNode }) {
  return (
    <Alert variant="information">
      <Info aria-hidden="true" />
      <AlertTitle>Staff-assisted application</AlertTitle>
      <AlertDescription>{children ?? 'You can track this application here. If information, evidence, or a decision is needed from you, Meridian Staff will coordinate it with you.'}</AlertDescription>
    </Alert>
  )
}
