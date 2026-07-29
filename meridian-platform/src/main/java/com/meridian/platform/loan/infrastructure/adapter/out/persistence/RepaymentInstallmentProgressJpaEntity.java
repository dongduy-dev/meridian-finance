package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.domain.model.RepaymentInstallmentProgress;
import com.meridian.platform.loan.domain.model.RepaymentInstallmentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "repayment_installment_progress")
public class RepaymentInstallmentProgressJpaEntity {

    @Id
    @Column(name = "repayment_schedule_item_id", nullable = false, updatable = false)
    private UUID repaymentScheduleItemId;

    @Column(name = "repayment_schedule_id", nullable = false, updatable = false)
    private UUID repaymentScheduleId;

    @Column(name = "loan_account_id", nullable = false, updatable = false)
    private UUID loanAccountId;

    @Column(name = "installment_number", nullable = false, updatable = false)
    private int installmentNumber;

    @Column(name = "principal_paid", nullable = false, precision = 19, scale = 2)
    private BigDecimal principalPaid;

    @Column(name = "interest_paid", nullable = false, precision = 19, scale = 2)
    private BigDecimal interestPaid;

    @Column(name = "fee_paid", nullable = false, precision = 19, scale = 2)
    private BigDecimal feePaid;

    @Column(name = "total_paid", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalPaid;

    @Column(name = "principal_outstanding",
            nullable = false, precision = 19, scale = 2)
    private BigDecimal principalOutstanding;

    @Column(name = "interest_outstanding",
            nullable = false, precision = 19, scale = 2)
    private BigDecimal interestOutstanding;

    @Column(name = "fee_outstanding",
            nullable = false, precision = 19, scale = 2)
    private BigDecimal feeOutstanding;

    @Column(name = "total_outstanding",
            nullable = false, precision = 19, scale = 2)
    private BigDecimal totalOutstanding;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private RepaymentInstallmentStatus status;

    @Column(name = "last_payment_value_date")
    private LocalDate lastPaymentValueDate;

    @Column(name = "last_payment_recorded_at")
    private LocalDateTime lastPaymentRecordedAt;

    @Column(name = "servicing_evaluation_date", nullable = false)
    private LocalDate servicingEvaluationDate;

    @Column(name = "created_at",
            nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected RepaymentInstallmentProgressJpaEntity() {
    }

    public RepaymentInstallmentProgressJpaEntity(
            RepaymentInstallmentProgress progress
    ) {
        apply(progress);
    }

    public void apply(RepaymentInstallmentProgress progress) {
        this.repaymentScheduleItemId = progress.repaymentScheduleItemId();
        this.repaymentScheduleId = progress.repaymentScheduleId();
        this.loanAccountId = progress.loanAccountId();
        this.installmentNumber = progress.installmentNumber();
        this.principalPaid = progress.principalPaid();
        this.interestPaid = progress.interestPaid();
        this.feePaid = progress.feePaid();
        this.totalPaid = progress.totalPaid();
        this.principalOutstanding = progress.principalOutstanding();
        this.interestOutstanding = progress.interestOutstanding();
        this.feeOutstanding = progress.feeOutstanding();
        this.totalOutstanding = progress.totalOutstanding();
        this.status = progress.status();
        this.lastPaymentValueDate = progress.lastPaymentValueDate();
        this.lastPaymentRecordedAt = progress.lastPaymentRecordedAt();
        this.servicingEvaluationDate = progress.servicingEvaluationDate();
        this.updatedAt = progress.updatedAt();
    }

    public RepaymentInstallmentProgress toDomain() {
        return new RepaymentInstallmentProgress(
                repaymentScheduleItemId,
                repaymentScheduleId,
                loanAccountId,
                installmentNumber,
                principalPaid,
                interestPaid,
                feePaid,
                totalPaid,
                principalOutstanding,
                interestOutstanding,
                feeOutstanding,
                totalOutstanding,
                status,
                lastPaymentValueDate,
                lastPaymentRecordedAt,
                servicingEvaluationDate,
                updatedAt
        );
    }
}
