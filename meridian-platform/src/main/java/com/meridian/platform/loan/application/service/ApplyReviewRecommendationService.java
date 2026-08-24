package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.service.collateral.CollateralLoanReviewGate;

import com.meridian.platform.loan.application.dto.ApplyReviewRecommendationCommand;
import com.meridian.platform.loan.application.dto.LoanApplicationReviewDto;
import com.meridian.platform.loan.application.port.in.ApplyReviewRecommendationUseCase;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.LoanDocumentChecklistPort;
import com.meridian.platform.loan.application.port.out.LoanReviewCycleRepository;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationReviewCycle;
import com.meridian.platform.loan.domain.model.LoanApplicationTransitionResult;
import com.meridian.platform.loan.domain.model.LoanReviewRecommendationAction;
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
    private final LoanReviewCycleRepository reviewCycleRepository;
    private final CustomerCorrectionWorkflowService customerCorrectionWorkflowService;
    private final LoanApplicationStatusTransitionRecorder transitionRecorder;
    private final CollateralLoanReviewGate collateralLoanReviewGate;

    public ApplyReviewRecommendationService(
            LoanApplicationRepository loanApplicationRepository,
            LoanDocumentChecklistPort documentChecklistPort,
            LoanReviewCycleRepository reviewCycleRepository,
            CustomerCorrectionWorkflowService customerCorrectionWorkflowService,
            LoanApplicationStatusTransitionRecorder transitionRecorder,
            CollateralLoanReviewGate collateralLoanReviewGate
    ) {
        this.loanApplicationRepository = loanApplicationRepository;
        this.documentChecklistPort = documentChecklistPort;
        this.reviewCycleRepository = reviewCycleRepository;
        this.customerCorrectionWorkflowService = customerCorrectionWorkflowService;
        this.transitionRecorder = transitionRecorder;
        this.collateralLoanReviewGate = collateralLoanReviewGate;
    }

    @Override
    @Transactional
    public LoanApplicationReviewDto applyReviewRecommendation(ApplyReviewRecommendationCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        Objects.requireNonNull(command.loanApplicationId(), "loanApplicationId must not be null");
        Objects.requireNonNull(command.recommendationId(), "recommendationId must not be null");
        Objects.requireNonNull(command.reviewCycleId(), "reviewCycleId must not be null");
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

        collateralLoanReviewGate.requireProgressionAllowed(loanApplication);

        LoanApplicationReviewCycle activeCycle = reviewCycleRepository
                .findActiveByLoanApplicationIdForUpdate(command.loanApplicationId())
                .orElseThrow(() -> new BusinessStateConflictException(
                        "REVIEW_CYCLE_REQUIRED",
                        "An active review cycle is required."
                ));
        if (!activeCycle.id().equals(command.reviewCycleId())) {
            throw new BusinessStateConflictException(
                    "STALE_REVIEW_CYCLE", "The recommendation review cycle is no longer active.");
        }

        if (!documentChecklistPort.isProcessingReady(command.loanApplicationId())) {
            throw new BusinessStateConflictException(
                    "LOAN_REVIEW_DOCUMENTS_NOT_READY",
                    "Loan Application documents are not ready for recommendation."
            );
        }

        LoanApplicationTransitionResult transition = loanApplication.applyReviewRecommendation(command.action());
        if (command.action() == com.meridian.platform.loan.domain.model.LoanReviewRecommendationAction
                .RETURN_TO_CUSTOMER_REVISION
                || command.action() == com.meridian.platform.loan.domain.model.LoanReviewRecommendationAction.REQUEST_STAFF_CORRECTION) {
            Objects.requireNonNull(command.reasonCode(), "reasonCode must not be null");
            Objects.requireNonNull(command.correctionPlan(), "correctionPlan must not be null");
            customerCorrectionWorkflowService.createFromRecommendation(
                    loanApplication,
                    activeCycle,
                    command.action().name(),
                    command.reasonCode(),
                    command.correctionPlan(),
                    command.operationContext()
            );
        }
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
