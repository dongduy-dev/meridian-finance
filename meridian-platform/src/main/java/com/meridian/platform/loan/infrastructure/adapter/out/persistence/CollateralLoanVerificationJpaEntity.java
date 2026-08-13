package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.domain.model.CollateralLoanVerification;
import com.meridian.platform.loan.domain.model.ProductVerificationResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "collateral_loan_verifications")
public class CollateralLoanVerificationJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "loan_application_id", nullable = false)
    private UUID loanApplicationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_verification_result", nullable = false, length = 50)
    private ProductVerificationResult productVerificationResult;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected CollateralLoanVerificationJpaEntity() {
    }

    public CollateralLoanVerificationJpaEntity(CollateralLoanVerification verification) {
        this.id = verification.id();
        this.loanApplicationId = verification.loanApplicationId();
        this.productVerificationResult = verification.productVerificationResult();
        this.createdAt = verification.createdAt();
    }

    public CollateralLoanVerification toDomain() {
        return new CollateralLoanVerification(
                id,
                loanApplicationId,
                productVerificationResult,
                createdAt
        );
    }
}
