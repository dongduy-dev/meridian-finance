import * as Dialog from '@radix-ui/react-dialog'
import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { hasPermission } from '@/features/auth/model/access-control'
import type { AuthSessionManager } from '@/features/auth/model/auth-session'
import { operatorErrorMessage } from '@/lib/api/operator-error-message'
import { ApiError } from '@/lib/api'
import { changeInternalUserRole, changeInternalUserStatus, sendInternalUserPasswordSetup } from '../api/admin-users-api'
import { knownInternalUserStatuses, type AssignableInternalRole, type InternalUser, type InternalUserStatus } from '../api/contracts'
import { adminUserKeys } from '../api/queries'
import { InternalUserRoles, InternalUserStatusBadge, internalUserStatusLabel, internalUserStatusLabels } from './InternalUserSummary'

type Feedback = { title: string; message: string; variant: 'success' | 'warning' | 'destructive' }

// A single Radix modal owns management and its confirmation step. The navigation
// Sheet is intentionally not reused: its narrow, dark content is for navigation.
export function InternalUserManagementDialog({ user, roles, manager, refreshing, unavailable, onClose, returnFocus }: {
  user: InternalUser | undefined
  roles: readonly AssignableInternalRole[]
  manager: AuthSessionManager
  refreshing: boolean
  unavailable: boolean
  onClose: () => void
  returnFocus: () => void
}) {
  const queryClient = useQueryClient()
  const [selectedStatus, setSelectedStatus] = useState<InternalUserStatus>()
  const [confirmation, setConfirmation] = useState<{ target: Exclude<InternalUserStatus, 'ACTIVE'>; previous: string }>()
  const [pending, setPending] = useState(false)
  const [feedback, setFeedback] = useState<Feedback>()
  const [setupResult, setSetupResult] = useState<'confirmed' | 'unknown' | 'rejected'>()
  const [refreshFailed, setRefreshFailed] = useState(false)
  const title = useRef<HTMLHeadingElement>(null)
  const result = useRef<HTMLHeadingElement>(null)
  const statusTrigger = useRef<HTMLButtonElement>(null)
  const cancel = useRef<HTMLButtonElement>(null)
  const restoreStatusFocus = useRef(false)
  const knownStatus = knownInternalUserStatuses.some((value) => value === user?.status)
  const statusTarget = selectedStatus ?? (knownStatus ? user!.status as InternalUserStatus : '')
  const locked = pending || refreshing || unavailable || refreshFailed || !user

  useEffect(() => {
    if (confirmation) cancel.current?.focus()
    else if (restoreStatusFocus.current) {
      restoreStatusFocus.current = false
      if (statusTrigger.current?.isConnected && !statusTrigger.current.disabled) statusTrigger.current.focus()
      else title.current?.focus()
    } else if (feedback && !pending) result.current?.focus()
  }, [confirmation, feedback, pending])

  const dismissConfirmation = () => {
    restoreStatusFocus.current = true
    setConfirmation(undefined)
  }
  const dismiss = () => {
    if (pending) return
    if (confirmation) dismissConfirmation()
    else onClose()
  }
  const refresh = async () => {
    try {
      await queryClient.invalidateQueries({ queryKey: adminUserKeys.all }, { throwOnError: true })
      setRefreshFailed(false)
      return true
    } catch {
      setRefreshFailed(true)
      return false
    }
  }
  const run = async (kind: 'status' | 'role' | 'setup', command: () => Promise<InternalUser | void>) => {
    if (locked) return
    const originatingSession = manager.getSnapshot()
    setPending(true)
    setFeedback(undefined)
    let confirmed = false
    let failure: unknown
    try {
      const returned = await command()
      confirmed = true
      const session = manager.getSnapshot()
      // Never restore private cache data after a command loses session authority.
      if (returned && originatingSession.status === 'authenticated' && session.status === 'authenticated'
        && session.actor.userId === originatingSession.actor.userId && hasPermission(session.actor, 'identity:user:manage')) {
        queryClient.setQueryData<InternalUser[]>(adminUserKeys.list(), (current) =>
          current?.map((item) => item.userId === returned.userId ? returned : item))
      }
      if (kind === 'status') setSelectedStatus(undefined)
    } catch (error) { failure = error }
    const refreshed = await refresh()
    if (!confirmed) {
      const rejected = failure instanceof ApiError && failure.status >= 400 && failure.status < 500
        && failure.status !== 408 && failure.errorCode !== 'UNEXPECTED_RESPONSE'
      const resource = kind === 'status' ? 'User status' : 'role assignment'
      if (kind === 'setup') setSetupResult(rejected ? 'rejected' : 'unknown')
      setFeedback({
        title: kind === 'setup' ? rejected ? 'Password setup request rejected' : 'Password setup request not confirmed'
          : kind === 'status' ? 'Status was not confirmed' : 'Role assignment was not confirmed',
        message: kind === 'setup'
          ? rejected
            ? failure instanceof ApiError && failure.errorCode === 'INTERNAL_USER_NOT_ACTIVE'
              ? 'Password setup requires an active Staff account with verified email. Reactivation does not verify email. Ask your system administrator to review account eligibility before retrying.'
              : operatorErrorMessage(failure, 'The setup request was rejected. Review the Staff account before trying again.')
            : 'Meridian could not confirm whether a new link was issued. Ask the Staff member to check their inbox and spam folder, and confirm the email address shown here before sending a replacement. Refreshing Staff details cannot confirm email delivery.'
          : operatorErrorMessage(failure, `Meridian could not confirm the ${resource} change. ${refreshed ? 'The latest information was refreshed; review it before retrying the same change.' : 'Refresh and review the latest information before trying again.'}`),
        variant: kind === 'setup' && !rejected ? 'warning' : 'destructive',
      })
    } else {
      if (kind === 'setup') setSetupResult('confirmed')
      setFeedback({ title: kind === 'setup' ? `Password setup requested for ${user?.displayName}` : `Staff access updated for ${user?.displayName}`,
        message: kind === 'setup'
          ? 'The setup request was accepted. Ask the Staff member to check their inbox and spam folder. If no message arrives, confirm the email address shown here before sending a replacement.'
          : refreshed ? 'The latest Staff information is shown below.' : 'The change was confirmed, but the latest Staff information could not be refreshed. Refresh before continuing.',
        variant: refreshed ? 'success' : 'warning' })
    }
    setConfirmation(undefined)
    setPending(false)
  }

  const staleConfirmation = !user || user.status !== confirmation?.previous

  return <Dialog.Root open modal onOpenChange={(open) => { if (!open) dismiss() }}>
    <Dialog.Portal>
      {/* Clipping preserves the sidebar's scroll ancestor and the root scrollbar.
          Suppress Radix's extra gutter margin; keep its wheel/touch scroll lock. */}
      <style>{'body[data-scroll-locked]:has([data-internal-user-management-dialog]) { overflow: clip !important; margin-right: 0 !important; }'}</style>
      <Dialog.Overlay data-staff-management-backdrop className="fixed inset-0 z-50 bg-primary/45" />
      <Dialog.Content
        data-internal-user-management-dialog
        className="fixed inset-0 z-50 flex min-h-0 flex-col bg-card shadow-xl outline-none md:inset-auto md:left-1/2 md:top-1/2 md:max-h-[calc(100dvh-3rem)] md:w-[calc(100%-3rem)] md:max-w-3xl md:-translate-x-1/2 md:-translate-y-1/2 md:rounded-lg"
        onOpenAutoFocus={(event) => { event.preventDefault(); title.current?.focus({ preventScroll: true }) }}
        onCloseAutoFocus={(event) => { event.preventDefault(); returnFocus() }}
        onPointerDownOutside={(event) => { if (confirmation || pending) event.preventDefault() }}
        onEscapeKeyDown={(event) => { event.preventDefault(); dismiss() }}
      >
        <div className="min-h-0 space-y-5 overflow-y-auto overscroll-contain p-5 [overflow-wrap:anywhere] sm:p-6">
          <header className="space-y-2">
            <Dialog.Title ref={title} tabIndex={-1} className="text-xl font-semibold">{confirmation ? 'Confirm User access change' : user ? `Manage ${user.displayName}` : 'Staff member unavailable'}</Dialog.Title>
            <Dialog.Description className="text-sm text-muted-foreground">{confirmation
              ? 'The User will lose access and all active sessions will be revoked. Reactivation does not restore those sessions.'
              : 'Manage password setup, Staff access status, and predefined roles.'}</Dialog.Description>
          </header>
          {confirmation ? <>
            <dl className="space-y-3 text-sm"><div><dt className="text-muted-foreground">Internal User</dt><dd className="font-semibold">{user?.displayName} ({user?.email})</dd></div>
              <div><dt className="text-muted-foreground">Status change</dt><dd className="font-semibold">{internalUserStatusLabel(confirmation.previous)} → {internalUserStatusLabels[confirmation.target]}</dd></div></dl>
            {staleConfirmation && !pending ? <p role="alert" className="text-sm text-warning">Staff information changed. Cancel and review the latest status before continuing.</p> : null}
            {pending ? <p role="status" className="text-sm text-muted-foreground">Waiting for confirmation and refreshed Staff information…</p> : null}
            <div className="flex flex-wrap justify-end gap-2">
              <Button asChild variant="outline" disabled={pending} onClick={dismissConfirmation}><button ref={cancel}>Cancel</button></Button>
              <Button variant="destructive" disabled={locked || staleConfirmation} onClick={() => {
                if (user && !staleConfirmation) void run('status', () => changeInternalUserStatus(manager, user.userId, confirmation.target))
              }}>{pending ? 'Waiting for confirmation…' : 'Update access status'}</Button>
            </div>
          </> : null}
          <div hidden={Boolean(confirmation)} className="space-y-5">
            {feedback ? <Alert variant={feedback.variant}><h3 ref={result} tabIndex={-1} className="col-start-2 font-semibold leading-5">{feedback.title}</h3><AlertDescription>{feedback.message}</AlertDescription></Alert> : null}
            {refreshFailed || unavailable ? <div className="space-y-2 rounded-md border p-3 text-sm"><p>The latest Staff information could not be refreshed. Actions remain unavailable until refresh succeeds.</p><Button variant="outline" disabled={pending || refreshing} onClick={() => {
              setPending(true)
              void refresh().finally(() => setPending(false))
            }}>Refresh Staff information</Button></div> : null}
            {!user ? <p role="status">This Staff member is no longer in the directory. Close this dialog and refresh the list.</p> : <>
              <div className="space-y-3 rounded-md border bg-muted/30 p-4"><p className="font-semibold">{user.displayName}</p><p className="text-sm text-muted-foreground">{user.email}</p><InternalUserStatusBadge status={user.status} /><InternalUserRoles codes={user.assignedRoleCodes} roles={roles} /></div>
              <section className="space-y-3 rounded-md border p-4" aria-labelledby="staff-password-setup">
                <h3 id="staff-password-setup" className="font-semibold">Password setup</h3>
                <p className="text-sm text-muted-foreground">Send a fresh one-time link to this Staff member. Sending a new link invalidates an older unused link.</p>
                {user.status !== 'ACTIVE' ? <p className="text-sm text-muted-foreground">Password setup requires an active Staff account.</p> : null}
                <Button variant="outline" disabled={locked || user.status !== 'ACTIVE'} onClick={() => void run('setup', () => sendInternalUserPasswordSetup(manager, user.userId))}>{setupResult === 'unknown' || setupResult === 'confirmed' ? 'Send replacement setup link' : 'Send password setup link'}</Button>
              </section>
              <section className="space-y-3 rounded-md border p-4" aria-labelledby="staff-access-status">
                <h3 id="staff-access-status" className="font-semibold">Staff access status</h3>
                <p className="text-sm text-muted-foreground">Suspended and disabled Staff members lose access immediately and must sign in again after reactivation.</p>
                {!knownStatus ? <p className="text-sm text-warning">Current status is unavailable. Review the intended status before making a change.</p> : null}
                <label className="block space-y-1 text-sm font-medium">Target status
                  <select className="h-10 w-full rounded-md border border-input bg-background px-3 text-sm" value={statusTarget} disabled={locked} onChange={(event) => setSelectedStatus(event.target.value as InternalUserStatus)}>
                    {!knownStatus ? <option value="">Choose a status</option> : null}
                    {knownInternalUserStatuses.map((status) => <option key={status} value={status}>{internalUserStatusLabels[status]}</option>)}
                  </select>
                </label>
                <Button asChild disabled={locked || !statusTarget || statusTarget === user.status} onClick={() => {
                  if (!statusTarget || statusTarget === user.status) return
                  if (statusTarget === 'ACTIVE') void run('status', () => changeInternalUserStatus(manager, user.userId, statusTarget))
                  else setConfirmation({ target: statusTarget, previous: user.status })
                }}><button ref={statusTrigger}>{pending ? 'Waiting for confirmation…' : 'Update access status'}</button></Button>
              </section>
              <section className="space-y-3 rounded-md border p-4" aria-labelledby="staff-predefined-roles">
                <h3 id="staff-predefined-roles" className="font-semibold">Predefined roles</h3>
                <p className="text-sm text-muted-foreground">Assign roles to manage Staff responsibilities and access.</p>
                {roles.map((role) => {
                  const assigned = user.assignedRoleCodes.includes(role.code)
                  return <div key={role.code} className="flex flex-col gap-2 rounded-md bg-muted/50 p-3 sm:flex-row sm:items-center sm:justify-between">
                    <div><p className="font-medium">{role.name}</p><p className="text-xs text-muted-foreground">{assigned ? 'Assigned' : 'Not assigned'}</p></div>
                    <Button variant={assigned ? 'outline' : 'default'} disabled={locked} onClick={() => void run('role', () => changeInternalUserRole(manager, user.userId, role.code, !assigned))}>{assigned ? `Remove ${role.name}` : `Assign ${role.name}`}</Button>
                  </div>
                })}
                {user.assignedRoleCodes.some((code) => !roles.some((role) => role.code === code)) ? <p className="text-sm text-muted-foreground">Other assigned roles remain listed above and cannot be changed here.</p> : null}
              </section>
            </>}
            <div className="flex flex-wrap items-center justify-between gap-3">
              {pending ? <p role="status" className="text-sm text-muted-foreground">Waiting for confirmation and refreshed Staff information…</p> : null}
              <Button variant="outline" disabled={pending} onClick={onClose}>Close Staff management</Button>
            </div>
          </div>
        </div>
      </Dialog.Content>
    </Dialog.Portal>
  </Dialog.Root>
}
