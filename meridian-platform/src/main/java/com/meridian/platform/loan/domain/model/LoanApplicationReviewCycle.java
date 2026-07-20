package com.meridian.platform.loan.domain.model;

import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public record LoanApplicationReviewCycle(
        UUID id,
        UUID loanApplicationId,
        int cycleNumber,
        LoanReviewCycleStatus status,
        LocalDateTime startedAt,
        LocalDateTime endedAt
) {
    public LoanApplicationReviewCycle {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(loanApplicationId, "loanApplicationId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(startedAt, "startedAt must not be null");
        if (cycleNumber <= 0) {
            throw new IllegalArgumentException("cycleNumber must be positive");
        }
        if ((status == LoanReviewCycleStatus.ACTIVE) != (endedAt == null)) {
            throw new IllegalArgumentException("Review cycle end time must match its state.");
        }
    }

    public static LoanApplicationReviewCycle active(
            UUID id, UUID loanApplicationId, int cycleNumber, LocalDateTime startedAt
    ) {
        return new LoanApplicationReviewCycle(id, loanApplicationId, cycleNumber,
                LoanReviewCycleStatus.ACTIVE, startedAt, null);
    }

    public LoanApplicationReviewCycle complete(LocalDateTime at) {
        return end(LoanReviewCycleStatus.COMPLETED, at);
    }

    public LoanApplicationReviewCycle supersede(LocalDateTime at) {
        return end(LoanReviewCycleStatus.SUPERSEDED, at);
    }

    public LoanApplicationReviewCycle requireCorrection(LocalDateTime at) {
        return end(LoanReviewCycleStatus.CORRECTION_REQUIRED, at);
    }

    public LoanApplicationReviewCycle corrected(LocalDateTime at) {
        if (status != LoanReviewCycleStatus.CORRECTION_REQUIRED) {
            throw conflict("Only a correction-required review cycle can be corrected.");
        }
        return new LoanApplicationReviewCycle(id, loanApplicationId, cycleNumber,
                LoanReviewCycleStatus.CORRECTED, startedAt, Objects.requireNonNull(at));
    }

    private LoanApplicationReviewCycle end(LoanReviewCycleStatus target, LocalDateTime at) {
        if (status != LoanReviewCycleStatus.ACTIVE) {
            throw conflict("Only an active review cycle can change state.");
        }
        return new LoanApplicationReviewCycle(id, loanApplicationId, cycleNumber,
                target, startedAt, Objects.requireNonNull(at));
    }

    private BusinessStateConflictException conflict(String message) {
        return new BusinessStateConflictException("REVIEW_CYCLE_CONFLICT", message);
    }
}
