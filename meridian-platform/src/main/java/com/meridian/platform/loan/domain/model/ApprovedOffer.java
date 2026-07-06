package com.meridian.platform.loan.domain.model;

import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ApprovedOffer(
        UUID id,
        UUID loanApplicationId,
        UUID sourceLoanProductPolicyId,
        ApprovedOfferStatus status,
        ApprovedOfferFinancialTerms financialTerms,
        List<ProvisionalRepaymentItem> repaymentItems,
        LocalDateTime generatedAt,
        LocalDateTime expiresAt,
        LocalDateTime acceptedAt,
        LocalDateTime declinedAt,
        LocalDateTime expiredAt
) {

    public ApprovedOffer {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(loanApplicationId, "loanApplicationId must not be null");
        Objects.requireNonNull(sourceLoanProductPolicyId, "sourceLoanProductPolicyId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(financialTerms, "financialTerms must not be null");
        repaymentItems = List.copyOf(Objects.requireNonNull(repaymentItems, "repaymentItems must not be null"))
                .stream()
                .sorted(Comparator.comparingInt(ProvisionalRepaymentItem::installmentNumber))
                .toList();
        Objects.requireNonNull(generatedAt, "generatedAt must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");

        if (!expiresAt.isAfter(generatedAt)) {
            throw invalidState("Approved offer expiry must be after generation.");
        }
        validateStatusTimestamps(status, acceptedAt, declinedAt, expiredAt);
        validateRepaymentItems(financialTerms, repaymentItems);
    }

    public static ApprovedOffer pending(
            UUID id,
            UUID loanApplicationId,
            UUID sourceLoanProductPolicyId,
            ApprovedOfferFinancialTerms financialTerms,
            List<ProvisionalRepaymentItem> repaymentItems,
            LocalDateTime generatedAt,
            LocalDateTime expiresAt
    ) {
        return new ApprovedOffer(
                id,
                loanApplicationId,
                sourceLoanProductPolicyId,
                ApprovedOfferStatus.PENDING,
                financialTerms,
                repaymentItems,
                generatedAt,
                expiresAt,
                null,
                null,
                null
        );
    }

    public boolean isExpiredAt(LocalDateTime now) {
        Objects.requireNonNull(now, "now must not be null");
        return !now.isBefore(expiresAt);
    }

    public ApprovedOfferStatus effectiveStatusAt(LocalDateTime now) {
        if (status == ApprovedOfferStatus.PENDING && isExpiredAt(now)) {
            return ApprovedOfferStatus.EXPIRED;
        }
        return status;
    }

    public ApprovedOffer accept(LocalDateTime acceptedAt) {
        Objects.requireNonNull(acceptedAt, "acceptedAt must not be null");
        if (status == ApprovedOfferStatus.ACCEPTED) {
            return this;
        }
        if (status != ApprovedOfferStatus.PENDING) {
            throw offerActionConflict();
        }
        return withStatus(ApprovedOfferStatus.ACCEPTED, acceptedAt, null, null);
    }

    public ApprovedOffer decline(LocalDateTime declinedAt) {
        Objects.requireNonNull(declinedAt, "declinedAt must not be null");
        if (status == ApprovedOfferStatus.DECLINED) {
            return this;
        }
        if (status != ApprovedOfferStatus.PENDING) {
            throw offerActionConflict();
        }
        return withStatus(ApprovedOfferStatus.DECLINED, null, declinedAt, null);
    }

    public ApprovedOffer expire(LocalDateTime expiredAt) {
        Objects.requireNonNull(expiredAt, "expiredAt must not be null");
        if (status == ApprovedOfferStatus.EXPIRED) {
            return this;
        }
        if (status != ApprovedOfferStatus.PENDING) {
            throw offerActionConflict();
        }
        return withStatus(ApprovedOfferStatus.EXPIRED, null, null, expiredAt);
    }

    private ApprovedOffer withStatus(
            ApprovedOfferStatus nextStatus,
            LocalDateTime acceptedAt,
            LocalDateTime declinedAt,
            LocalDateTime expiredAt
    ) {
        return new ApprovedOffer(
                id,
                loanApplicationId,
                sourceLoanProductPolicyId,
                nextStatus,
                financialTerms,
                repaymentItems,
                generatedAt,
                expiresAt,
                acceptedAt,
                declinedAt,
                expiredAt
        );
    }

    private static void validateStatusTimestamps(
            ApprovedOfferStatus status,
            LocalDateTime acceptedAt,
            LocalDateTime declinedAt,
            LocalDateTime expiredAt
    ) {
        boolean valid = switch (status) {
            case PENDING -> acceptedAt == null && declinedAt == null && expiredAt == null;
            case ACCEPTED -> acceptedAt != null && declinedAt == null && expiredAt == null;
            case DECLINED -> acceptedAt == null && declinedAt != null && expiredAt == null;
            case EXPIRED -> acceptedAt == null && declinedAt == null && expiredAt != null;
        };

        if (!valid) {
            throw invalidState("Approved offer status timestamps are inconsistent.");
        }
    }

    private static void validateRepaymentItems(
            ApprovedOfferFinancialTerms financialTerms,
            List<ProvisionalRepaymentItem> repaymentItems
    ) {
        if (repaymentItems.size() != financialTerms.approvedTermMonths()) {
            throw invalidState("Approved offer must contain one provisional item per approved term month.");
        }

        BigDecimal principalSum = sum(repaymentItems.stream()
                .map(ProvisionalRepaymentItem::principalDue)
                .toList());
        BigDecimal interestSum = sum(repaymentItems.stream()
                .map(ProvisionalRepaymentItem::interestDue)
                .toList());
        BigDecimal feeSum = sum(repaymentItems.stream()
                .map(ProvisionalRepaymentItem::feeDue)
                .toList());
        BigDecimal totalSum = sum(repaymentItems.stream()
                .map(ProvisionalRepaymentItem::totalDue)
                .toList());

        if (principalSum.compareTo(financialTerms.approvedPrincipal()) != 0
                || interestSum.compareTo(financialTerms.totalInterest()) != 0
                || feeSum.compareTo(financialTerms.feeAmount()) != 0
                || totalSum.compareTo(financialTerms.totalRepaymentAmount()) != 0) {
            throw invalidState("Approved offer repayment items must reconcile to the financial snapshot.");
        }
    }

    private static BigDecimal sum(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    private static BusinessStateConflictException invalidState(String message) {
        return new BusinessStateConflictException("SYSTEM_STATE_CONFLICT", message);
    }

    private static BusinessStateConflictException offerActionConflict() {
        return new BusinessStateConflictException(
                "OFFER_ACTION_CONFLICT",
                "Approved offer action conflicts with the current offer state."
        );
    }
}
