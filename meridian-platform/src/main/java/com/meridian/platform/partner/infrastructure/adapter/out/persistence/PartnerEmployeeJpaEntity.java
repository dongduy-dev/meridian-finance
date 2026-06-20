package com.meridian.platform.partner.infrastructure.adapter.out.persistence;

import com.meridian.platform.partner.domain.model.PartnerEmployeeStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "partner_employees")
public class PartnerEmployeeJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "partner_company_id", nullable = false)
    private UUID partnerCompanyId;

    @Column(name = "import_batch_id", nullable = false)
    private UUID importBatchId;

    @Column(name = "employee_code", nullable = false)
    private String employeeCode;

    @Column(name = "identity_reference", nullable = false)
    private String identityReference;

    @Column(name = "salary_amount", nullable = false)
    private BigDecimal salaryAmount;

    @Column(name = "salary_advance_limit", nullable = false)
    private BigDecimal salaryAdvanceLimit;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_status", nullable = false)
    private PartnerEmployeeStatus employmentStatus;

    @Column(name = "active", nullable = false)
    private boolean active;

    protected PartnerEmployeeJpaEntity() {
    }

    public UUID getId() {
        return id;
    }

    public UUID getPartnerCompanyId() {
        return partnerCompanyId;
    }

    public UUID getImportBatchId() {
        return importBatchId;
    }

    public String getEmployeeCode() {
        return employeeCode;
    }

    public String getIdentityReference() {
        return identityReference;
    }

    public BigDecimal getSalaryAmount() {
        return salaryAmount;
    }

    public BigDecimal getSalaryAdvanceLimit() {
        return salaryAdvanceLimit;
    }

    public PartnerEmployeeStatus getEmploymentStatus() {
        return employmentStatus;
    }

    public boolean isActive() {
        return active;
    }
}