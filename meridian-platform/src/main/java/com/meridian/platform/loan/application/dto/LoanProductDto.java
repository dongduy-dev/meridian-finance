package com.meridian.platform.loan.application.dto;

import java.math.BigDecimal;
import java.util.List;

public record LoanProductDto(
        String productCode,
        String productType,
        String name,
        String description,
        boolean active,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        PolicyPresentationDto policy
) {
    public record PolicyPresentationDto(
            List<Integer> allowedTermsMonths,
            PricingPresentationDto pricing,
            String interestCalculationMethod,
            String repaymentMethod,
            int offerValidityDays,
            List<SubmissionEvidenceRequirementDto> submissionEvidenceRequirements,
            List<String> eligibilityNotes
    ) {
        public PolicyPresentationDto {
            allowedTermsMonths = List.copyOf(allowedTermsMonths);
            submissionEvidenceRequirements = List.copyOf(submissionEvidenceRequirements);
            eligibilityNotes = List.copyOf(eligibilityNotes);
        }
    }

    public record PricingPresentationDto(
            BigDecimal flatMonthlyInterestRate,
            BigDecimal feeAmount
    ) {
    }

    public record SubmissionEvidenceRequirementDto(
            String documentType,
            String requirementStatus
    ) {
    }
}
