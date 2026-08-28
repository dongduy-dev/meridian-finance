import type { ReactNode } from 'react'

export interface FormFieldProps {
  children: ReactNode
  description?: string
  error?: string
  htmlFor: string
  label: string
}

export function FormField({ children, description, error, htmlFor, label }: FormFieldProps) {
  const descriptionId = `${htmlFor}-description`
  const errorId = `${htmlFor}-error`

  return (
    <div className="space-y-2">
      <label htmlFor={htmlFor} className="block text-sm font-semibold text-foreground">
        {label}
      </label>
      {children}
      {description ? (
        <p id={descriptionId} className="text-xs leading-5 text-muted-foreground">
          {description}
        </p>
      ) : null}
      {error ? (
        <p id={errorId} className="text-sm leading-5 text-danger">
          {error}
        </p>
      ) : null}
    </div>
  )
}
