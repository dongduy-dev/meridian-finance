package com.meridian.platform.partner.infrastructure.adapter.out.persistence;

import com.meridian.platform.partner.domain.model.CustomerPartnerEmployeeLinkStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaCustomerPartnerEmployeeLinkRepository
        extends JpaRepository<CustomerPartnerEmployeeLinkJpaEntity, UUID> {

    Optional<CustomerPartnerEmployeeLinkJpaEntity> findByCustomerIdAndLinkStatus(
            UUID customerId,
            CustomerPartnerEmployeeLinkStatus linkStatus
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select link from CustomerPartnerEmployeeLinkJpaEntity link
            where link.customerId = :customerId and link.linkStatus = :linkStatus
            """)
    Optional<CustomerPartnerEmployeeLinkJpaEntity> findByCustomerIdAndLinkStatusForUpdate(
            @Param("customerId") UUID customerId,
            @Param("linkStatus") CustomerPartnerEmployeeLinkStatus linkStatus
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select link from CustomerPartnerEmployeeLinkJpaEntity link where link.id = :linkId")
    Optional<CustomerPartnerEmployeeLinkJpaEntity> findByIdForUpdate(@Param("linkId") UUID linkId);

    List<CustomerPartnerEmployeeLinkJpaEntity>
            findByPartnerCompanyIdAndLinkStatusOrderByCustomerIdAscIdAsc(
                    UUID partnerCompanyId,
                    CustomerPartnerEmployeeLinkStatus linkStatus
            );

    @Query(value = "select pg_advisory_xact_lock(hashtextextended(cast(:lockKey as text), 0))", nativeQuery = true)
    void acquireCustomerEmploymentLock(@Param("lockKey") String lockKey);
}
