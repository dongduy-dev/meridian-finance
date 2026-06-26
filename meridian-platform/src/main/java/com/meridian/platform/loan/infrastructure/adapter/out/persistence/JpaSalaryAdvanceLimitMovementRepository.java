package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JpaSalaryAdvanceLimitMovementRepository
        extends JpaRepository<SalaryAdvanceLimitMovementJpaEntity, UUID> {
}
