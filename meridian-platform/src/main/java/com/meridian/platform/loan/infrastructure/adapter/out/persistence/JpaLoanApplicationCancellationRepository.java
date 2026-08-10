package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

interface JpaLoanApplicationCancellationRepository
        extends JpaRepository<LoanApplicationCancellationJpaEntity, UUID> {

    @Query(value = """
            select pg_advisory_xact_lock(
                hashtextextended('loan-application:cancel-request:'
                    || cast(:requestId as text), 0)
            )
            """, nativeQuery = true)
    void acquireCancellationRequestLock(@Param("requestId") UUID requestId);

    Optional<LoanApplicationCancellationJpaEntity> findByRequestId(UUID requestId);

    Optional<LoanApplicationCancellationJpaEntity> findByLoanApplicationId(UUID loanApplicationId);

    @Modifying
    @Query(value = """
            insert into loan_application_cancellations (
                id,
                loan_application_id,
                correction_request_id,
                reservation_release_movement_id,
                request_id,
                cancelled_by_user_id,
                cancelled_at
            ) values (
                :id,
                :loanApplicationId,
                :correctionRequestId,
                :reservationReleaseMovementId,
                :requestId,
                :cancelledByUserId,
                :cancelledAt
            )
            on conflict do nothing
            """, nativeQuery = true)
    int insertIfNoConflict(
            @Param("id") UUID id,
            @Param("loanApplicationId") UUID loanApplicationId,
            @Param("correctionRequestId") UUID correctionRequestId,
            @Param("reservationReleaseMovementId") UUID reservationReleaseMovementId,
            @Param("requestId") UUID requestId,
            @Param("cancelledByUserId") UUID cancelledByUserId,
            @Param("cancelledAt") LocalDateTime cancelledAt
    );
}
