import { useEffect, type ReactNode } from 'react'
import { Link } from 'react-router-dom'

import { MeridianLogo } from '@/components/common/MeridianLogo'

export interface FocusedFlowLayoutProps {
  eyebrow?: string
  title: string
  description: string
  currentStep?: number
  totalSteps?: number
  children: ReactNode
  backAction?: ReactNode
  continueAction?: ReactNode
}

export function FocusedFlowLayout({
  eyebrow = 'Focused flow template',
  title,
  description,
  currentStep,
  totalSteps,
  children,
  backAction,
  continueAction,
}: FocusedFlowLayoutProps) {
  const showsProgress = currentStep !== undefined && totalSteps !== undefined
  const progress = showsProgress ? Math.round((currentStep / totalSteps) * 100) : undefined
  const showsActions = backAction !== undefined || continueAction !== undefined

  useEffect(() => {
    const frame = requestAnimationFrame(() => document.getElementById('page-heading')?.focus())
    return () => cancelAnimationFrame(frame)
  }, [title])

  return (
    <div className={`min-h-svh bg-background${showsActions ? ' pb-24 sm:pb-28' : ''}`}>
      <header className="border-b border-border bg-card">
        <div className="mx-auto flex min-h-20 max-w-3xl items-center justify-between px-4 sm:px-6">
          <Link to="/" aria-label="Return to Meridian Customer Web">
            <MeridianLogo variant="primary" className="w-28" />
          </Link>
          {showsProgress ? (
            <p className="text-sm font-medium text-muted-foreground">
              Step {currentStep} of {totalSteps}
            </p>
          ) : null}
        </div>
      </header>

      <main className="mx-auto max-w-3xl px-4 py-8 sm:px-6 sm:py-12">
        {showsProgress ? (
          <div className="mb-8" aria-label={`Step ${currentStep} of ${totalSteps}`}>
            <div className="h-1.5 overflow-hidden rounded-full bg-muted" aria-hidden="true">
              <div className="h-full rounded-full bg-primary" style={{ width: `${progress}%` }} />
            </div>
          </div>
        ) : null}
        <header className="mb-8 space-y-3">
          <p className="text-xs font-semibold tracking-[0.16em] text-muted-foreground uppercase">
            {eyebrow}
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

      {showsActions ? (
        <div className="fixed inset-x-0 bottom-0 z-30 border-t border-border bg-card/96 backdrop-blur-sm">
          <div className="mx-auto flex min-h-20 max-w-3xl items-center justify-between gap-3 px-4 sm:px-6">
            {backAction}
            {continueAction}
          </div>
        </div>
      ) : null}
    </div>
  )
}
