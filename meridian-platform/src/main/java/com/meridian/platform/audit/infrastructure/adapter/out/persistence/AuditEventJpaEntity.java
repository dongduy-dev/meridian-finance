package com.meridian.platform.audit.infrastructure.adapter.out.persistence;

import com.meridian.platform.audit.domain.model.AuditEvent;
import com.meridian.platform.shared.domain.audit.BusinessAuditAction;
import com.meridian.platform.shared.domain.audit.BusinessAuditEntityType;
import com.meridian.platform.shared.domain.audit.BusinessAuditPayload;
import com.meridian.platform.shared.domain.model.ActorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "audit_events")
public class AuditEventJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "operation_id", nullable = false)
    private UUID operationId;

    @Column(name = "sequence_number", nullable = false)
    private short sequenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false)
    private ActorType actorType;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false)
    private BusinessAuditEntityType entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false)
    private BusinessAuditAction action;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private Map<String, String> payload = new LinkedHashMap<>();

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected AuditEventJpaEntity() {
    }

    public AuditEventJpaEntity(AuditEvent auditEvent) {
        this.id = auditEvent.id();
        this.operationId = auditEvent.operationId();
        this.sequenceNumber = toSmallInt(auditEvent.sequenceNumber());
        this.actorType = auditEvent.actorType();
        this.actorUserId = auditEvent.actorUserId();
        this.entityType = auditEvent.entityType();
        this.entityId = auditEvent.entityId();
        this.action = auditEvent.action();
        this.payload = new LinkedHashMap<>(auditEvent.payload().values());
        this.occurredAt = auditEvent.occurredAt();
    }

    private short toSmallInt(int value) {
        if (value > Short.MAX_VALUE) {
            throw new IllegalArgumentException("sequenceNumber exceeds smallint range.");
        }
        return (short) value;
    }

    public AuditEvent toDomain() {
        return new AuditEvent(
                id,
                operationId,
                sequenceNumber,
                actorType,
                actorUserId,
                entityType,
                entityId,
                action,
                BusinessAuditPayload.fromStored(payload),
                occurredAt
        );
    }
}
