package com.meridian.platform.shared.application.audit;

import com.meridian.platform.shared.domain.audit.BusinessAuditAction;
import com.meridian.platform.shared.domain.audit.BusinessAuditEntityType;

import java.util.UUID;

public interface BusinessAuditEvidenceReader {

    long countMatching(
            BusinessAuditAction action,
            BusinessAuditEntityType entityType,
            UUID entityId
    );

    long countMatchingOperation(
            UUID operationId,
            BusinessAuditAction action,
            BusinessAuditEntityType entityType,
            UUID entityId
    );

    long countMatchingOperationAction(
            UUID operationId,
            BusinessAuditAction action
    );
}
