package com.meridian.platform.loan.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record ApprovedLoanSettlementDto(
        UUID loanApplicationId,
        UUID loanAccountId,
        UUID repaymentTransactionId,
        UUID finalScheduleId,
        BigDecimal settlementAmount,
        LocalDate paymentValueDate,
        LocalDateTime approvedAt,
        BigDecimal principalAllocated,
        BigDecimal principalReleased,
        String resultingLoanAccountStatus,
        AccountBalanceDto accountBalance,
        boolean idempotentReplay
) {
    @Override
    public String toString() {
        return "ApprovedLoanSettlementDto[loanApplicationId=" + loanApplicationId
                + ", loanAccountId=" + loanAccountId
                + ", repaymentTransactionId=" + repaymentTransactionId
                + ", idempotentReplay=" + idempotentReplay
                + ", financialEvidence=redacted]";
    }

    public record AccountBalanceDto(
            BigDecimal principalPaid,
            BigDecimal interestPaid,
            BigDecimal feePaid,
            BigDecimal totalPaid,
            BigDecimal principalOutstanding,
            BigDecimal interestOutstanding,
            BigDecimal feeOutstanding,
            BigDecimal totalOutstanding,
            LocalDate lastPaymentValueDate,
            LocalDateTime lastPaymentRecordedAt,
            LocalDate servicingEvaluationDate,
            String status
    ) {
    }
}
