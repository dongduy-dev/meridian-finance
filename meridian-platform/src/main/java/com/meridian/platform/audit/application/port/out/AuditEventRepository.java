package com.meridian.platform.audit.application.port.out;

import com.meridian.platform.audit.domain.model.AuditEvent;

public interface AuditEventRepository {

    void save(AuditEvent auditEvent);
}
