package com.meridian.platform.approval.application.port.out;

import com.meridian.platform.approval.application.event.ReviewRecommendationRecordedEvent;

public interface ReviewRecommendationEventPublisher {

    void publish(ReviewRecommendationRecordedEvent event);
}
