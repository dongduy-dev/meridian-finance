package com.meridian.platform.approval.application.service;

import com.meridian.platform.approval.application.dto.ApprovalDecisionDto;
import com.meridian.platform.approval.application.dto.ApprovalDecisionRequest;
import com.meridian.platform.approval.application.mapper.ApprovalMapper;
import com.meridian.platform.approval.application.port.in.SubmitApprovalDecisionUseCase;
import com.meridian.platform.approval.application.port.out.ApprovalDecisionEventPublisher;
import com.meridian.platform.approval.application.port.out.ApprovalDecisionRepository;
import com.meridian.platform.approval.application.port.out.ReviewRecommendationRepository;
import com.meridian.platform.approval.domain.model.ApprovalDecision;
import com.meridian.platform.approval.domain.model.ReviewRecommendation;
import com.meridian.platform.shared.application.audit.AuditAction;
import com.meridian.platform.shared.application.audit.AuditEntityType;
import com.meridian.platform.shared.application.audit.AuditEventPublisher;
import com.meridian.platform.shared.application.audit.AuditPayloadEntry;
import com.meridian.platform.shared.application.audit.AuditPayloadKey;
import com.meridian.platform.shared.application.audit.AuditRecordRequestedEvent;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.model.ActionActor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class SubmitApprovalDecisionService implements SubmitApprovalDecisionUseCase {

    private final ReviewRecommendationRepository reviewRecommendationRepository;
    private final ApprovalDecisionRepository approvalDecisionRepository;
    private final ApprovalDecisionEventPublisher approvalDecisionEventPublisher;
    private final CurrentUserProvider currentUserProvider;
    private final ApprovalMapper approvalMapper;
    private final AuditEventPublisher auditEventPublisher;
    private final Clock clock;

    public SubmitApprovalDecisionService(
            ReviewRecommendationRepository reviewRecommendationRepository,
            ApprovalDecisionRepository approvalDecisionRepository,
            ApprovalDecisionEventPublisher approvalDecisionEventPublisher,
            CurrentUserProvider currentUserProvider,
            ApprovalMapper approvalMapper,
            AuditEventPublisher auditEventPublisher,
            Clock clock
    ) {
        this.reviewRecommendationRepository = reviewRecommendationRepository;
        this.approvalDecisionRepository = approvalDecisionRepository;
        this.approvalDecisionEventPublisher = approvalDecisionEventPublisher;
        this.currentUserProvider = currentUserProvider;
        this.approvalMapper = approvalMapper;
        this.auditEventPublisher = auditEventPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ApprovalDecisionDto submitApprovalDecision(UUID loanApplicationId, ApprovalDecisionRequest request) {
        Objects.requireNonNull(loanApplicationId, "loanApplicationId must not be null");
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(request.action(), "action must not be null");

        ReviewRecommendation latestRecommendation = reviewRecommendationRepository
                .findLatestByLoanApplicationId(loanApplicationId)
                .orElseThrow(() -> new BusinessStateConflictException(
                        "REVIEW_RECOMMENDATION_REQUIRED",
                        "A Loan Officer recommendation is required before an approval decision."
                ));

        AuthenticatedUser currentUser = currentUserProvider.currentUser();
        validateMakerChecker(latestRecommendation, currentUser);

        UUID operationId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now(clock);
        ApprovalDecision decision = ApprovalDecision.recorded(
                UUID.randomUUID(),
                loanApplicationId,
                latestRecommendation.id(),
                currentUser.userId(),
                request.action(),
                request.reason(),
                request.internalNotes(),
                now
        );

        ApprovalDecision savedDecision = approvalDecisionRepository.save(decision);
        auditEventPublisher.publish(new AuditRecordRequestedEvent(
                operationId, (short) 1, ActionActor.user(currentUser.userId()), AuditEntityType.APPROVAL_DECISION,
                savedDecision.id(), AuditAction.APPROVAL_DECISION_RECORDED,
                List.of(
                        new AuditPayloadEntry(AuditPayloadKey.APPROVAL_DECISION_ACTION, savedDecision.action().name()),
                        new AuditPayloadEntry(AuditPayloadKey.RECOMMENDATION_ID, savedDecision.reviewRecommendationId().toString())
                ),
                savedDecision.decidedAt()
        ));
        approvalDecisionEventPublisher.publish(approvalMapper.toRecordedEvent(savedDecision, operationId));

        return approvalMapper.toDto(savedDecision);
    }

    private void validateMakerChecker(ReviewRecommendation recommendation, AuthenticatedUser currentUser) {
        if (recommendation.loanOfficerUserId().equals(currentUser.userId())) {
            throw new BusinessRuleViolationException(
                    "MAKER_CHECKER_VIOLATION",
                    "The same user cannot submit a recommendation and approve the loan application."
            );
        }
    }
}
