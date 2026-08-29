import type { ReactNode } from 'react'

export function AccountFormField({
  children,
  description,
  error,
  htmlFor,
  label,
  required = false,
}: {
  children: ReactNode
  description?: string
  error?: string
  htmlFor: string
  label: string
  required?: boolean
}) {
  return (
    <div className="space-y-2">
      <label htmlFor={htmlFor} className="block text-sm font-semibold text-foreground">
        {label}
        {required ? <span className="ml-1 text-danger" aria-hidden="true">*</span> : null}
      </label>
      {children}
      {description ? (
        <p id={`${htmlFor}-description`} className="text-xs leading-5 text-muted-foreground">
          {description}
        </p>
      ) : null}
      {error ? (
        <p id={`${htmlFor}-error`} className="text-sm leading-5 text-danger">
          {error}
        </p>
      ) : null}
    </div>
  )
}

export function ConsentField({
  error,
  id,
  label,
  children,
}: {
  error?: string
  id: string
  label: string
  children: ReactNode
}) {
  return (
    <div>
      <label
        htmlFor={id}
        className="flex min-h-11 cursor-pointer items-start gap-3 rounded-md border border-border bg-background p-3 text-sm leading-6"
      >
        {children}
        <span>{label}</span>
      </label>
      {error ? <p id={`${id}-error`} className="mt-2 text-sm text-danger">{error}</p> : null}
    </div>
  )
}
