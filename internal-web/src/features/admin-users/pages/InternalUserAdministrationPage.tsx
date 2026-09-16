import { useQuery } from '@tanstack/react-query'
import { Button } from '@/components/ui/button'
import { Spinner } from '@/components/ui/spinner'
import { hasPermission } from '@/features/auth/model/access-control'
import { useAuth } from '@/features/auth/model/auth-context'
import { assignableInternalRolesQuery, internalUsersQuery } from '../api/queries'
import { InternalUserCard } from '../components/InternalUserCard'
import { UserAdministrationQueryErrorPanel } from '../components/UserAdministrationQueryErrorPanel'

export function InternalUserAdministrationPage() {
  const { manager, state } = useAuth()
  const enabled = state.status === 'authenticated' && hasPermission(state.actor, 'identity:user:manage')
  const users = useQuery(internalUsersQuery(manager, enabled))
  const roles = useQuery(assignableInternalRolesQuery(manager, enabled))
  const pending = users.isPending || roles.isPending
  const error = users.error ?? roles.error

  const refresh = () => {
    void Promise.all([users.refetch(), roles.refetch()])
  }

  return <section className="mx-auto max-w-6xl space-y-6">
    <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
      <div>
        <p className="text-sm font-semibold text-muted-foreground">IDENTITY ADMINISTRATION</p>
        <h1 data-route-heading tabIndex={-1} className="mt-1 text-2xl font-semibold tracking-tight sm:text-3xl">Internal Users</h1>
        <p className="mt-2 max-w-3xl text-muted-foreground">Inspect internal Staff accounts, control administrative status, and assign predefined backend-owned roles.</p>
      </div>
      <Button variant="outline" disabled={users.isFetching || roles.isFetching} onClick={refresh}>
        {(users.isFetching || roles.isFetching) && !pending ? 'Refreshing…' : 'Refresh Users'}
      </Button>
    </div>
    {pending ? <div className="flex items-center gap-2 text-sm text-muted-foreground"><Spinner /> Loading Internal Users…</div> : null}
    {error && (!users.data || !roles.data) ? <UserAdministrationQueryErrorPanel error={error} onRetry={refresh} /> : null}
    {users.data?.length === 0 && roles.data ? <p className="rounded-md border p-5 text-sm text-muted-foreground">No internal Staff Users are available.</p> : null}
    {users.data?.length && roles.data ? <div className="grid gap-5">{users.data.map((user) =>
      <InternalUserCard key={user.userId} user={user} roles={roles.data} manager={manager} />
    )}</div> : null}
  </section>
}
