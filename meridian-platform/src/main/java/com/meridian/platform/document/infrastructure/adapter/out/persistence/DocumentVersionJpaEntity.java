package com.meridian.platform.document.infrastructure.adapter.out.persistence;

import com.meridian.platform.document.domain.model.DocumentUploaderActorType;
import com.meridian.platform.document.domain.model.DocumentVersion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "document_versions")
public class DocumentVersionJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(name = "upload_request_id", nullable = false)
    private UUID uploadRequestId;

    @Column(name = "baseline_document_version_id")
    private UUID baselineDocumentVersionId;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "declared_mime_type", nullable = false)
    private String declaredMimeType;

    @Column(name = "detected_mime_type", nullable = false)
    private String detectedMimeType;

    @Column(name = "byte_size", nullable = false)
    private long byteSize;

    @Column(name = "sha256_hex", nullable = false, length = 64)
    private String sha256Hex;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "uploader_actor_type", nullable = false)
    private DocumentUploaderActorType uploaderActorType;

    @Column(name = "uploader_user_id", nullable = false)
    private UUID uploaderUserId;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected DocumentVersionJpaEntity() {
    }

    public DocumentVersionJpaEntity(DocumentVersion version) {
        this.id = version.id();
        this.documentId = version.documentId();
        this.versionNumber = version.versionNumber();
        this.uploadRequestId = version.uploadRequestId();
        this.baselineDocumentVersionId = version.baselineDocumentVersionId();
        this.originalFilename = version.originalFilename();
        this.declaredMimeType = version.declaredMimeType();
        this.detectedMimeType = version.detectedMimeType();
        this.byteSize = version.byteSize();
        this.sha256Hex = version.sha256Hex();
        this.storageKey = version.storageKey();
        this.uploaderActorType = version.uploaderActorType();
        this.uploaderUserId = version.uploaderUserId();
        this.uploadedAt = version.uploadedAt();
        this.createdAt = version.uploadedAt();
    }

    public DocumentVersion toDomain() {
        return new DocumentVersion(
                id,
                documentId,
                versionNumber,
                uploadRequestId,
                baselineDocumentVersionId,
                originalFilename,
                declaredMimeType,
                detectedMimeType,
                byteSize,
                sha256Hex,
                storageKey,
                uploaderActorType,
                uploaderUserId,
                uploadedAt
        );
    }

    public UUID getId() {
        return id;
    }

    public String getStorageKey() {
        return storageKey;
    }
}
