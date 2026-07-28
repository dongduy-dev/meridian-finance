package com.meridian.platform.loan.application.dto;

import java.util.UUID;

public record DisbursementDestinationRevealDto(
        UUID contractId,
        int contractVersion,
        String bankCode,
        String bankName,
        String accountHolderName,
        String accountNumber
) {
    @Override
    public String toString() {
        return "DisbursementDestinationRevealDto[contractId=" + contractId
                + ", contractVersion=" + contractVersion
                + ", destination=redacted]";
    }
}
