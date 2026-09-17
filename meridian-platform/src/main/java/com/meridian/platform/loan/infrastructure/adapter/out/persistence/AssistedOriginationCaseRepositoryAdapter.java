package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.application.port.out.AssistedOriginationCaseRepository;
import com.meridian.platform.loan.domain.model.AssistedOriginationCase;
import com.meridian.platform.loan.domain.model.AssistedOriginationCaseStatus;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AssistedOriginationCaseRepositoryAdapter implements AssistedOriginationCaseRepository {

    private final JpaAssistedOriginationCaseRepository repository;

    public AssistedOriginationCaseRepositoryAdapter(JpaAssistedOriginationCaseRepository repository) {
        this.repository = repository;
    }

    @Override
    public AssistedOriginationCase save(AssistedOriginationCase assistedCase) {
        AssistedOriginationCaseJpaEntity entity = repository.findById(assistedCase.id())
                .orElseGet(() -> new AssistedOriginationCaseJpaEntity(assistedCase));
        entity.update(assistedCase);
        return repository.saveAndFlush(entity).toDomain();
    }

    @Override
    public Optional<AssistedOriginationCase> findById(UUID caseId) {
        return repository.findById(caseId).map(AssistedOriginationCaseJpaEntity::toDomain);
    }

    @Override
    public Optional<AssistedOriginationCase> findByIdForUpdate(UUID caseId) {
        return repository.findByIdForUpdate(caseId).map(AssistedOriginationCaseJpaEntity::toDomain);
    }

    @Override
    public List<AssistedOriginationCase> findAll(AssistedOriginationCaseStatus status) {
        List<AssistedOriginationCaseJpaEntity> entities = status == null
                ? repository.findAllByOrderByUpdatedAtDescIdDesc()
                : repository.findAllByStatusOrderByUpdatedAtDescIdDesc(status);
        return entities.stream().map(AssistedOriginationCaseJpaEntity::toDomain).toList();
    }
}
