package com.meridian.platform.audit.infrastructure.adapter.out.persistence;

import com.meridian.platform.audit.application.port.out.AuditEventRepository;
import com.meridian.platform.audit.domain.model.AuditEvent;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class AuditEventRepositoryAdapter implements AuditEventRepository {

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
}
