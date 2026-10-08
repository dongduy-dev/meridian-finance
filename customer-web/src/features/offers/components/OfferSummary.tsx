import { CalendarClock } from 'lucide-react'

import { MoneyDisplay } from '@/components/common/MoneyDisplay'
import { StatusBadge } from '@/components/common/StatusBadge'
import { formatPercentage, formatTimestamp } from '@/lib/format/presentation'

import type { ApprovedOffer } from '../offer-api'
import {
  interestMethodLabel,
  offerStatusPresentation,
  repaymentMethodLabel,
  repaymentTimingLabel,
} from '../offer-presentation'

export function OfferSummary({ offer }: { offer: ApprovedOffer }) {
  return (
    <div className="min-w-0 space-y-[var(--section-transactional)]">
      <section aria-labelledby="approved-offer-terms" className="min-w-0 space-y-6">
        <div className="flex min-w-0 flex-wrap items-start justify-between gap-4">
          <div className="min-w-0 space-y-2">
            <h2 id="approved-offer-terms" className="type-section">Approved offer</h2>
            <p className="max-w-[70ch] text-sm leading-5 text-muted-foreground">Review the financial terms approved for your application.</p>
          </div>
          <StatusBadge presentation={offerStatusPresentation(offer.status)} />
        </div>
        <dl className="grid min-w-0 gap-x-6 border-b border-border sm:grid-cols-2">
          <Fact label="Approved principal"><MoneyDisplay value={offer.approvedPrincipal} emphasis="primary" /></Fact>
          <Fact label="Approved term">{offer.approvedTermMonths} months</Fact>
          <Fact label="Interest method">{interestMethodLabel(offer.interestCalculationMethod)}</Fact>
          <Fact label="Monthly flat interest rate">{formatPercentage(offer.flatMonthlyInterestRate)}</Fact>
          <Fact label="Total interest"><MoneyDisplay value={offer.totalInterest} /></Fact>
          <Fact label="Fee"><MoneyDisplay value={offer.feeAmount} /></Fact>
          <Fact label="Total repayment"><MoneyDisplay value={offer.totalRepaymentAmount} /></Fact>
          <Fact label="Repayment method">{repaymentMethodLabel(offer.repaymentMethod)}</Fact>
          <Fact label="Generated">{formatTimestamp(offer.generatedAt)}</Fact>
          <Fact label="Expires">{formatTimestamp(offer.expiresAt)}</Fact>
          {offer.acceptedAt ? <Fact label="Accepted">{formatTimestamp(offer.acceptedAt)}</Fact> : null}
          {offer.declinedAt ? <Fact label="Declined">{formatTimestamp(offer.declinedAt)}</Fact> : null}
          {offer.expiredAt ? <Fact label="Expired">{formatTimestamp(offer.expiredAt)}</Fact> : null}
        </dl>
      </section>

      <section aria-labelledby="provisional-repayments" className="space-y-4">
        <div>
          <h2 id="provisional-repayments" className="type-section">Provisional repayment preview</h2>
          <p className="mt-2 max-w-[70ch] text-sm leading-5 text-muted-foreground">These amounts are a preview. Your final repayment schedule will include the due dates.</p>
        </div>
        <ol className="min-w-0 divide-y divide-border border-y border-border">
          {offer.repaymentItems.map((item) => (
            <li key={item.installmentNumber} className="min-w-0 space-y-4 py-6">
              <div className="flex min-w-0 flex-wrap items-start justify-between gap-2">
                <h3 className="text-base font-semibold leading-6">Installment {item.installmentNumber}</h3>
                <p className="flex min-w-0 items-start gap-2 text-sm leading-5 text-muted-foreground"><CalendarClock aria-hidden="true" className="size-5 shrink-0" />{repaymentTimingLabel(item.repaymentTiming)}</p>
              </div>
              <dl className="grid min-w-0 gap-x-6 gap-y-4 sm:grid-cols-2 lg:grid-cols-4">
                <PreviewFact label="Principal"><MoneyDisplay value={item.principalDue} /></PreviewFact>
                <PreviewFact label="Interest"><MoneyDisplay value={item.interestDue} /></PreviewFact>
                <PreviewFact label="Fee"><MoneyDisplay value={item.feeDue} /></PreviewFact>
                <PreviewFact label="Total"><MoneyDisplay value={item.totalDue} /></PreviewFact>
              </dl>
            </li>
          ))}
        </ol>
      </section>
    </div>
  )
}

function Fact({ label, children }: { label: string; children: React.ReactNode }) {
  return <div className="min-w-0 border-t border-border py-4"><dt className="text-sm leading-5 text-muted-foreground">{label}</dt><dd className="mt-2 min-w-0 text-base font-medium leading-6 [overflow-wrap:anywhere]">{children}</dd></div>
}

function PreviewFact({ label, children }: { label: string; children: React.ReactNode }) {
  return <div className="min-w-0"><dt className="text-sm leading-5 text-muted-foreground">{label}</dt><dd className="mt-2 min-w-0 text-base leading-6 [overflow-wrap:anywhere]">{children}</dd></div>
}
