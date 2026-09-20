package com.meridian.platform.document.infrastructure.adapter.out.persistence;

import com.meridian.platform.document.domain.model.OcrResult;
import com.meridian.platform.document.domain.model.OcrResultDisposition;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ocr_results")
public class OcrResultJpaEntity {

    @Id private UUID id;
    @Column(name = "ocr_job_id", nullable = false, unique = true) private UUID ocrJobId;
    @Column(name = "provider", nullable = false) private String provider;
    @Column(name = "processor_name", nullable = false) private String processorName;
    @Column(name = "processor_version") private String processorVersion;
    @Column(name = "encrypted_extracted_text", nullable = false) private String encryptedExtractedText;
    @Column(name = "encrypted_normalized_payload", nullable = false) private String encryptedNormalizedPayload;
    @Column(name = "encrypted_structured_suggestions", nullable = false) private String encryptedStructuredSuggestions;
    @Column(name = "normalized_confidence") private java.math.BigDecimal normalizedConfidence;
    @Enumerated(EnumType.STRING)
    @Column(name = "disposition", nullable = false) private OcrResultDisposition disposition;
    @Column(name = "processing_duration_ms", nullable = false) private long processingDurationMs;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;

    protected OcrResultJpaEntity() {
    }

    OcrResult toDomain() {
        return new OcrResult(id, ocrJobId, encryptedStructuredSuggestions, disposition, createdAt);
    }

    void update(OcrResult result) {
        disposition = result.disposition();
    }
}
