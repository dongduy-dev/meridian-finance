package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaApprovedOfferRepository extends JpaRepository<ApprovedOfferJpaEntity, UUID> {

    Optional<ApprovedOfferJpaEntity> findByLoanApplicationId(UUID loanApplicationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select approvedOffer from ApprovedOfferJpaEntity approvedOffer where approvedOffer.loanApplicationId = :loanApplicationId")
    Optional<ApprovedOfferJpaEntity> findByLoanApplicationIdForUpdate(
            @Param("loanApplicationId") UUID loanApplicationId
    );

    @Query(value = """
            SELECT loan_application_id
            FROM approved_offers
            WHERE status = 'PENDING'
              AND expires_at <= :now
            ORDER BY expires_at, id
            LIMIT :batchSize
            """, nativeQuery = true)
    List<UUID> findExpiredPendingLoanApplicationIds(
            @Param("now") LocalDateTime now,
            @Param("batchSize") int batchSize
    );
}
