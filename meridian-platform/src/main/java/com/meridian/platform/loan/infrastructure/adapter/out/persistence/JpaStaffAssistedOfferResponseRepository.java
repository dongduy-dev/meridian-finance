package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface JpaStaffAssistedOfferResponseRepository
        extends JpaRepository<StaffAssistedOfferResponseJpaEntity, UUID> {
    Optional<StaffAssistedOfferResponseJpaEntity> findByRequestId(UUID requestId);
    Optional<StaffAssistedOfferResponseJpaEntity> findByApprovedOfferId(UUID approvedOfferId);
}
