package com.meridian.platform.loan.application.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record LoanContractDto(
        UUID contractId,
        String contractReference,
        int contractVersion,
        String status,
        BigDecimal approvedPrincipal,
        int approvedTermMonths,
        String interestCalculationMethod,
        BigDecimal flatMonthlyInterestRate,
        BigDecimal totalInterest,
        BigDecimal feeAmount,
        BigDecimal totalRepaymentAmount,
        String repaymentMethod,
        List<LoanContractRepaymentItemDto> repaymentPreview,
        LoanContractBankAccountDto disbursementBankAccount,
        LocalDateTime preparedAt,
        LocalDateTime acknowledgedAt,
        LocalDateTime readinessConfirmedAt,
        String availableCustomerAction
) {
    public LoanContractDto {
        repaymentPreview = List.copyOf(repaymentPreview);
    }
}
