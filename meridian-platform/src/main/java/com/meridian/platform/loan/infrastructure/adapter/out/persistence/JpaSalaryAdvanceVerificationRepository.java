package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface JpaSalaryAdvanceVerificationRepository
        extends JpaRepository<SalaryAdvanceVerificationJpaEntity, UUID> {

    Optional<SalaryAdvanceVerificationJpaEntity> findFirstByLoanApplicationIdOrderByVerificationSequenceDesc(UUID loanApplicationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select verification
            from SalaryAdvanceVerificationJpaEntity verification
            where verification.loanApplicationId = :loanApplicationId
              and verification.verificationSequence = (
                  select max(candidate.verificationSequence)
                  from SalaryAdvanceVerificationJpaEntity candidate
                  where candidate.loanApplicationId = :loanApplicationId
              )
            """)
    Optional<SalaryAdvanceVerificationJpaEntity> findLatestByLoanApplicationIdForUpdate(
            @Param("loanApplicationId") UUID loanApplicationId
    );
}
