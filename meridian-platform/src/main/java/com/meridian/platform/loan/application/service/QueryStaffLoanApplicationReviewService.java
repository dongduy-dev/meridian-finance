package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.dto.StaffLoanApplicationReviewDto;
import com.meridian.platform.loan.application.port.in.QueryStaffLoanApplicationReviewUseCase;
import com.meridian.platform.loan.application.port.out.CollateralLoanVerificationRepository;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.LoanDocumentChecklistPort;
import com.meridian.platform.loan.application.port.out.LoanReviewCycleRepository;
import com.meridian.platform.loan.application.port.out.SalaryAdvanceVerificationRepository;
import com.meridian.platform.loan.application.port.out.UnsecuredConsumerLoanVerificationRepository;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationReviewCycle;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.ProductVerificationResult;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
public class QueryStaffLoanApplicationReviewService
        implements QueryStaffLoanApplicationReviewUseCase {

    private final LoanApplicationRepository applications;
    private final SalaryAdvanceVerificationRepository salaryAdvanceVerifications;
    private final UnsecuredConsumerLoanVerificationRepository uclVerifications;
    private final CollateralLoanVerificationRepository collateralVerifications;
    private final LoanReviewCycleRepository reviewCycles;
    private final LoanDocumentChecklistPort documents;
    private final CurrentUserProvider currentUserProvider;

    public QueryStaffLoanApplicationReviewService(
            LoanApplicationRepository applications,
            SalaryAdvanceVerificationRepository salaryAdvanceVerifications,
            UnsecuredConsumerLoanVerificationRepository uclVerifications,
            CollateralLoanVerificationRepository collateralVerifications,
            LoanReviewCycleRepository reviewCycles,
            LoanDocumentChecklistPort documents,
            CurrentUserProvider currentUserProvider
    ) {
        this.applications = applications;
        this.salaryAdvanceVerifications = salaryAdvanceVerifications;
        this.uclVerifications = uclVerifications;
        this.collateralVerifications = collateralVerifications;
        this.reviewCycles = reviewCycles;
        this.documents = documents;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public StaffLoanApplicationReviewDto query(UUID loanApplicationId) {
        Objects.requireNonNull(loanApplicationId, "loanApplicationId must not be null");
        requireAuthority(currentUserProvider.currentUser());

        LoanApplication application = applications.findById(loanApplicationId)
                .orElseThrow(QueryStaffLoanApplicationReviewService::notFound);
        LoanDocumentChecklistPort.ChecklistReadinessSnapshot readiness = documents.readiness(
                application.id()
        );
        ProductVerificationResult verificationResult = verificationResult(application);
        boolean productReady = verificationResult == ProductVerificationResult.VERIFIED;
        LoanApplicationReviewCycle currentCycle = reviewCycles
                .findLatestByLoanApplicationId(application.id())
                .orElse(null);

        return new StaffLoanApplicationReviewDto(
                application.id(),
                application.applicationNumber(),
                application.productCode().name(),
                application.productType().name(),
                application.requestedAmount(),
                application.requestedTermMonths(),
                application.status().name(),
                application.submittedAt(),
                new StaffLoanApplicationReviewDto.DocumentReadinessDto(
                        readiness.uploadComplete(), readiness.processingReady()
                ),
                new StaffLoanApplicationReviewDto.ProductReadinessDto(
                        verificationResult.name(), productReady
                ),
                application.status() == LoanApplicationStatus.SUBMITTED
                        && readiness.processingReady()
                        && productReady,
                currentCycle == null ? null : new StaffLoanApplicationReviewDto.ReviewCycleDto(
                        currentCycle.id(),
                        currentCycle.cycleNumber(),
                        currentCycle.status().name(),
                        currentCycle.startedAt(),
                        currentCycle.endedAt()
                )
        );
    }

    private ProductVerificationResult verificationResult(LoanApplication application) {
        return switch (application.productCode()) {
            case SALARY_ADVANCE -> salaryAdvanceVerifications.findByLoanApplicationId(application.id())
                    .orElseThrow(QueryStaffLoanApplicationReviewService::systemConflict)
                    .productVerificationResult();
            case UNSECURED_CONSUMER_LOAN -> uclVerifications
                    .findLatestByLoanApplicationId(application.id())
                    .orElseThrow(() -> new BusinessStateConflictException(
                            "UCL_VERIFICATION_REQUIRED",
                            "Unsecured Consumer Loan verification evidence is required."
                    ))
                    .productVerificationResult();
            case COLLATERAL_LOAN -> collateralVerifications
                    .findLatestByLoanApplicationId(application.id())
                    .orElseThrow(() -> new BusinessStateConflictException(
                            "COLLATERAL_VERIFICATION_REQUIRED",
                            "Collateral Loan verification evidence is required."
                    ))
                    .productVerificationResult();
        };
    }

    private static void requireAuthority(AuthenticatedUser actor) {
        if (!"STAFF".equals(actor.userType())
                || actor.optionalCustomerId().isPresent()
                || !actor.hasPermission("loan:review")) {
            throw new AuthorizationException(
                    "LOAN_REVIEW_ACCESS_DENIED",
                    "Loan review access is denied."
            );
        }
    }

    private static EntityNotFoundException notFound() {
        return new EntityNotFoundException(
                "LOAN_APPLICATION_NOT_FOUND", "Loan Application was not found."
        );
    }

    private static BusinessStateConflictException systemConflict() {
        return new BusinessStateConflictException(
                "SYSTEM_STATE_CONFLICT", "Authoritative product verification evidence is inconsistent."
        );
    }
}
