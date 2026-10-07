import { cva, type VariantProps } from 'class-variance-authority'
import type { HTMLAttributes } from 'react'

import { cn } from '@/lib/cn'

const alertVariants = cva(
  'relative grid grid-cols-[auto_minmax(0,1fr)] gap-x-3 border p-4 text-sm leading-5 break-words [&>svg]:mt-0.5 [&>svg]:size-5',
  {
    variants: {
      variant: {
        information:
          'border-information/25 bg-information-subtle text-information',
        success: 'border-success/25 bg-success-subtle text-success',
        warning: 'border-warning/25 bg-warning-subtle text-warning',
        destructive: 'border-danger/25 bg-danger-subtle text-danger',
      },
    },
    defaultVariants: {
      variant: 'information',
    },
  },
)

export interface AlertProps
  extends HTMLAttributes<HTMLDivElement>,
    VariantProps<typeof alertVariants> {}

export function Alert({ className, variant, ...props }: AlertProps) {
  return (
    <div
      role="alert"
      className={cn(alertVariants({ variant }), className)}
      {...props}
    />
  )
}

export function AlertTitle({ className, ...props }: HTMLAttributes<HTMLHeadingElement>) {
  return (
    <h3 className={cn('col-start-2 font-semibold leading-5', className)} {...props} />
  )
}

export function AlertDescription({
  className,
  ...props
}: HTMLAttributes<HTMLDivElement>) {
  return (
    <div className={cn('col-start-2 mt-2 min-w-0 leading-5 text-current [overflow-wrap:anywhere]', className)} {...props} />
  )
}

export { alertVariants }
