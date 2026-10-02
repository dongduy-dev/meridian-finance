package com.meridian.platform.loan.application.dto;

import java.time.LocalDateTime;

public record RecordedCustomerActionDto(
        String action, StaffLoanApplicationCaseDto.StaffActorDto recordedBy,
        LocalDateTime recordedAt, AssistedActionEvidenceMetadataDto evidence
) {
}
