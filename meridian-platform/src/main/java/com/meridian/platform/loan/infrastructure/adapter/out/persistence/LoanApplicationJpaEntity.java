package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "loan_applications")
public class LoanApplicationJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "loan_product_id", nullable = false)
    private UUID loanProductId;

    @Column(name = "application_number", nullable = false)
    private String applicationNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_code", nullable = false)
    private ProductCode productCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false)
    private ProductType productType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private LoanApplicationStatus status;

    @Column(name = "requested_amount", nullable = false)
    private BigDecimal requestedAmount;

    @Column(name = "requested_term_months", nullable = false)
    private int requestedTermMonths;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected LoanApplicationJpaEntity() {
    }

    public LoanApplicationJpaEntity(LoanApplication loanApplication) {
        LocalDateTime now = LocalDateTime.now();
        this.id = loanApplication.id();
        this.createdAt = now;
        this.updatedAt = now;
        apply(loanApplication);
    }

    public void updateFrom(LoanApplication loanApplication) {
        this.updatedAt = LocalDateTime.now();
        apply(loanApplication);
    }

    private void apply(LoanApplication loanApplication) {
        Objects.requireNonNull(loanApplication, "loanApplication must not be null");
        this.customerId = loanApplication.customerId();
        this.loanProductId = loanApplication.loanProductId();
        this.applicationNumber = loanApplication.applicationNumber();
        this.productCode = loanApplication.productCode();
        this.productType = loanApplication.productType();
        this.status = loanApplication.status();
        this.requestedAmount = loanApplication.requestedAmount();
        this.requestedTermMonths = loanApplication.requestedTermMonths();
        this.submittedAt = loanApplication.submittedAt();
    }

    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public UUID getLoanProductId() {
        return loanProductId;
    }

    public String getApplicationNumber() {
        return applicationNumber;
    }

    public ProductCode getProductCode() {
        return productCode;
    }

    public ProductType getProductType() {
        return productType;
    }

    public LoanApplicationStatus getStatus() {
        return status;
    }

    public BigDecimal getRequestedAmount() {
        return requestedAmount;
    }

    public int getRequestedTermMonths() {
        return requestedTermMonths;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }
}
