package com.meridian.platform.partner.infrastructure.adapter.out.persistence;

import com.meridian.platform.partner.domain.model.PartnerEligibilityReviewStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaPartnerEligibilityReviewRepository
        extends JpaRepository<PartnerEligibilityReviewJpaEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select review from PartnerEligibilityReviewJpaEntity review where review.id = :reviewId")
    Optional<PartnerEligibilityReviewJpaEntity> findByIdForUpdate(@Param("reviewId") UUID reviewId);

    @Query(value = """
            select customer_id as customerId, partner_company_id as partnerCompanyId
            from partner_eligibility_reviews
            where id = :reviewId
            """, nativeQuery = true)
    Optional<ReviewLockIdentityProjection> findLockIdentityById(@Param("reviewId") UUID reviewId);

    Optional<PartnerEligibilityReviewJpaEntity>
            findFirstByCustomerIdAndPartnerCompanyIdAndStatus(
                    UUID customerId,
                    UUID partnerCompanyId,
                    PartnerEligibilityReviewStatus status
            );

    @Query(value = """
            select latest.*
            from (
                select distinct on (review.partner_company_id) review.*
                from partner_eligibility_reviews review
                where review.customer_id = :customerId
                  and review.effective_month = :effectiveMonth
                order by review.partner_company_id, review.created_at desc, review.id desc
            ) latest
            where latest.status <> 'SUPERSEDED'
            order by latest.partner_company_id
            """, nativeQuery = true)
    List<PartnerEligibilityReviewJpaEntity> findCurrentLatestByCustomerIdAndEffectiveMonth(
            @Param("customerId") UUID customerId,
            @Param("effectiveMonth") String effectiveMonth
    );

    Page<PartnerEligibilityReviewJpaEntity> findByStatusOrderByCreatedAtAscIdAsc(
            PartnerEligibilityReviewStatus status,
            Pageable pageable
    );

    @Query(value = "select pg_advisory_xact_lock(hashtextextended(cast(:lockKey as text), 0))", nativeQuery = true)
    void acquireCustomerPartnerLock(@Param("lockKey") String lockKey);

    interface ReviewLockIdentityProjection {
        UUID getCustomerId();

        UUID getPartnerCompanyId();
    }
}
