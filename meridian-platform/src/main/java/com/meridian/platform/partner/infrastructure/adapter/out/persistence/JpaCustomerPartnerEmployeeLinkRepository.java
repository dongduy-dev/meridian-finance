package com.meridian.platform.partner.infrastructure.adapter.out.persistence;

import com.meridian.platform.partner.domain.model.CustomerPartnerEmployeeLinkStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaCustomerPartnerEmployeeLinkRepository
        extends JpaRepository<CustomerPartnerEmployeeLinkJpaEntity, UUID> {

    Optional<CustomerPartnerEmployeeLinkJpaEntity> findFirstByCustomerIdAndPartnerCompanyIdOrderByUpdatedAtDesc(
            UUID customerId,
            UUID partnerCompanyId
    );

    List<CustomerPartnerEmployeeLinkJpaEntity>
            findByCustomerIdAndLinkStatusOrderByLastRefreshedAtDescIdAsc(
                    UUID customerId,
                    CustomerPartnerEmployeeLinkStatus linkStatus
            );
}
