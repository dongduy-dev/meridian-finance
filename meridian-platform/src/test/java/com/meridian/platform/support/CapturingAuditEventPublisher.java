package com.meridian.platform.support;

import com.meridian.platform.shared.application.audit.AuditEventPublisher;
import com.meridian.platform.shared.application.audit.AuditRecordRequestedEvent;

import java.util.ArrayList;
import java.util.List;

public class CapturingAuditEventPublisher implements AuditEventPublisher {

    private final List<AuditRecordRequestedEvent> events = new ArrayList<>();
    private RuntimeException failure;

    @Override
    public void publish(AuditRecordRequestedEvent event) {
        if (failure != null) {
            throw failure;
        }
        events.add(event);
    }

    public List<AuditRecordRequestedEvent> events() {
        return events;
    }

    public void failWith(RuntimeException failure) {
        this.failure = failure;
    }
}
