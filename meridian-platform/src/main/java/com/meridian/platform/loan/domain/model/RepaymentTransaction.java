package com.meridian.platform.loan.domain.model;

import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public record RepaymentTransaction(
        UUID id,
        UUID loanApplicationId,
        UUID loanAccountId,
        UUID repaymentScheduleId,
        RepaymentTransactionType transactionType,
        UUID requestId,
        String externalPaymentReference,
        BigDecimal receivedAmount,
        LocalDate paymentValueDate,
        UUID recordedByUserId,
        LocalDateTime recordedAt,
        List<RepaymentAllocation> allocations
) {

    private static final Pattern EXTERNAL_REFERENCE_PATTERN =
            Pattern.compile("[A-Z0-9][A-Z0-9._:/-]{0,63}");

    public RepaymentTransaction {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(loanApplicationId, "loanApplicationId must not be null");
        Objects.requireNonNull(loanAccountId, "loanAccountId must not be null");
        Objects.requireNonNull(repaymentScheduleId,
                "repaymentScheduleId must not be null");
        Objects.requireNonNull(transactionType, "transactionType must not be null");
        Objects.requireNonNull(requestId, "requestId must not be null");
        requireCanonicalReference(externalPaymentReference);
        Objects.requireNonNull(receivedAmount, "receivedAmount must not be null");
        Objects.requireNonNull(paymentValueDate, "paymentValueDate must not be null");
        Objects.requireNonNull(recordedByUserId, "recordedByUserId must not be null");
        Objects.requireNonNull(recordedAt, "recordedAt must not be null");
        allocations = List.copyOf(Objects.requireNonNull(
                allocations,
                "allocations must not be null"
        ));

        if (receivedAmount.signum() <= 0
                || receivedAmount.remainder(BigDecimal.ONE).signum() != 0) {
            throw invalid("Received amount must be a positive whole VND amount.");
        }
        validateAllocations(id, receivedAmount, allocations);
    }

    public RepaymentTransaction(
            UUID id,
            UUID loanApplicationId,
            UUID loanAccountId,
            UUID repaymentScheduleId,
            UUID requestId,
            String externalPaymentReference,
            BigDecimal receivedAmount,
            LocalDate paymentValueDate,
            UUID recordedByUserId,
            LocalDateTime recordedAt,
            List<RepaymentAllocation> allocations
    ) {
        this(
                id,
                loanApplicationId,
                loanAccountId,
                repaymentScheduleId,
                RepaymentTransactionType.REPAYMENT,
                requestId,
                externalPaymentReference,
                receivedAmount,
                paymentValueDate,
                recordedByUserId,
                recordedAt,
                allocations
        );
    }

    public static RepaymentTransaction recorded(
            UUID id,
            UUID loanApplicationId,
            UUID loanAccountId,
            UUID repaymentScheduleId,
            UUID requestId,
            String externalPaymentReference,
            BigDecimal receivedAmount,
            LocalDate paymentValueDate,
            LocalDate disbursementValueDate,
            LocalDate currentUtcDate,
            UUID recordedByUserId,
            LocalDateTime recordedAt,
            List<RepaymentAllocation> allocations
    ) {
        validateValueDate(paymentValueDate, disbursementValueDate, currentUtcDate);
        return new RepaymentTransaction(
                id,
                loanApplicationId,
                loanAccountId,
                repaymentScheduleId,
                RepaymentTransactionType.REPAYMENT,
                requestId,
                externalPaymentReference,
                receivedAmount,
                paymentValueDate,
                recordedByUserId,
                recordedAt,
                allocations
        );
    }

    public static RepaymentTransaction approvedSettlement(
            UUID id,
            UUID loanApplicationId,
            UUID loanAccountId,
            UUID repaymentScheduleId,
            UUID requestId,
            String externalPaymentReference,
            BigDecimal receivedAmount,
            LocalDate paymentValueDate,
            LocalDate disbursementValueDate,
            LocalDate currentUtcDate,
            UUID approvedByUserId,
            LocalDateTime approvedAt,
            List<RepaymentAllocation> allocations
    ) {
        validateValueDate(paymentValueDate, disbursementValueDate, currentUtcDate);
        return new RepaymentTransaction(
                id,
                loanApplicationId,
                loanAccountId,
                repaymentScheduleId,
                RepaymentTransactionType.APPROVED_SETTLEMENT,
                requestId,
                externalPaymentReference,
                receivedAmount,
                paymentValueDate,
                approvedByUserId,
                approvedAt,
                allocations
        );
    }

    public static String canonicalizeReference(String reference) {
        if (reference == null) {
            throw invalid("External payment reference is required.");
        }
        String canonical = reference.trim().toUpperCase(Locale.ROOT);
        requireCanonicalReference(canonical);
        return canonical;
    }

    public static void requireCanonicalReference(String reference) {
        if (reference == null
                || !reference.equals(reference.trim().toUpperCase(Locale.ROOT))
                || !EXTERNAL_REFERENCE_PATTERN.matcher(reference).matches()) {
            throw invalid("External payment reference must already be canonical.");
        }
    }

    public static void validateValueDate(
            LocalDate paymentValueDate,
            LocalDate disbursementValueDate,
            LocalDate currentUtcDate
    ) {
        Objects.requireNonNull(paymentValueDate, "paymentValueDate must not be null");
        Objects.requireNonNull(disbursementValueDate,
                "disbursementValueDate must not be null");
        Objects.requireNonNull(currentUtcDate, "currentUtcDate must not be null");
        if (paymentValueDate.isBefore(disbursementValueDate)
                || paymentValueDate.isAfter(currentUtcDate)) {
            throw invalid("Payment value date is outside the permitted UTC date range.");
        }
    }

    @Override
    public String toString() {
        return "RepaymentTransaction[id=" + id
                + ", loanApplicationId=" + loanApplicationId
                + ", loanAccountId=" + loanAccountId
                + ", repaymentScheduleId=" + repaymentScheduleId
                + ", transactionType=" + transactionType
                + ", paymentEvidence=redacted]";
    }

    private static void validateAllocations(
            UUID transactionId,
            BigDecimal receivedAmount,
            List<RepaymentAllocation> allocations
    ) {
        if (allocations.isEmpty()) {
            throw invalid("Repayment transaction requires allocations.");
        }
        BigDecimal allocated = BigDecimal.ZERO;
        HashSet<String> allocatedComponents = new HashSet<>();
        for (int index = 0; index < allocations.size(); index++) {
            RepaymentAllocation allocation = allocations.get(index);
            if (!allocation.repaymentTransactionId().equals(transactionId)
                    || allocation.allocationSequence() != index + 1) {
                throw invalid("Repayment allocations are not in deterministic sequence.");
            }
            String componentKey = allocation.repaymentScheduleItemId()
                    + ":" + allocation.component();
            if (!allocatedComponents.add(componentKey)) {
                throw invalid("Repayment component is allocated more than once.");
            }
            allocated = allocated.add(allocation.amount());
        }
        if (allocated.compareTo(receivedAmount) != 0) {
            throw invalid("Repayment transaction amount does not equal allocation total.");
        }
    }

    private static BusinessRuleViolationException invalid(String message) {
        return new BusinessRuleViolationException("REPAYMENT_TRANSACTION_INVALID", message);
    }
}
