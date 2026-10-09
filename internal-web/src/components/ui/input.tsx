import type { InputHTMLAttributes } from 'react'

import { cn } from '@/lib/cn'

export function Input({ className, ...props }: InputHTMLAttributes<HTMLInputElement>) {
  return (
    <input
      className={cn(
        'flex h-11 w-full rounded-md border border-input bg-card px-3 py-2 text-base text-foreground shadow-sm outline-none placeholder:text-muted-foreground/70 focus-visible:border-ring focus-visible:ring-2 focus-visible:ring-ring/15 disabled:cursor-not-allowed disabled:opacity-60 sm:text-sm',
        'aria-invalid:border-danger aria-invalid:ring-2 aria-invalid:ring-danger/12',
        props.type === 'file' && 'block h-auto min-h-11 min-w-0 cursor-pointer overflow-hidden py-3 file:mr-3 file:rounded-sm file:border-0 file:bg-selected file:px-3 file:py-2 file:text-sm file:font-semibold file:text-foreground disabled:file:cursor-not-allowed',
        className,
      )}
      {...props}
    />
  )
}
