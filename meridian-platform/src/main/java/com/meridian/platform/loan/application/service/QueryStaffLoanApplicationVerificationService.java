package com.meridian.platform.loan.application.service;

import com.meridian.platform.document.domain.model.DocumentType;
import com.meridian.platform.loan.application.dto.CollateralAssessmentSnapshotDto;
import com.meridian.platform.loan.application.dto.StaffLoanApplicationVerificationDto;
import com.meridian.platform.loan.application.port.in.QueryStaffLoanApplicationVerificationUseCase;
import com.meridian.platform.loan.application.port.out.CollateralLoanVerificationRepository;
import com.meridian.platform.loan.application.port.out.CollateralRepository;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.LoanDocumentChecklistPort;
import com.meridian.platform.loan.application.port.out.SalaryAdvanceVerificationRepository;
import com.meridian.platform.loan.application.port.out.UnsecuredConsumerLoanVerificationRepository;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductVerificationResult;
import com.meridian.platform.loan.domain.model.collateral.Collateral;
import com.meridian.platform.loan.domain.model.collateral.CollateralLoanVerification;
import com.meridian.platform.loan.domain.model.salaryadvance.SalaryAdvanceVerification;
import com.meridian.platform.loan.domain.model.unsecured.UnsecuredConsumerLoanVerification;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class QueryStaffLoanApplicationVerificationService
        implements QueryStaffLoanApplicationVerificationUseCase {

    private static final Set<DocumentType> UCL_CORRECTION_TYPES = Set.of(
            DocumentType.INCOME_PROOF,
            DocumentType.BANK_STATEMENT,
            DocumentType.EMPLOYMENT_PROOF
    );

    private final LoanApplicationRepository applications;
    private final SalaryAdvanceVerificationRepository salaryAdvanceVerifications;
    private final UnsecuredConsumerLoanVerificationRepository uclVerifications;
    private final CollateralLoanVerificationRepository collateralVerifications;
    private final CollateralRepository collaterals;
    private final LoanDocumentChecklistPort documents;
    private final CurrentUserProvider currentUserProvider;

    public QueryStaffLoanApplicationVerificationService(
            LoanApplicationRepository applications,
            SalaryAdvanceVerificationRepository salaryAdvanceVerifications,
            UnsecuredConsumerLoanVerificationRepository uclVerifications,
            CollateralLoanVerificationRepository collateralVerifications,
            CollateralRepository collaterals,
            LoanDocumentChecklistPort documents,
            CurrentUserProvider currentUserProvider
    ) {
        this.applications = applications;
        this.salaryAdvanceVerifications = salaryAdvanceVerifications;
        this.uclVerifications = uclVerifications;
        this.collateralVerifications = collateralVerifications;
        this.collaterals = collaterals;
        this.documents = documents;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public StaffLoanApplicationVerificationDto query(UUID loanApplicationId) {
        Objects.requireNonNull(loanApplicationId, "loanApplicationId must not be null");
        requireAuthority(currentUserProvider.currentUser());

        LoanApplication application = applications.findById(loanApplicationId)
                .orElseThrow(QueryStaffLoanApplicationVerificationService::notFound);
        LoanDocumentChecklistPort.ChecklistReadinessSnapshot readiness = documents.readiness(
                application.id()
        );
        ProductProjection projection = productProjection(application, readiness.processingReady());

        return new StaffLoanApplicationVerificationDto(
                application.id(),
                application.applicationNumber(),
                application.productCode().name(),
                application.productType().name(),
                application.requestedAmount(),
                application.requestedTermMonths(),
                application.status().name(),
                application.submittedAt(),
                new StaffLoanApplicationVerificationDto.DocumentReadinessDto(
                        readiness.uploadComplete(), readiness.processingReady()
                ),
                projection.actions(),
                projection.verification(),
                projection.correctionTargets()
        );
    }

    private ProductProjection productProjection(LoanApplication application, boolean processingReady) {
        return switch (application.productCode()) {
            case SALARY_ADVANCE -> salaryAdvanceProjection(application);
            case UNSECURED_CONSUMER_LOAN -> uclProjection(application, processingReady);
            case COLLATERAL_LOAN -> collateralProjection(application, processingReady);
        };
    }

    private ProductProjection salaryAdvanceProjection(LoanApplication application) {
        SalaryAdvanceVerification verification = salaryAdvanceVerifications
                .findByLoanApplicationId(application.id())
                .orElseThrow(QueryStaffLoanApplicationVerificationService::systemConflict);
        return new ProductProjection(
                new StaffLoanApplicationVerificationDto.ActionPresentationDto(false, false),
                new StaffLoanApplicationVerificationDto.SalaryAdvanceVerificationDto(
                        verification.verificationSequence(),
                        verification.employeeVerificationOutcome().name(),
                        verification.productVerificationResult().name(),
                        verification.totalLimitSnapshot(),
                        verification.usedAmountSnapshot(),
                        verification.reservedAmountSnapshot(),
                        verification.availableLimitSnapshot(),
                        verification.verifiedAt()
                ),
                List.of()
        );
    }

    private ProductProjection uclProjection(LoanApplication application, boolean processingReady) {
        List<UnsecuredConsumerLoanVerification> history = uclVerifications
                .findAllByLoanApplicationIdOrderByVerificationSequenceAsc(application.id());
        if (history.isEmpty()) {
            throw new BusinessStateConflictException(
                    "UCL_VERIFICATION_REQUIRED",
                    "Unsecured Consumer Loan verification evidence is required."
            );
        }
        UnsecuredConsumerLoanVerification current = history.getLast();
        return new ProductProjection(
                manualActions(application, current.productVerificationResult(), processingReady),
                new StaffLoanApplicationVerificationDto.ManualVerificationDto(
                        toCycle(current),
                        history.stream().map(QueryStaffLoanApplicationVerificationService::toCycle).toList(),
                        null
                ),
                correctionTargets(application.id(), UCL_CORRECTION_TYPES)
        );
    }

    private ProductProjection collateralProjection(LoanApplication application, boolean processingReady) {
        List<CollateralLoanVerification> history = collateralVerifications
                .findAllByLoanApplicationIdOrderByVerificationSequenceAsc(application.id());
        if (history.isEmpty()) {
            throw new BusinessStateConflictException(
                    "COLLATERAL_VERIFICATION_REQUIRED",
                    "Collateral Loan verification evidence is required."
            );
        }
        List<Collateral> facts = collaterals.findByLoanApplicationId(application.id());
        if (facts.size() != 1) {
            throw systemConflict();
        }
        Collateral collateral = facts.getFirst();
        CollateralLoanVerification current = history.getLast();
        return new ProductProjection(
                manualActions(application, current.productVerificationResult(), processingReady),
                new StaffLoanApplicationVerificationDto.ManualVerificationDto(
                        toCycle(current),
                        history.stream().map(QueryStaffLoanApplicationVerificationService::toCycle).toList(),
                        new CollateralAssessmentSnapshotDto(
                                collateral.collateralType().name(),
                                collateral.description(),
                                collateral.estimatedValue(),
                                collateral.ownershipStatus(),
                                collateral.conditionNote()
                        )
                ),
                correctionTargets(
                        application.id(), Set.of(DocumentType.COLLATERAL_OWNERSHIP_EVIDENCE)
                )
        );
    }

    private List<StaffLoanApplicationVerificationDto.CorrectionTargetDto> correctionTargets(
            UUID loanApplicationId,
            Set<DocumentType> allowedTypes
    ) {
        return documents.currentVersionTargets(loanApplicationId).stream()
                .filter(target -> allowedTypes.contains(target.documentType()))
                .sorted(Comparator.comparing(target -> target.documentType().name()))
                .map(target -> new StaffLoanApplicationVerificationDto.CorrectionTargetDto(
                        target.checklistItemId(),
                        target.documentType().name(),
                        target.requirementStatus().name(),
                        target.currentDocumentVersionId()
                ))
                .toList();
    }

    private static StaffLoanApplicationVerificationDto.ActionPresentationDto manualActions(
            LoanApplication application,
            ProductVerificationResult result,
            boolean processingReady
    ) {
        boolean pending = result == ProductVerificationResult.PENDING_MANUAL_REVIEW;
        return new StaffLoanApplicationVerificationDto.ActionPresentationDto(
                application.status() == LoanApplicationStatus.SUBMITTED && pending && processingReady,
                application.status() == LoanApplicationStatus.VERIFICATION_PENDING
                        && pending
                        && processingReady
        );
    }

    private static StaffLoanApplicationVerificationDto.VerificationCycleDto toCycle(
            UnsecuredConsumerLoanVerification verification
    ) {
        return new StaffLoanApplicationVerificationDto.VerificationCycleDto(
                verification.id(),
                verification.verificationSequence(),
                verification.productVerificationResult().name(),
                verification.createdAt(),
                verification.reviewedAt()
        );
    }

    private static StaffLoanApplicationVerificationDto.VerificationCycleDto toCycle(
            CollateralLoanVerification verification
    ) {
        return new StaffLoanApplicationVerificationDto.VerificationCycleDto(
                verification.id(),
                verification.verificationSequence(),
                verification.productVerificationResult().name(),
                verification.createdAt(),
                verification.reviewedAt()
        );
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

    private record ProductProjection(
            StaffLoanApplicationVerificationDto.ActionPresentationDto actions,
            StaffLoanApplicationVerificationDto.ProductVerificationDto verification,
            List<StaffLoanApplicationVerificationDto.CorrectionTargetDto> correctionTargets
    ) {
    }
}
