package com.meridian.platform.loan.application.port.in;

import com.meridian.platform.loan.application.dto.AssistedOriginationCaseDto;
import com.meridian.platform.loan.application.dto.CreateAssistedOriginationCaseRequest;
import com.meridian.platform.loan.domain.model.AssistedOriginationCaseStatus;

import java.util.List;
import java.util.UUID;

public interface ManageAssistedOriginationUseCase {

    List<AssistedOriginationCaseDto> findCases(AssistedOriginationCaseStatus status);

    AssistedOriginationCaseDto getCase(UUID caseId);

    AssistedOriginationCaseDto createCase(CreateAssistedOriginationCaseRequest request);

    AssistedOriginationCaseDto associateCustomer(UUID caseId, UUID customerId);

    AssistedOriginationCaseDto abandon(UUID caseId);
}
