package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

public interface JpaSalaryAdvanceLimitRepository extends JpaRepository<SalaryAdvanceLimitJpaEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<SalaryAdvanceLimitJpaEntity> findByCustomerIdAndCustomerPartnerEmployeeLinkId(
            UUID customerId,
            UUID customerPartnerEmployeeLinkId
    );
}
