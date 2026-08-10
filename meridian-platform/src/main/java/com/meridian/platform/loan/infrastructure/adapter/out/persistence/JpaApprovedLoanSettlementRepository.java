package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

interface JpaApprovedLoanSettlementRepository
        extends JpaRepository<ApprovedLoanSettlementJpaEntity, UUID> {

    @Query(value = """
            select pg_advisory_xact_lock(
                hashtextextended('loan-settlement:approve-request:'
                    || cast(:requestId as text), 0)
            )
            """, nativeQuery = true)
    void acquireApprovalRequestLock(@Param("requestId") UUID requestId);

    Optional<ApprovedLoanSettlementJpaEntity> findByRequestId(UUID requestId);

    Optional<ApprovedLoanSettlementJpaEntity> findByLoanAccountId(UUID loanAccountId);

    Optional<ApprovedLoanSettlementJpaEntity> findByRepaymentTransactionId(
            UUID repaymentTransactionId
    );

    @Modifying
    @Query(value = """
            insert into approved_loan_settlements (
                id,
                loan_application_id,
                loan_account_id,
                repayment_transaction_id,
                request_id,
                settlement_amount,
                approved_by_user_id,
                approved_at
            ) values (
                :id,
                :loanApplicationId,
                :loanAccountId,
                :repaymentTransactionId,
                :requestId,
                :settlementAmount,
                :approvedByUserId,
                :approvedAt
            )
            on conflict do nothing
            """, nativeQuery = true)
    int insertIfNoConflict(
            @Param("id") UUID id,
            @Param("loanApplicationId") UUID loanApplicationId,
            @Param("loanAccountId") UUID loanAccountId,
            @Param("repaymentTransactionId") UUID repaymentTransactionId,
            @Param("requestId") UUID requestId,
            @Param("settlementAmount") BigDecimal settlementAmount,
            @Param("approvedByUserId") UUID approvedByUserId,
            @Param("approvedAt") LocalDateTime approvedAt
    );
}
