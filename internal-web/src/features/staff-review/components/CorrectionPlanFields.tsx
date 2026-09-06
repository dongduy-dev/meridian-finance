import { AlertTriangle } from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { humanizeKnownValue } from '@/features/staff-applications/model/presentation'
import type {
  CorrectionOption,
  CorrectionResponsibility,
  CorrectionScope,
  CorrectionTaskInput,
  CorrectionTaskRequest,
} from '../api/contracts'

type Mode = 'CUSTOMER' | 'STAFF' | 'MIXED'

function knownScopes(option: CorrectionOption): CorrectionScope[] {
  return option.allowedScopes.filter((scope): scope is CorrectionScope =>
    scope === 'SUPPORTING_DOCUMENT_UPLOAD'
      || scope === 'DOCUMENT_REPLACEMENT'
      || scope === 'DOCUMENT_REVIEW')
}

function responsibilityFor(scope: CorrectionScope, fallback: CorrectionResponsibility): CorrectionResponsibility {
  if (scope === 'DOCUMENT_REPLACEMENT') return 'CUSTOMER'
  if (scope === 'DOCUMENT_REVIEW') return 'STAFF'
  return fallback
}

export function allowedTaskChoices(options: CorrectionOption[], mode: Mode) {
  return options.flatMap((option, optionIndex) => knownScopes(option).flatMap((scope) => {
    if (mode === 'CUSTOMER' && scope === 'DOCUMENT_REVIEW') return []
    if (mode === 'STAFF' && scope === 'DOCUMENT_REPLACEMENT') return []
    return [{ optionIndex, scope }]
  }))
}

export function validateCorrectionTasks(
  tasks: CorrectionTaskInput[],
  options: CorrectionOption[],
  mode: Mode,
): string | undefined {
  if (tasks.length < 1 || tasks.length > 10) return 'Provide between 1 and 10 structured correction tasks.'
  const choices = allowedTaskChoices(options, mode)
  const invalid = tasks.some((task) => {
    const option = options[task.optionIndex]
    const scopeAllowed = choices.some((choice) => choice.optionIndex === task.optionIndex && choice.scope === task.scope)
    const responsible = responsibilityFor(task.scope, task.responsibleParty)
    return !option || !scopeAllowed || responsible !== task.responsibleParty
      || task.instruction.trim().length < 1 || task.instruction.trim().length > 500
  })
  if (invalid) return 'Every task must use an authoritative option and a 1 to 500 character instruction.'
  const tuples = tasks.map((task) => `${task.optionIndex}:${task.scope}:${task.responsibleParty}`)
  if (new Set(tuples).size !== tuples.length) return 'Duplicate correction tasks are not allowed.'
  const createdTypes = tasks
    .filter((task) => options[task.optionIndex]?.checklistItemId === null)
    .map((task) => options[task.optionIndex]?.documentType)
  if (new Set(createdTypes).size !== createdTypes.length) return 'The same checklist item cannot be created twice.'
  if (mode === 'MIXED') {
    const responsibilities = new Set(tasks.map((task) => task.responsibleParty))
    if (!responsibilities.has('CUSTOMER') || !responsibilities.has('STAFF')) {
      return 'A mixed correction requires separate Customer-owned and Staff-owned tasks.'
    }
  }
  return undefined
}

export function toCorrectionTaskRequests(
  tasks: CorrectionTaskInput[],
  options: CorrectionOption[],
): CorrectionTaskRequest[] {
  return tasks.map((task) => {
    const option = options[task.optionIndex]!
    const instruction = task.instruction.trim()
    return {
      scope: task.scope,
      responsibleParty: task.responsibleParty,
      documentType: option.documentType,
      createChecklistItem: option.checklistItemId === null,
      checklistItemId: option.checklistItemId,
      baselineDocumentVersionId: option.currentDocumentVersionId,
      customerInstruction: task.responsibleParty === 'CUSTOMER' ? instruction : null,
      staffInstruction: task.responsibleParty === 'STAFF' ? instruction : null,
    }
  })
}

export function CorrectionPlanFields({
  options,
  mode,
  tasks,
  onChange,
}: {
  options: CorrectionOption[]
  mode: Mode
  tasks: CorrectionTaskInput[]
  onChange: (tasks: CorrectionTaskInput[]) => void
}) {
  const choices = allowedTaskChoices(options, mode)
  const addTask = () => {
    const choice = choices[0]
    if (!choice) return
    const responsibility = responsibilityFor(
      choice.scope,
      mode === 'STAFF' ? 'STAFF' : 'CUSTOMER',
    )
    onChange([...tasks, { ...choice, responsibleParty: responsibility, instruction: '' }])
  }

  return <div className="space-y-4 rounded-md border p-4"><div className="flex flex-wrap items-center justify-between gap-2"><h3 className="font-semibold">Structured correction plan</h3><Button type="button" variant="outline" onClick={addTask} disabled={tasks.length >= 10 || choices.length === 0}>Add task</Button></div>{choices.length === 0 ? <Alert variant="warning"><AlertTriangle /><AlertTitle>No authoritative correction option</AlertTitle><AlertDescription>The backend did not provide a product-valid target. This correction action remains unavailable.</AlertDescription></Alert> : null}{tasks.map((task, index) => {
    const selected = options[task.optionIndex]
    const selectedChoices = choices.filter((choice) => choice.optionIndex === task.optionIndex)
    return <div key={`${index}-${task.optionIndex}-${task.scope}`} className="grid gap-3 rounded-md bg-muted/25 p-4"><label className="grid gap-2 text-sm font-semibold">Evidence<select className="h-11 rounded-md border bg-card px-3 font-normal" value={task.optionIndex} onChange={(event) => {
      const optionIndex = Number(event.target.value)
      const firstScope = choices.find((choice) => choice.optionIndex === optionIndex)?.scope
      if (!firstScope) return
      const responsibleParty = responsibilityFor(firstScope, mode === 'STAFF' ? 'STAFF' : 'CUSTOMER')
      onChange(tasks.map((item, itemIndex) => itemIndex === index ? { ...item, optionIndex, scope: firstScope, responsibleParty } : item))
    }}>{[...new Set(choices.map((choice) => choice.optionIndex))].map((optionIndex) => <option key={optionIndex} value={optionIndex}>{humanizeKnownValue(options[optionIndex]!.documentType)}</option>)}</select></label><label className="grid gap-2 text-sm font-semibold">Task type<select className="h-11 rounded-md border bg-card px-3 font-normal" value={task.scope} onChange={(event) => {
      const scope = event.target.value as CorrectionScope
      const responsibleParty = responsibilityFor(scope, task.responsibleParty)
      onChange(tasks.map((item, itemIndex) => itemIndex === index ? { ...item, scope, responsibleParty } : item))
    }}>{selectedChoices.map((choice) => <option key={choice.scope} value={choice.scope}>{humanizeKnownValue(choice.scope)}</option>)}</select></label>{mode === 'MIXED' && task.scope === 'SUPPORTING_DOCUMENT_UPLOAD' ? <label className="grid gap-2 text-sm font-semibold">Responsible party<select className="h-11 rounded-md border bg-card px-3 font-normal" value={task.responsibleParty} onChange={(event) => onChange(tasks.map((item, itemIndex) => itemIndex === index ? { ...item, responsibleParty: event.target.value as CorrectionResponsibility } : item))}><option value="CUSTOMER">Customer</option><option value="STAFF">Staff</option></select></label> : null}<label className="grid gap-2 text-sm font-semibold">{task.responsibleParty === 'CUSTOMER' ? 'Customer instruction' : 'Staff instruction'}<textarea className="min-h-24 rounded-md border bg-card p-3 font-normal" value={task.instruction} maxLength={500} onChange={(event) => onChange(tasks.map((item, itemIndex) => itemIndex === index ? { ...item, instruction: event.target.value } : item))} /></label><p className="text-xs text-muted-foreground">{selected?.checklistItemId ? `Current evidence target ${selected.checklistItemId}` : 'Creates the backend-authorized checklist item.'}</p><Button type="button" variant="outline" onClick={() => onChange(tasks.filter((_, itemIndex) => itemIndex !== index))}>Remove task</Button></div>
  })}</div>
}
