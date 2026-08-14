package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface JpaCollateralLoanVerificationRepository
        extends JpaRepository<CollateralLoanVerificationJpaEntity, UUID> {

    Optional<CollateralLoanVerificationJpaEntity> findByLoanApplicationId(UUID loanApplicationId);
}
