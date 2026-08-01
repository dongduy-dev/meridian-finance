package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

interface JpaLoanAccountRepository extends JpaRepository<LoanAccountJpaEntity, UUID> {

    Optional<LoanAccountJpaEntity> findByLoanApplicationId(UUID loanApplicationId);

    Optional<LoanAccountJpaEntity> findByLoanContractId(UUID loanContractId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select account
            from LoanAccountJpaEntity account
            where account.id = :loanAccountId
            """)
    Optional<LoanAccountJpaEntity> findByIdForUpdate(
            @Param("loanAccountId") UUID loanAccountId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select account
            from LoanAccountJpaEntity account
            where account.loanApplicationId = :loanApplicationId
            """)
    Optional<LoanAccountJpaEntity> findByLoanApplicationIdForUpdate(
            @Param("loanApplicationId") UUID loanApplicationId
    );
}
