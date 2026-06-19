package com.meridian.platform.partner.infrastructure.adapter.out.persistence;

import com.meridian.platform.partner.domain.model.PartnerCompanyStatus;
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
@Table(name = "partner_companies")
public class PartnerCompanyJpaEntity {

    @Id
    private UUID id;

    @Column(name = "company_code", nullable = false)
    private String companyCode;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PartnerCompanyStatus status;

    @Column(name = "salary_advance_policy_limit", nullable = false)
    private BigDecimal salaryAdvancePolicyLimit;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected PartnerCompanyJpaEntity() {
    }

    public UUID getId() {
        return id;
    }

    public String getCompanyCode() {
        return companyCode;
    }

    public String getName() {
        return name;
    }

    public PartnerCompanyStatus getStatus() {
        return status;
    }

    public BigDecimal getSalaryAdvancePolicyLimit() {
        return salaryAdvancePolicyLimit;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}