package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface JpaUnsecuredConsumerLoanVerificationRepository
        extends JpaRepository<UnsecuredConsumerLoanVerificationJpaEntity, UUID> {

    Optional<UnsecuredConsumerLoanVerificationJpaEntity> findByLoanApplicationId(UUID loanApplicationId);
}
