package com.meridian.platform.loan.application.port.out;

import com.meridian.platform.loan.domain.model.AssistedOriginationCase;
import com.meridian.platform.loan.domain.model.AssistedOriginationCaseStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssistedOriginationCaseRepository {

    AssistedOriginationCase save(AssistedOriginationCase assistedCase);

    Optional<AssistedOriginationCase> findById(UUID caseId);

    Optional<AssistedOriginationCase> findByIdForUpdate(UUID caseId);

    List<AssistedOriginationCase> findAll(AssistedOriginationCaseStatus status);
}
