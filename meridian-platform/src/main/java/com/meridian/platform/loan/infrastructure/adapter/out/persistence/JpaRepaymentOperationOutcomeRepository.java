package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface JpaRepaymentOperationOutcomeRepository
        extends JpaRepository<RepaymentOperationOutcomeJpaEntity, UUID> {
}
