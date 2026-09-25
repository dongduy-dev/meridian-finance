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

    @Query("""
            select link.id from CustomerPartnerEmployeeLinkJpaEntity link
            where link.partnerCompanyId = :partnerCompanyId and link.linkStatus = :linkStatus
            order by link.customerId asc, link.id asc
            """)
    List<UUID> findIdsByPartnerCompanyIdAndLinkStatus(
            @Param("partnerCompanyId") UUID partnerCompanyId,
            @Param("linkStatus") CustomerPartnerEmployeeLinkStatus linkStatus
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select link from CustomerPartnerEmployeeLinkJpaEntity link
            where link.id = :linkId
              and link.partnerCompanyId = :partnerCompanyId
              and link.linkStatus = :linkStatus
            """)
    Optional<CustomerPartnerEmployeeLinkJpaEntity> findByIdAndPartnerCompanyIdAndLinkStatusForUpdate(
            @Param("linkId") UUID linkId,
            @Param("partnerCompanyId") UUID partnerCompanyId,
            @Param("linkStatus") CustomerPartnerEmployeeLinkStatus linkStatus
    );

    @Query(value = "select pg_advisory_xact_lock(hashtextextended(cast(:lockKey as text), 0))", nativeQuery = true)
    void acquireCustomerEmploymentLock(@Param("lockKey") String lockKey);
}
