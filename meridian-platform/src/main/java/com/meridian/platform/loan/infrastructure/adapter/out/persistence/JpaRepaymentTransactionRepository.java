package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface JpaRepaymentTransactionRepository
        extends JpaRepository<RepaymentTransactionJpaEntity, UUID> {

    @Query(value = """
            select pg_advisory_xact_lock(
                hashtextextended('repayment-recording-request:' || cast(:requestId as text), 0)
            )
            """, nativeQuery = true)
    void acquireRecordingRequestLock(@Param("requestId") UUID requestId);

    Optional<RepaymentTransactionJpaEntity> findByRequestId(UUID requestId);

    Optional<RepaymentTransactionJpaEntity> findByExternalPaymentReference(
            String externalPaymentReference
    );

    List<RepaymentTransactionJpaEntity> findByLoanAccountIdOrderByRecordedAtAscIdAsc(
            UUID loanAccountId
    );

    @Modifying
    @Query(value = """
            insert into repayment_transactions (
                id,
                loan_application_id,
                loan_account_id,
                repayment_schedule_id,
                request_id,
                external_payment_reference,
                received_amount,
                payment_value_date,
                recorded_by_user_id,
                recorded_at
            ) values (
                :id,
                :loanApplicationId,
                :loanAccountId,
                :repaymentScheduleId,
                :requestId,
                :externalPaymentReference,
                :receivedAmount,
                :paymentValueDate,
                :recordedByUserId,
                :recordedAt
            )
            on conflict do nothing
            """, nativeQuery = true)
    int insertIfNoConflict(
            @Param("id") UUID id,
            @Param("loanApplicationId") UUID loanApplicationId,
            @Param("loanAccountId") UUID loanAccountId,
            @Param("repaymentScheduleId") UUID repaymentScheduleId,
            @Param("requestId") UUID requestId,
            @Param("externalPaymentReference") String externalPaymentReference,
            @Param("receivedAmount") BigDecimal receivedAmount,
            @Param("paymentValueDate") LocalDate paymentValueDate,
            @Param("recordedByUserId") UUID recordedByUserId,
            @Param("recordedAt") LocalDateTime recordedAt
    );
}
