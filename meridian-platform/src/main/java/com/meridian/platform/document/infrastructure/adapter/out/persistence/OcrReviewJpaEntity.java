package com.meridian.platform.document.infrastructure.adapter.out.persistence;

import com.meridian.platform.document.domain.model.OcrResultDisposition;
import com.meridian.platform.document.domain.model.OcrReview;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ocr_reviews")
public class OcrReviewJpaEntity {

    @Id private UUID id;
    @Column(name = "ocr_result_id", nullable = false, unique = true) private UUID ocrResultId;
    @Column(name = "reviewer_staff_user_id", nullable = false) private UUID reviewerStaffUserId;
    @Enumerated(EnumType.STRING)
    @Column(name = "source_disposition", nullable = false) private OcrResultDisposition sourceDisposition;
    @Column(name = "encrypted_reviewed_fields", nullable = false) private String encryptedReviewedFields;
    @Column(name = "reviewed_at", nullable = false) private LocalDateTime reviewedAt;

    protected OcrReviewJpaEntity() {
    }

    OcrReviewJpaEntity(OcrReview review) {
        id = review.id();
        ocrResultId = review.ocrResultId();
        reviewerStaffUserId = review.reviewerStaffUserId();
        sourceDisposition = review.sourceDisposition();
        encryptedReviewedFields = review.encryptedReviewedFields();
        reviewedAt = review.reviewedAt();
    }

    OcrReview toDomain() {
        return new OcrReview(id, ocrResultId, reviewerStaffUserId, sourceDisposition,
                encryptedReviewedFields, reviewedAt);
    }
}
