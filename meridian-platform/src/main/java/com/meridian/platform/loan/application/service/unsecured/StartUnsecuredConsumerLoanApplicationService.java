package com.meridian.platform.loan.application.service.unsecured;

import com.meridian.platform.loan.application.service.LoanApplicationStatusTransitionRecorder;

import com.meridian.platform.loan.application.dto.UnsecuredConsumerLoanApplicationDto;
import com.meridian.platform.loan.application.dto.UnsecuredConsumerLoanApplicationRequest;
import com.meridian.platform.loan.application.mapper.LoanMapper;
import com.meridian.platform.loan.application.port.in.StartUnsecuredConsumerLoanApplicationUseCase;
import com.meridian.platform.loan.application.port.out.CustomerReadinessPort;
import com.meridian.platform.loan.application.port.out.CustomerReadinessSnapshot;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.LoanDocumentChecklistPort;
import com.meridian.platform.loan.application.port.out.LoanProductRepository;
import com.meridian.platform.loan.application.port.out.OutstandingLoanAccountQuery;
import com.meridian.platform.loan.application.port.out.UnsecuredConsumerLoanVerificationRepository;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.LoanApplicationTransitionResult;
import com.meridian.platform.loan.domain.model.LoanProduct;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.unsecured.UnsecuredConsumerLoanVerification;
import com.meridian.platform.loan.domain.service.unsecured.UnsecuredConsumerLoanApplicationPolicy;
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
public class StartUnsecuredConsumerLoanApplicationService
        implements StartUnsecuredConsumerLoanApplicationUseCase {

    private static final DateTimeFormatter APPLICATION_NUMBER_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    private final LoanProductRepository loanProductRepository;
    private final LoanApplicationRepository loanApplicationRepository;
    private final LoanDocumentChecklistPort documentChecklistPort;
    private final UnsecuredConsumerLoanVerificationRepository verificationRepository;
    private final CustomerReadinessPort customerReadinessPort;
    private final OutstandingLoanAccountQuery outstandingLoanAccounts;
    private final LoanMapper loanMapper;
    private final CurrentUserProvider currentUserProvider;
    private final LoanApplicationStatusTransitionRecorder transitionRecorder;
    private final BusinessAuditPublisher businessAuditPublisher;
    private final Clock clock;
    private final UnsecuredConsumerLoanApplicationPolicy applicationPolicy =
            new UnsecuredConsumerLoanApplicationPolicy();

    public StartUnsecuredConsumerLoanApplicationService(
            LoanProductRepository loanProductRepository,
            LoanApplicationRepository loanApplicationRepository,
            LoanDocumentChecklistPort documentChecklistPort,
            UnsecuredConsumerLoanVerificationRepository verificationRepository,
            CustomerReadinessPort customerReadinessPort,
            OutstandingLoanAccountQuery outstandingLoanAccounts,
            LoanMapper loanMapper,
            CurrentUserProvider currentUserProvider,
            LoanApplicationStatusTransitionRecorder transitionRecorder,
            BusinessAuditPublisher businessAuditPublisher,
            Clock clock
    ) {
        this.loanProductRepository = loanProductRepository;
        this.loanApplicationRepository = loanApplicationRepository;
        this.documentChecklistPort = documentChecklistPort;
        this.verificationRepository = verificationRepository;
        this.customerReadinessPort = customerReadinessPort;
        this.outstandingLoanAccounts = outstandingLoanAccounts;
        this.loanMapper = loanMapper;
        this.currentUserProvider = currentUserProvider;
        this.transitionRecorder = transitionRecorder;
        this.businessAuditPublisher = businessAuditPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public UnsecuredConsumerLoanApplicationDto startUnsecuredConsumerLoanApplication(
            UnsecuredConsumerLoanApplicationRequest request
    ) {
        Objects.requireNonNull(request, "request must not be null");
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

        LoanProduct product = loanProductRepository.findByProductCode(ProductCode.UNSECURED_CONSUMER_LOAN)
                .orElseThrow(() -> new EntityNotFoundException(
                        "PRODUCT_NOT_FOUND",
                        "Unsecured Consumer Loan product was not found."
                ));
        applicationPolicy.validateProduct(product);
        applicationPolicy.validateRequestedAmount(request.requestedAmount());
        applicationPolicy.validateRequestedTerm(request.requestedTermMonths());

        loanApplicationRepository.acquireCustomerProductLock(customerId, product.productCode());
        assertNoBlockingApplicationExists(customerId);
        assertNoOutstandingLoanAccountExists(customerId);

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
        documentChecklistPort.createSubmissionChecklist(
                savedApplication.id(),
                savedApplication.productCode(),
                operationContext
        );
        UnsecuredConsumerLoanVerification savedVerification = verificationRepository.save(
                UnsecuredConsumerLoanVerification.pendingManualReview(
                        UUID.randomUUID(),
                        savedApplication,
                        now
                )
        );
        transitionRecorder.record(operationContext, submission.facts(), null);
        businessAuditPublisher.publish(BusinessAuditEvent.single(
                operationContext,
                BusinessAuditEntry.of(
                        BusinessAuditAction.UNSECURED_CONSUMER_LOAN_APPLICATION_SUBMITTED,
                        BusinessAuditEntityType.LOAN_APPLICATION,
                        savedApplication.id()
                )
        ));

        return loanMapper.toUnsecuredConsumerLoanApplicationDto(savedApplication, savedVerification);
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
                    "Customer must be active before creating an Unsecured Consumer Loan application."
            );
        }
        if (!readiness.profileComplete()) {
            throw new BusinessRuleViolationException(
                    "PROFILE_INCOMPLETE",
                    "Customer profile must be complete before creating an Unsecured Consumer Loan application."
            );
        }
        if (!readiness.hasPrimaryActiveBankAccount()) {
            throw new BusinessRuleViolationException(
                    "PRIMARY_BANK_ACCOUNT_REQUIRED",
                    "Customer must have a primary active bank account before creating an Unsecured Consumer Loan application."
            );
        }
    }

    private void assertNoBlockingApplicationExists(UUID customerId) {
        if (loanApplicationRepository.existsByCustomerIdAndProductCodeAndStatusIn(
                customerId,
                ProductCode.UNSECURED_CONSUMER_LOAN,
                LoanApplicationStatus.blockingStatuses()
        )) {
            throw new BusinessStateConflictException(
                    "BLOCKING_APPLICATION_EXISTS",
                    "A blocking Unsecured Consumer Loan application already exists for this customer."
            );
        }
    }

    private void assertNoOutstandingLoanAccountExists(UUID customerId) {
        OutstandingLoanAccountQuery.GuardResult result = outstandingLoanAccounts.inspect(
                customerId,
                ProductCode.UNSECURED_CONSUMER_LOAN
        );
        if (result == OutstandingLoanAccountQuery.GuardResult.INCONSISTENT) {
            throw new BusinessStateConflictException(
                    "SYSTEM_STATE_CONFLICT",
                    "Unsecured Consumer Loan LoanAccount evidence is inconsistent."
            );
        }
        if (result == OutstandingLoanAccountQuery.GuardResult.OUTSTANDING_EXISTS) {
            throw new BusinessStateConflictException(
                    "OUTSTANDING_LOAN_ACCOUNT_EXISTS",
                    "A prior Unsecured Consumer Loan must be fully repaid before another application."
            );
        }
    }

    private String formatApplicationNumber(long sequence, LocalDateTime submittedAt) {
        return "UCL-" + submittedAt.format(APPLICATION_NUMBER_DATE_FORMAT) + "-" + String.format("%06d", sequence);
    }
}
