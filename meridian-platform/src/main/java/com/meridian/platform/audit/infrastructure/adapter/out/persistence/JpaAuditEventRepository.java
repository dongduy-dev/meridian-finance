package com.meridian.platform.audit.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface JpaAuditEventRepository extends JpaRepository<AuditEventJpaEntity, UUID> {

    @Query("""
            select coalesce(max(auditEvent.sequenceNumber), 0) + 1
            from AuditEventJpaEntity auditEvent
            where auditEvent.operationId = :operationId
            """)
    int nextSequenceNumber(@Param("operationId") UUID operationId);
}
