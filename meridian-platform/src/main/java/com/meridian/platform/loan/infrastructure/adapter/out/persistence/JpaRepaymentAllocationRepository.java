package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface JpaRepaymentAllocationRepository
        extends JpaRepository<RepaymentAllocationJpaEntity, UUID> {

    List<RepaymentAllocationJpaEntity>
    findByRepaymentTransactionIdOrderByAllocationSequenceAsc(
            UUID repaymentTransactionId
    );
}
