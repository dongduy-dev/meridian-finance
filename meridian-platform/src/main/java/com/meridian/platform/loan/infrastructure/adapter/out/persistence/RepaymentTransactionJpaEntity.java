package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.domain.model.RepaymentAllocation;
import com.meridian.platform.loan.domain.model.RepaymentTransaction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "repayment_transactions")
public class RepaymentTransactionJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "loan_application_id", nullable = false, updatable = false)
    private UUID loanApplicationId;

    @Column(name = "loan_account_id", nullable = false, updatable = false)
    private UUID loanAccountId;

    @Column(name = "repayment_schedule_id", nullable = false, updatable = false)
    private UUID repaymentScheduleId;

    @Column(name = "request_id", nullable = false, updatable = false)
    private UUID requestId;

    @Column(name = "external_payment_reference",
            nullable = false, length = 64, updatable = false)
    private String externalPaymentReference;

    @Column(name = "received_amount",
            nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal receivedAmount;

    @Column(name = "payment_value_date", nullable = false, updatable = false)
    private LocalDate paymentValueDate;

    @Column(name = "recorded_by_user_id", nullable = false, updatable = false)
    private UUID recordedByUserId;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private LocalDateTime recordedAt;

    @Column(name = "created_at",
            nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected RepaymentTransactionJpaEntity() {
    }

    public RepaymentTransactionJpaEntity(RepaymentTransaction transaction) {
        this.id = transaction.id();
        this.loanApplicationId = transaction.loanApplicationId();
        this.loanAccountId = transaction.loanAccountId();
        this.repaymentScheduleId = transaction.repaymentScheduleId();
        this.requestId = transaction.requestId();
        this.externalPaymentReference = transaction.externalPaymentReference();
        this.receivedAmount = transaction.receivedAmount();
        this.paymentValueDate = transaction.paymentValueDate();
        this.recordedByUserId = transaction.recordedByUserId();
        this.recordedAt = transaction.recordedAt();
    }

    public RepaymentTransaction toDomain(List<RepaymentAllocation> allocations) {
        return new RepaymentTransaction(
                id,
                loanApplicationId,
                loanAccountId,
                repaymentScheduleId,
                requestId,
                externalPaymentReference,
                receivedAmount,
                paymentValueDate,
                recordedByUserId,
                recordedAt,
                allocations
        );
    }

    UUID id() {
        return id;
    }
}
