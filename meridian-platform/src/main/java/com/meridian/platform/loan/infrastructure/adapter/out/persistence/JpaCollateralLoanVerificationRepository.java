package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface JpaCollateralLoanVerificationRepository
        extends JpaRepository<CollateralLoanVerificationJpaEntity, UUID> {

    Optional<CollateralLoanVerificationJpaEntity>
    findFirstByLoanApplicationIdOrderByVerificationSequenceDesc(UUID loanApplicationId);

    @Query(value = """
            SELECT *
            FROM collateral_loan_verifications
            WHERE loan_application_id = :loanApplicationId
            ORDER BY verification_sequence DESC
            LIMIT 1
            FOR UPDATE
            """, nativeQuery = true)
    Optional<CollateralLoanVerificationJpaEntity> findLatestForUpdate(
            @Param("loanApplicationId") UUID loanApplicationId
    );
}
