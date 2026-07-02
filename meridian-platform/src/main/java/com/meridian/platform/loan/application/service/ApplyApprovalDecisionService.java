package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.dto.ApplyApprovalDecisionCommand;
import com.meridian.platform.loan.application.dto.LoanApplicationReviewDto;
import com.meridian.platform.loan.application.port.in.ApplyApprovalDecisionUseCase;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.SalaryAdvanceLimitMovementRepository;
import com.meridian.platform.loan.application.port.out.SalaryAdvanceLimitRepository;
import com.meridian.platform.loan.application.port.out.SalaryAdvanceVerificationRepository;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApprovalDecisionAction;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.SalaryAdvanceLimit;
import com.meridian.platform.loan.domain.model.SalaryAdvanceLimitMovement;
import com.meridian.platform.loan.domain.model.SalaryAdvanceVerification;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
public class ApplyApprovalDecisionService implements ApplyApprovalDecisionUseCase {

    private final LoanApplicationRepository loanApplicationRepository;
    private final SalaryAdvanceVerificationRepository salaryAdvanceVerificationRepository;
    private final SalaryAdvanceLimitRepository salaryAdvanceLimitRepository;
    private final SalaryAdvanceLimitMovementRepository salaryAdvanceLimitMovementRepository;

    public ApplyApprovalDecisionService(
            LoanApplicationRepository loanApplicationRepository,
            SalaryAdvanceVerificationRepository salaryAdvanceVerificationRepository,
            SalaryAdvanceLimitRepository salaryAdvanceLimitRepository,
            SalaryAdvanceLimitMovementRepository salaryAdvanceLimitMovementRepository
    ) {
        this.loanApplicationRepository = loanApplicationRepository;
        this.salaryAdvanceVerificationRepository = salaryAdvanceVerificationRepository;
        this.salaryAdvanceLimitRepository = salaryAdvanceLimitRepository;
        this.salaryAdvanceLimitMovementRepository = salaryAdvanceLimitMovementRepository;
    }

    @Override
    @Transactional
    public LoanApplicationReviewDto applyApprovalDecision(ApplyApprovalDecisionCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        Objects.requireNonNull(command.loanApplicationId(), "loanApplicationId must not be null");
        Objects.requireNonNull(command.decisionId(), "decisionId must not be null");
        Objects.requireNonNull(command.reviewRecommendationId(), "reviewRecommendationId must not be null");
        Objects.requireNonNull(command.approverUserId(), "approverUserId must not be null");
        Objects.requireNonNull(command.action(), "action must not be null");
        Objects.requireNonNull(command.decidedAt(), "decidedAt must not be null");

        LoanApplication loanApplication = loanApplicationRepository.findByIdForUpdate(command.loanApplicationId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "LOAN_APPLICATION_NOT_FOUND",
                        "Loan application was not found."
                ));

        LoanApplication transitionedApplication = loanApplication.applyApprovalDecision(command.action());
        if (shouldReleaseSalaryAdvanceReservation(loanApplication, command.action())) {
            releaseSalaryAdvanceReservation(loanApplication, command);
        }

        LoanApplication savedApplication = loanApplicationRepository.save(transitionedApplication);

        return new LoanApplicationReviewDto(
                savedApplication.id(),
                savedApplication.status().name()
        );
    }

    private boolean shouldReleaseSalaryAdvanceReservation(
            LoanApplication loanApplication,
            LoanApprovalDecisionAction action
    ) {
        return loanApplication.productCode() == ProductCode.SALARY_ADVANCE
                && action == LoanApprovalDecisionAction.REJECT;
    }

    private void releaseSalaryAdvanceReservation(
            LoanApplication loanApplication,
            ApplyApprovalDecisionCommand command
    ) {
        SalaryAdvanceVerification verification = salaryAdvanceVerificationRepository
                .findByLoanApplicationId(loanApplication.id())
                .orElseThrow(() -> new EntityNotFoundException(
                        "SALARY_ADVANCE_VERIFICATION_NOT_FOUND",
                        "Salary Advance verification was not found for the loan application."
                ));

        SalaryAdvanceLimit currentLimit = salaryAdvanceLimitRepository
                .findByCustomerIdAndCustomerPartnerEmployeeLinkIdForUpdate(
                        verification.customerId(),
                        verification.customerPartnerEmployeeLinkId()
                )
                .orElseThrow(() -> new EntityNotFoundException(
                        "SALARY_ADVANCE_LIMIT_NOT_FOUND",
                        "Salary Advance limit was not found for the loan application."
                ));

        SalaryAdvanceLimit releasedLimit = currentLimit.releaseReservation(loanApplication.requestedAmount());
        SalaryAdvanceLimit savedLimit = salaryAdvanceLimitRepository.save(releasedLimit);
        salaryAdvanceLimitMovementRepository.save(SalaryAdvanceLimitMovement.reservationReleased(
                UUID.randomUUID(),
                savedLimit.id(),
                loanApplication.id(),
                loanApplication.requestedAmount(),
                command.decidedAt()
        ));
    }
}
