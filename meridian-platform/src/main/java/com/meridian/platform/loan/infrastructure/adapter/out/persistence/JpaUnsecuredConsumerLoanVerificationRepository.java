package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaUnsecuredConsumerLoanVerificationRepository
        extends JpaRepository<UnsecuredConsumerLoanVerificationJpaEntity, UUID> {

    Optional<UnsecuredConsumerLoanVerificationJpaEntity>
    findFirstByLoanApplicationIdOrderByVerificationSequenceDesc(UUID loanApplicationId);

    List<UnsecuredConsumerLoanVerificationJpaEntity>
    findAllByLoanApplicationIdOrderByVerificationSequenceAsc(UUID loanApplicationId);

    @Query(value = """
            SELECT *
            FROM unsecured_consumer_loan_verifications
            WHERE loan_application_id = :loanApplicationId
            ORDER BY verification_sequence DESC
            LIMIT 1
            FOR UPDATE
            """, nativeQuery = true)
    Optional<UnsecuredConsumerLoanVerificationJpaEntity> findLatestForUpdate(
            @Param("loanApplicationId") UUID loanApplicationId
    );
}
