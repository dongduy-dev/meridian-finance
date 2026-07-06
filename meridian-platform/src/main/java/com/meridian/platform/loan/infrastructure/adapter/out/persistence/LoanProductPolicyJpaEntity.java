package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.domain.model.InterestCalculationMethod;
import com.meridian.platform.loan.domain.model.RepaymentMethod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "loan_product_policies")
public class LoanProductPolicyJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "loan_product_id", nullable = false)
    private UUID loanProductId;

    @Column(name = "policy_code", nullable = false)
    private String policyCode;

    @Column(name = "offer_validity_days", nullable = false)
    private int offerValidityDays;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Enumerated(EnumType.STRING)
    @Column(name = "interest_calculation_method")
    private InterestCalculationMethod interestCalculationMethod;

    @Column(name = "flat_monthly_interest_rate")
    private BigDecimal flatMonthlyInterestRate;

    @Column(name = "fee_amount")
    private BigDecimal feeAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "repayment_method")
    private RepaymentMethod repaymentMethod;

    protected LoanProductPolicyJpaEntity() {
    }

    public UUID getId() {
        return id;
    }

    public int getOfferValidityDays() {
        return offerValidityDays;
    }

    public InterestCalculationMethod getInterestCalculationMethod() {
        return interestCalculationMethod;
    }

    public BigDecimal getFlatMonthlyInterestRate() {
        return flatMonthlyInterestRate;
    }

    public BigDecimal getFeeAmount() {
        return feeAmount;
    }

    public RepaymentMethod getRepaymentMethod() {
        return repaymentMethod;
    }
}
