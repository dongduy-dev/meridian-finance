package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface JpaRepaymentInstallmentStatusTransitionRepository
        extends JpaRepository<RepaymentInstallmentStatusTransitionJpaEntity, UUID> {

    @Query("""
            select coalesce(max(transition.sequenceNumber), 0) + 1
            from RepaymentInstallmentStatusTransitionJpaEntity transition
            where transition.repaymentScheduleItemId = :repaymentScheduleItemId
            """)
    int nextSequenceNumber(
            @Param("repaymentScheduleItemId") UUID repaymentScheduleItemId
    );

    List<RepaymentInstallmentStatusTransitionJpaEntity>
    findByRepaymentScheduleItemIdOrderBySequenceNumberAsc(
            UUID repaymentScheduleItemId
    );
}
