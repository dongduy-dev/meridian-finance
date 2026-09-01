import { ArrowLeft, Clock3, Landmark, ReceiptText } from 'lucide-react'
import { useEffect } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'

import { EmptyState } from '@/components/common/EmptyState'
import { MoneyDisplay } from '@/components/common/MoneyDisplay'
import { PageHeader } from '@/components/common/PageHeader'
import { QueryErrorFeedback } from '@/components/common/QueryErrorFeedback'
import { StatusBadge } from '@/components/common/StatusBadge'
import { DetailLayout } from '@/components/layout/DetailLayout'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { InstallmentRow } from '@/features/loans/components/InstallmentRow'
import { RepaymentHistoryItem } from '@/features/loans/components/RepaymentHistoryItem'
import { RepaymentSummary } from '@/features/loans/components/RepaymentSummary'
import { loanAccountStatusPresentation } from '@/features/loans/loan-presentation'
import {
  useLoanAccountQuery,
  useRepaymentHistoryQuery,
} from '@/features/loans/loan-queries'
import { ApiError } from '@/lib/api'
import { cn } from '@/lib/cn'
import { formatDateOnly, formatTimestamp } from '@/lib/format/presentation'

const REPAYMENT_PAGE_SIZE = 20

function normalizePage(value: string | null) {
  if (value === null || !/^(0|[1-9]\d*)$/.test(value)) return 0
  const page = Number(value)
  return Number.isSafeInteger(page) ? page : 0
}

function isInvalidPage(value: string | null) {
  return value !== null && String(normalizePage(value)) !== value
}

function BackToLoans() {
  return <Button variant="secondary" asChild><Link to="/loans"><ArrowLeft aria-hidden="true" />Back to loans</Link></Button>
}

function DetailNavigation({ loanApplicationId, historySelected }: { loanApplicationId: string; historySelected: boolean }) {
  const baseClass = 'inline-flex min-h-11 items-center rounded-md px-4 text-sm font-semibold transition-colors'
  return (
    <nav aria-label="Loan detail views" className="flex flex-wrap gap-2 rounded-lg border border-border bg-card p-2 shadow-soft">
      <Link
        to={`/loans/${loanApplicationId}`}
        aria-current={!historySelected ? 'page' : undefined}
        className={cn(baseClass, !historySelected ? 'bg-primary text-primary-foreground' : 'text-foreground hover:bg-selected')}
      >
        Overview
      </Link>
      <Link
        to={`/loans/${loanApplicationId}?tab=repayments&page=0`}
        aria-current={historySelected ? 'page' : undefined}
        className={cn(baseClass, historySelected ? 'bg-primary text-primary-foreground' : 'text-foreground hover:bg-selected')}
      >
        Repayment history
      </Link>
    </nav>
  )
}

export function LoanDetailPage() {
  const { loanApplicationId } = useParams()
  const [searchParams, setSearchParams] = useSearchParams()
  const historySelected = searchParams.get('tab') === 'repayments'
  const rawPage = searchParams.get('page')
  const page = normalizePage(rawPage)
  const detailQuery = useLoanAccountQuery(loanApplicationId)
  const historyQuery = useRepaymentHistoryQuery(
    loanApplicationId,
    page,
    REPAYMENT_PAGE_SIZE,
    historySelected && Boolean(detailQuery.data),
  )

  useEffect(() => {
    if (!historySelected || !isInvalidPage(rawPage)) return
    const normalized = new URLSearchParams(searchParams)
    normalized.set('tab', 'repayments')
    normalized.set('page', '0')
    setSearchParams(normalized, { replace: true })
  }, [historySelected, rawPage, searchParams, setSearchParams])

  const setHistoryPage = (nextPage: number) => {
    const next = new URLSearchParams(searchParams)
    next.set('tab', 'repayments')
    next.set('page', String(Math.max(0, nextPage)))
    setSearchParams(next)
  }

  const account = detailQuery.data
  const concealedUnavailable = detailQuery.error instanceof ApiError
    && detailQuery.error.status === 404
    && detailQuery.error.errorCode === 'LOAN_ACCOUNT_NOT_FOUND'

  return (
    <DetailLayout
      header={(
        <PageHeader
          eyebrow="LoanAccount"
          title={account?.accountNumber ?? (concealedUnavailable ? 'Loan unavailable' : 'Loan details')}
          description={account ? `Activated ${formatTimestamp(account.activatedAt)}` : 'Review Customer-safe LoanAccount servicing information.'}
          actions={<BackToLoans />}
        />
      )}
      rail={account ? <LoanStatusRail account={account} /> : undefined}
    >
      <div className="space-y-6">
        {detailQuery.isPending ? (
          <div role="status" aria-label="Loading LoanAccount detail" className="space-y-4">
            <Skeleton className="h-16" />
            <Skeleton className="h-96" />
            <Skeleton className="h-72" />
          </div>
        ) : null}
        {concealedUnavailable ? (
          <EmptyState
            icon={Landmark}
            title="Loan unavailable"
            description="This loan could not be found or is not available to this Customer."
          />
        ) : null}
        {detailQuery.isError && !concealedUnavailable ? (
          <QueryErrorFeedback
            error={detailQuery.error}
            title="Loan details could not be loaded"
            onRetry={() => void detailQuery.refetch()}
          />
        ) : null}
        {account && loanApplicationId ? (
          <>
            <DetailNavigation loanApplicationId={loanApplicationId} historySelected={historySelected} />
            {historySelected ? (
              <RepaymentHistory
                query={historyQuery}
                page={page}
                onPageChange={setHistoryPage}
              />
            ) : (
              <LoanOverview account={account} />
            )}
          </>
        ) : null}
      </div>
    </DetailLayout>
  )
}

type LoanAccountData = NonNullable<ReturnType<typeof useLoanAccountQuery>['data']>
type RepaymentHistoryQuery = ReturnType<typeof useRepaymentHistoryQuery>

function LoanStatusRail({ account }: { account: LoanAccountData }) {
  return (
    <Card>
      <CardHeader><CardTitle>Servicing status</CardTitle></CardHeader>
      <CardContent className="space-y-5">
        <StatusBadge presentation={loanAccountStatusPresentation(account.status)} />
        <dl className="space-y-4 text-sm">
          <div><dt className="text-muted-foreground">Total paid</dt><dd className="mt-1"><MoneyDisplay value={account.servicing.totalPaid} /></dd></div>
          <div><dt className="text-muted-foreground">Total outstanding</dt><dd className="mt-1"><MoneyDisplay value={account.servicing.totalOutstanding} /></dd></div>
          <div><dt className="text-muted-foreground">Servicing state</dt><dd className="mt-1 font-medium">As of {formatDateOnly(account.servicing.servicingEvaluationDate)}</dd></div>
        </dl>
      </CardContent>
    </Card>
  )
}

function LoanOverview({ account }: { account: LoanAccountData }) {
  const schedule = account.finalRepaymentSchedule
  return (
    <div className="space-y-6">
      <RepaymentSummary account={account} />
      <Card>
        <CardHeader><CardTitle>Disbursement destination</CardTitle></CardHeader>
        <CardContent>
          <dl className="grid gap-4 text-sm sm:grid-cols-2">
            <div><dt className="text-muted-foreground">Bank</dt><dd className="mt-1 font-medium">{account.disbursementDestination.bankName} ({account.disbursementDestination.bankCode})</dd></div>
            <div><dt className="text-muted-foreground">Account holder</dt><dd className="mt-1 break-words font-medium">{account.disbursementDestination.accountHolderName}</dd></div>
            <div><dt className="text-muted-foreground">Masked account number</dt><dd className="mt-1 break-all font-medium">{account.disbursementDestination.maskedAccountNumber}</dd></div>
          </dl>
        </CardContent>
      </Card>
      <section aria-labelledby="final-schedule-heading" className="space-y-4">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <h2 id="final-schedule-heading" className="text-xl font-semibold">Final repayment schedule</h2>
            <p className="mt-1 text-sm text-muted-foreground">
              {schedule.scheduleType === 'FINAL' ? 'Final schedule' : 'Schedule type unavailable'} · Version {schedule.version}
            </p>
          </div>
          <p className="text-sm text-muted-foreground">
            {formatDateOnly(schedule.firstDueDate)} – {formatDateOnly(schedule.lastDueDate)}
          </p>
        </div>
        {schedule.items.length ? schedule.items.map((item) => (
          <InstallmentRow key={item.installmentNumber} item={item} />
        )) : (
          <EmptyState icon={Clock3} title="No schedule items available" description="Meridian returned a final schedule without installment items." />
        )}
      </section>
    </div>
  )
}

function RepaymentHistory({ query, page, onPageChange }: { query: RepaymentHistoryQuery; page: number; onPageChange: (page: number) => void }) {
  const history = query.data
  return (
    <section aria-labelledby="repayment-history-heading" className="space-y-5">
      <div>
        <h2 id="repayment-history-heading" className="text-xl font-semibold">Repayment history</h2>
        <p className="mt-1 text-sm text-muted-foreground">Immutable payment outcomes and the balance returned after each repayment.</p>
      </div>
      {query.isPending ? (
        <div role="status" aria-label="Loading repayment history" className="space-y-4"><Skeleton className="h-72" /><Skeleton className="h-72" /></div>
      ) : null}
      {query.isError ? (
        <QueryErrorFeedback error={query.error} title="Repayment history could not be loaded" onRetry={() => void query.refetch()} />
      ) : null}
      {history?.items.length ? history.items.map((item) => (
        <RepaymentHistoryItem key={item.repaymentTransactionId} item={item} />
      )) : null}
      {history?.totalElements === 0 ? (
        <EmptyState icon={ReceiptText} title="No repayments recorded yet" description="No immutable repayment outcome has been recorded for this LoanAccount." />
      ) : null}
      {history ? (
        <nav aria-label="Repayment history pagination" className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-border bg-card p-4">
          <Button variant="secondary" disabled={page <= 0} aria-label="Previous repayment history page" onClick={() => onPageChange(page - 1)}>Previous</Button>
          <p className="text-sm text-muted-foreground">Page <span className="font-medium text-foreground">{history.page + 1}</span> · {history.totalElements} repayments</p>
          <Button variant="secondary" disabled={page + 1 >= history.totalPages} aria-label="Next repayment history page" onClick={() => onPageChange(page + 1)}>Next</Button>
        </nav>
      ) : null}
    </section>
  )
}
