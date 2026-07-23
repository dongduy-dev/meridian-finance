package com.meridian.platform.loan.application.dto;

import java.time.LocalDateTime;

public record LoanContractBankAccountDto(
        String bankCode,
        String bankNameSnapshot,
        String accountHolderName,
        String maskedAccountNumber,
        boolean primaryAtCapture,
        boolean activeAtCapture,
        LocalDateTime capturedAt
) {
}
