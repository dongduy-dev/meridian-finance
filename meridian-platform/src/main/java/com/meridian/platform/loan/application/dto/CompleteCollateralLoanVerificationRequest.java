package com.meridian.platform.loan.application.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.meridian.platform.approval.application.dto.CorrectionPlanRequest;
import com.meridian.platform.approval.domain.model.CorrectionReasonCode;
import com.meridian.platform.loan.domain.model.CollateralLoanManualVerificationOutcome;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CompleteCollateralLoanVerificationRequest(
        @NotNull
        UUID expectedVerificationId,
        @NotNull
        CollateralLoanManualVerificationOutcome outcome,
        @NotBlank
        @Size(max = 2000)
        String assessmentNote,
        CorrectionReasonCode reasonCode,
        @Valid
        CorrectionPlanRequest correctionPlan
) {

    @JsonAnySetter
    public void rejectUnknownField(String fieldName, Object value) {
        throw new IllegalArgumentException("Unsupported Collateral Loan verification request field.");
    }
}
