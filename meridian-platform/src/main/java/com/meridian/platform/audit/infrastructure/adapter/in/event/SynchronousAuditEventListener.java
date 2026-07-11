package com.meridian.platform.audit.infrastructure.adapter.in.event;

import com.meridian.platform.audit.application.port.in.RecordAuditEventUseCase;
import com.meridian.platform.shared.application.audit.AuditRecordRequestedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class SynchronousAuditEventListener {

    private final RecordAuditEventUseCase recordAuditEventUseCase;

    public SynchronousAuditEventListener(RecordAuditEventUseCase recordAuditEventUseCase) {
        this.recordAuditEventUseCase = recordAuditEventUseCase;
    }

    @EventListener
    public void onAuditRecordRequested(AuditRecordRequestedEvent event) {
        recordAuditEventUseCase.record(event);
    }
}
