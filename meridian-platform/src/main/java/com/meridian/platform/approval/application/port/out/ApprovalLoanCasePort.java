package com.meridian.platform.approval.application.port.out;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApprovalLoanCasePort {

    Optional<CaseSnapshot> findCase(UUID loanApplicationId);

    QueuePageSnapshot findDecisionQueue(String productCode, int page, int size);

    record CaseSnapshot(
            UUID loanApplicationId,
            String applicationNumber,
            String productCode,
            String productType,
            BigDecimal requestedAmount,
            int requestedTermMonths,
            String applicationStatus,
            LocalDateTime submittedAt,
            DocumentReadinessSnapshot documentReadiness,
            ProductReadinessSnapshot productReadiness,
            ReviewCycleSnapshot currentReviewCycle,
            List<CorrectionOptionSnapshot> correctionOptions
    ) {
        public CaseSnapshot {
            correctionOptions = List.copyOf(correctionOptions);
        }
    }

    record DocumentReadinessSnapshot(boolean uploadComplete, boolean processingReady) {
    }

    record ProductReadinessSnapshot(String productVerificationResult, boolean readyForDecision) {
    }

    record ReviewCycleSnapshot(
            UUID reviewCycleId,
            int cycleNumber,
            String status,
            LocalDateTime startedAt,
            LocalDateTime endedAt
    ) {
    }

    record CorrectionOptionSnapshot(
            String documentType,
            UUID checklistItemId,
            UUID currentDocumentVersionId,
            List<String> allowedScopes
    ) {
        public CorrectionOptionSnapshot {
            allowedScopes = List.copyOf(allowedScopes);
        }
    }

    record QueuePageSnapshot(
            int page,
            int size,
            long totalElements,
            int totalPages,
            List<QueueItemSnapshot> items
    ) {
        public QueuePageSnapshot {
            items = List.copyOf(items);
        }
    }

    record QueueItemSnapshot(
            UUID loanApplicationId,
            String applicationNumber,
            String productCode,
            String productType,
            BigDecimal requestedAmount,
            int requestedTermMonths,
            String applicationStatus,
            LocalDateTime submittedAt
    ) {
    }
}
