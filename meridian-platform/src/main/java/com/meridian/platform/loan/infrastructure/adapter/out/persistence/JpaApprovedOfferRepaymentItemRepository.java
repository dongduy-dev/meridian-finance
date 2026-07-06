package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JpaApprovedOfferRepaymentItemRepository
        extends JpaRepository<ApprovedOfferRepaymentItemJpaEntity, UUID> {

    List<ApprovedOfferRepaymentItemJpaEntity> findByApprovedOfferIdOrderByInstallmentNumberAsc(UUID approvedOfferId);

    void deleteByApprovedOfferId(UUID approvedOfferId);
}
