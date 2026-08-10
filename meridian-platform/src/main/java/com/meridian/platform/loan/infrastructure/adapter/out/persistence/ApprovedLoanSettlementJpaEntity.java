package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.domain.model.ApprovedLoanSettlement;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "approved_loan_settlements")
public class ApprovedLoanSettlementJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "loan_application_id", nullable = false, updatable = false)
    private UUID loanApplicationId;

    @Column(name = "loan_account_id", nullable = false, updatable = false)
    private UUID loanAccountId;

    @Column(name = "repayment_transaction_id", nullable = false, updatable = false)
    private UUID repaymentTransactionId;

    @Column(name = "request_id", nullable = false, updatable = false)
    private UUID requestId;

    @Column(name = "settlement_amount",
            nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal settlementAmount;

    @Column(name = "approved_by_user_id", nullable = false, updatable = false)
    private UUID approvedByUserId;

    @Column(name = "approved_at", nullable = false, updatable = false)
    private LocalDateTime approvedAt;

    @Column(name = "created_at",
            nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected ApprovedLoanSettlementJpaEntity() {
    }

    public ApprovedLoanSettlementJpaEntity(ApprovedLoanSettlement settlement) {
        this.id = settlement.id();
        this.loanApplicationId = settlement.loanApplicationId();
        this.loanAccountId = settlement.loanAccountId();
        this.repaymentTransactionId = settlement.repaymentTransactionId();
        this.requestId = settlement.requestId();
        this.settlementAmount = settlement.settlementAmount();
        this.approvedByUserId = settlement.approvedByUserId();
        this.approvedAt = settlement.approvedAt();
    }

    public ApprovedLoanSettlement toDomain() {
        return new ApprovedLoanSettlement(
                id,
                loanApplicationId,
                loanAccountId,
                repaymentTransactionId,
                requestId,
                settlementAmount,
                approvedByUserId,
                approvedAt
        );
    }
}
