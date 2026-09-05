package com.meridian.platform.loan.application.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record StaffLoanApplicationVerificationDto(
        UUID loanApplicationId,
        String applicationNumber,
        String productCode,
        String productType,
        BigDecimal requestedAmount,
        int requestedTermMonths,
        String applicationStatus,
        LocalDateTime submittedAt,
        DocumentReadinessDto documentReadiness,
        ActionPresentationDto actions,
        ProductVerificationDto productVerification,
        List<CorrectionTargetDto> correctionTargets
) {
    public StaffLoanApplicationVerificationDto {
        correctionTargets = List.copyOf(correctionTargets);
    }

    public record DocumentReadinessDto(boolean uploadComplete, boolean processingReady) {
    }

    public record ActionPresentationDto(boolean startAvailable, boolean completeAvailable) {
    }

    public sealed interface ProductVerificationDto permits
            SalaryAdvanceVerificationDto, ManualVerificationDto {
    }

    public record SalaryAdvanceVerificationDto(
            int verificationSequence,
            String employeeVerificationOutcome,
            String productVerificationResult,
            BigDecimal totalLimitSnapshot,
            BigDecimal usedAmountSnapshot,
            BigDecimal reservedAmountSnapshot,
            BigDecimal availableLimitSnapshot,
            LocalDateTime verifiedAt
    ) implements ProductVerificationDto {
    }

    public record ManualVerificationDto(
            VerificationCycleDto currentCycle,
            List<VerificationCycleDto> history,
            CollateralAssessmentSnapshotDto collateral
    ) implements ProductVerificationDto {
        public ManualVerificationDto {
            history = List.copyOf(history);
        }
    }

    public record VerificationCycleDto(
            UUID verificationId,
            int verificationSequence,
            String productVerificationResult,
            LocalDateTime createdAt,
            LocalDateTime reviewedAt
    ) {
    }

    public record CorrectionTargetDto(
            UUID checklistItemId,
            String documentType,
            String requirementStatus,
            UUID currentDocumentVersionId
    ) {
    }
}
