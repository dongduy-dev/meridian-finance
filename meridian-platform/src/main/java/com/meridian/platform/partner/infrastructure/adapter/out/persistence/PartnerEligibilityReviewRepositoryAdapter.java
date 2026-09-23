package com.meridian.platform.partner.infrastructure.adapter.out.persistence;

import com.meridian.platform.partner.application.port.out.PartnerEligibilityReviewRepository;
import com.meridian.platform.partner.domain.model.PartnerEligibilityReview;
import com.meridian.platform.partner.domain.model.PartnerEligibilityReviewStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class PartnerEligibilityReviewRepositoryAdapter implements PartnerEligibilityReviewRepository {

    private final JpaPartnerEligibilityReviewRepository repository;

    public PartnerEligibilityReviewRepositoryAdapter(JpaPartnerEligibilityReviewRepository repository) {
        this.repository = repository;
    }

    @Override
    public void acquireCustomerPartnerLock(UUID customerId, UUID partnerCompanyId) {
        repository.acquireCustomerPartnerLock(
                "partner-eligibility-review:" + customerId + ":" + partnerCompanyId
        );
    }

    @Override
    public Optional<PartnerEligibilityReview> findById(UUID reviewId) {
        return repository.findById(reviewId).map(PartnerEligibilityReviewJpaEntity::toDomain);
    }

    @Override
    public Optional<LockIdentity> findLockIdentityById(UUID reviewId) {
        return repository.findLockIdentityById(reviewId)
                .map(identity -> new LockIdentity(identity.getCustomerId(), identity.getPartnerCompanyId()));
    }

    @Override
    public Optional<PartnerEligibilityReview> findByIdForUpdate(UUID reviewId) {
        return repository.findByIdForUpdate(reviewId).map(PartnerEligibilityReviewJpaEntity::toDomain);
    }

    @Override
    public Optional<PartnerEligibilityReview> findPendingByCustomerIdAndPartnerCompanyId(
            UUID customerId,
            UUID partnerCompanyId
    ) {
        return repository.findFirstByCustomerIdAndPartnerCompanyIdAndStatus(
                customerId, partnerCompanyId, PartnerEligibilityReviewStatus.PENDING
        ).map(PartnerEligibilityReviewJpaEntity::toDomain);
    }

    @Override
    public Page findPage(PartnerEligibilityReviewStatus status, int page, int size) {
        org.springframework.data.domain.Page<PartnerEligibilityReviewJpaEntity> selected =
                repository.findByStatusOrderByCreatedAtAscIdAsc(status, PageRequest.of(page, size));
        return new Page(
                selected.getNumber(), selected.getSize(), selected.getTotalElements(),
                selected.getTotalPages(), selected.getContent().stream()
                        .map(PartnerEligibilityReviewJpaEntity::toDomain).toList()
        );
    }

    @Override
    public PartnerEligibilityReview save(PartnerEligibilityReview review) {
        PartnerEligibilityReviewJpaEntity entity = repository.findById(review.id())
                .map(existing -> {
                    existing.updateFrom(review);
                    return existing;
                })
                .orElseGet(() -> new PartnerEligibilityReviewJpaEntity(review));
        return repository.saveAndFlush(entity).toDomain();
    }
}
