import type { InputHTMLAttributes } from 'react'

import { cn } from '@/lib/cn'

export function Input({ className, ...props }: InputHTMLAttributes<HTMLInputElement>) {
  return (
    <input
      className={cn(
        'flex min-h-11 w-full min-w-0 rounded-md border border-input bg-card px-3 py-2 text-base leading-6 text-foreground placeholder:text-muted-foreground focus-visible:border-ring disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground',
        'aria-invalid:border-danger',
        className,
      )}
      {...props}
    />
  )
}
