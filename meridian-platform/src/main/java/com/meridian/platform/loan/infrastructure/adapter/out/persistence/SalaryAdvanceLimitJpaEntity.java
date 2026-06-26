package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.domain.model.SalaryAdvanceLimit;
import com.meridian.platform.loan.domain.model.SalaryAdvanceLimitStatus;
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
@Table(name = "salary_advance_limits")
public class SalaryAdvanceLimitJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "customer_partner_employee_link_id", nullable = false)
    private UUID customerPartnerEmployeeLinkId;

    @Column(name = "total_limit", nullable = false)
    private BigDecimal totalLimit;

    @Column(name = "used_amount", nullable = false)
    private BigDecimal usedAmount;

    @Column(name = "reserved_amount", nullable = false)
    private BigDecimal reservedAmount;

    @Column(name = "available_amount", nullable = false)
    private BigDecimal availableAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SalaryAdvanceLimitStatus status;

    @Column(name = "last_refreshed_at", nullable = false)
    private LocalDateTime lastRefreshedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected SalaryAdvanceLimitJpaEntity() {
    }

    public SalaryAdvanceLimitJpaEntity(SalaryAdvanceLimit salaryAdvanceLimit) {
        LocalDateTime now = LocalDateTime.now();
        this.id = salaryAdvanceLimit.id();
        this.createdAt = now;
        apply(salaryAdvanceLimit, now);
    }

    public void updateFrom(SalaryAdvanceLimit salaryAdvanceLimit) {
        apply(salaryAdvanceLimit, LocalDateTime.now());
    }

    private void apply(SalaryAdvanceLimit salaryAdvanceLimit, LocalDateTime updatedAt) {
        this.customerId = salaryAdvanceLimit.customerId();
        this.customerPartnerEmployeeLinkId = salaryAdvanceLimit.customerPartnerEmployeeLinkId();
        this.totalLimit = salaryAdvanceLimit.totalLimit();
        this.usedAmount = salaryAdvanceLimit.usedAmount();
        this.reservedAmount = salaryAdvanceLimit.reservedAmount();
        this.availableAmount = salaryAdvanceLimit.availableAmount();
        this.status = salaryAdvanceLimit.status();
        this.lastRefreshedAt = salaryAdvanceLimit.lastRefreshedAt();
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public UUID getCustomerPartnerEmployeeLinkId() {
        return customerPartnerEmployeeLinkId;
    }

    public BigDecimal getTotalLimit() {
        return totalLimit;
    }

    public BigDecimal getUsedAmount() {
        return usedAmount;
    }

    public BigDecimal getReservedAmount() {
        return reservedAmount;
    }

    public BigDecimal getAvailableAmount() {
        return availableAmount;
    }

    public SalaryAdvanceLimitStatus getStatus() {
        return status;
    }

    public LocalDateTime getLastRefreshedAt() {
        return lastRefreshedAt;
    }
}
