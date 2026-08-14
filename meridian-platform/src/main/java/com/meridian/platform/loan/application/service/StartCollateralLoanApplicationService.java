package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.dto.CollateralDetailsRequest;
import com.meridian.platform.loan.application.dto.CollateralLoanApplicationDto;
import com.meridian.platform.loan.application.dto.CollateralLoanApplicationRequest;
import com.meridian.platform.loan.application.mapper.LoanMapper;
import com.meridian.platform.loan.application.port.in.StartCollateralLoanApplicationUseCase;
import com.meridian.platform.loan.application.port.out.CollateralLoanVerificationRepository;
import com.meridian.platform.loan.application.port.out.CollateralRepository;
import com.meridian.platform.loan.application.port.out.CustomerReadinessPort;
import com.meridian.platform.loan.application.port.out.CustomerReadinessSnapshot;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.LoanDocumentChecklistPort;
import com.meridian.platform.loan.application.port.out.LoanProductRepository;
import com.meridian.platform.loan.domain.model.Collateral;
import com.meridian.platform.loan.domain.model.CollateralLoanVerification;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.LoanApplicationTransitionResult;
import com.meridian.platform.loan.domain.model.LoanProduct;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.service.CollateralLoanApplicationPolicy;
import com.meridian.platform.shared.application.audit.BusinessAuditEntry;
import com.meridian.platform.shared.application.audit.BusinessAuditEvent;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.operation.BusinessOperationContext;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.audit.BusinessAuditAction;
import com.meridian.platform.shared.domain.audit.BusinessAuditEntityType;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.UUID;

@Service
public class StartCollateralLoanApplicationService implements StartCollateralLoanApplicationUseCase {

    private static final DateTimeFormatter APPLICATION_NUMBER_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    private final LoanProductRepository loanProductRepository;
    private final LoanApplicationRepository loanApplicationRepository;
    private final CollateralRepository collateralRepository;
    private final LoanDocumentChecklistPort documentChecklistPort;
    private final CollateralLoanVerificationRepository verificationRepository;
    private final CustomerReadinessPort customerReadinessPort;
    private final LoanMapper loanMapper;
    private final CurrentUserProvider currentUserProvider;
    private final LoanApplicationStatusTransitionRecorder transitionRecorder;
    private final BusinessAuditPublisher businessAuditPublisher;
    private final Clock clock;
    private final CollateralLoanApplicationPolicy applicationPolicy = new CollateralLoanApplicationPolicy();

    public StartCollateralLoanApplicationService(
            LoanProductRepository loanProductRepository,
            LoanApplicationRepository loanApplicationRepository,
            CollateralRepository collateralRepository,
            LoanDocumentChecklistPort documentChecklistPort,
            CollateralLoanVerificationRepository verificationRepository,
            CustomerReadinessPort customerReadinessPort,
            LoanMapper loanMapper,
            CurrentUserProvider currentUserProvider,
            LoanApplicationStatusTransitionRecorder transitionRecorder,
            BusinessAuditPublisher businessAuditPublisher,
            Clock clock
    ) {
        this.loanProductRepository = loanProductRepository;
        this.loanApplicationRepository = loanApplicationRepository;
        this.collateralRepository = collateralRepository;
        this.documentChecklistPort = documentChecklistPort;
        this.verificationRepository = verificationRepository;
        this.customerReadinessPort = customerReadinessPort;
        this.loanMapper = loanMapper;
        this.currentUserProvider = currentUserProvider;
        this.transitionRecorder = transitionRecorder;
        this.businessAuditPublisher = businessAuditPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public CollateralLoanApplicationDto startCollateralLoanApplication(
            CollateralLoanApplicationRequest request
    ) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(request.requestedAmount(), "requestedAmount must not be null");
        Objects.requireNonNull(request.requestedTermMonths(), "requestedTermMonths must not be null");
        CollateralDetailsRequest details = Objects.requireNonNull(
                request.collateral(),
                "collateral must not be null"
        );

        AuthenticatedUser currentUser = currentUserProvider.currentUser();
        UUID customerId = currentUser.requireCustomerId();
        LocalDateTime now = LocalDateTime.now(clock);
        BusinessOperationContext operationContext = BusinessOperationContext.user(
                UUID.randomUUID(),
                currentUser.userId(),
                now
        );
        validateCustomerReadiness(customerId);

        LoanProduct product = loanProductRepository.findByProductCode(ProductCode.COLLATERAL_LOAN)
                .orElseThrow(() -> new EntityNotFoundException(
                        "PRODUCT_NOT_FOUND",
                        "Collateral Loan product was not found."
                ));
        applicationPolicy.validateProduct(product);
        applicationPolicy.validateRequestedAmount(product, request.requestedAmount());
        applicationPolicy.validateRequestedTerm(request.requestedTermMonths());
        applicationPolicy.validateCollateralDetails(
                details.type(),
                details.description(),
                details.estimatedValue(),
                details.ownershipStatus(),
                details.conditionNote()
        );

        loanApplicationRepository.acquireCustomerProductLock(customerId, product.productCode());
        assertNoBlockingApplicationExists(customerId);

        LoanDocumentChecklistPort.SubmissionChecklistInitialState checklistInitialState =
                documentChecklistPort.resolveSubmissionInitialState(product.productCode());
        LoanApplicationStatus initialStatus = checklistInitialState.uploadComplete()
                ? LoanApplicationStatus.SUBMITTED : LoanApplicationStatus.DOCUMENTS_PENDING;
        LoanApplicationTransitionResult submission = LoanApplication.submit(
                UUID.randomUUID(),
                customerId,
                product,
                formatApplicationNumber(loanApplicationRepository.nextApplicationNumberSequence(), now),
                request.requestedAmount(),
                request.requestedTermMonths(),
                now,
                initialStatus
        );

        LoanApplication savedApplication = loanApplicationRepository.save(submission.loanApplication());
        Collateral savedCollateral = collateralRepository.save(applicationPolicy.createCollateral(
                UUID.randomUUID(),
                savedApplication,
                details.type(),
                details.description(),
                details.estimatedValue(),
                details.ownershipStatus(),
                details.conditionNote(),
                now
        ));
        LoanDocumentChecklistPort.SubmissionChecklistSnapshot checklist =
                documentChecklistPort.createSubmissionChecklist(
                        savedApplication.id(),
                        savedApplication.productCode(),
                        operationContext
                );
        CollateralLoanVerification savedVerification = verificationRepository.save(
                CollateralLoanVerification.pendingManualReview(
                        UUID.randomUUID(),
                        savedApplication,
                        now
                )
        );
        transitionRecorder.record(operationContext, submission.facts(), null);
        businessAuditPublisher.publish(BusinessAuditEvent.single(
                operationContext,
                BusinessAuditEntry.of(
                        BusinessAuditAction.COLLATERAL_LOAN_APPLICATION_SUBMITTED,
                        BusinessAuditEntityType.LOAN_APPLICATION,
                        savedApplication.id()
                )
        ));

        return loanMapper.toCollateralLoanApplicationDto(
                savedApplication,
                savedCollateral,
                savedVerification,
                checklist
        );
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
                    "Customer must be active before creating a Collateral Loan application."
            );
        }
        if (!readiness.profileComplete()) {
            throw new BusinessRuleViolationException(
                    "PROFILE_INCOMPLETE",
                    "Customer profile must be complete before creating a Collateral Loan application."
            );
        }
        if (!readiness.hasPrimaryActiveBankAccount()) {
            throw new BusinessRuleViolationException(
                    "PRIMARY_BANK_ACCOUNT_REQUIRED",
                    "Customer must have a primary active bank account before creating a Collateral Loan application."
            );
        }
    }

    private void assertNoBlockingApplicationExists(UUID customerId) {
        if (loanApplicationRepository.existsByCustomerIdAndProductCodeAndStatusIn(
                customerId,
                ProductCode.COLLATERAL_LOAN,
                LoanApplicationStatus.blockingStatuses()
        )) {
            throw new BusinessStateConflictException(
                    "BLOCKING_APPLICATION_EXISTS",
                    "A blocking Collateral Loan application already exists for this customer."
            );
        }
    }

    private String formatApplicationNumber(long sequence, LocalDateTime submittedAt) {
        return "CL-" + submittedAt.format(APPLICATION_NUMBER_DATE_FORMAT) + "-" + String.format("%06d", sequence);
    }
}
