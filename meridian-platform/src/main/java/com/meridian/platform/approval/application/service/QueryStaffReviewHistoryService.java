package com.meridian.platform.approval.application.service;

import com.meridian.platform.approval.application.dto.StaffRecommendationCaseDto.StaffActorDto;
import com.meridian.platform.approval.application.dto.StaffReviewHistoryDto;
import com.meridian.platform.approval.application.port.in.QueryStaffReviewHistoryUseCase;
import com.meridian.platform.approval.application.port.out.ApprovalDecisionRepository;
import com.meridian.platform.approval.application.port.out.ApprovalLoanCasePort;
import com.meridian.platform.approval.application.port.out.ApprovalLoanReviewHistoryPort;
import com.meridian.platform.approval.application.port.out.ReviewRecommendationRepository;
import com.meridian.platform.approval.application.port.out.StaffActorDirectoryPort;
import com.meridian.platform.approval.application.port.out.StaffActorSummary;
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

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class QueryStaffReviewHistoryService implements QueryStaffReviewHistoryUseCase {
    private final ApprovalLoanReviewHistoryPort loanHistory;
    private final ReviewRecommendationRepository recommendations;
    private final ApprovalDecisionRepository decisions;
    private final StaffActorDirectoryPort staffActors;
    private final CurrentUserProvider currentUserProvider;

    public QueryStaffReviewHistoryService(ApprovalLoanReviewHistoryPort loanHistory,
                                         ReviewRecommendationRepository recommendations,
                                         ApprovalDecisionRepository decisions,
                                         StaffActorDirectoryPort staffActors, CurrentUserProvider currentUserProvider) {
        this.loanHistory = loanHistory;
        this.recommendations = recommendations;
        this.decisions = decisions;
        this.staffActors = staffActors;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public StaffReviewHistoryDto query(UUID loanApplicationId) {
        Objects.requireNonNull(loanApplicationId, "loanApplicationId must not be null");
        AuthenticatedUser actor = currentUserProvider.currentUser();
        if (!"STAFF".equals(actor.userType()) || actor.optionalCustomerId().isPresent()
                || !(actor.hasPermission("loan:review") || actor.hasPermission("approval:recommend")
                || actor.hasPermission("approval:decide"))) {
            throw new AuthorizationException("APPROVAL_WORK_ACCESS_DENIED", "Staff review history access is denied.");
        }
        var history = loanHistory.findHistory(loanApplicationId).orElseThrow(() -> new EntityNotFoundException(
                "LOAN_APPLICATION_NOT_FOUND", "Loan Application was not found."));
        if (!loanApplicationId.equals(history.loanApplicationId())) throw conflict();
        var cycles = history.cycles().stream()
                .sorted(Comparator.comparingInt(ApprovalLoanCasePort.ReviewCycleSnapshot::cycleNumber)).toList();
        Map<UUID, ApprovalLoanCasePort.ReviewCycleSnapshot> cycleById = new LinkedHashMap<>();
        Set<Integer> numbers = new HashSet<>();
        Set<UUID> actorIds = new LinkedHashSet<>();
        for (var cycle : cycles) {
            if (cycleById.put(cycle.reviewCycleId(), cycle) != null || !numbers.add(cycle.cycleNumber())) throw conflict();
            if (cycle.assignedLoanOfficerUserId() != null) actorIds.add(cycle.assignedLoanOfficerUserId());
        }
        Map<UUID, ReviewRecommendation> recommendationByCycle = new HashMap<>();
        Map<UUID, ReviewRecommendation> recommendationById = new HashMap<>();
        for (var recommendation : recommendations.findByLoanApplicationIdOrderBySubmittedAtAsc(loanApplicationId)) {
            if (!loanApplicationId.equals(recommendation.loanApplicationId())
                    || !cycleById.containsKey(recommendation.reviewCycleId())
                    || recommendationByCycle.put(recommendation.reviewCycleId(), recommendation) != null
                    || recommendationById.put(recommendation.id(), recommendation) != null) throw conflict();
            actorIds.add(recommendation.loanOfficerUserId());
        }
        Map<UUID, ApprovalDecision> decisionByRecommendation = new HashMap<>();
        for (var decision : decisions.findByLoanApplicationIdOrderByDecidedAtDesc(loanApplicationId)) {
            if (!loanApplicationId.equals(decision.loanApplicationId())
                    || !recommendationById.containsKey(decision.reviewRecommendationId())
                    || decisionByRecommendation.put(decision.reviewRecommendationId(), decision) != null) throw conflict();
            actorIds.add(decision.approverUserId());
        }
        Map<UUID, StaffActorSummary> summaries = staffActors.findByUserIds(actorIds);
        boolean approver = actor.hasPermission("approval:decide");
        List<StaffReviewHistoryDto.CycleDto> result = cycles.stream().map(cycle -> {
            var recommendation = recommendationByCycle.get(cycle.reviewCycleId());
            var decision = recommendation == null ? null : decisionByRecommendation.get(recommendation.id());
            boolean assigned = actor.userId().equals(cycle.assignedLoanOfficerUserId());
            boolean recommendationNote = approver || assigned
                    || (recommendation != null && actor.userId().equals(recommendation.loanOfficerUserId()));
            return new StaffReviewHistoryDto.CycleDto(cycle.reviewCycleId(), cycle.cycleNumber(),
                    summary(summaries, cycle.assignedLoanOfficerUserId()), cycle.status(), cycle.startedAt(), cycle.endedAt(),
                    recommendation == null ? null : new StaffReviewHistoryDto.RecommendationDto(
                            recommendation.id(), recommendation.reviewCycleId(), recommendation.action().name(),
                            recommendation.reason(), recommendation.reasonCode() == null ? null : recommendation.reasonCode().name(),
                            recommendationNote, recommendationNote ? recommendation.internalNotes() : null,
                            summary(summaries, recommendation.loanOfficerUserId()), recommendation.submittedAt()),
                    decision == null ? null : new StaffReviewHistoryDto.DecisionDto(
                            decision.id(), decision.reviewRecommendationId(), decision.action().name(), decision.reason(),
                            decision.reasonCode() == null ? null : decision.reasonCode().name(),
                            approver || assigned, approver || assigned ? decision.internalNotes() : null,
                            summary(summaries, decision.approverUserId()), decision.decidedAt()));
        }).toList();
        return new StaffReviewHistoryDto(loanApplicationId, history.applicationNumber(), history.applicationStatus(), result);
    }

    private static StaffActorDto summary(Map<UUID, StaffActorSummary> summaries, UUID userId) {
        var value = userId == null ? null : summaries.get(userId);
        return value == null ? null : new StaffActorDto(value.userId(), value.displayName(), value.email());
    }

    private static BusinessStateConflictException conflict() {
        return new BusinessStateConflictException("SYSTEM_STATE_CONFLICT", "Review history evidence is inconsistent.");
    }
}
