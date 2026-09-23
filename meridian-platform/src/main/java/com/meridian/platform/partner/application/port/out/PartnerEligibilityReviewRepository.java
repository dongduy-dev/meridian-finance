package com.meridian.platform.partner.application.port.out;

import com.meridian.platform.partner.domain.model.PartnerEligibilityReview;
import com.meridian.platform.partner.domain.model.PartnerEligibilityReviewStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PartnerEligibilityReviewRepository {

    record LockIdentity(UUID customerId, UUID partnerCompanyId) {
    }

    record Page(int page, int size, long totalElements, int totalPages, List<PartnerEligibilityReview> reviews) {
        public Page {
            reviews = List.copyOf(reviews);
        }
    }

    void acquireCustomerPartnerLock(UUID customerId, UUID partnerCompanyId);

    Optional<PartnerEligibilityReview> findById(UUID reviewId);

    default Optional<LockIdentity> findLockIdentityById(UUID reviewId) {
        return findById(reviewId).map(review -> new LockIdentity(
                review.customerId(), review.partnerCompanyId()
        ));
    }

    Optional<PartnerEligibilityReview> findByIdForUpdate(UUID reviewId);

    Optional<PartnerEligibilityReview> findPendingByCustomerIdAndPartnerCompanyId(
            UUID customerId,
            UUID partnerCompanyId
    );

    Page findPage(PartnerEligibilityReviewStatus status, int page, int size);

    PartnerEligibilityReview save(PartnerEligibilityReview review);
}
