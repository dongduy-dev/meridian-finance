package com.meridian.platform.partner.infrastructure.adapter.out.persistence;

import com.meridian.platform.partner.application.port.out.PartnerEmployeeImportBatchRepository;
import com.meridian.platform.partner.domain.model.PartnerEmployeeImportBatch;
import com.meridian.platform.partner.domain.model.PartnerEmployeeImportBatchStatus;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PartnerEmployeeImportBatchRepositoryAdapter implements PartnerEmployeeImportBatchRepository {

    private final JpaPartnerEmployeeImportBatchRepository jpaRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public PartnerEmployeeImportBatchRepositoryAdapter(
            JpaPartnerEmployeeImportBatchRepository jpaRepository,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.jpaRepository = jpaRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
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
        return jpaRepository.findFirstByPartnerCompanyIdAndStatusOrderByEffectiveMonthDescCreatedAtDescIdDesc(
                        partnerCompanyId,
                        PartnerEmployeeImportBatchStatus.COMPLETED
                )
                .map(this::toDomain);
    }

    @Override
    public Optional<PartnerEmployeeImportBatch> findLatestCompletedByPartnerCompanyIdAndEffectiveMonth(
            UUID partnerCompanyId,
            String effectiveMonth
    ) {
        return jpaRepository
                .findFirstByPartnerCompanyIdAndEffectiveMonthAndStatusOrderByCreatedAtDescIdDesc(
                        partnerCompanyId,
                        effectiveMonth,
                        PartnerEmployeeImportBatchStatus.COMPLETED
                )
                .map(this::toDomain);
    }

    @Override
    public void acquireRequestLock(UUID requestId) {
        jpaRepository.acquireRequestLock("partner-employee-import:" + requestId);
    }

    @Override
    public Optional<PartnerEmployeeImportBatch> findByRequestId(UUID requestId) {
        return jpaRepository.findByRequestId(requestId).map(this::toDomain);
    }

    @Override
    public PartnerEmployeeImportBatch save(PartnerEmployeeImportBatch importBatch) {
        try {
            String rejectionSummary = objectMapper.writeValueAsString(importBatch.rejections());
            return toDomain(jpaRepository.saveAndFlush(new PartnerEmployeeImportBatchJpaEntity(
                    importBatch.id(), importBatch.partnerCompanyId(), importBatch.effectiveMonth(),
                    importBatch.status(), importBatch.validRowCount(), importBatch.invalidRowCount(),
                    LocalDateTime.now(clock), importBatch.requestId(), importBatch.requestFingerprint(),
                    rejectionSummary
            )));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Partner import result could not be persisted.", exception);
        }
    }

    private PartnerEmployeeImportBatch toDomain(PartnerEmployeeImportBatchJpaEntity entity) {
        try {
            var rejectionType = objectMapper.getTypeFactory().constructCollectionType(
                    List.class,
                    com.meridian.platform.partner.domain.model.PartnerEmployeeImportRejection.class
            );
            List<com.meridian.platform.partner.domain.model.PartnerEmployeeImportRejection> rejections =
                    entity.getRejectionSummary() == null
                            ? List.of()
                            : objectMapper.readValue(entity.getRejectionSummary(), rejectionType);
            return new PartnerEmployeeImportBatch(
                    entity.getId(), entity.getPartnerCompanyId(), entity.getEffectiveMonth(),
                    entity.getStatus(), entity.getValidRowCount(), entity.getInvalidRowCount(),
                    entity.getRequestId(), entity.getRequestFingerprint(), rejections
            );
        } catch (JacksonException exception) {
            throw new IllegalStateException("Partner import result could not be read.", exception);
        }
    }
}
