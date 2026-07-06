package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.domain.model.ApprovedOffer;
import com.meridian.platform.loan.domain.model.ApprovedOfferFinancialTerms;
import com.meridian.platform.loan.domain.model.ApprovedOfferStatus;
import com.meridian.platform.loan.domain.model.InterestCalculationMethod;
import com.meridian.platform.loan.domain.model.RepaymentMethod;
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
@Table(name = "approved_offers")
public class ApprovedOfferJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "loan_application_id", nullable = false)
    private UUID loanApplicationId;

    @Column(name = "source_loan_product_policy_id", nullable = false)
    private UUID sourceLoanProductPolicyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ApprovedOfferStatus status;

    @Column(name = "approved_principal", nullable = false)
    private BigDecimal approvedPrincipal;

    @Column(name = "approved_term_months", nullable = false)
    private int approvedTermMonths;

    @Enumerated(EnumType.STRING)
    @Column(name = "interest_calculation_method", nullable = false)
    private InterestCalculationMethod interestCalculationMethod;

    @Column(name = "flat_monthly_interest_rate", nullable = false)
    private BigDecimal flatMonthlyInterestRate;

    @Column(name = "total_interest", nullable = false)
    private BigDecimal totalInterest;

    @Column(name = "fee_amount", nullable = false)
    private BigDecimal feeAmount;

    @Column(name = "total_repayment_amount", nullable = false)
    private BigDecimal totalRepaymentAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "repayment_method", nullable = false)
    private RepaymentMethod repaymentMethod;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "declined_at")
    private LocalDateTime declinedAt;

    @Column(name = "expired_at")
    private LocalDateTime expiredAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected ApprovedOfferJpaEntity() {
    }

    public ApprovedOfferJpaEntity(ApprovedOffer approvedOffer) {
        LocalDateTime now = LocalDateTime.now();
        this.id = approvedOffer.id();
        this.createdAt = now;
        apply(approvedOffer, now);
    }

    public void updateFrom(ApprovedOffer approvedOffer) {
        apply(approvedOffer, LocalDateTime.now());
    }

    private void apply(ApprovedOffer approvedOffer, LocalDateTime updatedAt) {
        ApprovedOfferFinancialTerms terms = approvedOffer.financialTerms();
        this.loanApplicationId = approvedOffer.loanApplicationId();
        this.sourceLoanProductPolicyId = approvedOffer.sourceLoanProductPolicyId();
        this.status = approvedOffer.status();
        this.approvedPrincipal = terms.approvedPrincipal();
        this.approvedTermMonths = terms.approvedTermMonths();
        this.interestCalculationMethod = terms.interestCalculationMethod();
        this.flatMonthlyInterestRate = terms.flatMonthlyInterestRate();
        this.totalInterest = terms.totalInterest();
        this.feeAmount = terms.feeAmount();
        this.totalRepaymentAmount = terms.totalRepaymentAmount();
        this.repaymentMethod = terms.repaymentMethod();
        this.generatedAt = approvedOffer.generatedAt();
        this.expiresAt = approvedOffer.expiresAt();
        this.acceptedAt = approvedOffer.acceptedAt();
        this.declinedAt = approvedOffer.declinedAt();
        this.expiredAt = approvedOffer.expiredAt();
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getLoanApplicationId() {
        return loanApplicationId;
    }

    public UUID getSourceLoanProductPolicyId() {
        return sourceLoanProductPolicyId;
    }

    public ApprovedOfferStatus getStatus() {
        return status;
    }

    public BigDecimal getApprovedPrincipal() {
        return approvedPrincipal;
    }

    public int getApprovedTermMonths() {
        return approvedTermMonths;
    }

    public InterestCalculationMethod getInterestCalculationMethod() {
        return interestCalculationMethod;
    }

    public BigDecimal getFlatMonthlyInterestRate() {
        return flatMonthlyInterestRate;
    }

    public BigDecimal getTotalInterest() {
        return totalInterest;
    }

    public BigDecimal getFeeAmount() {
        return feeAmount;
    }

    public BigDecimal getTotalRepaymentAmount() {
        return totalRepaymentAmount;
    }

    public RepaymentMethod getRepaymentMethod() {
        return repaymentMethod;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public LocalDateTime getAcceptedAt() {
        return acceptedAt;
    }

    public LocalDateTime getDeclinedAt() {
        return declinedAt;
    }

    public LocalDateTime getExpiredAt() {
        return expiredAt;
    }
}
