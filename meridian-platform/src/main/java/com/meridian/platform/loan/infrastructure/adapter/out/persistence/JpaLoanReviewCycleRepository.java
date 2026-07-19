package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.domain.model.LoanReviewCycleStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface JpaLoanReviewCycleRepository extends JpaRepository<LoanReviewCycleJpaEntity, UUID> {
    Optional<LoanReviewCycleJpaEntity> findByLoanApplicationIdAndStatus(
            UUID loanApplicationId, LoanReviewCycleStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select cycle from LoanReviewCycleJpaEntity cycle where cycle.loanApplicationId = :applicationId and cycle.status = :status")
    Optional<LoanReviewCycleJpaEntity> findByApplicationAndStatusForUpdate(
            @Param("applicationId") UUID loanApplicationId,
            @Param("status") LoanReviewCycleStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select cycle from LoanReviewCycleJpaEntity cycle where cycle.id = :id")
    Optional<LoanReviewCycleJpaEntity> findByIdForUpdate(@Param("id") UUID id);

    @Query("select coalesce(max(cycle.cycleNumber), 0) from LoanReviewCycleJpaEntity cycle where cycle.loanApplicationId = :applicationId")
    int findMaximumCycleNumber(@Param("applicationId") UUID loanApplicationId);
}
