package com.meridian.platform.loan.domain.model;

import com.meridian.platform.approval.domain.model.CorrectionReasonCode;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record LoanCorrectionRequest(
        UUID id,
        UUID loanApplicationId,
        UUID sourceReviewCycleId,
        String sourceAction,
        CorrectionReasonCode reasonCode,
        UUID createdByUserId,
        LoanCorrectionRequestStatus status,
        UUID resubmissionRequestId,
        LocalDateTime createdAt,
        LocalDateTime readyAt,
        LocalDateTime resubmittedAt,
        LocalDateTime cancelledAt
) {
    public LoanCorrectionRequest(
            UUID id,
            UUID loanApplicationId,
            UUID sourceReviewCycleId,
            String sourceAction,
            CorrectionReasonCode reasonCode,
            UUID createdByUserId,
            LoanCorrectionRequestStatus status,
            UUID resubmissionRequestId,
            LocalDateTime createdAt,
            LocalDateTime readyAt,
            LocalDateTime resubmittedAt
    ) {
        this(
                id,
                loanApplicationId,
                sourceReviewCycleId,
                sourceAction,
                reasonCode,
                createdByUserId,
                status,
                resubmissionRequestId,
                createdAt,
                readyAt,
                resubmittedAt,
                null
        );
    }

    public LoanCorrectionRequest markReady(List<LoanCorrectionTask> tasks, LocalDateTime at) {
        if (tasks.isEmpty() || tasks.stream().anyMatch(task -> task.status() != LoanCorrectionTaskStatus.COMPLETED)) {
            throw new BusinessStateConflictException(
                    "CORRECTION_TASKS_INCOMPLETE",
                    "Every correction task must be complete before resubmission."
            );
        }
        if (status == LoanCorrectionRequestStatus.READY_FOR_RESUBMISSION) {
            return this;
        }
        if (status != LoanCorrectionRequestStatus.OPEN) {
            throw new BusinessStateConflictException("CORRECTION_REQUEST_CONFLICT", "Correction request is not open.");
        }
        return new LoanCorrectionRequest(id, loanApplicationId, sourceReviewCycleId, sourceAction, reasonCode,
                createdByUserId, LoanCorrectionRequestStatus.READY_FOR_RESUBMISSION, null,
                createdAt, Objects.requireNonNull(at), null, null);
    }

    public LoanCorrectionRequest reopen() {
        if (status == LoanCorrectionRequestStatus.OPEN) {
            return this;
        }
        if (status != LoanCorrectionRequestStatus.READY_FOR_RESUBMISSION) {
            throw new BusinessStateConflictException(
                    "CORRECTION_REQUEST_CONFLICT", "Only a ready correction request can be reopened.");
        }
        return new LoanCorrectionRequest(id, loanApplicationId, sourceReviewCycleId, sourceAction, reasonCode,
                createdByUserId, LoanCorrectionRequestStatus.OPEN, null, createdAt, null, null, null);
    }

    public LoanCorrectionRequest resubmit(UUID requestId, LocalDateTime at) {
        Objects.requireNonNull(requestId);
        Objects.requireNonNull(at);
        if (status == LoanCorrectionRequestStatus.RESUBMITTED) {
            if (requestId.equals(resubmissionRequestId)) {
                return this;
            }
            throw new BusinessStateConflictException(
                    "CORRECTION_ALREADY_RESUBMITTED",
                    "Correction request was already resubmitted."
            );
        }
        if (status != LoanCorrectionRequestStatus.READY_FOR_RESUBMISSION) {
            throw new BusinessStateConflictException(
                    "CORRECTION_TASKS_INCOMPLETE",
                    "Correction request is not ready for resubmission."
            );
        }
        return new LoanCorrectionRequest(id, loanApplicationId, sourceReviewCycleId, sourceAction, reasonCode,
                createdByUserId, LoanCorrectionRequestStatus.RESUBMITTED, requestId,
                createdAt, readyAt, at, null);
    }

    public LoanCorrectionRequest cancel(LocalDateTime at) {
        Objects.requireNonNull(at, "at must not be null");
        if (status != LoanCorrectionRequestStatus.OPEN
                && status != LoanCorrectionRequestStatus.READY_FOR_RESUBMISSION) {
            throw new BusinessStateConflictException(
                    "CORRECTION_REQUEST_CONFLICT",
                    "Only an active correction request can be cancelled."
            );
        }
        return new LoanCorrectionRequest(
                id,
                loanApplicationId,
                sourceReviewCycleId,
                sourceAction,
                reasonCode,
                createdByUserId,
                LoanCorrectionRequestStatus.CANCELLED,
                null,
                createdAt,
                readyAt,
                null,
                at
        );
    }

    public boolean isActive() {
        return status == LoanCorrectionRequestStatus.OPEN
                || status == LoanCorrectionRequestStatus.READY_FOR_RESUBMISSION;
    }
}
