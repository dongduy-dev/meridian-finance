package com.meridian.platform.loan.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public record SalaryAdvanceVerification(
        UUID id,
        UUID loanApplicationId,
        UUID customerId,
        UUID customerPartnerEmployeeLinkId,
        UUID salaryAdvanceLimitId,
        UUID partnerCompanyId,
        UUID partnerEmployeeId,
        UUID sourceImportBatchId,
        SalaryAdvanceEmployeeVerificationOutcome employeeVerificationOutcome,
        ProductVerificationResult productVerificationResult,
        BigDecimal totalLimitSnapshot,
        BigDecimal usedAmountSnapshot,
        BigDecimal reservedAmountSnapshot,
        BigDecimal availableLimitSnapshot,
        LocalDateTime verifiedAt
) {

    public static SalaryAdvanceVerification verified(
            UUID id,
            LoanApplication loanApplication,
            SalaryAdvanceLimit reservedLimit,
            VerifiedPartnerEmployeeLinkSnapshot partnerSnapshot,
            LocalDateTime verifiedAt
    ) {
        Objects.requireNonNull(loanApplication, "loanApplication must not be null");
        Objects.requireNonNull(reservedLimit, "reservedLimit must not be null");
        Objects.requireNonNull(partnerSnapshot, "partnerSnapshot must not be null");

        return new SalaryAdvanceVerification(
                Objects.requireNonNull(id, "id must not be null"),
                loanApplication.id(),
                loanApplication.customerId(),
                partnerSnapshot.customerPartnerEmployeeLinkId(),
                reservedLimit.id(),
                partnerSnapshot.partnerCompanyId(),
                partnerSnapshot.partnerEmployeeId(),
                partnerSnapshot.sourceImportBatchId(),
                partnerSnapshot.employeeVerificationOutcome(),
                ProductVerificationResult.VERIFIED,
                reservedLimit.totalLimit(),
                reservedLimit.usedAmount(),
                reservedLimit.reservedAmount(),
                reservedLimit.availableAmount(),
                Objects.requireNonNull(verifiedAt, "verifiedAt must not be null")
        );
    }
}
