package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.domain.model.collateral.CollateralLoanVerification;
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

    @Column(name = "verification_sequence", nullable = false)
    private int verificationSequence;

    @Column(name = "source_correction_request_id")
    private UUID sourceCorrectionRequestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_verification_result", nullable = false, length = 50)
    private ProductVerificationResult productVerificationResult;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "reviewed_by_user_id")
    private UUID reviewedByUserId;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "assessment_note", length = 2000)
    private String assessmentNote;

    protected CollateralLoanVerificationJpaEntity() {
    }

    public CollateralLoanVerificationJpaEntity(CollateralLoanVerification verification) {
        this.id = verification.id();
        this.loanApplicationId = verification.loanApplicationId();
        this.verificationSequence = verification.verificationSequence();
        this.sourceCorrectionRequestId = verification.sourceCorrectionRequestId();
        this.productVerificationResult = verification.productVerificationResult();
        this.createdAt = verification.createdAt();
        this.reviewedByUserId = verification.reviewedByUserId();
        this.reviewedAt = verification.reviewedAt();
        this.assessmentNote = verification.assessmentNote();
    }

    public CollateralLoanVerification toDomain() {
        return new CollateralLoanVerification(
                id,
                loanApplicationId,
                verificationSequence,
                sourceCorrectionRequestId,
                productVerificationResult,
                createdAt,
                reviewedByUserId,
                reviewedAt,
                assessmentNote
        );
    }
}
