package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.dto.LoanApplicationReviewDto;
import com.meridian.platform.loan.application.port.in.StartLoanApplicationReviewUseCase;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationTransitionResult;
import com.meridian.platform.shared.application.audit.AuditAction;
import com.meridian.platform.shared.application.audit.AuditEntityType;
import com.meridian.platform.shared.application.audit.AuditEventPublisher;
import com.meridian.platform.shared.application.audit.AuditRecordRequestedEvent;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import com.meridian.platform.shared.domain.model.ActionActor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class StartLoanApplicationReviewService implements StartLoanApplicationReviewUseCase {

    private final LoanApplicationRepository loanApplicationRepository;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;
    private final LoanApplicationLifecycleHistoryRecorder historyRecorder;
    private final AuditEventPublisher auditEventPublisher;

    public StartLoanApplicationReviewService(
            LoanApplicationRepository loanApplicationRepository,
            CurrentUserProvider currentUserProvider,
            Clock clock,
            LoanApplicationLifecycleHistoryRecorder historyRecorder,
            AuditEventPublisher auditEventPublisher
    ) {
        this.loanApplicationRepository = loanApplicationRepository;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
        this.historyRecorder = historyRecorder;
        this.auditEventPublisher = auditEventPublisher;
    }

    @Override
    @Transactional
    public LoanApplicationReviewDto startReview(UUID loanApplicationId) {
        Objects.requireNonNull(loanApplicationId, "loanApplicationId must not be null");

        LoanApplication loanApplication = loanApplicationRepository.findByIdForUpdate(loanApplicationId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "LOAN_APPLICATION_NOT_FOUND",
                        "Loan application was not found."
                ));
        LocalDateTime now = LocalDateTime.now(clock);
        UUID operationId = UUID.randomUUID();
        ActionActor actor = ActionActor.user(currentUserProvider.currentUser().userId());
        LoanApplicationTransitionResult transition = loanApplication.startReviewWithTransition();
        LoanApplication savedApplication = loanApplicationRepository.save(transition.loanApplication());
        historyRecorder.record(operationId, actor, null, now, transition);
        auditEventPublisher.publish(new AuditRecordRequestedEvent(
                operationId, (short) 1, actor, AuditEntityType.LOAN_APPLICATION, savedApplication.id(),
                AuditAction.REVIEW_STARTED, List.of(), now
        ));

        return new LoanApplicationReviewDto(savedApplication.id(), savedApplication.status().name());
    }
}
