package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.dto.CorrectionResubmissionDto;
import com.meridian.platform.loan.application.dto.CorrectionResubmissionRequest;
import com.meridian.platform.loan.application.port.in.ResubmitOwnCorrectionUseCase;
import com.meridian.platform.loan.application.port.in.ResubmitStaffCorrectionUseCase;
import com.meridian.platform.loan.application.port.out.CustomerReadinessPort;
import com.meridian.platform.loan.application.port.out.CustomerReadinessSnapshot;
import com.meridian.platform.loan.application.port.out.CollateralLoanVerificationRepository;
import com.meridian.platform.loan.application.port.out.CollateralRepository;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.LoanCorrectionRepository;
import com.meridian.platform.loan.application.port.out.LoanDocumentChecklistPort;
import com.meridian.platform.loan.application.port.out.LoanProductRepository;
import com.meridian.platform.loan.application.port.out.LoanReviewCycleRepository;
import com.meridian.platform.loan.application.port.out.OutstandingLoanAccountQuery;
import com.meridian.platform.loan.application.port.out.PartnerEligibilityAssessment;
import com.meridian.platform.loan.application.port.out.PartnerEligibilityPort;
import com.meridian.platform.loan.application.port.out.SalaryAdvanceLimitMovementRepository;
import com.meridian.platform.loan.application.port.out.SalaryAdvanceLimitRepository;
import com.meridian.platform.loan.application.port.out.SalaryAdvanceVerificationRepository;
import com.meridian.platform.loan.application.port.out.UnsecuredConsumerLoanVerificationRepository;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.collateral.CollateralLoanVerification;
import com.meridian.platform.loan.domain.model.LoanApplicationReviewCycle;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.LoanApplicationTransitionResult;
import com.meridian.platform.loan.domain.model.LoanCorrectionRequest;
import com.meridian.platform.loan.domain.model.LoanCorrectionRequestStatus;
import com.meridian.platform.loan.domain.model.LoanCorrectionResponsibility;
import com.meridian.platform.loan.domain.model.LoanCorrectionTask;
import com.meridian.platform.loan.domain.model.LoanCorrectionTaskStatus;
import com.meridian.platform.loan.domain.model.LoanProduct;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductVerificationResult;
import com.meridian.platform.loan.domain.model.salaryadvance.SalaryAdvanceLimit;
import com.meridian.platform.loan.domain.model.salaryadvance.SalaryAdvanceLimitMovementType;
import com.meridian.platform.loan.domain.model.salaryadvance.SalaryAdvanceLimitStatus;
import com.meridian.platform.loan.domain.model.salaryadvance.SalaryAdvanceVerification;
import com.meridian.platform.loan.domain.model.unsecured.UnsecuredConsumerLoanVerification;
import com.meridian.platform.loan.domain.model.salaryadvance.VerifiedPartnerEmployeeLinkSnapshot;
import com.meridian.platform.loan.domain.service.salaryadvance.SalaryAdvanceApplicationPolicy;
import com.meridian.platform.loan.domain.service.collateral.CollateralLoanApplicationPolicy;
import com.meridian.platform.loan.domain.service.unsecured.UnsecuredConsumerLoanApplicationPolicy;
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
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class ResubmitCustomerCorrectionService
        implements ResubmitOwnCorrectionUseCase, ResubmitStaffCorrectionUseCase {

    private final LoanApplicationRepository applicationRepository;
    private final LoanCorrectionRepository correctionRepository;
    private final LoanReviewCycleRepository reviewCycleRepository;
    private final LoanDocumentChecklistPort documentChecklistPort;
    private final CustomerReadinessPort customerReadinessPort;
    private final LoanProductRepository productRepository;
    private final OutstandingLoanAccountQuery outstandingLoanAccounts;
    private final PartnerEligibilityPort partnerEligibilityPort;
    private final SalaryAdvanceLimitRepository limitRepository;
    private final SalaryAdvanceLimitMovementRepository movementRepository;
    private final SalaryAdvanceVerificationRepository salaryVerificationRepository;
    private final UnsecuredConsumerLoanVerificationRepository uclVerificationRepository;
    private final CollateralLoanVerificationRepository collateralVerificationRepository;
    private final CollateralRepository collateralRepository;
    private final LoanApplicationStatusTransitionRecorder transitionRecorder;
    private final BusinessAuditPublisher auditPublisher;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;
    private final SalaryAdvanceApplicationPolicy salaryAdvancePolicy =
            new SalaryAdvanceApplicationPolicy();
    private final UnsecuredConsumerLoanApplicationPolicy uclPolicy =
            new UnsecuredConsumerLoanApplicationPolicy();
    private final CollateralLoanApplicationPolicy collateralPolicy =
            new CollateralLoanApplicationPolicy();

    public ResubmitCustomerCorrectionService(
            LoanApplicationRepository applicationRepository,
            LoanCorrectionRepository correctionRepository,
            LoanReviewCycleRepository reviewCycleRepository,
            LoanDocumentChecklistPort documentChecklistPort,
            CustomerReadinessPort customerReadinessPort,
            LoanProductRepository productRepository,
            OutstandingLoanAccountQuery outstandingLoanAccounts,
            PartnerEligibilityPort partnerEligibilityPort,
            SalaryAdvanceLimitRepository limitRepository,
            SalaryAdvanceLimitMovementRepository movementRepository,
            SalaryAdvanceVerificationRepository salaryVerificationRepository,
            UnsecuredConsumerLoanVerificationRepository uclVerificationRepository,
            CollateralLoanVerificationRepository collateralVerificationRepository,
            CollateralRepository collateralRepository,
            LoanApplicationStatusTransitionRecorder transitionRecorder,
            BusinessAuditPublisher auditPublisher,
            CurrentUserProvider currentUserProvider,
            Clock clock
    ) {
        this.applicationRepository = applicationRepository;
        this.correctionRepository = correctionRepository;
        this.reviewCycleRepository = reviewCycleRepository;
        this.documentChecklistPort = documentChecklistPort;
        this.customerReadinessPort = customerReadinessPort;
        this.productRepository = productRepository;
        this.outstandingLoanAccounts = outstandingLoanAccounts;
        this.partnerEligibilityPort = partnerEligibilityPort;
        this.limitRepository = limitRepository;
        this.movementRepository = movementRepository;
        this.salaryVerificationRepository = salaryVerificationRepository;
        this.uclVerificationRepository = uclVerificationRepository;
        this.collateralVerificationRepository = collateralVerificationRepository;
        this.collateralRepository = collateralRepository;
        this.transitionRecorder = transitionRecorder;
        this.auditPublisher = auditPublisher;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    @Override
    @Transactional
    public CorrectionResubmissionDto resubmit(
            UUID loanApplicationId,
            CorrectionResubmissionRequest command
    ) {
        return resubmitInternal(loanApplicationId, command, ResubmissionActor.CUSTOMER);
    }

    @Override
    @Transactional
    public CorrectionResubmissionDto resubmitAsStaff(
            UUID loanApplicationId,
            CorrectionResubmissionRequest command
    ) {
        return resubmitInternal(loanApplicationId, command, ResubmissionActor.STAFF);
    }

    private CorrectionResubmissionDto resubmitInternal(
            UUID loanApplicationId,
            CorrectionResubmissionRequest command,
            ResubmissionActor actor
    ) {
        Objects.requireNonNull(loanApplicationId, "loanApplicationId must not be null");
        Objects.requireNonNull(command, "command must not be null");
        Objects.requireNonNull(command.resubmissionRequestId(), "resubmissionRequestId must not be null");

        AuthenticatedUser user = currentUserProvider.currentUser();
        UUID authenticatedCustomerId = actor == ResubmissionActor.CUSTOMER
                ? user.requireCustomerId() : null;
        if (actor == ResubmissionActor.STAFF && !user.hasPermission("loan:correction:staff")) {
            throw new AuthorizationException(
                    "CORRECTION_RESUBMISSION_DENIED",
                    "Staff correction permission is required."
            );
        }
        LocalDateTime now = LocalDateTime.now(clock);
        BusinessOperationContext operation = BusinessOperationContext.user(
                UUID.randomUUID(),
                user.userId(),
                now
        );

        applicationRepository.acquireWorkflowLock(loanApplicationId);
        LoanApplication application = applicationRepository.findByIdForUpdate(loanApplicationId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "LOAN_APPLICATION_NOT_FOUND",
                        "Loan Application was not found."
                ));
        if (actor == ResubmissionActor.CUSTOMER
                && !application.customerId().equals(authenticatedCustomerId)) {
            throw new AuthorizationException(
                    "CORRECTION_ACCESS_DENIED",
                    "Customer cannot resubmit another Loan Application."
            );
        }

        LoanCorrectionRequest latest = correctionRepository
                .findLatestRequestByApplicationId(loanApplicationId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "CORRECTION_REQUEST_NOT_FOUND",
                        "Correction request was not found."
                ));
        if (latest.status() == LoanCorrectionRequestStatus.RESUBMITTED) {
            List<LoanCorrectionTask> completedTasks =
                    correctionRepository.findTasksByRequestIdForUpdate(latest.id());
            validateResubmitter(actor, completedTasks);
            if (command.resubmissionRequestId().equals(latest.resubmissionRequestId())) {
                return toDto(latest, application);
            }
            throw new BusinessStateConflictException(
                    "CORRECTION_ALREADY_RESUBMITTED",
                    "Correction request was already resubmitted."
            );
        }

        LoanCorrectionRequest request = correctionRepository
                .findActiveRequestByApplicationIdForUpdate(loanApplicationId)
                .orElseThrow(() -> new BusinessStateConflictException(
                        "CORRECTION_REQUEST_CONFLICT",
                        "No active correction request is available."
                ));
        List<LoanCorrectionTask> tasks = correctionRepository
                .findTasksByRequestIdForUpdate(request.id());
        validateResubmitter(actor, tasks);
        if (tasks.stream().anyMatch(task -> task.status() != LoanCorrectionTaskStatus.COMPLETED)) {
            throw new BusinessStateConflictException(
                    "CORRECTION_TASKS_INCOMPLETE",
                    "Every correction task must be complete before resubmission."
            );
        }
        request = request.markReady(tasks, now);

        validateCustomerReadiness(application.customerId());
        LoanProduct product = productRepository.findByProductCode(application.productCode())
                .orElseThrow(() -> new EntityNotFoundException(
                        "PRODUCT_NOT_FOUND",
                        "Loan product was not found."
                ));
        validateProduct(application, product);

        applicationRepository.acquireCustomerProductLock(
                application.customerId(),
                application.productCode()
        );
        assertNoOtherBlockingApplication(application);

        LoanDocumentChecklistPort.ChecklistReadinessSnapshot documentReadiness =
                documentChecklistPort.readiness(loanApplicationId);
        if (!documentReadiness.uploadComplete()) {
            throw new BusinessStateConflictException(
                    "CORRECTION_DOCUMENTS_INCOMPLETE",
                    "Required correction document uploads are incomplete."
            );
        }

        ProductResubmission productResubmission = switch (application.productCode()) {
            case SALARY_ADVANCE -> prepareSalaryAdvanceResubmission(
                    application,
                    request,
                    product,
                    documentReadiness,
                    now
            );
            case UNSECURED_CONSUMER_LOAN -> prepareUclResubmission(
                    application,
                    request,
                    now
            );
            case COLLATERAL_LOAN -> prepareCollateralResubmission(
                    application,
                    request,
                    now
            );
        };

        int nextReviewCycleNumber = reviewCycleRepository.nextCycleNumber(loanApplicationId);
        if (request.sourceReviewCycleId() != null) {
            LoanApplicationReviewCycle sourceCycle = reviewCycleRepository
                    .findByIdForUpdate(request.sourceReviewCycleId())
                    .orElseThrow(() -> new BusinessStateConflictException(
                            "REVIEW_CYCLE_CONFLICT",
                            "Correction source review cycle was not found."
                    ));
            reviewCycleRepository.save(sourceCycle.corrected(now));
        }
        if (productResubmission.targetStatus() == LoanApplicationStatus.UNDER_REVIEW) {
            reviewCycleRepository.save(LoanApplicationReviewCycle.active(
                    UUID.randomUUID(),
                    loanApplicationId,
                    nextReviewCycleNumber,
                    now
            ));
        }

        LoanApplicationTransitionResult transition = application.resubmitCorrection(
                productResubmission.targetStatus()
        );
        LoanApplication savedApplication = applicationRepository.save(transition.loanApplication());
        LoanCorrectionRequest resubmitted = correctionRepository.saveRequest(
                request.resubmit(command.resubmissionRequestId(), now)
        );
        transitionRecorder.record(operation, transition.facts(), null);

        List<BusinessAuditEntry> auditEntries = new ArrayList<>(productResubmission.auditEntries());
        auditEntries.add(new BusinessAuditEntry(
                BusinessAuditAction.CORRECTION_RESUBMITTED,
                BusinessAuditEntityType.LOAN_CORRECTION_REQUEST,
                request.id(),
                BusinessAuditPayload.builder()
                        .put(BusinessAuditPayloadKey.LOAN_APPLICATION_ID, loanApplicationId)
                        .put(BusinessAuditPayloadKey.CORRECTION_REQUEST_ID, request.id())
                        .put(
                                BusinessAuditPayloadKey.RESUBMISSION_TARGET_STATUS,
                                productResubmission.targetStatus()
                        )
                        .build()
        ));
        auditPublisher.publish(new BusinessAuditEvent(operation, auditEntries));
        return toDto(resubmitted, savedApplication);
    }

    private ProductResubmission prepareSalaryAdvanceResubmission(
            LoanApplication application,
            LoanCorrectionRequest request,
            LoanProduct product,
            LoanDocumentChecklistPort.ChecklistReadinessSnapshot documentReadiness,
            LocalDateTime now
    ) {
        SalaryAdvanceVerification previousVerification = salaryVerificationRepository
                .findByLoanApplicationId(application.id())
                .orElseThrow(() -> new BusinessStateConflictException(
                        "SALARY_ADVANCE_VERIFICATION_REQUIRED",
                        "Existing Salary Advance verification was not found."
                ));
        VerifiedPartnerEmployeeLinkSnapshot partnerSnapshot = requireEligiblePartnerEmployeeLink(
                application.customerId(),
                previousVerification.customerPartnerEmployeeLinkId()
        );

        limitRepository.acquireCustomerLinkLock(
                application.customerId(),
                partnerSnapshot.customerPartnerEmployeeLinkId()
        );
        assertNoOtherBlockingApplication(application);
        assertNoOutstandingLoanAccountExists(application.customerId(), application.productCode());
        SalaryAdvanceLimit limit = limitRepository
                .findByCustomerIdAndCustomerPartnerEmployeeLinkIdForUpdate(
                        application.customerId(),
                        partnerSnapshot.customerPartnerEmployeeLinkId()
                )
                .orElseThrow(() -> new BusinessStateConflictException(
                        "SALARY_ADVANCE_LIMIT_UNAVAILABLE",
                        "Salary Advance limit was not found."
                ));
        validateReservation(application, limit);
        BigDecimal effectiveLimit = salaryAdvancePolicy.calculateEffectiveTotalLimit(
                product,
                partnerSnapshot
        );
        SalaryAdvanceVerification revalidation = SalaryAdvanceVerification.revalidated(
                UUID.randomUUID(),
                previousVerification.verificationSequence() + 1,
                request.id(),
                application,
                limit,
                effectiveLimit,
                partnerSnapshot,
                now
        );
        salaryVerificationRepository.save(revalidation);

        boolean hasPriorReviewCycle = reviewCycleRepository.nextCycleNumber(application.id()) > 1;
        LoanApplicationStatus target = hasPriorReviewCycle && documentReadiness.processingReady()
                ? LoanApplicationStatus.UNDER_REVIEW
                : LoanApplicationStatus.SUBMITTED;
        BusinessAuditEntry revalidationAudit = new BusinessAuditEntry(
                BusinessAuditAction.SALARY_ADVANCE_REVALIDATED,
                BusinessAuditEntityType.SALARY_ADVANCE_VERIFICATION,
                revalidation.id(),
                BusinessAuditPayload.builder()
                        .put(BusinessAuditPayloadKey.LOAN_APPLICATION_ID, application.id())
                        .put(BusinessAuditPayloadKey.CORRECTION_REQUEST_ID, request.id())
                        .build()
        );
        return new ProductResubmission(target, List.of(revalidationAudit));
    }

    private ProductResubmission prepareUclResubmission(
            LoanApplication application,
            LoanCorrectionRequest request,
            LocalDateTime now
    ) {
        assertNoOutstandingLoanAccountExists(application.customerId(), application.productCode());
        UnsecuredConsumerLoanVerification latestVerification = uclVerificationRepository
                .findLatestByLoanApplicationIdForUpdate(application.id())
                .orElseThrow(() -> new BusinessStateConflictException(
                        "UCL_VERIFICATION_REQUIRED",
                        "Unsecured Consumer Loan verification evidence is required."
                ));
        if (latestVerification.productVerificationResult() == ProductVerificationResult.FAILED) {
            throw new BusinessStateConflictException(
                    "CORRECTION_RESUBMISSION_NOT_ALLOWED",
                    "A failed Unsecured Consumer Loan application cannot be resubmitted."
            );
        }
        if (latestVerification.productVerificationResult()
                != ProductVerificationResult.PENDING_MANUAL_REVIEW) {
            uclVerificationRepository.save(UnsecuredConsumerLoanVerification.pendingReverification(
                    UUID.randomUUID(),
                    application.id(),
                    latestVerification.verificationSequence() + 1,
                    request.id(),
                    now
            ));
        }
        return new ProductResubmission(LoanApplicationStatus.SUBMITTED, List.of());
    }

    private ProductResubmission prepareCollateralResubmission(
            LoanApplication application,
            LoanCorrectionRequest request,
            LocalDateTime now
    ) {
        if (collateralRepository.findByLoanApplicationId(application.id()).size() != 1) {
            throw new BusinessStateConflictException(
                    "SYSTEM_STATE_CONFLICT",
                    "Collateral Loan application evidence is inconsistent."
            );
        }
        CollateralLoanVerification latestVerification = collateralVerificationRepository
                .findLatestByLoanApplicationIdForUpdate(application.id())
                .orElseThrow(() -> new BusinessStateConflictException(
                        "COLLATERAL_VERIFICATION_REQUIRED",
                        "Collateral Loan verification evidence is required."
                ));
        if (latestVerification.productVerificationResult() == ProductVerificationResult.FAILED) {
            throw new BusinessStateConflictException(
                    "CORRECTION_RESUBMISSION_NOT_ALLOWED",
                    "A failed Collateral Loan application cannot be resubmitted."
            );
        }
        if (latestVerification.productVerificationResult()
                != ProductVerificationResult.PENDING_MANUAL_REVIEW) {
            collateralVerificationRepository.save(CollateralLoanVerification.pendingReverification(
                    UUID.randomUUID(),
                    application.id(),
                    latestVerification.verificationSequence() + 1,
                    request.id(),
                    now
            ));
        }
        return new ProductResubmission(LoanApplicationStatus.SUBMITTED, List.of());
    }

    private void validateProduct(LoanApplication application, LoanProduct product) {
        switch (application.productCode()) {
            case SALARY_ADVANCE -> {
                salaryAdvancePolicy.validateProduct(product);
                salaryAdvancePolicy.validateRequestedAmount(product, application.requestedAmount());
                salaryAdvancePolicy.validateRequestedTerm(application.requestedTermMonths());
            }
            case UNSECURED_CONSUMER_LOAN -> {
                uclPolicy.validateProduct(product);
                uclPolicy.validateRequestedAmount(application.requestedAmount());
                uclPolicy.validateRequestedTerm(application.requestedTermMonths());
            }
            case COLLATERAL_LOAN -> {
                collateralPolicy.validateProduct(product);
                collateralPolicy.validateRequestedAmount(product, application.requestedAmount());
                collateralPolicy.validateRequestedTerm(application.requestedTermMonths());
            }
        }
    }

    private void validateResubmitter(
            ResubmissionActor actor,
            List<LoanCorrectionTask> tasks
    ) {
        boolean hasCustomerTasks = tasks.stream().anyMatch(
                task -> task.responsibleParty() == LoanCorrectionResponsibility.CUSTOMER
        );
        boolean hasStaffTasks = tasks.stream().anyMatch(
                task -> task.responsibleParty() == LoanCorrectionResponsibility.STAFF
        );
        if (tasks.isEmpty()
                || (actor == ResubmissionActor.CUSTOMER && hasStaffTasks)
                || (actor == ResubmissionActor.STAFF && !hasStaffTasks)) {
            throw new AuthorizationException(
                    "CORRECTION_RESUBMISSION_DENIED",
                    hasCustomerTasks && hasStaffTasks
                            ? "Only authorized staff can resubmit a mixed correction request."
                            : "The authenticated actor cannot resubmit this correction request."
            );
        }
    }

    private void validateCustomerReadiness(UUID customerId) {
        CustomerReadinessSnapshot readiness = customerReadinessPort
                .findReadinessByCustomerId(customerId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "CUSTOMER_NOT_FOUND",
                        "Customer was not found."
                ));
        if (!readiness.active()) {
            throw new BusinessStateConflictException(
                    "CUSTOMER_NOT_ACTIVE",
                    "Customer must remain active for correction resubmission."
            );
        }
        if (!readiness.profileComplete()) {
            throw new BusinessRuleViolationException(
                    "PROFILE_INCOMPLETE",
                    "Customer profile must be complete for correction resubmission."
            );
        }
        if (!readiness.hasPrimaryActiveBankAccount()) {
            throw new BusinessRuleViolationException(
                    "PRIMARY_BANK_ACCOUNT_REQUIRED",
                    "A primary active bank account is required."
            );
        }
    }

    private VerifiedPartnerEmployeeLinkSnapshot requireEligiblePartnerEmployeeLink(
            UUID customerId,
            UUID customerPartnerEmployeeLinkId
    ) {
        PartnerEligibilityAssessment assessment = partnerEligibilityPort.inspectEmployeeLink(
                customerId,
                customerPartnerEmployeeLinkId
        );
        if (assessment.status() == PartnerEligibilityAssessment.Status.EVIDENCE_STALE) {
            throw new BusinessRuleViolationException(
                    "SALARY_ADVANCE_ELIGIBILITY_DATA_STALE",
                    "Partner employment evidence must be refreshed before correction resubmission."
            );
        }
        return assessment.optionalSnapshot()
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "EMPLOYEE_NOT_VERIFIED",
                        "Current verified employee eligibility is required."
                ));
    }

    private void assertNoOtherBlockingApplication(LoanApplication application) {
        if (applicationRepository.existsByCustomerIdAndProductCodeAndStatusInExcludingApplication(
                application.customerId(),
                application.productCode(),
                LoanApplicationStatus.blockingStatuses(),
                application.id()
        )) {
            String productName = application.productCode() == ProductCode.SALARY_ADVANCE
                    ? "Salary Advance" : "Unsecured Consumer Loan";
            throw new BusinessStateConflictException(
                    "BLOCKING_APPLICATION_EXISTS",
                    "Another blocking " + productName + " application exists."
            );
        }
    }

    private void assertNoOutstandingLoanAccountExists(
            UUID customerId,
            ProductCode productCode
    ) {
        OutstandingLoanAccountQuery.GuardResult result = outstandingLoanAccounts.inspect(
                customerId,
                productCode
        );
        String productName = productCode == ProductCode.SALARY_ADVANCE
                ? "Salary Advance" : "Unsecured Consumer Loan";
        if (result == OutstandingLoanAccountQuery.GuardResult.INCONSISTENT) {
            throw new BusinessStateConflictException(
                    "SYSTEM_STATE_CONFLICT",
                    productName + " LoanAccount evidence is inconsistent."
            );
        }
        if (result == OutstandingLoanAccountQuery.GuardResult.OUTSTANDING_EXISTS) {
            throw new BusinessStateConflictException(
                    "OUTSTANDING_LOAN_ACCOUNT_EXISTS",
                    "A prior " + productName + " must be fully repaid before resubmission."
            );
        }
    }

    private void validateReservation(LoanApplication application, SalaryAdvanceLimit limit) {
        if (limit.status() != SalaryAdvanceLimitStatus.ACTIVE
                || limit.reservedAmount().compareTo(application.requestedAmount()) < 0
                || !movementRepository.existsByLoanApplicationIdAndMovementType(
                        application.id(),
                        SalaryAdvanceLimitMovementType.RESERVED
                )
                || movementRepository.existsByLoanApplicationIdAndMovementType(
                        application.id(),
                        SalaryAdvanceLimitMovementType.RESERVATION_RELEASED
                )) {
            throw new BusinessStateConflictException(
                    "SALARY_ADVANCE_RESERVATION_INVALID",
                    "Existing Salary Advance reservation is not valid for resubmission."
            );
        }
    }

    private CorrectionResubmissionDto toDto(
            LoanCorrectionRequest request,
            LoanApplication application
    ) {
        return new CorrectionResubmissionDto(
                request.id(),
                application.id(),
                application.status().name(),
                request.resubmissionRequestId(),
                request.resubmittedAt()
        );
    }

    private record ProductResubmission(
            LoanApplicationStatus targetStatus,
            List<BusinessAuditEntry> auditEntries
    ) {
    }

    private enum ResubmissionActor {
        CUSTOMER,
        STAFF
    }
}
