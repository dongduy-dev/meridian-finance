package com.meridian.platform.loan.domain.model;

import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record LoanContract(
        UUID id, UUID loanApplicationId, UUID approvedOfferId, String contractReference,
        int contractVersion, LoanContractStatus status, ApprovedOfferFinancialTerms financialTerms,
        List<LoanContractRepaymentItem> repaymentItems,
        ProtectedDisbursementBankAccount disbursementBankAccount,
        UUID preparationRequestId, Integer expectedPreviousVersion,
        ContractSupersessionReason supersessionReason, UUID preparedByUserId, LocalDateTime preparedAt,
        UUID acknowledgmentRequestId, UUID acknowledgedByUserId, LocalDateTime acknowledgedAt,
        UUID confirmationRequestId, UUID confirmedByUserId, LocalDateTime confirmedAt,
        UUID supersedesContractId, UUID supersededByUserId, LocalDateTime supersededAt
) {
    public LoanContract {
        Objects.requireNonNull(id);
        Objects.requireNonNull(loanApplicationId);
        Objects.requireNonNull(approvedOfferId);
        if (contractReference == null || contractReference.isBlank() || contractVersion <= 0) {
            throw invalid("Contract identity is invalid.");
        }
        Objects.requireNonNull(status);
        Objects.requireNonNull(financialTerms);
        Objects.requireNonNull(disbursementBankAccount);
        Objects.requireNonNull(preparationRequestId);
        Objects.requireNonNull(preparedByUserId);
        Objects.requireNonNull(preparedAt);
        repaymentItems = List.copyOf(Objects.requireNonNull(repaymentItems)).stream()
                .sorted(Comparator.comparingInt(LoanContractRepaymentItem::installmentNumber)).toList();
        validateRepayment(financialTerms, repaymentItems);
        validateLifecycle(status, acknowledgmentRequestId, acknowledgedByUserId, acknowledgedAt,
                confirmationRequestId, confirmedByUserId, confirmedAt, supersededByUserId, supersededAt);
        if (contractVersion == 1 && (expectedPreviousVersion != null || supersessionReason != null || supersedesContractId != null)) {
            throw invalid("First contract cannot supersede an earlier version.");
        }
        if (contractVersion > 1 && (expectedPreviousVersion == null
                || expectedPreviousVersion != contractVersion - 1
                || supersessionReason != ContractSupersessionReason.DISBURSEMENT_ACCOUNT_REFRESH
                || supersedesContractId == null)) {
            throw invalid("Regenerated contract metadata is invalid.");
        }
    }

    public static LoanContract prepared(
            UUID id, UUID applicationId, UUID offerId, String reference, int version,
            ApprovedOfferFinancialTerms terms, List<LoanContractRepaymentItem> items,
            ProtectedDisbursementBankAccount account, UUID requestId, Integer expectedPreviousVersion,
            ContractSupersessionReason reason, UUID actor, LocalDateTime at, UUID supersedesContractId
    ) {
        return new LoanContract(id, applicationId, offerId, reference, version, LoanContractStatus.PREPARED,
                terms, items, account, requestId, expectedPreviousVersion, reason, actor, at,
                null, null, null, null, null, null, supersedesContractId, null, null);
    }

    public LoanContract acknowledge(UUID requestId, UUID customerUserId, LocalDateTime at) {
        if (status != LoanContractStatus.PREPARED) {
            throw conflict("CONTRACT_ACKNOWLEDGMENT_NOT_ALLOWED", "Only a prepared current contract may be acknowledged.");
        }
        return copy(LoanContractStatus.ACKNOWLEDGED, requestId, customerUserId, at,
                null, null, null, supersededByUserId, supersededAt);
    }

    public LoanContract confirmReady(UUID requestId, UUID accountingUserId, LocalDateTime at) {
        if (status != LoanContractStatus.ACKNOWLEDGED) {
            throw conflict("CONTRACT_READINESS_NOT_ALLOWED", "Only an acknowledged current contract may be confirmed.");
        }
        return copy(LoanContractStatus.READY_FOR_DISBURSEMENT, acknowledgmentRequestId,
                acknowledgedByUserId, acknowledgedAt, requestId, accountingUserId, at,
                supersededByUserId, supersededAt);
    }

    public LoanContract supersede(UUID actor, LocalDateTime at) {
        if (status != LoanContractStatus.PREPARED && status != LoanContractStatus.ACKNOWLEDGED) {
            throw conflict("CONTRACT_REGENERATION_NOT_ALLOWED", "Ready or superseded contracts cannot be regenerated.");
        }
        return copy(LoanContractStatus.SUPERSEDED, acknowledgmentRequestId, acknowledgedByUserId,
                acknowledgedAt, confirmationRequestId, confirmedByUserId, confirmedAt, actor, at);
    }

    private LoanContract copy(
            LoanContractStatus nextStatus, UUID ackRequest, UUID ackActor, LocalDateTime ackAt,
            UUID confirmRequest, UUID confirmActor, LocalDateTime confirmAt,
            UUID supersedeActor, LocalDateTime supersedeAt
    ) {
        return new LoanContract(id, loanApplicationId, approvedOfferId, contractReference, contractVersion,
                nextStatus, financialTerms, repaymentItems, disbursementBankAccount, preparationRequestId,
                expectedPreviousVersion, supersessionReason, preparedByUserId, preparedAt,
                ackRequest, ackActor, ackAt, confirmRequest, confirmActor, confirmAt,
                supersedesContractId, supersedeActor, supersedeAt);
    }

    @Override
    public String toString() {
        return "LoanContract[id=" + id + ", loanApplicationId=" + loanApplicationId
                + ", contractVersion=" + contractVersion + ", status=" + status
                + ", snapshot=redacted]";
    }

    private static void validateRepayment(ApprovedOfferFinancialTerms terms, List<LoanContractRepaymentItem> items) {
        if (items.size() != terms.approvedTermMonths()) throw invalid("Contract repayment count is inconsistent.");
        BigDecimal principal = sum(items.stream().map(LoanContractRepaymentItem::principalDue).toList());
        BigDecimal interest = sum(items.stream().map(LoanContractRepaymentItem::interestDue).toList());
        BigDecimal fee = sum(items.stream().map(LoanContractRepaymentItem::feeDue).toList());
        BigDecimal total = sum(items.stream().map(LoanContractRepaymentItem::totalDue).toList());
        if (principal.compareTo(terms.approvedPrincipal()) != 0
                || interest.compareTo(terms.totalInterest()) != 0
                || fee.compareTo(terms.feeAmount()) != 0
                || total.compareTo(terms.totalRepaymentAmount()) != 0) {
            throw invalid("Contract repayment items do not reconcile.");
        }
    }

    private static BigDecimal sum(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static void validateLifecycle(
            LoanContractStatus status, UUID ackRequest, UUID ackActor, LocalDateTime ackAt,
            UUID confirmRequest, UUID confirmActor, LocalDateTime confirmAt,
            UUID supersedeActor, LocalDateTime supersedeAt
    ) {
        boolean ackComplete = ackRequest != null && ackActor != null && ackAt != null;
        boolean confirmComplete = confirmRequest != null && confirmActor != null && confirmAt != null;
        boolean supersedeComplete = supersedeActor != null && supersedeAt != null;
        if ((ackRequest != null || ackActor != null || ackAt != null) && !ackComplete
                || (confirmRequest != null || confirmActor != null || confirmAt != null) && !confirmComplete
                || (supersedeActor != null || supersedeAt != null) && !supersedeComplete) {
            throw invalid("Contract lifecycle evidence is incomplete.");
        }
        boolean valid = switch (status) {
            case PREPARED -> !ackComplete && !confirmComplete && !supersedeComplete;
            case ACKNOWLEDGED -> ackComplete && !confirmComplete && !supersedeComplete;
            case READY_FOR_DISBURSEMENT -> ackComplete && confirmComplete && !supersedeComplete;
            case SUPERSEDED -> !confirmComplete && supersedeComplete;
        };
        if (!valid) throw invalid("Contract lifecycle state is inconsistent.");
    }

    private static BusinessStateConflictException invalid(String message) {
        return conflict("CONTRACT_STATE_INVALID", message);
    }

    private static BusinessStateConflictException conflict(String code, String message) {
        return new BusinessStateConflictException(code, message);
    }
}
