package com.meridian.platform.loan.domain.model;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record LoanContractRepaymentItem(
        UUID id, UUID sourceApprovedOfferRepaymentItemId, int installmentNumber,
        BigDecimal principalDue, BigDecimal interestDue, BigDecimal feeDue, BigDecimal totalDue
) {
    public LoanContractRepaymentItem {
        Objects.requireNonNull(id);
        Objects.requireNonNull(sourceApprovedOfferRepaymentItemId);
        Objects.requireNonNull(principalDue);
        Objects.requireNonNull(interestDue);
        Objects.requireNonNull(feeDue);
        Objects.requireNonNull(totalDue);
        if (installmentNumber <= 0 || principalDue.signum() < 0 || interestDue.signum() < 0
                || feeDue.signum() < 0 || totalDue.signum() < 0
                || principalDue.remainder(BigDecimal.ONE).signum() != 0
                || interestDue.remainder(BigDecimal.ONE).signum() != 0
                || feeDue.remainder(BigDecimal.ONE).signum() != 0
                || totalDue.remainder(BigDecimal.ONE).signum() != 0
                || totalDue.compareTo(principalDue.add(interestDue).add(feeDue)) != 0) {
            throw new IllegalArgumentException("Contract repayment item is invalid.");
        }
    }
}
