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
import com.meridian.platform.shared.application.audit.BusinessAuditEntry;
import com.meridian.platform.shared.application.audit.BusinessAuditEvent;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.operation.BusinessOperationContext;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.audit.BusinessAuditAction;
import com.meridian.platform.shared.domain.audit.BusinessAuditEntityType;
import com.meridian.platform.shared.domain.audit.BusinessAuditPayload;
import com.meridian.platform.shared.domain.audit.BusinessAuditPayloadKey;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Service
public class SubmitApprovalDecisionService implements SubmitApprovalDecisionUseCase {

    private final ReviewRecommendationRepository reviewRecommendationRepository;
    private final ApprovalDecisionRepository approvalDecisionRepository;
    private final ApprovalDecisionEventPublisher approvalDecisionEventPublisher;
    private final CurrentUserProvider currentUserProvider;
    private final ApprovalMapper approvalMapper;
    private final BusinessAuditPublisher businessAuditPublisher;
    private final Clock clock;

    public SubmitApprovalDecisionService(
            ReviewRecommendationRepository reviewRecommendationRepository,
            ApprovalDecisionRepository approvalDecisionRepository,
            ApprovalDecisionEventPublisher approvalDecisionEventPublisher,
            CurrentUserProvider currentUserProvider,
            ApprovalMapper approvalMapper,
            BusinessAuditPublisher businessAuditPublisher,
            Clock clock
    ) {
        this.reviewRecommendationRepository = reviewRecommendationRepository;
        this.approvalDecisionRepository = approvalDecisionRepository;
        this.approvalDecisionEventPublisher = approvalDecisionEventPublisher;
        this.currentUserProvider = currentUserProvider;
        this.approvalMapper = approvalMapper;
        this.businessAuditPublisher = businessAuditPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ApprovalDecisionDto submitApprovalDecision(
            UUID loanApplicationId,
            ApprovalDecisionRequest request
    ) {
        Objects.requireNonNull(loanApplicationId, "loanApplicationId must not be null");
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(request.action(), "action must not be null");

        ReviewRecommendation latestRecommendation = reviewRecommendationRepository
                .findLatestByLoanApplicationId(loanApplicationId)
                .orElseThrow(() -> new BusinessStateConflictException(
                        "REVIEW_RECOMMENDATION_REQUIRED",
                        "An approval decision requires a Loan Officer recommendation."
                ));

        AuthenticatedUser currentUser = currentUserProvider.currentUser();
        validateMakerChecker(latestRecommendation, currentUser);

        LocalDateTime now = LocalDateTime.now(clock);
        BusinessOperationContext operationContext = BusinessOperationContext.user(
                UUID.randomUUID(),
                currentUser.userId(),
                now
        );

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
        businessAuditPublisher.publish(BusinessAuditEvent.single(
                operationContext,
                new BusinessAuditEntry(
                        BusinessAuditAction.APPROVAL_DECISION_RECORDED,
                        BusinessAuditEntityType.APPROVAL_DECISION,
                        savedDecision.id(),
                        BusinessAuditPayload.builder()
                                .put(BusinessAuditPayloadKey.LOAN_APPLICATION_ID, savedDecision.loanApplicationId())
                                .put(BusinessAuditPayloadKey.REVIEW_RECOMMENDATION_ID, savedDecision.reviewRecommendationId())
                                .put(BusinessAuditPayloadKey.APPROVAL_DECISION_ACTION, savedDecision.action())
                                .build()
                )
        ));
        approvalDecisionEventPublisher.publish(approvalMapper.toRecordedEvent(savedDecision, operationContext));

        return approvalMapper.toDto(savedDecision);
    }

    private void validateMakerChecker(
            ReviewRecommendation latestRecommendation,
            AuthenticatedUser currentUser
    ) {
        if (latestRecommendation.loanOfficerUserId().equals(currentUser.userId())) {
            throw new BusinessRuleViolationException(
                    "MAKER_CHECKER_VIOLATION",
                    "Approver must be different from the Loan Officer who submitted the recommendation."
            );
        }
    }
}
