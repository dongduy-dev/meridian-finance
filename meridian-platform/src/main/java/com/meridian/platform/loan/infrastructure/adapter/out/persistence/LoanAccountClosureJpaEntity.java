package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.domain.model.LoanAccountClosure;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "loan_account_closures")
public class LoanAccountClosureJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "loan_application_id", nullable = false, updatable = false)
    private UUID loanApplicationId;

    @Column(name = "loan_account_id", nullable = false, updatable = false)
    private UUID loanAccountId;

    @Column(name = "request_id", nullable = false, updatable = false)
    private UUID requestId;

    @Column(name = "closed_by_user_id", nullable = false, updatable = false)
    private UUID closedByUserId;

    @Column(name = "closed_at", nullable = false, updatable = false)
    private LocalDateTime closedAt;

    @Column(name = "created_at",
            nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected LoanAccountClosureJpaEntity() {
    }

    public LoanAccountClosureJpaEntity(LoanAccountClosure closure) {
        this.id = closure.id();
        this.loanApplicationId = closure.loanApplicationId();
        this.loanAccountId = closure.loanAccountId();
        this.requestId = closure.requestId();
        this.closedByUserId = closure.closedByUserId();
        this.closedAt = closure.closedAt();
    }

    public LoanAccountClosure toDomain() {
        return new LoanAccountClosure(
                id,
                loanApplicationId,
                loanAccountId,
                requestId,
                closedByUserId,
                closedAt
        );
    }
}
