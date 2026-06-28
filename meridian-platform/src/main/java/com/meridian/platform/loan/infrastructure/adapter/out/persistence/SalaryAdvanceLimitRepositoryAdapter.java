package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.application.port.out.SalaryAdvanceLimitRepository;
import com.meridian.platform.loan.domain.model.SalaryAdvanceLimit;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class SalaryAdvanceLimitRepositoryAdapter implements SalaryAdvanceLimitRepository {

    private final JpaSalaryAdvanceLimitRepository jpaSalaryAdvanceLimitRepository;
    private final EntityManager entityManager;

    public SalaryAdvanceLimitRepositoryAdapter(
            JpaSalaryAdvanceLimitRepository jpaSalaryAdvanceLimitRepository,
            EntityManager entityManager
    ) {
        this.jpaSalaryAdvanceLimitRepository = jpaSalaryAdvanceLimitRepository;
        this.entityManager = entityManager;
    }

    @Override
    public void acquireCustomerLinkLock(UUID customerId, UUID customerPartnerEmployeeLinkId) {
        String lockKey = "salary-advance-limit:" + customerId + ":" + customerPartnerEmployeeLinkId;
        entityManager.createNativeQuery("""
                        WITH lock AS (
                            SELECT pg_advisory_xact_lock(hashtextextended(CAST(:lockKey AS text), 0))
                        )
                        SELECT 1 FROM lock
                        """)
                .setParameter("lockKey", lockKey)
                .getSingleResult();
    }

    @Override
    public Optional<SalaryAdvanceLimit> findByCustomerIdAndCustomerPartnerEmployeeLinkIdForUpdate(
            UUID customerId,
            UUID customerPartnerEmployeeLinkId
    ) {
        return jpaSalaryAdvanceLimitRepository
                .findByCustomerIdAndCustomerPartnerEmployeeLinkId(customerId, customerPartnerEmployeeLinkId)
                .map(this::toDomain);
    }

    @Override
    public SalaryAdvanceLimit save(SalaryAdvanceLimit salaryAdvanceLimit) {
        SalaryAdvanceLimitJpaEntity entity = jpaSalaryAdvanceLimitRepository.findById(salaryAdvanceLimit.id())
                .map(existingEntity -> {
                    existingEntity.updateFrom(salaryAdvanceLimit);
                    return existingEntity;
                })
                .orElseGet(() -> new SalaryAdvanceLimitJpaEntity(salaryAdvanceLimit));

        return toDomain(jpaSalaryAdvanceLimitRepository.save(entity));
    }

    private SalaryAdvanceLimit toDomain(SalaryAdvanceLimitJpaEntity entity) {
        return new SalaryAdvanceLimit(
                entity.getId(),
                entity.getCustomerId(),
                entity.getCustomerPartnerEmployeeLinkId(),
                entity.getTotalLimit(),
                entity.getUsedAmount(),
                entity.getReservedAmount(),
                entity.getAvailableAmount(),
                entity.getStatus(),
                entity.getLastRefreshedAt()
        );
    }
}
