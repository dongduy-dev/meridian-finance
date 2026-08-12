package com.meridian.platform.loan.domain.model;

import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public record LoanApplicationCancellation(
        UUID id,
        UUID loanApplicationId,
        UUID correctionRequestId,
        UUID reservationReleaseMovementId,
        UUID requestId,
        UUID cancelledByUserId,
        LocalDateTime cancelledAt
) {

    public LoanApplicationCancellation {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(loanApplicationId, "loanApplicationId must not be null");
        Objects.requireNonNull(correctionRequestId, "correctionRequestId must not be null");
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(cancelledByUserId, "cancelledByUserId must not be null");
        Objects.requireNonNull(cancelledAt, "cancelledAt must not be null");
    }

    public static LoanApplicationCancellation recordedWithoutExposureEffect(
            UUID id,
            LoanApplication cancelledApplication,
            LoanCorrectionRequest cancelledCorrection,
            UUID requestId,
            UUID cancelledByUserId,
            LocalDateTime cancelledAt
    ) {
        Objects.requireNonNull(cancelledApplication, "cancelledApplication must not be null");
        Objects.requireNonNull(cancelledCorrection, "cancelledCorrection must not be null");
        if (cancelledApplication.status() != LoanApplicationStatus.CANCELLED
                || cancelledApplication.productCode() != ProductCode.UNSECURED_CONSUMER_LOAN
                || cancelledCorrection.status() != LoanCorrectionRequestStatus.CANCELLED
                || !cancelledCorrection.loanApplicationId().equals(cancelledApplication.id())
                || !cancelledAt.equals(cancelledCorrection.cancelledAt())) {
            throw new BusinessRuleViolationException(
                    "LOAN_APPLICATION_CANCELLATION_EVIDENCE_INVALID",
                    "UCL cancellation evidence requires a terminal correction and no exposure effect."
            );
        }
        return new LoanApplicationCancellation(
                Objects.requireNonNull(id, "id must not be null"),
                cancelledApplication.id(),
                cancelledCorrection.id(),
                null,
                Objects.requireNonNull(requestId, "requestId must not be null"),
                Objects.requireNonNull(cancelledByUserId, "cancelledByUserId must not be null"),
                cancelledAt
        );
    }

    public static LoanApplicationCancellation recorded(
            UUID id,
            LoanApplication cancelledApplication,
            LoanCorrectionRequest cancelledCorrection,
            SalaryAdvanceLimitMovement releaseMovement,
            UUID requestId,
            UUID cancelledByUserId,
            LocalDateTime cancelledAt
    ) {
        Objects.requireNonNull(cancelledApplication, "cancelledApplication must not be null");
        Objects.requireNonNull(cancelledCorrection, "cancelledCorrection must not be null");
        Objects.requireNonNull(releaseMovement, "releaseMovement must not be null");
        if (cancelledApplication.status() != LoanApplicationStatus.CANCELLED
                || cancelledApplication.productCode() != ProductCode.SALARY_ADVANCE
                || cancelledCorrection.status() != LoanCorrectionRequestStatus.CANCELLED
                || !cancelledCorrection.loanApplicationId().equals(cancelledApplication.id())
                || !cancelledAt.equals(cancelledCorrection.cancelledAt())
                || releaseMovement.movementType()
                != SalaryAdvanceLimitMovementType.RESERVATION_RELEASED
                || !cancelledApplication.id().equals(releaseMovement.loanApplicationId())
                || releaseMovement.amount().compareTo(cancelledApplication.requestedAmount()) != 0
                || releaseMovement.loanAccountId() != null
                || releaseMovement.repaymentTransactionId() != null) {
            throw new BusinessRuleViolationException(
                    "LOAN_APPLICATION_CANCELLATION_EVIDENCE_INVALID",
                    "Cancellation evidence requires a terminal correction and exact reservation release."
            );
        }
        return new LoanApplicationCancellation(
                Objects.requireNonNull(id, "id must not be null"),
                cancelledApplication.id(),
                cancelledCorrection.id(),
                releaseMovement.id(),
                Objects.requireNonNull(requestId, "requestId must not be null"),
                Objects.requireNonNull(cancelledByUserId, "cancelledByUserId must not be null"),
                cancelledAt
        );
    }

    @Override
    public String toString() {
        return "LoanApplicationCancellation[loanApplicationId=" + loanApplicationId
                + ", cancellationEvidence=redacted]";
    }
}
