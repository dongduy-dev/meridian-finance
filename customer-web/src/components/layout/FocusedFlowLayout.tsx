import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'

import { MeridianLogo } from '@/components/common/MeridianLogo'

export interface FocusedFlowLayoutProps {
  title: string
  description: string
  currentStep: number
  totalSteps: number
  children: ReactNode
  backAction: ReactNode
  continueAction: ReactNode
}

export function FocusedFlowLayout({
  title,
  description,
  currentStep,
  totalSteps,
  children,
  backAction,
  continueAction,
}: FocusedFlowLayoutProps) {
  const progress = Math.round((currentStep / totalSteps) * 100)

  return (
    <div className="min-h-svh bg-background pb-24 sm:pb-28">
      <header className="border-b border-border bg-card">
        <div className="mx-auto flex min-h-20 max-w-3xl items-center justify-between px-4 sm:px-6">
          <Link to="/" aria-label="Return to Meridian Customer Web foundation">
            <MeridianLogo variant="primary" className="w-28" />
          </Link>
          <p className="text-sm font-medium text-muted-foreground">
            Step {currentStep} of {totalSteps}
          </p>
        </div>
      </header>

      <main className="mx-auto max-w-3xl px-4 py-8 sm:px-6 sm:py-12">
        <div className="mb-8" aria-label={`Step ${currentStep} of ${totalSteps}`}>
          <div className="h-1.5 overflow-hidden rounded-full bg-muted" aria-hidden="true">
            <div className="h-full rounded-full bg-primary" style={{ width: `${progress}%` }} />
          </div>
        </div>
        <header className="mb-8 space-y-3">
          <p className="text-xs font-semibold tracking-[0.16em] text-muted-foreground uppercase">
            Focused flow template
          </p>
          <h1
            id="page-heading"
            tabIndex={-1}
            className="text-3xl leading-tight font-semibold tracking-[-0.025em] outline-none"
          >
            {title}
          </h1>
          <p className="max-w-2xl leading-6 text-muted-foreground">{description}</p>
        </header>
        {children}
      </main>

      <div className="fixed inset-x-0 bottom-0 z-30 border-t border-border bg-card/96 backdrop-blur-sm">
        <div className="mx-auto flex min-h-20 max-w-3xl items-center justify-between gap-3 px-4 sm:px-6">
          {backAction}
          {continueAction}
        </div>
      </div>
    </div>
  )
}
