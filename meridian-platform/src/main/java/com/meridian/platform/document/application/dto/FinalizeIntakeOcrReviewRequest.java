package com.meridian.platform.document.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.UUID;

public record FinalizeIntakeOcrReviewRequest(
        @NotNull UUID expectedOcrResultId,
        @NotNull @Size(max = 17) Map<
                @NotBlank @Size(max = 64) String,
                @NotNull @Size(max = 1000) String> reviewedFields
) {
}
