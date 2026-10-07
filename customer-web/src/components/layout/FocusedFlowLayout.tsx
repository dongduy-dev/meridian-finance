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
  eyebrow = 'Your application',
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
    <div className="focused-flow flex min-h-svh flex-col bg-background">
      <header className="on-navy">
        <div className="page-container flow-container flex min-h-16 flex-wrap items-center justify-between gap-4 py-3">
          <Link to="/" className="flex min-h-11 min-w-11 items-center" aria-label="Return to Meridian Home">
            <MeridianLogo variant="mark" className="w-8" />
          </Link>
          {showsProgress ? (
            <p className="text-sm leading-5 font-medium">
              Step {currentStep} of {totalSteps}
            </p>
          ) : null}
        </div>
      </header>

      <main className="page-container flow-container flex-1 py-8 md:py-12">
        {showsProgress ? (
          <div className="mb-8" aria-label={`Step ${currentStep} of ${totalSteps}`}>
            <div className="h-1 overflow-hidden bg-muted" aria-hidden="true">
              <div className="h-full bg-primary" style={{ width: `${progress}%` }} />
            </div>
          </div>
        ) : null}
        <header className="mb-8 space-y-2 [overflow-wrap:anywhere]">
          <p className="type-eyebrow text-muted-foreground">
            {eyebrow}
          </p>
          <h1
            id="page-heading"
            tabIndex={-1}
            className="type-transactional"
          >
            {title}
          </h1>
          <p className="max-w-[70ch] leading-6 text-muted-foreground">{description}</p>
        </header>
        {children}
      </main>

      {showsActions ? (
        <div className="focused-actions">
          <div className="page-container flow-container focused-actions-inner">
            {backAction}
            {continueAction}
          </div>
        </div>
      ) : null}
    </div>
  )
}
