package com.meridian.platform.loan.application.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record StaffLoanApplicationCaseDto(
        UUID loanApplicationId,
        String applicationNumber,
        String productCode,
        String productType,
        String originationChannel,
        BigDecimal requestedAmount,
        int requestedTermMonths,
        String status,
        LocalDateTime submittedAt,
        CustomerReadinessDto customerReadiness,
        CustomerContextDto customerContext,
        CollateralAssessmentSnapshotDto collateralContext,
        boolean formalReviewRecorded,
        StaffActorDto assignedLoanOfficer,
        List<LifecycleItemDto> lifecycleHistory
) {
    public StaffLoanApplicationCaseDto {
        lifecycleHistory = List.copyOf(lifecycleHistory);
    }

    public StaffLoanApplicationCaseDto(
            UUID loanApplicationId, String applicationNumber, String productCode,
            String productType, BigDecimal requestedAmount, int requestedTermMonths,
            String status, LocalDateTime submittedAt, CustomerReadinessDto customerReadiness,
            List<LifecycleItemDto> lifecycleHistory
    ) {
        this(loanApplicationId, applicationNumber, productCode, productType,
                "CUSTOMER_DIGITAL", requestedAmount, requestedTermMonths, status,
                submittedAt, customerReadiness, null, null, false, null, lifecycleHistory);
    }

    public StaffLoanApplicationCaseDto(
            UUID loanApplicationId, String applicationNumber, String productCode,
            String productType, String originationChannel, BigDecimal requestedAmount,
            int requestedTermMonths, String status, LocalDateTime submittedAt,
            CustomerReadinessDto customerReadiness, List<LifecycleItemDto> lifecycleHistory
    ) {
        this(loanApplicationId, applicationNumber, productCode, productType, originationChannel,
                requestedAmount, requestedTermMonths, status, submittedAt, customerReadiness,
                null, null, false, null, lifecycleHistory);
    }

    public StaffLoanApplicationCaseDto(
            UUID loanApplicationId, String applicationNumber, String productCode,
            String productType, String originationChannel, BigDecimal requestedAmount,
            int requestedTermMonths, String status, LocalDateTime submittedAt,
            CustomerReadinessDto customerReadiness, boolean formalReviewRecorded,
            StaffActorDto assignedLoanOfficer, List<LifecycleItemDto> lifecycleHistory
    ) {
        this(loanApplicationId, applicationNumber, productCode, productType, originationChannel,
                requestedAmount, requestedTermMonths, status, submittedAt, customerReadiness,
                null, null, formalReviewRecorded, assignedLoanOfficer, lifecycleHistory);
    }

    public record CustomerContextDto(
            String customerNumber, String fullName, String phoneNumber, String maskedIdentityReference
    ) {
    }

    public record StaffActorDto(UUID userId, String displayName, String email) {
    }

    public record CustomerReadinessDto(
            boolean active,
            boolean profileComplete,
            boolean hasPrimaryActiveBankAccount,
            String verificationStatus
    ) {
    }

    public record LifecycleItemDto(
            String fromStatus,
            String toStatus,
            String action,
            String actorType,
            StaffActorDto actor,
            LocalDateTime occurredAt
    ) {
        public LifecycleItemDto(
                String fromStatus,
                String toStatus,
                String action,
                String actorType,
                LocalDateTime occurredAt
        ) {
            this(fromStatus, toStatus, action, actorType, null, occurredAt);
        }
    }
}
