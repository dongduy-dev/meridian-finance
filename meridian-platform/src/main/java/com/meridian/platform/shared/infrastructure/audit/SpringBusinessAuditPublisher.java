package com.meridian.platform.shared.infrastructure.audit;

import com.meridian.platform.shared.application.audit.BusinessAuditEvent;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class SpringBusinessAuditPublisher implements BusinessAuditPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public SpringBusinessAuditPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public void publish(BusinessAuditEvent event) {
        applicationEventPublisher.publishEvent(event);
    }
}
