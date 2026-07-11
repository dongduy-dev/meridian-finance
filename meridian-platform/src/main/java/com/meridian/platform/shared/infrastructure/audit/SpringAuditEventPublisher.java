package com.meridian.platform.shared.infrastructure.audit;

import com.meridian.platform.shared.application.audit.AuditEventPublisher;
import com.meridian.platform.shared.application.audit.AuditRecordRequestedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class SpringAuditEventPublisher implements AuditEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public SpringAuditEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public void publish(AuditRecordRequestedEvent event) {
        applicationEventPublisher.publishEvent(event);
    }
}
