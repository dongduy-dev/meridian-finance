package com.meridian.platform.document.infrastructure.adapter.out.persistence;

import com.meridian.platform.document.domain.model.DocumentChecklistItem;
import com.meridian.platform.document.domain.model.DocumentRequirementStatus;
import com.meridian.platform.document.domain.model.DocumentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "document_checklist_items")
public class DocumentChecklistItemJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "checklist_id", nullable = false)
    private UUID checklistId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false)
    private DocumentType documentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "requirement_status", nullable = false)
    private DocumentRequirementStatus requirementStatus;

    @Column(name = "current_review_decision_id")
    private UUID currentReviewDecisionId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected DocumentChecklistItemJpaEntity() {
    }

    public DocumentChecklistItemJpaEntity(DocumentChecklistItem item) {
        this.id = item.id();
        apply(item);
    }

    public void updateFrom(DocumentChecklistItem item) {
        apply(item);
    }

    private void apply(DocumentChecklistItem item) {
        this.checklistId = item.checklistId();
        this.documentType = item.documentType();
        this.requirementStatus = item.requirementStatus();
        this.currentReviewDecisionId = item.currentReviewDecisionId();
        this.createdAt = item.createdAt();
        this.updatedAt = item.updatedAt();
    }

    public DocumentChecklistItem toDomain() {
        return new DocumentChecklistItem(
                id,
                checklistId,
                documentType,
                requirementStatus,
                currentReviewDecisionId,
                createdAt,
                updatedAt
        );
    }

    public UUID getId() {
        return id;
    }

    public UUID getChecklistId() {
        return checklistId;
    }

    public DocumentRequirementStatus getRequirementStatus() {
        return requirementStatus;
    }

    public UUID getCurrentReviewDecisionId() {
        return currentReviewDecisionId;
    }
}
