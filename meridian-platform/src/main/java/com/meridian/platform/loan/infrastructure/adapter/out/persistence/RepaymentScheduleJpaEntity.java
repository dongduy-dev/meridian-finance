package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.domain.model.RepaymentSchedule;
import com.meridian.platform.loan.domain.model.RepaymentScheduleItem;
import com.meridian.platform.loan.domain.model.RepaymentScheduleType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "repayment_schedules")
public class RepaymentScheduleJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "loan_application_id", nullable = false, updatable = false)
    private UUID loanApplicationId;

    @Column(name = "loan_contract_id", nullable = false, updatable = false)
    private UUID loanContractId;

    @Column(name = "loan_account_id", nullable = false, updatable = false)
    private UUID loanAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "schedule_type", nullable = false, length = 20, updatable = false)
    private RepaymentScheduleType scheduleType;

    @Column(name = "version", nullable = false, updatable = false)
    private int version;

    @Column(name = "approved_term_months", nullable = false, updatable = false)
    private int approvedTermMonths;

    @Column(name = "approved_principal", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal approvedPrincipal;

    @Column(name = "total_interest", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal totalInterest;

    @Column(name = "fee_amount", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal feeAmount;

    @Column(name = "total_repayment_amount", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal totalRepaymentAmount;

    @Column(name = "first_due_date", nullable = false, updatable = false)
    private LocalDate firstDueDate;

    @Column(name = "last_due_date", nullable = false, updatable = false)
    private LocalDate lastDueDate;

    @Column(name = "generated_at", nullable = false, updatable = false)
    private LocalDateTime generatedAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected RepaymentScheduleJpaEntity() {
    }

    public RepaymentScheduleJpaEntity(RepaymentSchedule repaymentSchedule) {
        this.id = repaymentSchedule.id();
        this.loanApplicationId = repaymentSchedule.loanApplicationId();
        this.loanContractId = repaymentSchedule.loanContractId();
        this.loanAccountId = repaymentSchedule.loanAccountId();
        this.scheduleType = repaymentSchedule.scheduleType();
        this.version = repaymentSchedule.version();
        this.approvedTermMonths = repaymentSchedule.approvedTermMonths();
        this.approvedPrincipal = repaymentSchedule.approvedPrincipal();
        this.totalInterest = repaymentSchedule.totalInterest();
        this.feeAmount = repaymentSchedule.feeAmount();
        this.totalRepaymentAmount = repaymentSchedule.totalRepaymentAmount();
        this.firstDueDate = repaymentSchedule.firstDueDate();
        this.lastDueDate = repaymentSchedule.lastDueDate();
        this.generatedAt = repaymentSchedule.generatedAt();
    }

    public RepaymentSchedule toDomain(List<RepaymentScheduleItem> items) {
        return new RepaymentSchedule(
                id,
                loanApplicationId,
                loanContractId,
                loanAccountId,
                scheduleType,
                version,
                approvedTermMonths,
                approvedPrincipal,
                totalInterest,
                feeAmount,
                totalRepaymentAmount,
                firstDueDate,
                lastDueDate,
                generatedAt,
                items
        );
    }

    UUID id() {
        return id;
    }
}
