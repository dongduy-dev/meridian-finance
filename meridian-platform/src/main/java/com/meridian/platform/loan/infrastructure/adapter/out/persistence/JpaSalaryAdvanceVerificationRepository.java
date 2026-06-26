package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JpaSalaryAdvanceVerificationRepository
        extends JpaRepository<SalaryAdvanceVerificationJpaEntity, UUID> {
}
