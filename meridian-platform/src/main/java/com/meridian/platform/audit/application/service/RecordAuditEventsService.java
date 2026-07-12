package com.meridian.platform.audit.application.service;

import com.meridian.platform.audit.application.port.in.RecordAuditEventsUseCase;
import com.meridian.platform.audit.application.port.out.AuditEventRepository;
import com.meridian.platform.audit.domain.model.AuditEvent;
import com.meridian.platform.shared.application.audit.BusinessAuditEntry;
import com.meridian.platform.shared.application.audit.BusinessAuditEvent;
import com.meridian.platform.shared.application.operation.BusinessOperationContext;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class RecordAuditEventsService implements RecordAuditEventsUseCase {

    private static final int MAX_PAYLOAD_BYTES = 2048;

    private final AuditEventRepository auditEventRepository;

    public RecordAuditEventsService(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(BusinessAuditEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        BusinessOperationContext context = event.operationContext();
        int sequenceNumber = auditEventRepository.nextSequenceNumber(context.operationId());

        for (BusinessAuditEntry entry : event.entries()) {
            validatePayloadSize(entry);
            auditEventRepository.save(new AuditEvent(
                    UUID.randomUUID(),
                    context.operationId(),
                    sequenceNumber++,
                    context.actorType(),
                    context.actorUserId(),
                    entry.entityType(),
                    entry.entityId(),
                    entry.action(),
                    entry.payload(),
                    context.occurredAt()
            ));
        }
    }

    private void validatePayloadSize(BusinessAuditEntry entry) {
        int estimatedBytes = estimateJsonObjectBytes(entry.payload().values());
        if (estimatedBytes > MAX_PAYLOAD_BYTES) {
            throw new BusinessRuleViolationException(
                    "AUDIT_PAYLOAD_TOO_LARGE",
                    "Audit payload exceeds the maximum safe size."
            );
        }
    }

    private int estimateJsonObjectBytes(Map<String, String> values) {
        int bytes = 2;
        boolean first = true;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (!first) {
                bytes += 1;
            }
            first = false;
            bytes += quotedUtf8Bytes(entry.getKey());
            bytes += 1;
            bytes += quotedUtf8Bytes(entry.getValue());
        }
        return bytes;
    }

    private int quotedUtf8Bytes(String value) {
        int bytes = 2;
        bytes += value.getBytes(StandardCharsets.UTF_8).length;
        return bytes;
    }
}