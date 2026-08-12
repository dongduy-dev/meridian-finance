package com.meridian.platform.audit.infrastructure.adapter.out.persistence;

import com.meridian.platform.audit.application.port.out.AuditEventRepository;
import com.meridian.platform.audit.domain.model.AuditEvent;
import com.meridian.platform.shared.application.audit.BusinessAuditEvidenceReader;
import com.meridian.platform.shared.domain.audit.BusinessAuditAction;
import com.meridian.platform.shared.domain.audit.BusinessAuditEntityType;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Repository
public class AuditEventRepositoryAdapter
        implements AuditEventRepository, BusinessAuditEvidenceReader {

    private final JpaAuditEventRepository jpaAuditEventRepository;

    public AuditEventRepositoryAdapter(JpaAuditEventRepository jpaAuditEventRepository) {
        this.jpaAuditEventRepository = jpaAuditEventRepository;
    }

    @Override
    public int nextSequenceNumber(UUID operationId) {
        return jpaAuditEventRepository.nextSequenceNumber(operationId);
    }

    @Override
    public AuditEvent save(AuditEvent auditEvent) {
        return jpaAuditEventRepository.save(new AuditEventJpaEntity(auditEvent)).toDomain();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public long countMatching(
            BusinessAuditAction action,
            BusinessAuditEntityType entityType,
            UUID entityId
    ) {
        return jpaAuditEventRepository.countByActionAndEntityTypeAndEntityId(
                action,
                entityType,
                entityId
        );
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public long countMatchingOperation(
            UUID operationId,
            BusinessAuditAction action,
            BusinessAuditEntityType entityType,
            UUID entityId
    ) {
        return jpaAuditEventRepository
                .countByOperationIdAndActionAndEntityTypeAndEntityId(
                        operationId,
                        action,
                        entityType,
                        entityId
                );
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public long countMatchingOperationAction(
            UUID operationId,
            BusinessAuditAction action
    ) {
        return jpaAuditEventRepository.countByOperationIdAndAction(operationId, action);
    }
}
