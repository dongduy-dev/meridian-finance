package com.meridian.platform.loan.application.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.meridian.platform.approval.application.dto.CorrectionPlanRequest;
import com.meridian.platform.approval.domain.model.CorrectionReasonCode;
import com.meridian.platform.loan.domain.model.UnsecuredConsumerLoanManualVerificationOutcome;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CompleteUnsecuredConsumerLoanVerificationRequest(
        @NotNull
        UnsecuredConsumerLoanManualVerificationOutcome outcome,
        @NotBlank
        @Size(max = 2000)
        String assessmentNote,
        CorrectionReasonCode reasonCode,
        @Valid
        CorrectionPlanRequest correctionPlan
) {

    public CompleteUnsecuredConsumerLoanVerificationRequest(String assessmentNote) {
        this(
                UnsecuredConsumerLoanManualVerificationOutcome.VERIFIED,
                assessmentNote,
                null,
                null
        );
    }

    @JsonAnySetter
    public void rejectUnknownField(String fieldName, Object value) {
        throw new IllegalArgumentException("Unsupported manual verification request field.");
    }
}
