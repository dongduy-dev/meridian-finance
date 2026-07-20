package com.meridian.platform.document.infrastructure.adapter.out.persistence;

import com.meridian.platform.document.domain.model.StoredDocument;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "documents")
public class DocumentJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "checklist_item_id", nullable = false)
    private UUID checklistItemId;

    @Column(name = "current_version_id")
    private UUID currentVersionId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected DocumentJpaEntity() {
    }

    public DocumentJpaEntity(StoredDocument document) {
        this.id = document.id();
        apply(document);
    }

    public void updateFrom(StoredDocument document) {
        apply(document);
    }

    private void apply(StoredDocument document) {
        this.checklistItemId = document.checklistItemId();
        this.currentVersionId = document.currentVersionId();
        this.createdAt = document.createdAt();
        this.updatedAt = document.updatedAt();
    }

    public StoredDocument toDomain() {
        return new StoredDocument(id, checklistItemId, currentVersionId, createdAt, updatedAt);
    }

    public UUID getId() {
        return id;
    }

    public UUID getChecklistItemId() {
        return checklistItemId;
    }

    public UUID getCurrentVersionId() {
        return currentVersionId;
    }
}
