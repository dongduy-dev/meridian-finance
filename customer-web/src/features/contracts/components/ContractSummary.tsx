import { Landmark } from 'lucide-react'

import { MoneyDisplay } from '@/components/common/MoneyDisplay'
import { StatusBadge } from '@/components/common/StatusBadge'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { formatPercentage, formatTimestamp } from '@/lib/format/presentation'

import type { LoanContract } from '../contract-api'
import {
  contractStatusPresentation,
  interestMethodLabel,
  repaymentMethodLabel,
} from '../contract-presentation'

export function ContractSummary({ contract }: { contract: LoanContract }) {
  const account = contract.disbursementBankAccount
  return (
    <div className="space-y-6">
      <Card className="min-w-0">
        <CardHeader className="gap-3">
          <div className="flex min-w-0 flex-wrap items-start justify-between gap-3">
            <div className="min-w-0">
              <CardTitle className="break-all">{contract.contractReference}</CardTitle>
              <CardDescription>Operational contract version {contract.contractVersion}</CardDescription>
            </div>
            <StatusBadge presentation={contractStatusPresentation(contract.status)} />
          </div>
        </CardHeader>
        <CardContent>
          <dl className="grid gap-x-6 gap-y-5 text-sm sm:grid-cols-2">
            <Fact label="Accepted principal"><MoneyDisplay value={contract.approvedPrincipal} /></Fact>
            <Fact label="Term">{contract.approvedTermMonths} months</Fact>
            <Fact label="Interest method">{interestMethodLabel(contract.interestCalculationMethod)}</Fact>
            <Fact label="Monthly flat rate">{formatPercentage(contract.flatMonthlyInterestRate)}</Fact>
            <Fact label="Total interest"><MoneyDisplay value={contract.totalInterest} /></Fact>
            <Fact label="Fee"><MoneyDisplay value={contract.feeAmount} /></Fact>
            <Fact label="Total repayment"><MoneyDisplay value={contract.totalRepaymentAmount} /></Fact>
            <Fact label="Repayment method">{repaymentMethodLabel(contract.repaymentMethod)}</Fact>
            <Fact label="Prepared">{formatTimestamp(contract.preparedAt)}</Fact>
            {contract.acknowledgedAt ? <Fact label="Acknowledged">{formatTimestamp(contract.acknowledgedAt)}</Fact> : null}
          </dl>
        </CardContent>
      </Card>

      <Card className="min-w-0">
        <CardHeader>
          <CardTitle className="flex items-center gap-2"><Landmark aria-hidden="true" className="size-5" />Captured disbursement destination</CardTitle>
          <CardDescription>This masked snapshot is bound to this exact contract version.</CardDescription>
        </CardHeader>
        <CardContent>
          <dl className="grid gap-x-6 gap-y-5 text-sm sm:grid-cols-2">
            <Fact label="Bank">{account.bankNameSnapshot}</Fact>
            <Fact label="Bank code">{account.bankCode}</Fact>
            <Fact label="Account holder">{account.accountHolderName}</Fact>
            <Fact label="Masked account"><span className="break-all font-mono">{account.maskedAccountNumber}</span></Fact>
            <Fact label="Captured">{formatTimestamp(account.capturedAt)}</Fact>
            <Fact label="Snapshot eligibility">{account.primaryAtCapture && account.activeAtCapture ? 'Primary and active when captured' : 'Historical eligibility unavailable'}</Fact>
          </dl>
        </CardContent>
      </Card>

      <section aria-labelledby="contract-repayment-preview" className="space-y-4">
        <div>
          <h2 id="contract-repayment-preview" className="text-xl font-semibold">Contract repayment preview</h2>
          <p className="mt-1 text-sm leading-6 text-muted-foreground">Meridian returned these version-bound amounts without final calendar due dates.</p>
        </div>
        <div className="grid gap-4 lg:grid-cols-2">
          {contract.repaymentPreview.map((item) => (
            <Card key={item.installmentNumber} className="min-w-0">
              <CardHeader className="pb-4"><CardTitle className="text-lg">Installment {item.installmentNumber}</CardTitle></CardHeader>
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
