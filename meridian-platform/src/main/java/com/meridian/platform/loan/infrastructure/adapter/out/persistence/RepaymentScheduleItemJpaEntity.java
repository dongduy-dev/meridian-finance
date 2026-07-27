package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.domain.model.RepaymentScheduleItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "repayment_schedule_items")
class RepaymentScheduleItemJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "repayment_schedule_id", nullable = false, updatable = false)
    private UUID repaymentScheduleId;

    @Column(name = "source_loan_contract_repayment_item_id", nullable = false, updatable = false)
    private UUID sourceLoanContractRepaymentItemId;

    @Column(name = "installment_number", nullable = false, updatable = false)
    private int installmentNumber;

    @Column(name = "due_date", nullable = false, updatable = false)
    private LocalDate dueDate;

    @Column(name = "principal_due", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal principalDue;

    @Column(name = "interest_due", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal interestDue;

    @Column(name = "fee_due", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal feeDue;

    @Column(name = "total_due", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal totalDue;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected RepaymentScheduleItemJpaEntity() {
    }

    RepaymentScheduleItemJpaEntity(UUID repaymentScheduleId, RepaymentScheduleItem item) {
        this.id = item.id();
        this.repaymentScheduleId = repaymentScheduleId;
        this.sourceLoanContractRepaymentItemId = item.sourceLoanContractRepaymentItemId();
        this.installmentNumber = item.installmentNumber();
        this.dueDate = item.dueDate();
        this.principalDue = item.principalDue();
        this.interestDue = item.interestDue();
        this.feeDue = item.feeDue();
        this.totalDue = item.totalDue();
    }

    RepaymentScheduleItem toDomain() {
        return new RepaymentScheduleItem(
                id,
                sourceLoanContractRepaymentItemId,
                installmentNumber,
                dueDate,
                principalDue,
                interestDue,
                feeDue,
                totalDue
        );
    }
}
