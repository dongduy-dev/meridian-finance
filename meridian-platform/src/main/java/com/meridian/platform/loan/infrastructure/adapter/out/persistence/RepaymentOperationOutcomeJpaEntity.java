package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "repayment_operation_outcomes")
class RepaymentOperationOutcomeJpaEntity {
    @Id
    private UUID repaymentTransactionId;
    @Column(nullable = false, updatable = false)
    private UUID loanApplicationId;
    @Column(nullable = false, updatable = false)
    private UUID loanAccountId;
    @Column(nullable = false, updatable = false)
    private UUID repaymentScheduleId;
    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal receivedAmount;
    @Column(nullable = false, updatable = false)
    private LocalDate paymentValueDate;
    @Column(nullable = false, updatable = false)
    private LocalDateTime recordedAt;
    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal principalReleased;
    @Column(nullable = false, length = 30, updatable = false)
    private String accountStatus;
    @Column(nullable = false, updatable = false)
    private boolean accountStatusChanged;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb", updatable = false)
    private String outcomeJson;

    protected RepaymentOperationOutcomeJpaEntity() {
    }

    RepaymentOperationOutcomeJpaEntity(
            UUID repaymentTransactionId,
            UUID loanApplicationId,
            UUID loanAccountId,
            UUID repaymentScheduleId,
            BigDecimal receivedAmount,
            LocalDate paymentValueDate,
            LocalDateTime recordedAt,
            BigDecimal principalReleased,
            String accountStatus,
            boolean accountStatusChanged,
            String outcomeJson
    ) {
        this.repaymentTransactionId = repaymentTransactionId;
        this.loanApplicationId = loanApplicationId;
        this.loanAccountId = loanAccountId;
        this.repaymentScheduleId = repaymentScheduleId;
        this.receivedAmount = receivedAmount;
        this.paymentValueDate = paymentValueDate;
        this.recordedAt = recordedAt;
        this.principalReleased = principalReleased;
        this.accountStatus = accountStatus;
        this.accountStatusChanged = accountStatusChanged;
        this.outcomeJson = outcomeJson;
    }

    UUID repaymentTransactionId() {
        return repaymentTransactionId;
    }

    String outcomeJson() {
        return outcomeJson;
    }
}
