package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.dto.ApplyReviewRecommendationCommand;
import com.meridian.platform.loan.application.dto.LoanApplicationReviewDto;
import com.meridian.platform.loan.application.port.in.ApplyReviewRecommendationUseCase;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.LoanDocumentChecklistPort;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationTransitionResult;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import com.meridian.platform.shared.domain.model.ActorType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class ApplyReviewRecommendationService implements ApplyReviewRecommendationUseCase {

    private final LoanApplicationRepository loanApplicationRepository;
    private final LoanDocumentChecklistPort documentChecklistPort;
    private final LoanApplicationStatusTransitionRecorder transitionRecorder;

    public ApplyReviewRecommendationService(
            LoanApplicationRepository loanApplicationRepository,
            LoanDocumentChecklistPort documentChecklistPort,
            LoanApplicationStatusTransitionRecorder transitionRecorder
    ) {
        this.loanApplicationRepository = loanApplicationRepository;
        this.documentChecklistPort = documentChecklistPort;
        this.transitionRecorder = transitionRecorder;
    }

    @Override
    @Transactional
    public LoanApplicationReviewDto applyReviewRecommendation(ApplyReviewRecommendationCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        Objects.requireNonNull(command.loanApplicationId(), "loanApplicationId must not be null");
        Objects.requireNonNull(command.recommendationId(), "recommendationId must not be null");
        Objects.requireNonNull(command.loanOfficerUserId(), "loanOfficerUserId must not be null");
        Objects.requireNonNull(command.action(), "action must not be null");
        Objects.requireNonNull(command.recommendedAt(), "recommendedAt must not be null");
        Objects.requireNonNull(command.operationContext(), "operationContext must not be null");
        validateOperationContext(command);

        loanApplicationRepository.acquireWorkflowLock(command.loanApplicationId());
        LoanApplication loanApplication = loanApplicationRepository.findByIdForUpdate(command.loanApplicationId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "LOAN_APPLICATION_NOT_FOUND",
                        "Loan application was not found."
                ));

        if (!documentChecklistPort.isProcessingReady(command.loanApplicationId())) {
            throw new BusinessStateConflictException(
                    "LOAN_REVIEW_DOCUMENTS_NOT_READY",
                    "Loan Application documents are not ready for recommendation."
            );
        }

        LoanApplicationTransitionResult transition = loanApplication.applyReviewRecommendation(command.action());
        LoanApplication savedApplication = loanApplicationRepository.save(transition.loanApplication());
        transitionRecorder.record(command.operationContext(), transition.facts(), command.reason());

        return new LoanApplicationReviewDto(
                savedApplication.id(),
                savedApplication.status().name()
        );
    }

    private void validateOperationContext(ApplyReviewRecommendationCommand command) {
        if (command.operationContext().actorType() != ActorType.USER
                || !command.loanOfficerUserId().equals(command.operationContext().actorUserId())
                || !command.recommendedAt().equals(command.operationContext().occurredAt())) {
            throw new BusinessRuleViolationException(
                    "INVALID_OPERATION_CONTEXT",
                    "Recommendation operation context must match the authoritative recommendation record."
            );
        }
    }
}
