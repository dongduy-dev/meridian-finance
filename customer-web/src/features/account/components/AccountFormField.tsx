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
    <div className="min-w-0 space-y-2 [overflow-wrap:anywhere]">
      <label htmlFor={htmlFor} className="block text-sm font-semibold text-foreground">
        {label}
        {required ? <span className="ml-1 text-danger" aria-hidden="true">*</span> : null}
      </label>
      {children}
      {description ? (
        <p id={`${htmlFor}-description`} className="text-sm leading-5 text-muted-foreground">
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
    <div className="min-w-0 [overflow-wrap:anywhere]">
      <label
        htmlFor={id}
        className="flex min-h-11 cursor-pointer items-start gap-3 py-2 text-sm leading-6"
      >
        {children}
        <span>{label}</span>
      </label>
      {error ? <p id={`${id}-error`} className="mt-2 text-sm text-danger">{error}</p> : null}
    </div>
  )
}
