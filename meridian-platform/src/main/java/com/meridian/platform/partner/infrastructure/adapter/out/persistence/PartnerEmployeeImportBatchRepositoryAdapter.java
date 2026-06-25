package com.meridian.platform.partner.infrastructure.adapter.out.persistence;

import com.meridian.platform.partner.application.port.out.PartnerEmployeeImportBatchRepository;
import com.meridian.platform.partner.domain.model.PartnerEmployeeImportBatch;
import com.meridian.platform.partner.domain.model.PartnerEmployeeImportBatchStatus;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PartnerEmployeeImportBatchRepositoryAdapter implements PartnerEmployeeImportBatchRepository {

    private final JpaPartnerEmployeeImportBatchRepository jpaRepository;

    public PartnerEmployeeImportBatchRepositoryAdapter(JpaPartnerEmployeeImportBatchRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<PartnerEmployeeImportBatch> findByPartnerCompanyId(UUID partnerCompanyId) {
        return jpaRepository.findByPartnerCompanyIdOrderByEffectiveMonthDesc(partnerCompanyId)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Optional<PartnerEmployeeImportBatch> findLatestCompletedByPartnerCompanyId(UUID partnerCompanyId) {
        return jpaRepository.findFirstByPartnerCompanyIdAndStatusOrderByEffectiveMonthDescIdDesc(
                        partnerCompanyId,
                        PartnerEmployeeImportBatchStatus.COMPLETED
                )
                .map(this::toDomain);
    }

    private PartnerEmployeeImportBatch toDomain(PartnerEmployeeImportBatchJpaEntity entity) {
        return new PartnerEmployeeImportBatch(
                entity.getId(),
                entity.getPartnerCompanyId(),
                entity.getEffectiveMonth(),
                entity.getStatus(),
                entity.getValidRowCount(),
                entity.getInvalidRowCount()
        );
    }
}