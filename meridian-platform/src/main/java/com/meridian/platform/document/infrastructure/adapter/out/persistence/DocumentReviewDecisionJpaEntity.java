package com.meridian.platform.document.infrastructure.adapter.out.persistence;

import com.meridian.platform.document.domain.model.DocumentReviewDecision;
import com.meridian.platform.document.domain.model.DocumentReviewOutcome;
import com.meridian.platform.document.domain.model.DocumentWaiverReasonCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "document_review_decisions")
public class DocumentReviewDecisionJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "checklist_item_id", nullable = false)
    private UUID checklistItemId;

    @Column(name = "document_version_id", nullable = false)
    private UUID documentVersionId;

    @Column(name = "review_request_id", nullable = false)
    private UUID reviewRequestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false)
    private DocumentReviewOutcome outcome;

    @Enumerated(EnumType.STRING)
    @Column(name = "waiver_reason_code")
    private DocumentWaiverReasonCode waiverReasonCode;

    @Column(name = "correction_reason_code")
    private String correctionReasonCode;

    @Column(name = "customer_instruction")
    private String customerInstruction;

    @Column(name = "restricted_staff_notes")
    private String restrictedStaffNotes;

    @Column(name = "reviewer_user_id", nullable = false)
    private UUID reviewerUserId;

    @Column(name = "decided_at", nullable = false)
    private LocalDateTime decidedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected DocumentReviewDecisionJpaEntity() {
    }

    public DocumentReviewDecisionJpaEntity(DocumentReviewDecision decision) {
        this.id = decision.id();
        this.checklistItemId = decision.checklistItemId();
        this.documentVersionId = decision.documentVersionId();
        this.reviewRequestId = decision.reviewRequestId();
        this.outcome = decision.outcome();
        this.waiverReasonCode = decision.waiverReasonCode();
        this.correctionReasonCode = decision.correctionReasonCode();
        this.customerInstruction = decision.customerInstruction();
        this.restrictedStaffNotes = decision.restrictedStaffNotes();
        this.reviewerUserId = decision.reviewerUserId();
        this.decidedAt = decision.decidedAt();
        this.createdAt = decision.decidedAt();
    }

    public DocumentReviewDecision toDomain() {
        return new DocumentReviewDecision(
                id,
                checklistItemId,
                documentVersionId,
                reviewRequestId,
                outcome,
                waiverReasonCode,
                correctionReasonCode,
                customerInstruction,
                restrictedStaffNotes,
                reviewerUserId,
                decidedAt
        );
    }

    public UUID getId() {
        return id;
    }
}
