package com.meridian.platform.approval.application.port.out;

import com.meridian.platform.approval.domain.model.ReviewRecommendation;

public interface ReviewRecommendationRepository {

    ReviewRecommendation save(ReviewRecommendation recommendation);
}
