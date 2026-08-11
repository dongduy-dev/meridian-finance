package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.domain.model.ProductVerificationResult;
import com.meridian.platform.loan.domain.model.UnsecuredConsumerLoanVerification;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "unsecured_consumer_loan_verifications")
public class UnsecuredConsumerLoanVerificationJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "loan_application_id", nullable = false)
    private UUID loanApplicationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_verification_result", nullable = false)
    private ProductVerificationResult productVerificationResult;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected UnsecuredConsumerLoanVerificationJpaEntity() {
    }

    public UnsecuredConsumerLoanVerificationJpaEntity(UnsecuredConsumerLoanVerification verification) {
        this.id = verification.id();
        this.loanApplicationId = verification.loanApplicationId();
        this.productVerificationResult = verification.productVerificationResult();
        this.createdAt = verification.createdAt();
    }

    public UnsecuredConsumerLoanVerification toDomain() {
        return new UnsecuredConsumerLoanVerification(
                id,
                loanApplicationId,
                productVerificationResult,
                createdAt
        );
    }
}
