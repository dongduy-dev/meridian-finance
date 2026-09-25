import { useState } from 'react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import type { ImportPartnerEmployeesInput } from '../api/contracts'
import {
  parsePartnerEmployeeCsv,
  partnerEmployeeCsvHeaders,
  type PartnerEmployeeCsvResult,
} from '../csv/partner-employee-csv'

const previewLimit = 25
const issueLimit = 25

type CsvSelection = {
  filename: string
  result: PartnerEmployeeCsvResult
}

type PartnerEmployeeCsvImportProps = {
  disabled: boolean
  onImport: (rows: ImportPartnerEmployeesInput['rows']) => void | Promise<void>
}

export function PartnerEmployeeCsvImport({ disabled, onImport }: PartnerEmployeeCsvImportProps) {
  const [selection, setSelection] = useState<CsvSelection>()
  const [fileError, setFileError] = useState<string>()

  const selectFile = async (file?: File) => {
    setSelection(undefined)
    setFileError(undefined)
    if (!file) return
    if (!file.name.toLowerCase().endsWith('.csv')) {
      setFileError('Choose a file with a .csv filename.')
      return
    }
    try {
      const csv = await file.text()
      setSelection({ filename: file.name, result: parsePartnerEmployeeCsv(csv) })
    } catch {
      setFileError('The selected CSV could not be read. Choose the file again.')
    }
  }

  const previewRows = selection?.result.previewRows.slice(0, previewLimit) ?? []
  const visibleIssues = selection?.result.issues.slice(0, issueLimit) ?? []
  const isSubmittable = selection
    ? selection.result.dataRowCount > 0
      && selection.result.problemRowCount === 0
      && selection.result.issues.length === 0
    : false

  return <div className="space-y-4">
    <div className="space-y-2">
      <label className="block max-w-xl space-y-1 text-sm font-medium">Partner Employee CSV
        <Input
          accept=".csv,text/csv"
          disabled={disabled}
          type="file"
          onChange={(event) => void selectFile(event.target.files?.[0])}
        />
      </label>
      <p className="text-sm text-muted-foreground">Required columns, in any order: {partnerEmployeeCsvHeaders.join(', ')}.</p>
      <p className="text-sm text-muted-foreground">The browser reviews the file and sends structured rows only. Edit problems in the source CSV, then choose it again.</p>
    </div>

    {fileError ? <Alert variant="destructive"><AlertTitle>CSV was not accepted</AlertTitle><AlertDescription>{fileError}</AlertDescription></Alert> : null}

    {selection ? <div className="space-y-4">
      <div>
        <p className="font-medium">Selected file: {selection.filename}</p>
        <dl className="mt-2 grid gap-3 text-sm sm:grid-cols-3">
          <div><dt className="text-muted-foreground">Total data rows</dt><dd className="text-lg font-semibold">{selection.result.dataRowCount}</dd></div>
          <div><dt className="text-muted-foreground">Client-valid rows</dt><dd className="text-lg font-semibold">{selection.result.validRowCount}</dd></div>
          <div><dt className="text-muted-foreground">Client-problem rows</dt><dd className="text-lg font-semibold">{selection.result.problemRowCount}</dd></div>
        </dl>
      </div>

      {selection.result.issues.length > 0 ? <Alert variant="destructive"><AlertTitle>Resolve CSV problems before import</AlertTitle><AlertDescription><ul className="mt-2 list-disc space-y-1 pl-5">{visibleIssues.map((issue, index) => <li key={`${issue.dataRow ?? 'header'}-${issue.field ?? 'csv'}-${index}`}>{issue.message}</li>)}</ul>{selection.result.issues.length > issueLimit ? <p className="mt-2">Showing the first {issueLimit} of {selection.result.issues.length} problems.</p> : null}</AlertDescription></Alert> : null}

      {previewRows.length > 0 ? <div className="space-y-2">
        <div><h3 className="font-semibold">CSV preview</h3>{selection.result.dataRowCount > previewLimit ? <p className="text-sm text-muted-foreground">Showing the first {previewLimit} of {selection.result.dataRowCount} rows. The full parsed batch will be submitted.</p> : <p className="text-sm text-muted-foreground">Showing all {selection.result.dataRowCount} parsed rows.</p>}</div>
        <div className="overflow-x-auto"><table className="w-full min-w-[70rem] text-left text-sm"><caption className="sr-only">Partner Employee CSV preview</caption><thead><tr className="border-b"><th className="p-2">Data row</th><th className="p-2">Employee code</th><th className="p-2">Identity reference</th><th className="p-2">Salary</th><th className="p-2">Advance limit</th><th className="p-2">Employment</th><th className="p-2">Active</th><th className="p-2">Client check</th></tr></thead><tbody>{previewRows.map((row) => <tr className="border-b" key={row.dataRow}><td className="p-2">{row.dataRow}</td><td className="p-2">{row.employeeCode}</td><td className="p-2">{row.identityReference}</td><td className="p-2">{row.salaryAmount}</td><td className="p-2">{row.salaryAdvanceLimit}</td><td className="p-2">{row.employmentStatus}</td><td className="p-2">{row.active}</td><td className="p-2">{row.valid ? 'Ready' : 'Problem'}</td></tr>)}</tbody></table></div>
      </div> : null}

      <Button type="button" disabled={disabled || !isSubmittable} onClick={() => void onImport(selection.result.rows)}>Import CSV rows</Button>
    </div> : null}
  </div>
}
