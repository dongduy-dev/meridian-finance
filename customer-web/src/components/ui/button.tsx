import { Slot } from '@radix-ui/react-slot'
import { cva, type VariantProps } from 'class-variance-authority'
import type { ButtonHTMLAttributes } from 'react'

import { cn } from '@/lib/cn'

const buttonVariants = cva(
  'inline-flex min-h-11 items-center justify-center gap-2 rounded-md px-4 text-base leading-6 font-semibold whitespace-normal break-words transition-colors disabled:pointer-events-none disabled:border-border disabled:bg-muted disabled:text-muted-foreground [&_svg]:pointer-events-none [&_svg]:size-5 [&_svg]:shrink-0',
  {
    variants: {
      variant: {
        default:
          'bg-primary text-primary-foreground hover:bg-primary-hover active:bg-primary-active',
        secondary:
          'border border-input bg-transparent text-card-foreground hover:bg-selected',
        ghost: 'text-foreground hover:bg-selected',
        destructive:
          'bg-danger text-primary-foreground hover:bg-danger/90 active:bg-danger',
        link: 'min-h-0 rounded-none px-0 text-primary underline underline-offset-4',
      },
      size: {
        default: 'min-h-11 px-4 py-2',
        sm: 'min-h-11 px-4 py-2',
        lg: 'min-h-12 px-4 py-3',
        icon: 'min-h-11 min-w-11 p-2',
      },
    },
    defaultVariants: {
      variant: 'default',
      size: 'default',
    },
  },
)

export interface ButtonProps
  extends ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof buttonVariants> {
  asChild?: boolean
}

export function Button({
  asChild = false,
  className,
  variant,
  size,
  ...props
}: ButtonProps) {
  const Component = asChild ? Slot : 'button'

  return (
    <Component
      className={cn(buttonVariants({ variant, size }), className)}
      {...props}
    />
  )
}

export { buttonVariants }
