package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

interface JpaRepaymentScheduleRepository
        extends JpaRepository<RepaymentScheduleJpaEntity, UUID> {

    Optional<RepaymentScheduleJpaEntity> findByLoanAccountId(UUID loanAccountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RepaymentScheduleJpaEntity> findForUpdateByLoanAccountId(UUID loanAccountId);

    Optional<RepaymentScheduleJpaEntity> findByLoanApplicationId(UUID loanApplicationId);

    Optional<RepaymentScheduleJpaEntity> findByLoanContractId(UUID loanContractId);
}
