package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.domain.model.ProductVerificationResult;
import com.meridian.platform.loan.domain.model.SalaryAdvanceVerification;
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
@Table(name = "salary_advance_verifications")
public class SalaryAdvanceVerificationJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "loan_application_id", nullable = false)
    private UUID loanApplicationId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "customer_partner_employee_link_id", nullable = false)
    private UUID customerPartnerEmployeeLinkId;

    @Column(name = "salary_advance_limit_id", nullable = false)
    private UUID salaryAdvanceLimitId;

    @Column(name = "partner_company_id", nullable = false)
    private UUID partnerCompanyId;

    @Column(name = "partner_employee_id", nullable = false)
    private UUID partnerEmployeeId;

    @Column(name = "source_import_batch_id", nullable = false)
    private UUID sourceImportBatchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_verification_result", nullable = false)
    private ProductVerificationResult productVerificationResult;

    @Column(name = "total_limit_snapshot", nullable = false)
    private BigDecimal totalLimitSnapshot;

    @Column(name = "used_amount_snapshot", nullable = false)
    private BigDecimal usedAmountSnapshot;

    @Column(name = "reserved_amount_snapshot", nullable = false)
    private BigDecimal reservedAmountSnapshot;

    @Column(name = "available_limit_snapshot", nullable = false)
    private BigDecimal availableLimitSnapshot;

    @Column(name = "verified_at", nullable = false)
    private LocalDateTime verifiedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected SalaryAdvanceVerificationJpaEntity() {
    }

    public SalaryAdvanceVerificationJpaEntity(SalaryAdvanceVerification verification) {
        this.id = verification.id();
        this.loanApplicationId = verification.loanApplicationId();
        this.customerId = verification.customerId();
        this.customerPartnerEmployeeLinkId = verification.customerPartnerEmployeeLinkId();
        this.salaryAdvanceLimitId = verification.salaryAdvanceLimitId();
        this.partnerCompanyId = verification.partnerCompanyId();
        this.partnerEmployeeId = verification.partnerEmployeeId();
        this.sourceImportBatchId = verification.sourceImportBatchId();
        this.productVerificationResult = verification.productVerificationResult();
        this.totalLimitSnapshot = verification.totalLimitSnapshot();
        this.usedAmountSnapshot = verification.usedAmountSnapshot();
        this.reservedAmountSnapshot = verification.reservedAmountSnapshot();
        this.availableLimitSnapshot = verification.availableLimitSnapshot();
        this.verifiedAt = verification.verifiedAt();
        this.createdAt = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getLoanApplicationId() {
        return loanApplicationId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public UUID getCustomerPartnerEmployeeLinkId() {
        return customerPartnerEmployeeLinkId;
    }

    public UUID getSalaryAdvanceLimitId() {
        return salaryAdvanceLimitId;
    }

    public UUID getPartnerCompanyId() {
        return partnerCompanyId;
    }

    public UUID getPartnerEmployeeId() {
        return partnerEmployeeId;
    }

    public UUID getSourceImportBatchId() {
        return sourceImportBatchId;
    }

    public ProductVerificationResult getProductVerificationResult() {
        return productVerificationResult;
    }

    public BigDecimal getTotalLimitSnapshot() {
        return totalLimitSnapshot;
    }

    public BigDecimal getUsedAmountSnapshot() {
        return usedAmountSnapshot;
    }

    public BigDecimal getReservedAmountSnapshot() {
        return reservedAmountSnapshot;
    }

    public BigDecimal getAvailableLimitSnapshot() {
        return availableLimitSnapshot;
    }

    public LocalDateTime getVerifiedAt() {
        return verifiedAt;
    }
}
