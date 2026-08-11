package com.meridian.platform.loan.application.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompleteUnsecuredConsumerLoanVerificationRequest(
        @NotBlank
        @Size(max = 2000)
        String assessmentNote
) {

    @JsonAnySetter
    public void rejectUnknownField(String fieldName, Object value) {
        throw new IllegalArgumentException("Unsupported manual verification request field.");
    }
}
