package com.meridian.platform.loan.application.service.salaryadvance;

import com.meridian.platform.loan.application.service.LoanApplicationStatusTransitionRecorder;

import com.meridian.platform.loan.application.dto.SalaryAdvanceApplicationDto;
import com.meridian.platform.loan.application.dto.SalaryAdvanceApplicationRequest;
import com.meridian.platform.loan.application.mapper.LoanMapper;
import com.meridian.platform.loan.application.port.in.StartSalaryAdvanceApplicationUseCase;
import com.meridian.platform.loan.application.port.out.CustomerReadinessPort;
import com.meridian.platform.loan.application.port.out.CustomerReadinessSnapshot;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.LoanDocumentChecklistPort;
import com.meridian.platform.loan.application.port.out.LoanProductRepository;
import com.meridian.platform.loan.application.port.out.OutstandingLoanAccountQuery;
import com.meridian.platform.loan.application.port.out.PartnerEligibilityAssessment;
import com.meridian.platform.loan.application.port.out.PartnerEligibilityPort;
import com.meridian.platform.loan.application.port.out.SalaryAdvanceLimitMovementRepository;
import com.meridian.platform.loan.application.port.out.SalaryAdvanceLimitRepository;
import com.meridian.platform.loan.application.port.out.SalaryAdvanceVerificationRepository;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.LoanApplicationTransitionResult;
import com.meridian.platform.loan.domain.model.LoanProduct;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.salaryadvance.SalaryAdvanceApplicationCreationResult;
import com.meridian.platform.loan.domain.model.salaryadvance.SalaryAdvanceLimit;
import com.meridian.platform.loan.domain.model.salaryadvance.SalaryAdvanceLimitMovement;
import com.meridian.platform.loan.domain.model.salaryadvance.SalaryAdvanceLimitMovementType;
import com.meridian.platform.loan.domain.model.salaryadvance.SalaryAdvanceVerification;
import com.meridian.platform.loan.domain.model.salaryadvance.VerifiedPartnerEmployeeLinkSnapshot;
import com.meridian.platform.loan.domain.service.salaryadvance.SalaryAdvanceApplicationPolicy;
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
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class StartSalaryAdvanceApplicationService implements StartSalaryAdvanceApplicationUseCase {

    private static final DateTimeFormatter APPLICATION_NUMBER_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    private final LoanProductRepository loanProductRepository;
    private final LoanApplicationRepository loanApplicationRepository;
    private final LoanDocumentChecklistPort documentChecklistPort;
    private final OutstandingLoanAccountQuery outstandingLoanAccounts;
    private final SalaryAdvanceLimitRepository salaryAdvanceLimitRepository;
    private final SalaryAdvanceLimitMovementRepository salaryAdvanceLimitMovementRepository;
    private final SalaryAdvanceVerificationRepository salaryAdvanceVerificationRepository;
    private final CustomerReadinessPort customerReadinessPort;
    private final PartnerEligibilityPort partnerEligibilityPort;
    private final LoanMapper loanMapper;
    private final CurrentUserProvider currentUserProvider;
    private final LoanApplicationStatusTransitionRecorder transitionRecorder;
    private final BusinessAuditPublisher businessAuditPublisher;
    private final Clock clock;
    private final SalaryAdvanceApplicationPolicy applicationPolicy = new SalaryAdvanceApplicationPolicy();

    public StartSalaryAdvanceApplicationService(
            LoanProductRepository loanProductRepository,
            LoanApplicationRepository loanApplicationRepository,
            LoanDocumentChecklistPort documentChecklistPort,
            OutstandingLoanAccountQuery outstandingLoanAccounts,
            SalaryAdvanceLimitRepository salaryAdvanceLimitRepository,
            SalaryAdvanceLimitMovementRepository salaryAdvanceLimitMovementRepository,
            SalaryAdvanceVerificationRepository salaryAdvanceVerificationRepository,
            CustomerReadinessPort customerReadinessPort,
            PartnerEligibilityPort partnerEligibilityPort,
            LoanMapper loanMapper,
            CurrentUserProvider currentUserProvider,
            LoanApplicationStatusTransitionRecorder transitionRecorder,
            BusinessAuditPublisher businessAuditPublisher,
            Clock clock
    ) {
        this.loanProductRepository = loanProductRepository;
        this.loanApplicationRepository = loanApplicationRepository;
        this.documentChecklistPort = documentChecklistPort;
        this.outstandingLoanAccounts = outstandingLoanAccounts;
        this.salaryAdvanceLimitRepository = salaryAdvanceLimitRepository;
        this.salaryAdvanceLimitMovementRepository = salaryAdvanceLimitMovementRepository;
        this.salaryAdvanceVerificationRepository = salaryAdvanceVerificationRepository;
        this.customerReadinessPort = customerReadinessPort;
        this.partnerEligibilityPort = partnerEligibilityPort;
        this.loanMapper = loanMapper;
        this.currentUserProvider = currentUserProvider;
        this.transitionRecorder = transitionRecorder;
        this.businessAuditPublisher = businessAuditPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public SalaryAdvanceApplicationDto startSalaryAdvanceApplication(SalaryAdvanceApplicationRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(request.customerPartnerEmployeeLinkId(), "customerPartnerEmployeeLinkId must not be null");
        Objects.requireNonNull(request.requestedAmount(), "requestedAmount must not be null");
        Objects.requireNonNull(request.requestedTermMonths(), "requestedTermMonths must not be null");

        AuthenticatedUser currentUser = currentUserProvider.currentUser();
        UUID customerId = currentUser.requireCustomerId();
        LocalDateTime now = LocalDateTime.now(clock);
        BusinessOperationContext operationContext = BusinessOperationContext.user(
                UUID.randomUUID(),
                currentUser.userId(),
                now
        );
        validateCustomerReadiness(customerId);

        LoanProduct salaryAdvanceProduct = loanProductRepository.findByProductCode(ProductCode.SALARY_ADVANCE)
                .orElseThrow(() -> new EntityNotFoundException(
                        "PRODUCT_NOT_FOUND",
                        "Salary Advance product was not found."
                ));

        applicationPolicy.validateProduct(salaryAdvanceProduct);
        applicationPolicy.validateRequestedTerm(request.requestedTermMonths());
        applicationPolicy.validateRequestedAmount(salaryAdvanceProduct, request.requestedAmount());
        loanApplicationRepository.acquireCustomerProductLock(customerId, salaryAdvanceProduct.productCode());
        assertNoBlockingApplicationExists(customerId);

        VerifiedPartnerEmployeeLinkSnapshot partnerSnapshot = requireEligiblePartnerEmployeeLink(
                customerId,
                request.customerPartnerEmployeeLinkId()
        );

        BigDecimal effectiveTotalLimit = applicationPolicy.calculateEffectiveTotalLimit(
                salaryAdvanceProduct,
                partnerSnapshot
        );

        salaryAdvanceLimitRepository.acquireCustomerLinkLock(
                customerId,
                request.customerPartnerEmployeeLinkId()
        );
        assertNoBlockingApplicationExists(customerId);
        assertNoOutstandingLoanAccountExists(customerId);

        LimitPreparationResult preparedLimit = findOrCreateLimit(
                customerId,
                request,
                partnerSnapshot,
                effectiveTotalLimit,
                now
        );
        SalaryAdvanceLimit reservedLimit = preparedLimit.limit().reserve(request.requestedAmount());

        long applicationSequence = loanApplicationRepository.nextApplicationNumberSequence();
        LoanDocumentChecklistPort.SubmissionChecklistInitialState checklistInitialState =
                documentChecklistPort.resolveSubmissionInitialState(salaryAdvanceProduct.productCode());
        LoanApplicationStatus initialStatus = checklistInitialState.uploadComplete()
                ? LoanApplicationStatus.SUBMITTED : LoanApplicationStatus.DOCUMENTS_PENDING;
        LoanApplicationTransitionResult submission = LoanApplication.submit(
                UUID.randomUUID(),
                customerId,
                salaryAdvanceProduct,
                formatApplicationNumber(applicationSequence, now),
                request.requestedAmount(),
                request.requestedTermMonths(),
                now,
                initialStatus
        );

        LoanApplication savedApplication = loanApplicationRepository.save(submission.loanApplication());
        documentChecklistPort.createSubmissionChecklist(
                savedApplication.id(),
                savedApplication.productCode(),
                operationContext
        );
        SalaryAdvanceLimit savedReservedLimit = salaryAdvanceLimitRepository.save(reservedLimit);
        SalaryAdvanceLimitMovement reservedMovement = salaryAdvanceLimitMovementRepository.save(
                SalaryAdvanceLimitMovement.reserved(
                        UUID.randomUUID(),
                        savedReservedLimit.id(),
                        savedApplication.id(),
                        request.requestedAmount(),
                        now
                )
        );

        SalaryAdvanceVerification verification = SalaryAdvanceVerification.verified(
                UUID.randomUUID(),
                savedApplication,
                savedReservedLimit,
                partnerSnapshot,
                now
        );
        SalaryAdvanceVerification savedVerification = salaryAdvanceVerificationRepository.save(verification);

        transitionRecorder.record(operationContext, submission.facts(), null);
        businessAuditPublisher.publish(new BusinessAuditEvent(
                operationContext,
                submissionAuditEntries(savedApplication, preparedLimit.movement(), reservedMovement)
        ));

        return loanMapper.toSalaryAdvanceApplicationDto(new SalaryAdvanceApplicationCreationResult(
                savedApplication,
                savedReservedLimit,
                savedVerification
        ));
    }

    private void validateCustomerReadiness(UUID customerId) {
        CustomerReadinessSnapshot readiness = customerReadinessPort.findReadinessByCustomerId(customerId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "CUSTOMER_NOT_FOUND",
                        "Customer was not found."
                ));
        if (!readiness.active()) {
            throw new BusinessStateConflictException(
                    "CUSTOMER_NOT_ACTIVE",
                    "Customer must be active before creating a Salary Advance application."
            );
        }
        if (!readiness.profileComplete()) {
            throw new BusinessRuleViolationException(
                    "PROFILE_INCOMPLETE",
                    "Customer profile must be complete before creating a Salary Advance application."
            );
        }
        if (!readiness.hasPrimaryActiveBankAccount()) {
            throw new BusinessRuleViolationException(
                    "PRIMARY_BANK_ACCOUNT_REQUIRED",
                    "Customer must have a primary active bank account before creating a Salary Advance application."
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
                    "Partner employment evidence must be refreshed before creating a Salary Advance application."
            );
        }
        return assessment.optionalSnapshot()
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "EMPLOYEE_NOT_VERIFIED",
                        "Customer must have a verified active employee link before creating a Salary Advance application."
                ));
    }

    private void assertNoBlockingApplicationExists(UUID customerId) {
        if (loanApplicationRepository.existsByCustomerIdAndProductCodeAndStatusIn(
                customerId,
                ProductCode.SALARY_ADVANCE,
                LoanApplicationStatus.blockingStatuses()
        )) {
            throw new BusinessStateConflictException(
                    "BLOCKING_APPLICATION_EXISTS",
                    "A blocking Salary Advance application already exists for this customer."
            );
        }
    }

    private void assertNoOutstandingLoanAccountExists(UUID customerId) {
        OutstandingLoanAccountQuery.GuardResult result = outstandingLoanAccounts.inspect(
                customerId,
                ProductCode.SALARY_ADVANCE
        );
        if (result == OutstandingLoanAccountQuery.GuardResult.INCONSISTENT) {
            throw new BusinessStateConflictException(
                    "SYSTEM_STATE_CONFLICT",
                    "Salary Advance LoanAccount evidence is inconsistent."
            );
        }
        if (result == OutstandingLoanAccountQuery.GuardResult.OUTSTANDING_EXISTS) {
            throw new BusinessStateConflictException(
                    "OUTSTANDING_LOAN_ACCOUNT_EXISTS",
                    "A prior Salary Advance must be fully repaid before another application."
            );
        }
    }

    private LimitPreparationResult findOrCreateLimit(
            UUID customerId,
            SalaryAdvanceApplicationRequest request,
            VerifiedPartnerEmployeeLinkSnapshot partnerSnapshot,
            BigDecimal effectiveTotalLimit,
            LocalDateTime occurredAt
    ) {
        return salaryAdvanceLimitRepository.findByCustomerIdAndCustomerPartnerEmployeeLinkIdForUpdate(
                        customerId,
                        request.customerPartnerEmployeeLinkId()
                )
                .map(currentLimit -> refreshLimitIfNeeded(
                        currentLimit,
                        effectiveTotalLimit,
                        partnerSnapshot.lastRefreshedAt(),
                        occurredAt
                ))
                .orElseGet(() -> initializeLimit(customerId, request, partnerSnapshot, effectiveTotalLimit, occurredAt));
    }

    private LimitPreparationResult initializeLimit(
            UUID customerId,
            SalaryAdvanceApplicationRequest request,
            VerifiedPartnerEmployeeLinkSnapshot partnerSnapshot,
            BigDecimal effectiveTotalLimit,
            LocalDateTime occurredAt
    ) {
        SalaryAdvanceLimit initializedLimit = SalaryAdvanceLimit.initialized(
                UUID.randomUUID(),
                customerId,
                request.customerPartnerEmployeeLinkId(),
                effectiveTotalLimit,
                partnerSnapshot.lastRefreshedAt()
        );

        SalaryAdvanceLimit savedLimit = salaryAdvanceLimitRepository.save(initializedLimit);
        SalaryAdvanceLimitMovement movement = salaryAdvanceLimitMovementRepository.save(
                SalaryAdvanceLimitMovement.initialized(
                        UUID.randomUUID(),
                        savedLimit,
                        occurredAt
                )
        );
        return new LimitPreparationResult(savedLimit, movement);
    }

    private LimitPreparationResult refreshLimitIfNeeded(
            SalaryAdvanceLimit currentLimit,
            BigDecimal effectiveTotalLimit,
            LocalDateTime lastRefreshedAt,
            LocalDateTime occurredAt
    ) {
        SalaryAdvanceLimit refreshedLimit = currentLimit.refreshTotalLimit(effectiveTotalLimit, lastRefreshedAt);
        if (!hasLimitRefreshChange(currentLimit, refreshedLimit)) {
            return new LimitPreparationResult(currentLimit, null);
        }

        SalaryAdvanceLimit savedLimit = salaryAdvanceLimitRepository.save(refreshedLimit);
        if (amountChanged(currentLimit.totalLimit(), savedLimit.totalLimit())) {
            SalaryAdvanceLimitMovement movement = salaryAdvanceLimitMovementRepository.save(
                    SalaryAdvanceLimitMovement.refreshed(
                            UUID.randomUUID(),
                            savedLimit,
                            occurredAt
                    )
            );
            return new LimitPreparationResult(savedLimit, movement);
        }
        return new LimitPreparationResult(savedLimit, null);
    }

    private List<BusinessAuditEntry> submissionAuditEntries(
            LoanApplication savedApplication,
            SalaryAdvanceLimitMovement preparedLimitMovement,
            SalaryAdvanceLimitMovement reservedMovement
    ) {
        List<BusinessAuditEntry> entries = new ArrayList<>();
        entries.add(BusinessAuditEntry.of(
                BusinessAuditAction.SALARY_ADVANCE_APPLICATION_SUBMITTED,
                BusinessAuditEntityType.LOAN_APPLICATION,
                savedApplication.id()
        ));
        if (preparedLimitMovement != null) {
            entries.add(limitMovementAuditEntry(preparedLimitMovement, null));
        }
        entries.add(limitMovementAuditEntry(reservedMovement, savedApplication.id()));
        return entries;
    }

    private BusinessAuditEntry limitMovementAuditEntry(
            SalaryAdvanceLimitMovement movement,
            UUID loanApplicationId
    ) {
        BusinessAuditPayload.Builder payload = BusinessAuditPayload.builder()
                .put(BusinessAuditPayloadKey.SALARY_ADVANCE_LIMIT_ID, movement.salaryAdvanceLimitId())
                .put(BusinessAuditPayloadKey.MOVEMENT_TYPE, movement.movementType());
        if (loanApplicationId != null) {
            payload.put(BusinessAuditPayloadKey.LOAN_APPLICATION_ID, loanApplicationId);
        }
        return new BusinessAuditEntry(
                auditActionFor(movement.movementType()),
                BusinessAuditEntityType.SALARY_ADVANCE_LIMIT_MOVEMENT,
                movement.id(),
                payload.build()
        );
    }

    private BusinessAuditAction auditActionFor(SalaryAdvanceLimitMovementType movementType) {
        return switch (movementType) {
            case INITIALIZED -> BusinessAuditAction.SALARY_ADVANCE_LIMIT_INITIALIZED;
            case REFRESHED -> BusinessAuditAction.SALARY_ADVANCE_LIMIT_REFRESHED;
            case RESERVED -> BusinessAuditAction.SALARY_ADVANCE_LIMIT_RESERVED;
            default -> throw new IllegalArgumentException("Unsupported submission movement type: " + movementType);
        };
    }

    private boolean hasLimitRefreshChange(SalaryAdvanceLimit currentLimit, SalaryAdvanceLimit refreshedLimit) {
        return amountChanged(currentLimit.totalLimit(), refreshedLimit.totalLimit())
                || amountChanged(currentLimit.availableAmount(), refreshedLimit.availableAmount())
                || !Objects.equals(currentLimit.lastRefreshedAt(), refreshedLimit.lastRefreshedAt());
    }

    private boolean amountChanged(BigDecimal first, BigDecimal second) {
        return first.compareTo(second) != 0;
    }

    private String formatApplicationNumber(long sequence, LocalDateTime submittedAt) {
        return "SA-" + submittedAt.format(APPLICATION_NUMBER_DATE_FORMAT) + "-" + String.format("%06d", sequence);
    }

    private record LimitPreparationResult(
            SalaryAdvanceLimit limit,
            SalaryAdvanceLimitMovement movement
    ) {
    }
}
