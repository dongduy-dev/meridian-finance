package com.meridian.platform.loan.domain.model.salaryadvance;

import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.ProductVerificationResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public record SalaryAdvanceVerification(
        UUID id,
        UUID loanApplicationId,
        int verificationSequence,
        UUID correctionRequestId,
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

    public SalaryAdvanceVerification(
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
        this(id, loanApplicationId, 1, null, customerId, customerPartnerEmployeeLinkId,
                salaryAdvanceLimitId, partnerCompanyId, partnerEmployeeId, sourceImportBatchId,
                employeeVerificationOutcome, productVerificationResult, totalLimitSnapshot,
                usedAmountSnapshot, reservedAmountSnapshot, availableLimitSnapshot, verifiedAt);
    }

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
                1,
                null,
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

    public static SalaryAdvanceVerification revalidated(
            UUID id,
            int verificationSequence,
            UUID correctionRequestId,
            LoanApplication loanApplication,
            SalaryAdvanceLimit currentLimit,
            BigDecimal currentEffectiveLimit,
            VerifiedPartnerEmployeeLinkSnapshot partnerSnapshot,
            LocalDateTime verifiedAt
    ) {
        Objects.requireNonNull(currentEffectiveLimit, "currentEffectiveLimit must not be null");
        BigDecimal exposure = currentLimit.usedAmount().add(currentLimit.reservedAmount());
        if (currentEffectiveLimit.compareTo(exposure) < 0) {
            throw new com.meridian.platform.shared.domain.exception.BusinessRuleViolationException(
                    "INSUFFICIENT_AVAILABLE_LIMIT",
                    "Current Salary Advance exposure exceeds the revalidated effective limit."
            );
        }
        return new SalaryAdvanceVerification(
                Objects.requireNonNull(id),
                loanApplication.id(),
                verificationSequence,
                Objects.requireNonNull(correctionRequestId),
                loanApplication.customerId(),
                partnerSnapshot.customerPartnerEmployeeLinkId(),
                currentLimit.id(),
                partnerSnapshot.partnerCompanyId(),
                partnerSnapshot.partnerEmployeeId(),
                partnerSnapshot.sourceImportBatchId(),
                partnerSnapshot.employeeVerificationOutcome(),
                ProductVerificationResult.VERIFIED,
                currentEffectiveLimit,
                currentLimit.usedAmount(),
                currentLimit.reservedAmount(),
                currentEffectiveLimit.subtract(exposure),
                Objects.requireNonNull(verifiedAt)
        );
    }
}
