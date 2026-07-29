package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.domain.model.RepaymentInstallmentServicingAction;
import com.meridian.platform.loan.domain.model.RepaymentInstallmentStatus;
import com.meridian.platform.loan.domain.model.RepaymentInstallmentStatusTransition;
import com.meridian.platform.shared.domain.model.ActorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "repayment_installment_status_transitions")
public class RepaymentInstallmentStatusTransitionJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "repayment_schedule_item_id", nullable = false, updatable = false)
    private UUID repaymentScheduleItemId;

    @Column(name = "sequence_number", nullable = false, updatable = false)
    private int sequenceNumber;

    @Column(name = "operation_id", nullable = false, updatable = false)
    private UUID operationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", updatable = false)
    private RepaymentInstallmentStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, updatable = false)
    private RepaymentInstallmentStatus toStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, updatable = false)
    private RepaymentInstallmentServicingAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, updatable = false)
    private ActorType actorType;

    @Column(name = "actor_user_id", updatable = false)
    private UUID actorUserId;

    @Column(name = "servicing_evaluation_date", nullable = false, updatable = false)
    private LocalDate servicingEvaluationDate;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    @Column(name = "created_at",
            nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected RepaymentInstallmentStatusTransitionJpaEntity() {
    }

    public RepaymentInstallmentStatusTransitionJpaEntity(
            RepaymentInstallmentStatusTransition transition
    ) {
        this.id = transition.id();
        this.repaymentScheduleItemId = transition.repaymentScheduleItemId();
        this.sequenceNumber = transition.sequenceNumber();
        this.operationId = transition.operationId();
        this.fromStatus = transition.fromStatus();
        this.toStatus = transition.toStatus();
        this.action = transition.action();
        this.actorType = transition.actorType();
        this.actorUserId = transition.actorUserId();
        this.servicingEvaluationDate = transition.servicingEvaluationDate();
        this.occurredAt = transition.occurredAt();
    }

    public RepaymentInstallmentStatusTransition toDomain() {
        return new RepaymentInstallmentStatusTransition(
                id,
                repaymentScheduleItemId,
                sequenceNumber,
                operationId,
                fromStatus,
                toStatus,
                action,
                actorType,
                actorUserId,
                servicingEvaluationDate,
                occurredAt
        );
    }
}
