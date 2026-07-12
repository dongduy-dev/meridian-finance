package com.meridian.platform.audit.application.port.in;

import com.meridian.platform.shared.application.audit.BusinessAuditEvent;

public interface RecordAuditEventsUseCase {

    void record(BusinessAuditEvent event);
}
