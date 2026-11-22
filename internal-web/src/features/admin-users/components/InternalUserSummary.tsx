import { CircleCheck, CircleHelp, CirclePause, CircleX } from 'lucide-react'
import { cn } from '@/lib/cn'
import type { AssignableInternalRole, InternalUserStatus } from '../api/contracts'

export const internalUserStatusLabels: Record<InternalUserStatus, string> = {
  ACTIVE: 'Active', SUSPENDED: 'Suspended', DISABLED: 'Disabled',
}

export function internalUserStatusLabel(status: string): string {
  return Object.hasOwn(internalUserStatusLabels, status)
    ? internalUserStatusLabels[status as InternalUserStatus] : 'Status unavailable'
}

export function InternalUserStatusBadge({ status }: { status: string }) {
  const treatment = status === 'ACTIVE' ? 'bg-success-subtle text-success'
    : status === 'SUSPENDED' ? 'bg-warning-subtle text-warning'
    : status === 'DISABLED' ? 'bg-danger-subtle text-danger' : 'bg-muted text-muted-foreground'
  const Icon = status === 'ACTIVE' ? CircleCheck : status === 'SUSPENDED' ? CirclePause
    : status === 'DISABLED' ? CircleX : CircleHelp
  return <span className={cn('inline-flex w-fit items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-semibold', treatment)}>
    <Icon aria-hidden="true" className="size-3.5 shrink-0" />{internalUserStatusLabel(status)}
  </span>
}

export function internalUserRoleLabel(code: string, roles: readonly AssignableInternalRole[]): string {
  return roles.find((role) => role.code === code)?.name ?? `Unrecognized role (${code})`
}

export function InternalUserRoles({ codes, roles }: { codes: readonly string[]; roles: readonly AssignableInternalRole[] }) {
  return codes.length ? <ul className="flex flex-wrap gap-1.5" aria-label="Assigned roles">
    {codes.map((code) => <li key={code} className="rounded border bg-muted/50 px-2 py-0.5 text-xs [overflow-wrap:anywhere]">
      {internalUserRoleLabel(code, roles)}
    </li>)}
  </ul> : <span className="text-sm text-muted-foreground">No roles assigned</span>
}
