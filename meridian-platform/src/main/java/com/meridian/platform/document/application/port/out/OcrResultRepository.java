package com.meridian.platform.document.application.port.out;

import com.meridian.platform.document.domain.model.OcrResult;

import java.util.Optional;
import java.util.UUID;

public interface OcrResultRepository {

    Optional<OcrResult> findByJobId(UUID jobId);

    Optional<OcrResult> findByIdForUpdate(UUID resultId);

    OcrResult save(OcrResult result);
}
