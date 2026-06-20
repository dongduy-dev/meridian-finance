package com.meridian.platform.partner.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JpaPartnerEmployeeRepository extends JpaRepository<PartnerEmployeeJpaEntity, UUID> {

    List<PartnerEmployeeJpaEntity> findByPartnerCompanyIdOrderByEmployeeCodeAsc(UUID partnerCompanyId);
}