package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.domain.model.SalaryAdvanceLimitMovement;
import com.meridian.platform.loan.domain.model.SalaryAdvanceLimitMovementType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "salary_advance_limit_movements")
public class SalaryAdvanceLimitMovementJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "salary_advance_limit_id", nullable = false)
    private UUID salaryAdvanceLimitId;

    @Column(name = "loan_application_id")
    private UUID loanApplicationId;

    @Column(name = "loan_account_id")
    private UUID loanAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false)
    private SalaryAdvanceLimitMovementType movementType;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected SalaryAdvanceLimitMovementJpaEntity() {
    }

    public SalaryAdvanceLimitMovementJpaEntity(SalaryAdvanceLimitMovement movement) {
        this.id = movement.id();
        this.salaryAdvanceLimitId = movement.salaryAdvanceLimitId();
        this.loanApplicationId = movement.loanApplicationId();
        this.loanAccountId = movement.loanAccountId();
        this.movementType = movement.movementType();
        this.amount = movement.amount();
        this.occurredAt = movement.occurredAt();
        this.createdAt = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getSalaryAdvanceLimitId() {
        return salaryAdvanceLimitId;
    }

    public UUID getLoanApplicationId() {
        return loanApplicationId;
    }

    public UUID getLoanAccountId() {
        return loanAccountId;
    }

    public SalaryAdvanceLimitMovementType getMovementType() {
        return movementType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }
}
