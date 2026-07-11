package com.meridian.platform.audit.infrastructure.adapter.out.persistence;

import com.meridian.platform.audit.domain.model.AuditEvent;
import com.meridian.platform.shared.domain.model.ActionActorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
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
    private ActionActorType actorType;
    @Column(name = "actor_user_id")
    private UUID actorUserId;
    @Column(name = "entity_type", nullable = false)
    private String entityType;
    @Column(name = "entity_id", nullable = false)
    private UUID entityId;
    @Column(name = "action", nullable = false)
    private String action;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;
    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected AuditEventJpaEntity() {
    }

    public AuditEventJpaEntity(AuditEvent event, String payload) {
        this.id = event.id();
        this.operationId = event.operationId();
        this.sequenceNumber = event.sequenceNumber();
        this.actorType = event.actor().type();
        this.actorUserId = event.actor().userId();
        this.entityType = event.entityType();
        this.entityId = event.entityId();
        this.action = event.action();
        this.payload = payload;
        this.occurredAt = event.occurredAt();
    }
}
