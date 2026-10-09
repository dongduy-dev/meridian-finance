import * as DialogPrimitive from '@radix-ui/react-dialog'
import { useRef, useState, type ComponentProps, type ReactNode } from 'react'

import { cn } from '@/lib/cn'

export const ConfirmationDialogCancel = DialogPrimitive.Close
export const ConfirmationDialogTitle = DialogPrimitive.Title
export const ConfirmationDialogDescription = DialogPrimitive.Description

// Feature state mounts the confirmation and owns dismissal and every command.
export function ConfirmationDialog({ children, onDismiss, className }: {
  children: ReactNode
  onDismiss: () => void
  className?: string
}) {
  const [invoker] = useState(() => document.activeElement instanceof HTMLElement
    ? document.activeElement : undefined)
  const dismissed = useRef(false)

  return <DialogPrimitive.Root open modal onOpenChange={(open) => {
    if (!open) {
      dismissed.current = true
      onDismiss()
    }
  }}>
    <DialogPrimitive.Portal>
      <DialogPrimitive.Overlay className="fixed inset-0 z-50 bg-black/45" />
      <DialogPrimitive.Content
        className={cn('fixed left-1/2 top-1/2 z-50 w-[calc(100%-2rem)] max-w-lg max-h-[calc(100dvh-2rem)] -translate-x-1/2 -translate-y-1/2 space-y-4 overflow-y-auto overscroll-contain break-words rounded-lg bg-card p-6 shadow-xl', className)}
        onInteractOutside={(event) => event.preventDefault()}
        onCloseAutoFocus={(event) => {
          event.preventDefault()
          // Command completion keeps the feature's existing result-focus policy.
          if (dismissed.current && invoker?.isConnected) invoker.focus()
        }}
      >
        {children}
      </DialogPrimitive.Content>
    </DialogPrimitive.Portal>
  </DialogPrimitive.Root>
}

export function ConfirmationDialogFooter({ className, ...props }: ComponentProps<'div'>) {
  return <div className={cn('flex flex-col gap-2 sm:flex-row sm:flex-wrap sm:justify-end', className)} {...props} />
}
