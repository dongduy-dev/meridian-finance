package com.meridian.platform.partner.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface JpaCustomerPartnerEmployeeLinkRepository
        extends JpaRepository<CustomerPartnerEmployeeLinkJpaEntity, UUID> {

    Optional<CustomerPartnerEmployeeLinkJpaEntity> findFirstByCustomerIdAndPartnerCompanyIdOrderByUpdatedAtDesc(
            UUID customerId,
            UUID partnerCompanyId
    );
}
