package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.domain.model.RepaymentAllocation;
import com.meridian.platform.loan.domain.model.RepaymentAllocationComponent;
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
@Table(name = "repayment_allocations")
public class RepaymentAllocationJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "repayment_transaction_id", nullable = false, updatable = false)
    private UUID repaymentTransactionId;

    @Column(name = "allocation_sequence", nullable = false, updatable = false)
    private int allocationSequence;

    @Column(name = "repayment_schedule_item_id", nullable = false, updatable = false)
    private UUID repaymentScheduleItemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "component", nullable = false, length = 20, updatable = false)
    private RepaymentAllocationComponent component;

    @Column(name = "amount",
            nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "created_at",
            nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected RepaymentAllocationJpaEntity() {
    }

    public RepaymentAllocationJpaEntity(RepaymentAllocation allocation) {
        this.id = allocation.id();
        this.repaymentTransactionId = allocation.repaymentTransactionId();
        this.allocationSequence = allocation.allocationSequence();
        this.repaymentScheduleItemId = allocation.repaymentScheduleItemId();
        this.component = allocation.component();
        this.amount = allocation.amount();
    }

    public RepaymentAllocation toDomain() {
        return new RepaymentAllocation(
                id,
                repaymentTransactionId,
                allocationSequence,
                repaymentScheduleItemId,
                component,
                amount
        );
    }
}
