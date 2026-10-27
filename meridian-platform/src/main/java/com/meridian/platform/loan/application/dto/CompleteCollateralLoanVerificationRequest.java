package com.meridian.platform.loan.application.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.meridian.platform.loan.application.dto.LoanCorrectionPlanRequest;
import com.meridian.platform.loan.domain.model.LoanCorrectionReasonCode;
import com.meridian.platform.loan.domain.model.collateral.CollateralLoanManualVerificationOutcome;
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
        LoanCorrectionReasonCode reasonCode,
        @Valid
        LoanCorrectionPlanRequest correctionPlan
) {

    @JsonAnySetter
    public void rejectUnknownField(String fieldName, Object value) {
        throw new IllegalArgumentException("Unsupported Collateral Loan verification request field.");
    }
}
