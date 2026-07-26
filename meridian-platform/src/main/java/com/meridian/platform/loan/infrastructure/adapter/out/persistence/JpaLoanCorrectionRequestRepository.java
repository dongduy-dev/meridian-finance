package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.domain.model.LoanCorrectionRequestStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface JpaLoanCorrectionRequestRepository extends JpaRepository<LoanCorrectionRequestJpaEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from LoanCorrectionRequestJpaEntity request where request.loanApplicationId = :applicationId and request.status in :statuses")
    Optional<LoanCorrectionRequestJpaEntity> findActiveForUpdate(
            @Param("applicationId") UUID loanApplicationId,
            @Param("statuses") Collection<LoanCorrectionRequestStatus> statuses
    );

    boolean existsByLoanApplicationIdAndStatusIn(
            UUID loanApplicationId,
            Collection<LoanCorrectionRequestStatus> statuses
    );

    Optional<LoanCorrectionRequestJpaEntity> findFirstByLoanApplicationIdOrderByCreatedAtDesc(UUID loanApplicationId);
}
