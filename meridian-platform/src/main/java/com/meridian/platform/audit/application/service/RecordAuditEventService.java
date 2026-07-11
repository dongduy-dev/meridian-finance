package com.meridian.platform.audit.application.service;

import com.meridian.platform.audit.application.port.in.RecordAuditEventUseCase;
import com.meridian.platform.audit.application.port.out.AuditEventRepository;
import com.meridian.platform.audit.domain.model.AuditEvent;
import com.meridian.platform.audit.domain.model.AuditEventPayloadEntry;
import com.meridian.platform.shared.application.audit.AuditPayloadEntry;
import com.meridian.platform.shared.application.audit.AuditRecordRequestedEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RecordAuditEventService implements RecordAuditEventUseCase {

    private final AuditEventRepository auditEventRepository;

    public RecordAuditEventService(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @Override
    @Transactional
    public void record(AuditRecordRequestedEvent event) {
        auditEventRepository.save(AuditEvent.from(
                event.operationId(), event.sequenceNumber(), event.actor(), event.entityType().name(), event.entityId(),
                event.action().name(), toDomainPayload(event.payload()), event.occurredAt()
        ));
    }

    private List<AuditEventPayloadEntry> toDomainPayload(List<AuditPayloadEntry> payload) {
        return payload.stream()
                .map(entry -> new AuditEventPayloadEntry(entry.key().name(), entry.value()))
                .toList();
    }
}
