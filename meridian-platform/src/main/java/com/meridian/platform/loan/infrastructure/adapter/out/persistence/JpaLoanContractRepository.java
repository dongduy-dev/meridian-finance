package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.domain.model.LoanContractStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface JpaLoanContractRepository extends JpaRepository<LoanContractJpaEntity, UUID> {
    Optional<LoanContractJpaEntity> findByLoanApplicationIdAndStatusNot(UUID loanApplicationId, LoanContractStatus status);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select contract from LoanContractJpaEntity contract where contract.loanApplicationId = :applicationId and contract.status <> com.meridian.platform.loan.domain.model.LoanContractStatus.SUPERSEDED")
    Optional<LoanContractJpaEntity> findCurrentForUpdate(@Param("applicationId") UUID applicationId);
    Optional<LoanContractJpaEntity> findByPreparationRequestId(UUID requestId);
    Optional<LoanContractJpaEntity> findByAcknowledgmentRequestId(UUID requestId);
    Optional<LoanContractJpaEntity> findByConfirmationRequestId(UUID requestId);
    @Query("select coalesce(max(contract.contractVersion), 0) from LoanContractJpaEntity contract where contract.loanApplicationId = :applicationId")
    int maximumVersion(@Param("applicationId") UUID applicationId);
}
