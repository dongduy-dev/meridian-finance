package com.meridian.platform.document.infrastructure.adapter.out.event;

import com.meridian.platform.document.application.event.DocumentUploadsCompletedEvent;
import com.meridian.platform.document.application.port.out.DocumentWorkflowEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class SpringDocumentWorkflowEventPublisher implements DocumentWorkflowEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    public SpringDocumentWorkflowEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void publish(DocumentUploadsCompletedEvent event) {
        eventPublisher.publishEvent(event);
    }
}
