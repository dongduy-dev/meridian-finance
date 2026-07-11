package com.meridian.platform.audit.infrastructure.adapter.out.persistence;

import com.meridian.platform.audit.application.port.out.AuditEventRepository;
import com.meridian.platform.audit.domain.model.AuditEvent;
import org.springframework.stereotype.Repository;

@Repository
public class AuditEventRepositoryAdapter implements AuditEventRepository {

    private final JpaAuditEventRepository repository;
    private final AuditPayloadJsonSerializer payloadJsonSerializer;

    public AuditEventRepositoryAdapter(
            JpaAuditEventRepository repository,
            AuditPayloadJsonSerializer payloadJsonSerializer
    ) {
        this.repository = repository;
        this.payloadJsonSerializer = payloadJsonSerializer;
    }

    @Override
    public void save(AuditEvent auditEvent) {
        repository.save(new AuditEventJpaEntity(auditEvent, payloadJsonSerializer.serialize(auditEvent.payload())));
    }
}
