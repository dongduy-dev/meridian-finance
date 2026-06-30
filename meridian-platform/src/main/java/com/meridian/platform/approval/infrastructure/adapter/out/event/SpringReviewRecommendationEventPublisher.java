package com.meridian.platform.approval.infrastructure.adapter.out.event;

import com.meridian.platform.approval.application.event.ReviewRecommendationRecordedEvent;
import com.meridian.platform.approval.application.port.out.ReviewRecommendationEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class SpringReviewRecommendationEventPublisher implements ReviewRecommendationEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public SpringReviewRecommendationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public void publish(ReviewRecommendationRecordedEvent event) {
        applicationEventPublisher.publishEvent(event);
    }
}
