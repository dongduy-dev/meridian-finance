import { Landmark } from 'lucide-react'

import { MoneyDisplay } from '@/components/common/MoneyDisplay'
import { StatusBadge } from '@/components/common/StatusBadge'
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
    <div className="min-w-0 space-y-[var(--section-transactional)]">
      <section aria-labelledby="contract-terms" className="min-w-0 space-y-6">
        <div className="flex min-w-0 flex-wrap items-start justify-between gap-4">
          <div className="min-w-0 space-y-2">
            <h2 id="contract-terms" className="type-section [overflow-wrap:anywhere]">{contract.contractReference}</h2>
            <p className="text-sm leading-5 text-muted-foreground">Contract version {contract.contractVersion}</p>
          </div>
          <StatusBadge presentation={contractStatusPresentation(contract.status)} />
        </div>
        <dl className="grid min-w-0 gap-x-6 border-b border-border sm:grid-cols-2">
          <Fact label="Accepted principal"><MoneyDisplay value={contract.approvedPrincipal} emphasis="primary" /></Fact>
          <Fact label="Term">{contract.approvedTermMonths} months</Fact>
          <Fact label="Interest method">{interestMethodLabel(contract.interestCalculationMethod)}</Fact>
          <Fact label="Monthly flat interest rate">{formatPercentage(contract.flatMonthlyInterestRate)}</Fact>
          <Fact label="Total interest"><MoneyDisplay value={contract.totalInterest} /></Fact>
          <Fact label="Fee"><MoneyDisplay value={contract.feeAmount} /></Fact>
          <Fact label="Total repayment"><MoneyDisplay value={contract.totalRepaymentAmount} /></Fact>
          <Fact label="Repayment method">{repaymentMethodLabel(contract.repaymentMethod)}</Fact>
          <Fact label="Prepared">{formatTimestamp(contract.preparedAt)}</Fact>
          {contract.acknowledgedAt ? <Fact label="Acknowledged">{formatTimestamp(contract.acknowledgedAt)}</Fact> : null}
        </dl>
      </section>

      <section aria-labelledby="contract-destination" className="min-w-0 space-y-6">
        <div className="space-y-2">
          <h2 id="contract-destination" className="type-section flex items-start gap-2"><Landmark aria-hidden="true" className="mt-1 size-5 shrink-0" />Disbursement account</h2>
          <p className="max-w-[70ch] text-sm leading-5 text-muted-foreground">This is the bank account recorded for payment of your loan funds under this contract version. Updating your saved bank accounts does not change this record.</p>
        </div>
        <dl className="grid min-w-0 gap-x-6 border-b border-border sm:grid-cols-2">
          <Fact label="Bank">{account.bankNameSnapshot}</Fact>
          <Fact label="Bank code">{account.bankCode}</Fact>
          <Fact label="Account holder">{account.accountHolderName}</Fact>
          <Fact label="Account number (partly hidden)"><span className="break-all font-mono">{account.maskedAccountNumber}</span></Fact>
          <Fact label="Recorded">{formatTimestamp(account.capturedAt)}</Fact>
          <Fact label="Account status when selected">{account.primaryAtCapture && account.activeAtCapture ? 'Primary and active when selected' : 'Previous status unavailable'}</Fact>
        </dl>
      </section>

      <section aria-labelledby="contract-repayment-preview" className="space-y-4">
        <div>
          <h2 id="contract-repayment-preview" className="type-section">Contract repayment preview</h2>
          <p className="mt-2 max-w-[70ch] text-sm leading-5 text-muted-foreground">These amounts are linked to this contract version. Your final repayment schedule will include due dates.</p>
        </div>
        <ol className="min-w-0 divide-y divide-border border-y border-border">
          {contract.repaymentPreview.map((item) => (
            <li key={item.installmentNumber} className="min-w-0 space-y-4 py-6">
              <h3 className="text-base font-semibold leading-6">Installment {item.installmentNumber}</h3>
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
