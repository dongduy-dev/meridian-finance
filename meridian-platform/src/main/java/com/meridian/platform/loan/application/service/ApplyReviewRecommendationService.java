package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.dto.ApplyReviewRecommendationCommand;
import com.meridian.platform.loan.application.dto.LoanApplicationReviewDto;
import com.meridian.platform.loan.application.port.in.ApplyReviewRecommendationUseCase;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationTransitionResult;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import com.meridian.platform.shared.domain.model.ActionActor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class ApplyReviewRecommendationService implements ApplyReviewRecommendationUseCase {

    private final LoanApplicationRepository loanApplicationRepository;
    private final LoanApplicationLifecycleHistoryRecorder historyRecorder;

    public ApplyReviewRecommendationService(
            LoanApplicationRepository loanApplicationRepository,
            LoanApplicationLifecycleHistoryRecorder historyRecorder
    ) {
        this.loanApplicationRepository = loanApplicationRepository;
        this.historyRecorder = historyRecorder;
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
        Objects.requireNonNull(command.operationId(), "operationId must not be null");

        LoanApplication loanApplication = loanApplicationRepository.findByIdForUpdate(command.loanApplicationId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "LOAN_APPLICATION_NOT_FOUND",
                        "Loan application was not found."
                ));

        LoanApplicationTransitionResult transition = loanApplication.applyReviewRecommendationWithTransition(command.action());
        LoanApplication savedApplication = loanApplicationRepository.save(transition.loanApplication());
        historyRecorder.record(
                command.operationId(),
                ActionActor.user(command.loanOfficerUserId()),
                command.reason(),
                command.recommendedAt(),
                transition
        );

        return new LoanApplicationReviewDto(savedApplication.id(), savedApplication.status().name());
    }
}
