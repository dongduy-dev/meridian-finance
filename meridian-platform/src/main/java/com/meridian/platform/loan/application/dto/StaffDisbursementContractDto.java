package com.meridian.platform.loan.application.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record StaffDisbursementContractDto(
        UUID contractId,
        String contractReference,
        int contractVersion,
        String status,
        BigDecimal approvedPrincipal,
        int approvedTermMonths,
        String repaymentMethod,
        LocalDateTime readinessConfirmedAt,
        DestinationDto disbursementDestination
) {
    public record DestinationDto(
            String bankCode,
            String bankName,
            String accountHolderName,
            String maskedAccountNumber
    ) {
        @Override
        public String toString() {
            return "DestinationDto[destination=redacted]";
        }
    }
}
