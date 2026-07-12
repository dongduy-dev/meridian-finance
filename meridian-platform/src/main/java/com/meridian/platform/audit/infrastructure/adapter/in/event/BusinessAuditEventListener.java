package com.meridian.platform.audit.infrastructure.adapter.in.event;

import com.meridian.platform.audit.application.port.in.RecordAuditEventsUseCase;
import com.meridian.platform.shared.application.audit.BusinessAuditEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class BusinessAuditEventListener {

    private final RecordAuditEventsUseCase recordAuditEventsUseCase;

    public BusinessAuditEventListener(RecordAuditEventsUseCase recordAuditEventsUseCase) {
        this.recordAuditEventsUseCase = recordAuditEventsUseCase;
    }

    @EventListener
    public void onBusinessAuditEvent(BusinessAuditEvent event) {
        recordAuditEventsUseCase.record(event);
    }
}
