package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.LoanApplicationStatusTransition;
import com.meridian.platform.loan.domain.model.LoanApplicationTransitionAction;
import com.meridian.platform.shared.domain.model.ActorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "loan_application_status_transitions")
public class LoanApplicationStatusTransitionJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "loan_application_id", nullable = false)
    private UUID loanApplicationId;

    @Column(name = "operation_id", nullable = false)
    private UUID operationId;

    @Column(name = "sequence_number", nullable = false)
    private short sequenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status")
    private LoanApplicationStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private LoanApplicationStatus toStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false)
    private LoanApplicationTransitionAction action;

    @Column(name = "reason")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false)
    private ActorType actorType;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected LoanApplicationStatusTransitionJpaEntity() {
    }

    public LoanApplicationStatusTransitionJpaEntity(LoanApplicationStatusTransition transition) {
        this.id = transition.id();
        this.loanApplicationId = transition.loanApplicationId();
        this.operationId = transition.operationId();
        this.sequenceNumber = toSmallInt(transition.sequenceNumber());
        this.fromStatus = transition.fromStatus();
        this.toStatus = transition.toStatus();
        this.action = transition.action();
        this.reason = transition.reason();
        this.actorType = transition.actorType();
        this.actorUserId = transition.actorUserId();
        this.occurredAt = transition.occurredAt();
        this.createdAt = LocalDateTime.now();
    }

    private short toSmallInt(int value) {
        if (value > Short.MAX_VALUE) {
            throw new IllegalArgumentException("sequenceNumber exceeds smallint range.");
        }
        return (short) value;
    }

    public LoanApplicationStatusTransition toDomain() {
        return new LoanApplicationStatusTransition(
                id,
                loanApplicationId,
                operationId,
                sequenceNumber,
                fromStatus,
                toStatus,
                action,
                reason,
                actorType,
                actorUserId,
                occurredAt
        );
    }
}
