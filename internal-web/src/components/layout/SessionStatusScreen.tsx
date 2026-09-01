import { Alert } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Spinner } from '@/components/ui/spinner'
import { useAuth } from '@/features/auth/model/auth-context'

export function SessionStatusScreen() {
  const { manager, state } = useAuth()
  return (
    <main className="grid min-h-screen place-items-center p-6">
      <div className="max-w-md space-y-4 text-center">
        {state.status === 'checking' && !state.error ? <Spinner className="mx-auto" /> : null}
        <h1 className="text-xl font-semibold">Verifying your staff session</h1>
        <p className="text-sm text-muted-foreground">Meridian keeps access credentials in memory and restores sessions through the protected refresh cookie.</p>
        {state.status === 'checking' && state.error ? (
          <Alert variant="warning">
            <p>{state.error}</p>
            <Button className="mt-3" variant="outline" onClick={() => void manager.restore()}>Try again</Button>
          </Alert>
        ) : null}
      </div>
    </main>
  )
}
