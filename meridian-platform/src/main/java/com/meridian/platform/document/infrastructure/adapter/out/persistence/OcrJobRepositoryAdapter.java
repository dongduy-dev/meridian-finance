package com.meridian.platform.document.infrastructure.adapter.out.persistence;

import com.meridian.platform.document.application.port.out.OcrJobRepository;
import com.meridian.platform.document.domain.model.OcrJob;
import com.meridian.platform.document.domain.model.OcrResultDisposition;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class OcrJobRepositoryAdapter implements OcrJobRepository {

    private final JpaOcrJobRepository jobs;

    public OcrJobRepositoryAdapter(JpaOcrJobRepository jobs) {
        this.jobs = jobs;
    }

    @Override
    public OcrJob save(OcrJob job) {
        OcrJobJpaEntity entity = jobs.findById(job.id())
                .orElseGet(() -> new OcrJobJpaEntity(job));
        entity.update(job);
        return jobs.saveAndFlush(entity).toDomain();
    }

    @Override
    public Optional<OcrJob> findByIntakeDocumentVersionId(UUID versionId) {
        return jobs.findByIntakeDocumentVersionId(versionId).map(OcrJobJpaEntity::toDomain);
    }

    @Override
    public Optional<OcrResultDisposition> findDispositionByJobId(UUID jobId) {
        return jobs.findResultDisposition(jobId).map(OcrResultDisposition::valueOf);
    }
}
