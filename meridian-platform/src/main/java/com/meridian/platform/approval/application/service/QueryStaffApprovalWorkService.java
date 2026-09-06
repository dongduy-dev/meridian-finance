package com.meridian.platform.approval.application.service;

import com.meridian.platform.approval.application.dto.StaffApprovalQueuePageDto;
import com.meridian.platform.approval.application.dto.StaffDecisionCaseDto;
import com.meridian.platform.approval.application.dto.StaffRecommendationCaseDto;
import com.meridian.platform.approval.application.port.in.QueryStaffApprovalWorkUseCase;
import com.meridian.platform.approval.application.port.out.ApprovalDecisionRepository;
import com.meridian.platform.approval.application.port.out.ApprovalLoanCasePort;
import com.meridian.platform.approval.application.port.out.ReviewRecommendationRepository;
import com.meridian.platform.approval.domain.model.ApprovalDecision;
import com.meridian.platform.approval.domain.model.ReviewRecommendation;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class QueryStaffApprovalWorkService implements QueryStaffApprovalWorkUseCase {

    public static final int MAX_PAGE_SIZE = 100;

    private final ApprovalLoanCasePort loanCases;
    private final ReviewRecommendationRepository recommendations;
    private final ApprovalDecisionRepository decisions;
    private final CurrentUserProvider currentUserProvider;

    public QueryStaffApprovalWorkService(
            ApprovalLoanCasePort loanCases,
            ReviewRecommendationRepository recommendations,
            ApprovalDecisionRepository decisions,
            CurrentUserProvider currentUserProvider
    ) {
        this.loanCases = loanCases;
        this.recommendations = recommendations;
        this.decisions = decisions;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public StaffRecommendationCaseDto queryRecommendationCase(UUID loanApplicationId) {
        Objects.requireNonNull(loanApplicationId, "loanApplicationId must not be null");
        requireAuthority(currentUserProvider.currentUser(), "approval:recommend");
        ApprovalLoanCasePort.CaseSnapshot loanCase = requireCase(loanApplicationId);
        ReviewRecommendation recommendation = loanCase.currentReviewCycle() == null ? null
                : recommendations.findByReviewCycleId(loanCase.currentReviewCycle().reviewCycleId()).orElse(null);
        boolean available = recommendation == null
                && "UNDER_REVIEW".equals(loanCase.applicationStatus())
                && activeCycle(loanCase)
                && loanCase.productReadiness().readyForDecision();
        return new StaffRecommendationCaseDto(
                loanCase.loanApplicationId(), loanCase.applicationNumber(), loanCase.productCode(),
                loanCase.productType(), loanCase.requestedAmount(), loanCase.requestedTermMonths(),
                loanCase.applicationStatus(), loanCase.submittedAt(), evidence(loanCase),
                recommendation(recommendation), available, reasonCodes(loanCase.productCode()),
                correctionOptions(loanCase)
        );
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public StaffDecisionCaseDto queryDecisionCase(UUID loanApplicationId) {
        Objects.requireNonNull(loanApplicationId, "loanApplicationId must not be null");
        AuthenticatedUser actor = currentUserProvider.currentUser();
        requireAuthority(actor, "approval:decide");
        ApprovalLoanCasePort.CaseSnapshot loanCase = requireCase(loanApplicationId);
        ReviewRecommendation recommendation = recommendations.findLatestByLoanApplicationId(loanApplicationId)
                .orElse(null);
        List<ApprovalDecision> history = decisions
                .findByLoanApplicationIdOrderByDecidedAtDesc(loanApplicationId);
        ApprovalDecision latestDecision = recommendation == null ? null
                : decisions.findByReviewRecommendationId(recommendation.id()).orElse(null);
        boolean makerCheckerEligible = recommendation != null
                && !recommendation.loanOfficerUserId().equals(actor.userId());
        boolean available = recommendation != null
                && latestDecision == null
                && makerCheckerEligible
                && "APPROVAL_PENDING".equals(loanCase.applicationStatus())
                && activeCycle(loanCase)
                && loanCase.currentReviewCycle().reviewCycleId().equals(recommendation.reviewCycleId())
                && loanCase.productReadiness().readyForDecision();
        return new StaffDecisionCaseDto(
                loanCase.loanApplicationId(), loanCase.applicationNumber(), loanCase.productCode(),
                loanCase.productType(), loanCase.requestedAmount(), loanCase.requestedTermMonths(),
                loanCase.applicationStatus(), loanCase.submittedAt(), evidence(loanCase),
                recommendation(recommendation), makerCheckerEligible, available,
                decision(latestDecision), history.stream().map(QueryStaffApprovalWorkService::decision).toList(),
                reasonCodes(loanCase.productCode()), correctionOptions(loanCase)
        );
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public StaffApprovalQueuePageDto queryDecisionQueue(String productCode, int page, int size) {
        AuthenticatedUser actor = currentUserProvider.currentUser();
        requireAuthority(actor, "approval:decide");
        requireValidPage(page, size);
        String normalizedProduct = normalizeProduct(productCode);
        ApprovalLoanCasePort.QueuePageSnapshot selected = loanCases
                .findDecisionQueue(normalizedProduct, page, size);
        List<StaffApprovalQueuePageDto.ItemDto> items = selected.items().stream()
                .map(item -> queueItem(item, actor))
                .toList();
        return new StaffApprovalQueuePageDto(
                selected.page(), selected.size(), selected.totalElements(), selected.totalPages(), items
        );
    }

    private StaffApprovalQueuePageDto.ItemDto queueItem(
            ApprovalLoanCasePort.QueueItemSnapshot item,
            AuthenticatedUser actor
    ) {
        ReviewRecommendation recommendation = recommendations
                .findLatestByLoanApplicationId(item.loanApplicationId())
                .orElseThrow(QueryStaffApprovalWorkService::systemConflict);
        if (decisions.findByReviewRecommendationId(recommendation.id()).isPresent()) {
            throw systemConflict();
        }
        boolean eligible = !recommendation.loanOfficerUserId().equals(actor.userId());
        return new StaffApprovalQueuePageDto.ItemDto(
                item.loanApplicationId(), item.applicationNumber(), item.productCode(), item.productType(),
                item.requestedAmount(), item.requestedTermMonths(), item.applicationStatus(), item.submittedAt(),
                recommendation.id(), recommendation.action().name(), recommendation.submittedAt(),
                eligible, eligible
        );
    }

    private ApprovalLoanCasePort.CaseSnapshot requireCase(UUID loanApplicationId) {
        return loanCases.findCase(loanApplicationId).orElseThrow(() -> new EntityNotFoundException(
                "LOAN_APPLICATION_NOT_FOUND", "Loan Application was not found."
        ));
    }

    private static StaffRecommendationCaseDto.EvidenceDto evidence(ApprovalLoanCasePort.CaseSnapshot loanCase) {
        ApprovalLoanCasePort.ReviewCycleSnapshot cycle = loanCase.currentReviewCycle();
        return new StaffRecommendationCaseDto.EvidenceDto(
                loanCase.documentReadiness().uploadComplete(),
                loanCase.documentReadiness().processingReady(),
                loanCase.productReadiness().productVerificationResult(),
                loanCase.productReadiness().readyForDecision(),
                cycle == null ? null : new StaffRecommendationCaseDto.ReviewCycleDto(
                        cycle.reviewCycleId(), cycle.cycleNumber(), cycle.status(), cycle.startedAt(), cycle.endedAt()
                )
        );
    }

    private static StaffRecommendationCaseDto.RecommendationDto recommendation(ReviewRecommendation value) {
        return value == null ? null : new StaffRecommendationCaseDto.RecommendationDto(
                value.id(), value.reviewCycleId(), value.action().name(), value.reason(),
                value.reasonCode() == null ? null : value.reasonCode().name(), value.submittedAt()
        );
    }

    private static StaffDecisionCaseDto.DecisionDto decision(ApprovalDecision value) {
        return value == null ? null : new StaffDecisionCaseDto.DecisionDto(
                value.id(), value.reviewRecommendationId(), value.action().name(), value.reason(),
                value.reasonCode() == null ? null : value.reasonCode().name(), value.decidedAt()
        );
    }

    private static List<StaffRecommendationCaseDto.CorrectionOptionDto> correctionOptions(
            ApprovalLoanCasePort.CaseSnapshot loanCase
    ) {
        return loanCase.correctionOptions().stream().map(option ->
                new StaffRecommendationCaseDto.CorrectionOptionDto(
                        option.documentType(), option.checklistItemId(),
                        option.currentDocumentVersionId(), option.allowedScopes()
                )).toList();
    }

    private static List<String> reasonCodes(String productCode) {
        return switch (productCode) {
            case "SALARY_ADVANCE" -> List.of(
                    "SUPPORTING_DOCUMENT_REQUIRED", "RECENT_PAYSLIP_REQUIRED",
                    "DOCUMENT_REPLACEMENT_REQUIRED", "DOCUMENT_REVIEW_REQUIRED"
            );
            case "UNSECURED_CONSUMER_LOAN" -> List.of(
                    "SUPPORTING_DOCUMENT_REQUIRED", "DOCUMENT_REPLACEMENT_REQUIRED",
                    "DOCUMENT_REVIEW_REQUIRED"
            );
            case "COLLATERAL_LOAN" -> List.of(
                    "DOCUMENT_REPLACEMENT_REQUIRED", "DOCUMENT_REVIEW_REQUIRED"
            );
            default -> List.of();
        };
    }

    private static boolean activeCycle(ApprovalLoanCasePort.CaseSnapshot loanCase) {
        return loanCase.currentReviewCycle() != null
                && "ACTIVE".equals(loanCase.currentReviewCycle().status());
    }

    private static String normalizeProduct(String productCode) {
        if (productCode == null || productCode.isBlank()) {
            return null;
        }
        String normalized = productCode.trim().toUpperCase(Locale.ROOT);
        if (!List.of("SALARY_ADVANCE", "UNSECURED_CONSUMER_LOAN", "COLLATERAL_LOAN")
                .contains(normalized)) {
            throw new IllegalArgumentException("Approval queue product filter is invalid.");
        }
        return normalized;
    }

    private static void requireValidPage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Approval queue page arguments are invalid.");
        }
    }

    private static void requireAuthority(AuthenticatedUser actor, String permission) {
        if (!"STAFF".equals(actor.userType())
                || actor.optionalCustomerId().isPresent()
                || !actor.hasPermission(permission)) {
            throw new AuthorizationException(
                    "APPROVAL_WORK_ACCESS_DENIED", "Staff approval work access is denied."
            );
        }
    }

    private static BusinessStateConflictException systemConflict() {
        return new BusinessStateConflictException(
                "SYSTEM_STATE_CONFLICT", "Approval work evidence is inconsistent."
        );
    }
}
