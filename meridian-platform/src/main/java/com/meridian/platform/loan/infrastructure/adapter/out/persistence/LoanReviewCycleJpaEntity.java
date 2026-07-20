package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.domain.model.LoanApplicationReviewCycle;
import com.meridian.platform.loan.domain.model.LoanReviewCycleStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "loan_application_review_cycles")
public class LoanReviewCycleJpaEntity {
    @Id
    private UUID id;

    @Column(name = "loan_application_id", nullable = false)
    private UUID loanApplicationId;

    @Column(name = "cycle_number", nullable = false)
    private int cycleNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private LoanReviewCycleStatus status;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected LoanReviewCycleJpaEntity() {
    }

    public LoanReviewCycleJpaEntity(LoanApplicationReviewCycle cycle) {
        this.id = cycle.id();
        this.createdAt = cycle.startedAt();
        updateFrom(cycle);
    }

    public void updateFrom(LoanApplicationReviewCycle cycle) {
        this.loanApplicationId = cycle.loanApplicationId();
        this.cycleNumber = cycle.cycleNumber();
        this.status = cycle.status();
        this.startedAt = cycle.startedAt();
        this.endedAt = cycle.endedAt();
        this.updatedAt = cycle.endedAt() == null ? cycle.startedAt() : cycle.endedAt();
    }

    public LoanApplicationReviewCycle toDomain() {
        return new LoanApplicationReviewCycle(id, loanApplicationId, cycleNumber, status, startedAt, endedAt);
    }
}
