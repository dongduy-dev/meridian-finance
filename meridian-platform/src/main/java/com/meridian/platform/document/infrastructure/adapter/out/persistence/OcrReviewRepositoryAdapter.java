package com.meridian.platform.document.infrastructure.adapter.out.persistence;

import com.meridian.platform.document.application.port.out.OcrReviewRepository;
import com.meridian.platform.document.domain.model.OcrReview;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class OcrReviewRepositoryAdapter implements OcrReviewRepository {

    private final JpaOcrReviewRepository reviews;

    public OcrReviewRepositoryAdapter(JpaOcrReviewRepository reviews) {
        this.reviews = reviews;
    }

    @Override
    public Optional<OcrReview> findByOcrResultId(UUID resultId) {
        return reviews.findByOcrResultId(resultId).map(OcrReviewJpaEntity::toDomain);
    }

    @Override
    public OcrReview save(OcrReview review) {
        return reviews.saveAndFlush(new OcrReviewJpaEntity(review)).toDomain();
    }
}
