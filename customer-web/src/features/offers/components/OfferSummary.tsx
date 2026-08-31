import { CalendarClock } from 'lucide-react'

import { MoneyDisplay } from '@/components/common/MoneyDisplay'
import { StatusBadge } from '@/components/common/StatusBadge'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
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
    <div className="space-y-6">
      <Card className="min-w-0">
        <CardHeader className="gap-3">
          <div className="flex min-w-0 flex-wrap items-start justify-between gap-3">
            <div>
              <CardTitle>Approved offer</CardTitle>
              <CardDescription>Immutable financial terms returned by Meridian.</CardDescription>
            </div>
            <StatusBadge presentation={offerStatusPresentation(offer.status)} />
          </div>
        </CardHeader>
        <CardContent>
          <dl className="grid gap-x-6 gap-y-5 text-sm sm:grid-cols-2">
            <Fact label="Approved principal"><MoneyDisplay value={offer.approvedPrincipal} /></Fact>
            <Fact label="Approved term">{offer.approvedTermMonths} months</Fact>
            <Fact label="Interest method">{interestMethodLabel(offer.interestCalculationMethod)}</Fact>
            <Fact label="Monthly flat rate">{formatPercentage(offer.flatMonthlyInterestRate)}</Fact>
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
        </CardContent>
      </Card>

      <section aria-labelledby="provisional-repayments" className="space-y-4">
        <div>
          <h2 id="provisional-repayments" className="text-xl font-semibold">Provisional repayment preview</h2>
          <p className="mt-1 text-sm leading-6 text-muted-foreground">These returned amounts are not the final dated LoanAccount schedule.</p>
        </div>
        <div className="grid gap-4 lg:grid-cols-2">
          {offer.repaymentItems.map((item) => (
            <Card key={item.installmentNumber} className="min-w-0">
              <CardHeader className="pb-4">
                <CardTitle className="text-lg">Installment {item.installmentNumber}</CardTitle>
                <CardDescription className="flex items-center gap-2"><CalendarClock aria-hidden="true" className="size-4" />{repaymentTimingLabel(item.repaymentTiming)}</CardDescription>
              </CardHeader>
              <CardContent>
                <dl className="grid gap-3 text-sm sm:grid-cols-2">
                  <Fact label="Principal"><MoneyDisplay value={item.principalDue} /></Fact>
                  <Fact label="Interest"><MoneyDisplay value={item.interestDue} /></Fact>
                  <Fact label="Fee"><MoneyDisplay value={item.feeDue} /></Fact>
                  <Fact label="Total"><MoneyDisplay value={item.totalDue} /></Fact>
                </dl>
              </CardContent>
            </Card>
          ))}
        </div>
      </section>
    </div>
  )
}

function Fact({ label, children }: { label: string; children: React.ReactNode }) {
  return <div className="min-w-0"><dt className="text-muted-foreground">{label}</dt><dd className="mt-1 min-w-0 break-words font-medium">{children}</dd></div>
}
