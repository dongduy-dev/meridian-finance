package com.meridian.platform.loan.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record ConfirmManualDisbursementRequest(
        @NotNull UUID requestId,
        @NotNull @Positive Integer expectedContractVersion,
        @NotBlank
        @Size(max = 128)
        @Pattern(regexp = "\\s*[A-Za-z0-9][A-Za-z0-9._:/-]{0,63}\\s*")
        String externalTransferReference,
        @NotNull LocalDate disbursementValueDate,
        @NotNull LocalDate firstRepaymentDate
) {
    @Override
    public String toString() {
        return "ConfirmManualDisbursementRequest[requestId=" + requestId
                + ", expectedContractVersion=" + expectedContractVersion
                + ", disbursementValueDate=" + disbursementValueDate
                + ", firstRepaymentDate=" + firstRepaymentDate
                + ", externalTransferReference=redacted]";
    }
}
