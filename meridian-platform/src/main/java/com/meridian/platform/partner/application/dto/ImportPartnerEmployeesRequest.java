package com.meridian.platform.partner.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record ImportPartnerEmployeesRequest(
        @NotNull UUID requestId,
        @NotBlank String effectiveMonth,
        @NotEmpty List<PartnerEmployeeImportRowRequest> rows
) {
}
