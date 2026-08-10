package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

interface JpaLoanAccountClosureRepository
        extends JpaRepository<LoanAccountClosureJpaEntity, UUID> {

    @Query(value = """
            select pg_advisory_xact_lock(
                hashtextextended('loan-account:close-request:'
                    || cast(:requestId as text), 0)
            )
            """, nativeQuery = true)
    void acquireClosureRequestLock(@Param("requestId") UUID requestId);

    Optional<LoanAccountClosureJpaEntity> findByRequestId(UUID requestId);

    Optional<LoanAccountClosureJpaEntity> findByLoanAccountId(UUID loanAccountId);

    @Modifying
    @Query(value = """
            insert into loan_account_closures (
                id,
                loan_application_id,
                loan_account_id,
                request_id,
                closed_by_user_id,
                closed_at
            ) values (
                :id,
                :loanApplicationId,
                :loanAccountId,
                :requestId,
                :closedByUserId,
                :closedAt
            )
            on conflict do nothing
            """, nativeQuery = true)
    int insertIfNoConflict(
            @Param("id") UUID id,
            @Param("loanApplicationId") UUID loanApplicationId,
            @Param("loanAccountId") UUID loanAccountId,
            @Param("requestId") UUID requestId,
            @Param("closedByUserId") UUID closedByUserId,
            @Param("closedAt") LocalDateTime closedAt
    );
}
