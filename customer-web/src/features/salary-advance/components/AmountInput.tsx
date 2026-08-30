import type { ControllerRenderProps, FieldValues, Path } from 'react-hook-form'

import { Input } from '@/components/ui/input'

function normalizeWholeVndInput(value: string) {
  const withoutGrouping = value.replace(/[ ,]/g, '')
  return /^\d*$/.test(withoutGrouping) ? withoutGrouping : value
}

export function AmountInput<TValues extends FieldValues>({
  describedBy,
  field,
  invalid,
}: {
  describedBy: string
  field: ControllerRenderProps<TValues, Path<TValues>>
  invalid: boolean
}) {
  return (
    <div className="relative">
      <Input
        {...field}
        value={typeof field.value === 'string' ? field.value : ''}
        id={field.name}
        type="text"
        inputMode="numeric"
        autoComplete="off"
        aria-invalid={invalid}
        aria-describedby={describedBy}
        className="pr-16 tabular-nums"
        onChange={(event) => field.onChange(normalizeWholeVndInput(event.target.value))}
      />
      <span className="pointer-events-none absolute inset-y-0 right-3 flex items-center text-sm font-semibold text-muted-foreground" aria-hidden="true">
        VND
      </span>
    </div>
  )
}
