package com.meridian.platform.audit.application.port.out;

import com.meridian.platform.audit.domain.model.AuditEvent;

import java.util.UUID;

public interface AuditEventRepository {

    int nextSequenceNumber(UUID operationId);

    AuditEvent save(AuditEvent auditEvent);
}
