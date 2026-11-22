import { useRef, useState } from 'react'
import { FilterX } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import type { AuthSessionManager } from '@/features/auth/model/auth-session'
import type { AssignableInternalRole, InternalUser } from '../api/contracts'
import { knownInternalUserStatuses } from '../api/contracts'
import { InternalUserManagementDialog } from './InternalUserManagementDialog'
import { InternalUserRoles, InternalUserStatusBadge, internalUserRoleLabel, internalUserStatusLabel } from './InternalUserSummary'

const PAGE_SIZE = 20
const selectClass = 'min-h-11 w-full rounded-md border border-input bg-background px-3 font-normal'
const normalize = (value: string) => value.trim().toLowerCase().replace(/\s+/g, ' ')

export function InternalUserDirectory({ users, roles, manager, refreshing, unavailable }: {
  users: InternalUser[]
  roles: AssignableInternalRole[]
  manager: AuthSessionManager
  refreshing: boolean
  unavailable: boolean
}) {
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState('')
  const [role, setRole] = useState('')
  const [paging, setPaging] = useState({ source: users, page: 1 })
  const [selection, setSelection] = useState<{ id: string; trigger: HTMLButtonElement }>()
  const heading = useRef<HTMLHeadingElement>(null)
  // A replacement authorized list starts at the first page, including same-size refreshes.
  if (paging.source !== users) setPaging({ source: users, page: 1 })
  const query = normalize(search)
  const filtered = users.filter((user) => (!query || normalize(user.displayName).includes(query) || normalize(user.email).includes(query))
    && (!status || (status === 'unavailable' ? !knownInternalUserStatuses.some((known) => known === user.status) : user.status === status))
    && (!role || user.assignedRoleCodes.includes(role)))
  const pages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE))
  const page = Math.min(paging.source === users ? paging.page : 1, pages)
  const start = (page - 1) * PAGE_SIZE
  const visible = filtered.slice(start, start + PAGE_SIZE)
  const assignedCodes = [...new Set([...users.flatMap((user) => user.assignedRoleCodes), ...(role ? [role] : [])])]
  const resetPage = () => setPaging({ source: users, page: 1 })
  const reset = () => { setSearch(''); setStatus(''); setRole(''); resetPage() }
  const open = (user: InternalUser, trigger: HTMLButtonElement) => setSelection({ id: user.userId, trigger })

  return <section aria-labelledby="staff-directory-heading" className="space-y-4">
    <h2 id="staff-directory-heading" ref={heading} tabIndex={-1} className="sr-only">Staff directory</h2>
    <div className="rounded-lg border bg-card p-4 shadow-soft sm:p-5" aria-label="Staff filters">
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-[2fr_1fr_1fr]">
        <label className="grid gap-2 text-sm font-semibold">Search Staff
          <Input className="font-normal" type="search" placeholder="Name or email" value={search} onChange={(event) => { setSearch(event.target.value); resetPage() }} />
        </label>
        <label className="grid gap-2 text-sm font-semibold">Status
          <select className={selectClass} value={status} onChange={(event) => { setStatus(event.target.value); resetPage() }}>
            <option value="">All statuses</option>
            {knownInternalUserStatuses.map((value) => <option key={value} value={value}>{internalUserStatusLabel(value)}</option>)}
            <option value="unavailable">Status unavailable</option>
          </select>
        </label>
        <label className="grid gap-2 text-sm font-semibold">Assigned role
          <select className={selectClass} value={role} onChange={(event) => { setRole(event.target.value); resetPage() }}>
            <option value="">All assigned roles</option>
            {assignedCodes.map((code) => <option key={code} value={code}>{internalUserRoleLabel(code, roles)}</option>)}
          </select>
        </label>
      </div>
      <Button className="mt-4" variant="outline" onClick={reset} disabled={!search && !status && !role && page === 1}><FilterX aria-hidden="true" /> Reset filters</Button>
    </div>
    <p role="status" className="text-sm text-muted-foreground">{filtered.length} of {users.length} Staff members</p>
    {!users.length ? <p className="rounded-md border p-5 text-sm text-muted-foreground">No internal Staff Users are available.</p>
      : !filtered.length ? <div className="rounded-md border p-5"><p className="font-medium">No Staff match these filters.</p><p className="mt-1 text-sm text-muted-foreground">Try another name or email, or clear the filters.</p></div>
      : <>
        <table className="w-full table-fixed border-separate border-spacing-0 text-left text-sm max-md:block">
          <caption className="sr-only">Staff directory</caption>
          <thead className="max-md:sr-only"><tr>
            <th scope="col" className="w-[36%] border-b px-4 py-3">Staff</th>
            <th scope="col" className="w-[32%] border-b px-4 py-3">Roles</th>
            <th scope="col" className="w-[20%] border-b px-4 py-3">Status</th>
            <th scope="col" className="border-b px-4 py-3"><span className="sr-only">Action</span></th>
          </tr></thead>
          <tbody className="max-md:grid max-md:gap-3">{visible.map((user) => <tr key={user.userId} className="bg-card max-md:grid max-md:gap-2 max-md:rounded-md max-md:border max-md:p-4">
            <th scope="row" className="border-b px-4 py-3 align-top font-normal max-md:border-0 max-md:p-0">
              <button type="button" className="text-left font-semibold text-primary underline decoration-primary/30 underline-offset-4 [overflow-wrap:anywhere] hover:decoration-primary" aria-label={`Manage ${user.displayName}`} onClick={(event) => open(user, event.currentTarget)}>{user.displayName}</button>
              <p className="mt-1 text-muted-foreground [overflow-wrap:anywhere]">{user.email}</p>
            </th>
            <td className="border-b px-4 py-3 align-top max-md:border-0 max-md:p-0"><span className="mb-1 block text-xs font-medium text-muted-foreground md:hidden">Roles</span><InternalUserRoles codes={user.assignedRoleCodes} roles={roles} /></td>
            <td className="border-b px-4 py-3 align-top max-md:border-0 max-md:p-0"><InternalUserStatusBadge status={user.status} /></td>
            <td className="border-b px-4 py-3 text-right align-top max-md:border-0 max-md:p-0 max-md:text-left"><Button variant="outline" aria-label={`Edit ${user.displayName}`} onClick={(event) => open(user, event.currentTarget)}>Edit</Button></td>
          </tr>)}</tbody>
        </table>
        <nav aria-label="Staff directory pages" className="flex flex-wrap items-center justify-between gap-3">
          <p className="text-sm text-muted-foreground">Page {page} of {pages} · Showing {start + 1}–{Math.min(start + PAGE_SIZE, filtered.length)} of {filtered.length}</p>
          <div className="flex gap-2"><Button variant="outline" disabled={page === 1} onClick={() => setPaging({ source: users, page: page - 1 })}>Previous</Button><Button variant="outline" disabled={page === pages} onClick={() => setPaging({ source: users, page: page + 1 })}>Next</Button></div>
        </nav>
      </>}
    {selection ? <InternalUserManagementDialog key={selection.id} user={users.find((user) => user.userId === selection.id)} roles={roles} manager={manager}
      refreshing={refreshing} unavailable={unavailable} onClose={() => setSelection(undefined)}
      returnFocus={() => { if (selection.trigger.isConnected) selection.trigger.focus({ preventScroll: true }); else heading.current?.focus({ preventScroll: true }) }} /> : null}
  </section>
}
