package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.application.port.out.StaffAssistedOfferResponseRepository;
import com.meridian.platform.loan.domain.model.StaffAssistedOfferResponse;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class StaffAssistedOfferResponseRepositoryAdapter implements StaffAssistedOfferResponseRepository {
    private final JpaStaffAssistedOfferResponseRepository responses;
    private final EntityManager entityManager;

    public StaffAssistedOfferResponseRepositoryAdapter(
            JpaStaffAssistedOfferResponseRepository responses, EntityManager entityManager
    ) {
        this.responses = responses;
        this.entityManager = entityManager;
    }

    @Override
    public void acquireRequestLock(UUID requestId) {
        entityManager.createNativeQuery("""
                        WITH lock AS (
                            SELECT pg_advisory_xact_lock(hashtextextended(CAST(:lockKey AS text), 0))
                        )
                        SELECT 1 FROM lock
                        """)
                .setParameter("lockKey", "staff-assisted-offer-response:" + requestId)
                .getSingleResult();
    }
    @Override public StaffAssistedOfferResponse save(StaffAssistedOfferResponse response) {
        return responses.save(new StaffAssistedOfferResponseJpaEntity(response)).toDomain();
    }
    @Override public Optional<StaffAssistedOfferResponse> findByRequestId(UUID requestId) {
        return responses.findByRequestId(requestId).map(StaffAssistedOfferResponseJpaEntity::toDomain);
    }
    @Override public Optional<StaffAssistedOfferResponse> findByApprovedOfferId(UUID approvedOfferId) {
        return responses.findByApprovedOfferId(approvedOfferId).map(StaffAssistedOfferResponseJpaEntity::toDomain);
    }
}
