package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.domain.model.ProvisionalRepaymentItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "approved_offer_repayment_items")
public class ApprovedOfferRepaymentItemJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "approved_offer_id", nullable = false)
    private UUID approvedOfferId;

    @Column(name = "installment_number", nullable = false)
    private int installmentNumber;

    @Column(name = "principal_due", nullable = false)
    private BigDecimal principalDue;

    @Column(name = "interest_due", nullable = false)
    private BigDecimal interestDue;

    @Column(name = "fee_due", nullable = false)
    private BigDecimal feeDue;

    @Column(name = "total_due", nullable = false)
    private BigDecimal totalDue;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected ApprovedOfferRepaymentItemJpaEntity() {
    }

    public ApprovedOfferRepaymentItemJpaEntity(UUID approvedOfferId, ProvisionalRepaymentItem item) {
        this.id = item.id();
        this.approvedOfferId = approvedOfferId;
        this.installmentNumber = item.installmentNumber();
        this.principalDue = item.principalDue();
        this.interestDue = item.interestDue();
        this.feeDue = item.feeDue();
        this.totalDue = item.totalDue();
        this.createdAt = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getApprovedOfferId() {
        return approvedOfferId;
    }

    public int getInstallmentNumber() {
        return installmentNumber;
    }

    public BigDecimal getPrincipalDue() {
        return principalDue;
    }

    public BigDecimal getInterestDue() {
        return interestDue;
    }

    public BigDecimal getFeeDue() {
        return feeDue;
    }

    public BigDecimal getTotalDue() {
        return totalDue;
    }
}
