package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface JpaRepaymentScheduleRepository
        extends JpaRepository<RepaymentScheduleJpaEntity, UUID> {

    Optional<RepaymentScheduleJpaEntity> findByLoanAccountId(UUID loanAccountId);

    Optional<RepaymentScheduleJpaEntity> findByLoanApplicationId(UUID loanApplicationId);

    Optional<RepaymentScheduleJpaEntity> findByLoanContractId(UUID loanContractId);
}
