import { useQuery } from '@tanstack/react-query'
import { Button } from '@/components/ui/button'
import { ConfirmationDialog, ConfirmationDialogTitle, ConfirmationDialogDescription, ConfirmationDialogCancel } from '@/components/ui/confirmation-dialog'
import { Spinner } from '@/components/ui/spinner'
import { hasPermission } from '@/features/auth/model/access-control'
import { useAuth } from '@/features/auth/model/auth-context'
import { ApiError } from '@/lib/api'
import { formatVnd, knownLabel } from '@/lib/format/presentation'
import { partnerImportBatchQuery } from '../api/queries'
import { employeeStatusLabel, importRejectionLabel } from '../model/presentation'
import { CopyImportId } from './CopyImportId'
import { PartnerQueryErrorPanel } from './PartnerQueryErrorPanel'

export function PartnerImportBatchDetails({ companyId, batchId, available, authorityKnown, authoritativeBatchId, onClose }: {
  companyId: string
  batchId: string
  available: boolean
  authorityKnown: boolean
  authoritativeBatchId: string | null | undefined
  onClose: () => void
}) {
  const { manager, state } = useAuth()
  const authorized = state.status === 'authenticated' && hasPermission(state.actor, 'partner:read')
  const detail = useQuery(partnerImportBatchQuery(manager, companyId, batchId, authorized && available))
  const missing = detail.error instanceof ApiError && detail.error.status === 404
  const data = authorized && available && !detail.isError ? detail.data : undefined
  return <ConfirmationDialog onDismiss={onClose} className="max-w-5xl">
    <ConfirmationDialogTitle className="text-xl font-semibold">Employee import details</ConfirmationDialogTitle>
    <ConfirmationDialogDescription className="sr-only">Read-only recorded import evidence.</ConfirmationDialogDescription>
    <ConfirmationDialogCancel asChild><Button variant="outline" autoFocus>Close details</Button></ConfirmationDialogCancel>
    {!available ? <p role="status">The selected import is no longer available in this history. Close details and refresh the history.</p>
      : !authorized ? <p role="status">Partner access changed.</p>
        : detail.isPending ? <p role="status" className="flex items-center gap-2"><Spinner /> Loading import details…</p>
          : missing ? <p role="status">The selected import is unavailable for this Partner Company. Close details and refresh the history.</p>
            : detail.isError ? <PartnerQueryErrorPanel error={detail.error} onRetry={() => void detail.refetch()} />
              : data ? <div className="space-y-4">
                <dl className="grid gap-4 text-sm sm:grid-cols-3">
                  <div><dt className="text-muted-foreground">Effective month</dt><dd>{data.effectiveMonth}</dd></div>
                  <div><dt className="text-muted-foreground">Status</dt><dd>{knownLabel({ COMPLETED: 'Completed', FAILED: 'Failed', PENDING: 'Pending', PROCESSING: 'Processing' }, data.status, 'Import status unavailable')}</dd></div>
                  <div><dt className="text-muted-foreground">Authority</dt><dd>{authorityKnown ? batchId === authoritativeBatchId ? 'Current' : 'Historical' : 'Current authority unavailable'}</dd></div>
                  <div><dt className="text-muted-foreground">Valid rows</dt><dd>{data.validRowCount}</dd></div>
                  <div><dt className="text-muted-foreground">Invalid rows</dt><dd>{data.invalidRowCount}</dd></div>
                </dl>
                <CopyImportId id={batchId} />
                {data.status === 'COMPLETED' ? data.employees.length === 0 ? <p>This completed import has no employee rows.</p>
                  : <div className="overflow-x-auto"><table className="w-full min-w-[45rem] text-left text-sm">
                    <caption className="sr-only">Selected import employees</caption>
                    <thead><tr className="border-b">{['Employee code', 'Masked identity reference', 'Salary', 'Advance limit', 'Employment', 'Active'].map((label) => <th key={label} className="p-2">{label}</th>)}</tr></thead>
                    <tbody>{data.employees.map((employee) => <tr key={employee.id} className="border-b">
                      <td className="p-2">{employee.employeeCode}</td><td className="p-2">{employee.maskedIdentityReference}</td>
                      <td className="p-2">{formatVnd(employee.salaryAmount)}</td><td className="p-2">{formatVnd(employee.salaryAdvanceLimit)}</td>
                      <td className="p-2">{employeeStatusLabel(employee.employmentStatus)}</td><td className="p-2">{employee.active ? 'Yes' : 'No'}</td>
                    </tr>)}</tbody>
                  </table></div>
                  : <p>{data.status === 'FAILED' ? 'No employee rows were imported. Validation counts do not represent a partial import; the current roster was not replaced.' : 'This import is not completed. No successfully imported roster is available.'}</p>}
                {data.rejections.length ? <div><h3 className="font-semibold">Recorded row rejections</h3><ul className="list-disc pl-5">
                  {data.rejections.slice(0, 25).map((rejection) => <li key={`${rejection.rowIndex}-${rejection.errorCode}`}>Row {rejection.rowIndex}: {importRejectionLabel(rejection.errorCode)}</li>)}
                </ul>{data.rejections.length > 25 ? <p>Showing the first 25 of {data.rejections.length} rejected rows.</p> : null}</div>
                  : data.status === 'FAILED' ? <p>No recorded row rejection details are available.</p> : null}
              </div> : null}
  </ConfirmationDialog>
}
