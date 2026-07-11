package com.meridian.platform.shared.application.audit;

public interface AuditEventPublisher {

    void publish(AuditRecordRequestedEvent event);
}
