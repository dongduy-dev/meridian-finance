package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface JpaRepaymentInstallmentProgressRepository
        extends JpaRepository<RepaymentInstallmentProgressJpaEntity, UUID> {

    List<RepaymentInstallmentProgressJpaEntity>
    findByRepaymentScheduleIdOrderByInstallmentNumberAsc(UUID repaymentScheduleId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select progress
            from RepaymentInstallmentProgressJpaEntity progress
            where progress.loanAccountId = :loanAccountId
            order by progress.installmentNumber
            """)
    List<RepaymentInstallmentProgressJpaEntity> findByLoanAccountIdForUpdate(
            @Param("loanAccountId") UUID loanAccountId
    );
}
