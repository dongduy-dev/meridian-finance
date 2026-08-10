package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.domain.model.LoanApplicationCancellation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "loan_application_cancellations")
public class LoanApplicationCancellationJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "loan_application_id", nullable = false, updatable = false)
    private UUID loanApplicationId;

    @Column(name = "correction_request_id", nullable = false, updatable = false)
    private UUID correctionRequestId;

    @Column(name = "reservation_release_movement_id", nullable = false, updatable = false)
    private UUID reservationReleaseMovementId;

    @Column(name = "request_id", nullable = false, updatable = false)
    private UUID requestId;

    @Column(name = "cancelled_by_user_id", nullable = false, updatable = false)
    private UUID cancelledByUserId;

    @Column(name = "cancelled_at", nullable = false, updatable = false)
    private LocalDateTime cancelledAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected LoanApplicationCancellationJpaEntity() {
    }

    public LoanApplicationCancellation toDomain() {
        return new LoanApplicationCancellation(
                id,
                loanApplicationId,
                correctionRequestId,
                reservationReleaseMovementId,
                requestId,
                cancelledByUserId,
                cancelledAt
        );
    }
}
