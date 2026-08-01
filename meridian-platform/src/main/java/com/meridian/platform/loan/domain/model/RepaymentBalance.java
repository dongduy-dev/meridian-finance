package com.meridian.platform.loan.domain.model;

import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

public record RepaymentBalance(
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
        LocalDate servicingEvaluationDate
) {

    public RepaymentBalance {
        requireWholeVnd(principalPaid, "principalPaid");
        requireWholeVnd(interestPaid, "interestPaid");
        requireWholeVnd(feePaid, "feePaid");
        requireWholeVnd(totalPaid, "totalPaid");
        requireWholeVnd(principalOutstanding, "principalOutstanding");
        requireWholeVnd(interestOutstanding, "interestOutstanding");
        requireWholeVnd(feeOutstanding, "feeOutstanding");
        requireWholeVnd(totalOutstanding, "totalOutstanding");
        Objects.requireNonNull(servicingEvaluationDate,
                "servicingEvaluationDate must not be null");

        if (totalPaid.compareTo(principalPaid.add(interestPaid).add(feePaid)) != 0
                || totalOutstanding.compareTo(
                        principalOutstanding.add(interestOutstanding).add(feeOutstanding)
                ) != 0) {
            throw invalid("Repayment balance totals do not reconcile.");
        }
        boolean hasPayment = totalPaid.signum() > 0;
        if (hasPayment != (lastPaymentValueDate != null)
                || hasPayment != (lastPaymentRecordedAt != null)) {
            throw invalid("Repayment balance payment dates do not match paid evidence.");
        }
    }

    public static RepaymentBalance initial(
            BigDecimal originatedPrincipal,
            BigDecimal originatedInterest,
            BigDecimal originatedFee,
            LocalDate evaluationDate
    ) {
        return new RepaymentBalance(
                zero(),
                zero(),
                zero(),
                zero(),
                originatedPrincipal,
                originatedInterest,
                originatedFee,
                originatedPrincipal.add(originatedInterest).add(originatedFee),
                null,
                null,
                evaluationDate
        );
    }

    public void validateAgainst(
            BigDecimal originatedPrincipal,
            BigDecimal originatedInterest,
            BigDecimal originatedFee
    ) {
        requireOriginReconciliation(
                principalPaid,
                principalOutstanding,
                originatedPrincipal,
                "principal"
        );
        requireOriginReconciliation(
                interestPaid,
                interestOutstanding,
                originatedInterest,
                "interest"
        );
        requireOriginReconciliation(feePaid, feeOutstanding, originatedFee, "fee");
        if (totalPaid.add(totalOutstanding).compareTo(
                originatedPrincipal.add(originatedInterest).add(originatedFee)
        ) != 0) {
            throw invalid("Repayment balance does not reconcile to originated total.");
        }
    }

    private static void requireOriginReconciliation(
            BigDecimal paid,
            BigDecimal outstanding,
            BigDecimal originated,
            String component
    ) {
        requireWholeVnd(originated, "originated" + component);
        if (paid.add(outstanding).compareTo(originated) != 0) {
            throw invalid("Repayment balance " + component
                    + " does not reconcile to originated amount.");
        }
    }

    private static void requireWholeVnd(BigDecimal value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.signum() < 0 || value.remainder(BigDecimal.ONE).signum() != 0) {
            throw invalid(fieldName + " must be a non-negative whole VND amount.");
        }
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2);
    }

    private static BusinessRuleViolationException invalid(String message) {
        return new BusinessRuleViolationException("REPAYMENT_BALANCE_INVALID", message);
    }
}
