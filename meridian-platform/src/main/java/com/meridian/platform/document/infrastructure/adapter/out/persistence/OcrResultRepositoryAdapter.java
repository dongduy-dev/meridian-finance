package com.meridian.platform.document.infrastructure.adapter.out.persistence;

import com.meridian.platform.document.application.port.out.OcrResultRepository;
import com.meridian.platform.document.domain.model.OcrResult;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class OcrResultRepositoryAdapter implements OcrResultRepository {

    private final JpaOcrResultRepository results;

    public OcrResultRepositoryAdapter(JpaOcrResultRepository results) {
        this.results = results;
    }

    @Override
    public Optional<OcrResult> findByJobId(UUID jobId) {
        return results.findByOcrJobId(jobId).map(OcrResultJpaEntity::toDomain);
    }

    @Override
    public Optional<OcrResult> findByIdForUpdate(UUID resultId) {
        return results.findByIdForUpdate(resultId).map(OcrResultJpaEntity::toDomain);
    }

    @Override
    public OcrResult save(OcrResult result) {
        OcrResultJpaEntity entity = results.findById(result.id()).orElseThrow();
        entity.update(result);
        return results.saveAndFlush(entity).toDomain();
    }
}
