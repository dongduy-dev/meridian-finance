package com.meridian.platform.document.infrastructure.adapter.out.persistence;

import com.meridian.platform.document.domain.model.AssistedActionDocumentVersion;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "assisted_action_document_versions")
public class AssistedActionDocumentVersionJpaEntity {
    @Id private UUID id;
    @Column(name = "assisted_action_document_id", nullable = false) private UUID assistedActionDocumentId;
    @Column(name = "version_number", nullable = false) private int versionNumber;
    @Column(name = "upload_request_id", nullable = false) private UUID uploadRequestId;
    @Column(name = "baseline_version_id") private UUID baselineVersionId;
    @Column(name = "original_filename", nullable = false) private String originalFilename;
    @Column(name = "declared_mime_type", nullable = false) private String declaredMimeType;
    @Column(name = "detected_mime_type", nullable = false) private String detectedMimeType;
    @Column(name = "byte_size", nullable = false) private long byteSize;
    @Column(name = "sha256_hex", nullable = false) private String sha256Hex;
    @Column(name = "storage_key", nullable = false) private String storageKey;
    @Column(name = "uploader_staff_user_id", nullable = false) private UUID uploaderStaffUserId;
    @Column(name = "uploaded_at", nullable = false) private LocalDateTime uploadedAt;

    protected AssistedActionDocumentVersionJpaEntity() {}
    AssistedActionDocumentVersionJpaEntity(AssistedActionDocumentVersion value) {
        id = value.id(); assistedActionDocumentId = value.assistedActionDocumentId();
        versionNumber = value.versionNumber(); uploadRequestId = value.uploadRequestId();
        baselineVersionId = value.baselineVersionId(); originalFilename = value.originalFilename();
        declaredMimeType = value.declaredMimeType(); detectedMimeType = value.detectedMimeType();
        byteSize = value.byteSize(); sha256Hex = value.sha256Hex(); storageKey = value.storageKey();
        uploaderStaffUserId = value.uploaderStaffUserId(); uploadedAt = value.uploadedAt();
    }

    AssistedActionDocumentVersion toDomain() {
        return new AssistedActionDocumentVersion(id, assistedActionDocumentId, versionNumber, uploadRequestId,
                baselineVersionId, originalFilename, declaredMimeType, detectedMimeType, byteSize, sha256Hex,
                storageKey, uploaderStaffUserId, uploadedAt);
    }
}
