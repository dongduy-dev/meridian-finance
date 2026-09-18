package com.meridian.platform.document.application.port.out;

import com.meridian.platform.document.domain.model.OcrJob;
import com.meridian.platform.document.domain.model.OcrResultDisposition;

import java.util.Optional;
import java.util.UUID;

public interface OcrJobRepository {

    OcrJob save(OcrJob job);

    Optional<OcrJob> findByIntakeDocumentVersionId(UUID versionId);

    Optional<OcrResultDisposition> findDispositionByJobId(UUID jobId);
}
