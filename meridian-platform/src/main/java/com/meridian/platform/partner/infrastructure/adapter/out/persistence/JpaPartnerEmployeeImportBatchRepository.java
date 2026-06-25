package com.meridian.platform.partner.infrastructure.adapter.out.persistence;

import com.meridian.platform.partner.domain.model.PartnerEmployeeImportBatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaPartnerEmployeeImportBatchRepository
        extends JpaRepository<PartnerEmployeeImportBatchJpaEntity, UUID> {

    List<PartnerEmployeeImportBatchJpaEntity> findByPartnerCompanyIdOrderByEffectiveMonthDesc(UUID partnerCompanyId);

    Optional<PartnerEmployeeImportBatchJpaEntity> findFirstByPartnerCompanyIdAndStatusOrderByEffectiveMonthDescIdDesc(
            UUID partnerCompanyId,
            PartnerEmployeeImportBatchStatus status
    );
}