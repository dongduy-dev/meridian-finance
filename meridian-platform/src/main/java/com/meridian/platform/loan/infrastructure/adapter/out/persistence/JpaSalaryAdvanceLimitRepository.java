package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface JpaSalaryAdvanceLimitRepository extends JpaRepository<SalaryAdvanceLimitJpaEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select salaryLimit from SalaryAdvanceLimitJpaEntity salaryLimit
            where salaryLimit.customerId = :customerId
              and salaryLimit.customerPartnerEmployeeLinkId = :linkId
            """)
    Optional<SalaryAdvanceLimitJpaEntity> findByCustomerIdAndLinkIdForUpdate(
            @Param("customerId") UUID customerId,
            @Param("linkId") UUID customerPartnerEmployeeLinkId
    );

    Optional<SalaryAdvanceLimitJpaEntity> findByCustomerIdAndCustomerPartnerEmployeeLinkId(
            UUID customerId,
            UUID customerPartnerEmployeeLinkId
    );

    Optional<SalaryAdvanceLimitJpaEntity> findFirstByCustomerIdOrderByLastRefreshedAtDescIdAsc(UUID customerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select salaryLimit from SalaryAdvanceLimitJpaEntity salaryLimit where salaryLimit.id = :id")
    Optional<SalaryAdvanceLimitJpaEntity> findByIdForUpdate(@Param("id") UUID id);
}
