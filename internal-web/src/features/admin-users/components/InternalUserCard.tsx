import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import type { AuthSessionManager } from '@/features/auth/model/auth-session'
import { ApiError, NetworkError } from '@/lib/api'
import { changeInternalUserRole, changeInternalUserStatus } from '../api/admin-users-api'
import {
  knownInternalUserStatuses,
  type AssignableInternalRole,
  type InternalUser,
  type InternalUserStatus,
} from '../api/contracts'
import { adminUserKeys } from '../api/queries'

const statusLabels: Record<InternalUserStatus, string> = {
  ACTIVE: 'Active',
  SUSPENDED: 'Suspended',
  DISABLED: 'Disabled',
}

function commandMessage(error: unknown, resource: string): string {
  if (error instanceof ApiError) return error.message
  if (error instanceof NetworkError) return `The result is unknown because Meridian did not confirm the response. Authoritative ${resource} state was refreshed; review it before retrying the same target state.`
  return `The ${resource} command was not confirmed. Refresh authoritative state before trying again.`
}

function displayStatus(status: string): string {
  return status in statusLabels ? statusLabels[status as InternalUserStatus] : `Unknown status (${status})`
}

export function InternalUserCard({ user, roles, manager }: {
  user: InternalUser
  roles: readonly AssignableInternalRole[]
  manager: AuthSessionManager
}) {
  const queryClient = useQueryClient()
  const knownStatus = knownInternalUserStatuses.includes(user.status as InternalUserStatus)
  const [selectedStatus, setSelectedStatus] = useState<InternalUserStatus>()
  const [statusPending, setStatusPending] = useState(false)
  const [statusError, setStatusError] = useState<string>()
  const [rolePending, setRolePending] = useState<string>()
  const [roleError, setRoleError] = useState<string>()

  const statusTarget = selectedStatus ?? (knownStatus ? user.status as InternalUserStatus : 'ACTIVE')

  const retainAndRefresh = async (returned: InternalUser) => {
    queryClient.setQueryData<InternalUser[]>(adminUserKeys.list(), (current) =>
      current?.map((item) => item.userId === returned.userId ? returned : item) ?? [returned])
    await queryClient.invalidateQueries({ queryKey: adminUserKeys.all }).catch(() => undefined)
  }

  const refreshAfterUnknownResult = async () => {
    await queryClient.invalidateQueries({ queryKey: adminUserKeys.list() }).catch(() => undefined)
  }

  const submitStatus = async () => {
    setStatusPending(true)
    setStatusError(undefined)
    try {
      const returned = await changeInternalUserStatus(manager, user.userId, statusTarget)
      setSelectedStatus(undefined)
      await retainAndRefresh(returned)
    } catch (error) {
      setStatusError(commandMessage(error, 'User status'))
      if (error instanceof NetworkError) await refreshAfterUnknownResult()
    } finally {
      setStatusPending(false)
    }
  }

  const submitRole = async (roleCode: string, assigned: boolean) => {
    setRolePending(roleCode)
    setRoleError(undefined)
    try {
      const returned = await changeInternalUserRole(manager, user.userId, roleCode, assigned)
      await retainAndRefresh(returned)
    } catch (error) {
      setRoleError(commandMessage(error, 'role assignment'))
      if (error instanceof NetworkError) await refreshAfterUnknownResult()
    } finally {
      setRolePending(undefined)
    }
  }

  const assignableCodes = new Set(roles.map((role) => role.code))
  const unknownAssignedRoles = user.assignedRoleCodes.filter((roleCode) => !assignableCodes.has(roleCode))

  return <Card>
    <CardHeader>
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          <CardTitle>{user.displayName}</CardTitle>
          <p className="mt-1 break-all text-sm text-muted-foreground">{user.email}</p>
        </div>
        <span className="w-fit rounded-full bg-muted px-3 py-1 text-xs font-semibold text-muted-foreground">
          {displayStatus(user.status)}
        </span>
      </div>
    </CardHeader>
    <CardContent className="grid gap-6 lg:grid-cols-2">
      <section className="space-y-3 rounded-md border p-4" aria-labelledby={`status-${user.userId}`}>
        <div>
          <h3 id={`status-${user.userId}`} className="font-semibold">Administrative status</h3>
          <p className="text-sm text-muted-foreground">Inactive Users lose access immediately and cannot refresh credentials.</p>
        </div>
        {!knownStatus ? <p className="text-sm text-warning">The backend returned {displayStatus(user.status)}. Select a known target only after confirming the intended change.</p> : null}
        <label className="block space-y-1 text-sm font-medium">Target status
          <select
            className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
            value={statusTarget}
            onChange={(event) => setSelectedStatus(event.target.value as InternalUserStatus)}
          >
            {knownInternalUserStatuses.map((status) => <option key={status} value={status}>{statusLabels[status]}</option>)}
          </select>
        </label>
        <Button
          type="button"
          disabled={statusPending || (knownStatus && statusTarget === user.status)}
          onClick={() => void submitStatus()}
        >
          {statusPending ? 'Waiting for confirmation…' : 'Apply status'}
        </Button>
        {statusError ? <Alert variant="destructive"><AlertTitle>Status was not confirmed</AlertTitle><AlertDescription>{statusError}</AlertDescription></Alert> : null}
      </section>

      <section className="space-y-3 rounded-md border p-4" aria-labelledby={`roles-${user.userId}`}>
        <div>
          <h3 id={`roles-${user.userId}`} className="font-semibold">Predefined roles</h3>
          <p className="text-sm text-muted-foreground">Roles are backend-owned permission bundles. Each action changes one assignment.</p>
        </div>
        {roles.map((role) => {
          const assigned = user.assignedRoleCodes.includes(role.code)
          return <div key={role.code} className="flex flex-col gap-2 rounded-md bg-muted/50 p-3 sm:flex-row sm:items-center sm:justify-between">
            <div><p className="font-medium">{role.name}</p><p className="font-mono text-xs text-muted-foreground">{role.code} · {assigned ? 'Assigned' : 'Not assigned'}</p></div>
            <Button
              type="button"
              variant={assigned ? 'outline' : 'default'}
              disabled={Boolean(rolePending)}
              onClick={() => void submitRole(role.code, !assigned)}
            >
              {rolePending === role.code ? 'Waiting…' : assigned ? `Remove ${role.name}` : `Assign ${role.name}`}
            </Button>
          </div>
        })}
        {unknownAssignedRoles.length ? <div className="rounded-md border border-dashed p-3 text-sm">
          <p className="font-medium">Other assigned backend roles</p>
          <p className="mt-1 break-all font-mono text-xs text-muted-foreground">{unknownAssignedRoles.join(', ')}</p>
        </div> : null}
        {roleError ? <Alert variant="destructive"><AlertTitle>Role assignment was not confirmed</AlertTitle><AlertDescription>{roleError}</AlertDescription></Alert> : null}
      </section>
    </CardContent>
  </Card>
}
