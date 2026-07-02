package com.meridian.platform.approval.infrastructure.adapter.out.event;

import com.meridian.platform.approval.application.event.ApprovalDecisionRecordedEvent;
import com.meridian.platform.approval.application.port.out.ApprovalDecisionEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class SpringApprovalDecisionEventPublisher implements ApprovalDecisionEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public SpringApprovalDecisionEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public void publish(ApprovalDecisionRecordedEvent event) {
        applicationEventPublisher.publishEvent(event);
    }
}
