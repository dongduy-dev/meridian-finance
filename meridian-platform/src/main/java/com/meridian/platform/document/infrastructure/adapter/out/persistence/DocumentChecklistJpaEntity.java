package com.meridian.platform.document.infrastructure.adapter.out.persistence;

import com.meridian.platform.document.domain.model.DocumentChecklist;
import com.meridian.platform.document.domain.model.DocumentChecklistStage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "document_checklists")
public class DocumentChecklistJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "loan_application_id", nullable = false)
    private UUID loanApplicationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", nullable = false)
    private DocumentChecklistStage stage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected DocumentChecklistJpaEntity() {
    }

    public DocumentChecklistJpaEntity(DocumentChecklist checklist) {
        this.id = checklist.id();
        this.loanApplicationId = checklist.loanApplicationId();
        this.stage = checklist.stage();
        this.createdAt = checklist.createdAt();
    }

    public UUID getId() {
        return id;
    }

    public UUID getLoanApplicationId() {
        return loanApplicationId;
    }

    public DocumentChecklistStage getStage() {
        return stage;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
