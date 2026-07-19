package com.meridian.platform.document.application.port.out;

import com.meridian.platform.document.application.event.DocumentUploadsCompletedEvent;

public interface DocumentWorkflowEventPublisher {
    void publish(DocumentUploadsCompletedEvent event);
}
