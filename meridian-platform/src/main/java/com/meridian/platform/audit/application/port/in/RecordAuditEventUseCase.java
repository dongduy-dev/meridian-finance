package com.meridian.platform.audit.application.port.in;

import com.meridian.platform.shared.application.audit.AuditRecordRequestedEvent;

public interface RecordAuditEventUseCase {

    void record(AuditRecordRequestedEvent event);
}
