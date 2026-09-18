package com.meridian.platform.loan.application.service.unsecured;

import com.meridian.platform.loan.application.port.out.CustomerReadinessPort;
import com.meridian.platform.loan.application.port.out.CustomerReadinessSnapshot;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.LoanDocumentChecklistPort;
import com.meridian.platform.loan.application.port.out.LoanProductRepository;
import com.meridian.platform.loan.application.port.out.OutstandingLoanAccountQuery;
import com.meridian.platform.loan.application.port.out.UnsecuredConsumerLoanVerificationRepository;
import com.meridian.platform.loan.application.service.LoanApplicationStatusTransitionRecorder;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.LoanApplicationTransitionResult;
import com.meridian.platform.loan.domain.model.LoanProduct;
import com.meridian.platform.loan.domain.model.OriginationChannel;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.unsecured.UnsecuredConsumerLoanVerification;
import com.meridian.platform.loan.domain.service.unsecured.UnsecuredConsumerLoanApplicationPolicy;
import com.meridian.platform.shared.application.audit.BusinessAuditEntry;
import com.meridian.platform.shared.application.audit.BusinessAuditEvent;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.operation.BusinessOperationContext;
import com.meridian.platform.shared.domain.audit.BusinessAuditAction;
import com.meridian.platform.shared.domain.audit.BusinessAuditEntityType;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

final class UnsecuredConsumerLoanOrigination {

    private static final DateTimeFormatter APPLICATION_NUMBER_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    private final LoanProductRepository products;
    private final LoanApplicationRepository applications;
    private final LoanDocumentChecklistPort checklists;
    private final UnsecuredConsumerLoanVerificationRepository verifications;
    private final CustomerReadinessPort customers;
    private final OutstandingLoanAccountQuery outstandingAccounts;
    private final LoanApplicationStatusTransitionRecorder transitions;
    private final BusinessAuditPublisher audits;
    private final UnsecuredConsumerLoanApplicationPolicy policy = new UnsecuredConsumerLoanApplicationPolicy();

    UnsecuredConsumerLoanOrigination(
            LoanProductRepository products,
            LoanApplicationRepository applications,
            LoanDocumentChecklistPort checklists,
            UnsecuredConsumerLoanVerificationRepository verifications,
            CustomerReadinessPort customers,
            OutstandingLoanAccountQuery outstandingAccounts,
            LoanApplicationStatusTransitionRecorder transitions,
            BusinessAuditPublisher audits
    ) {
        this.products = products;
        this.applications = applications;
        this.checklists = checklists;
        this.verifications = verifications;
        this.customers = customers;
        this.outstandingAccounts = outstandingAccounts;
        this.transitions = transitions;
        this.audits = audits;
    }

    Result create(
            UUID customerId,
            BigDecimal requestedAmount,
            int requestedTermMonths,
            OriginationChannel channel,
            BusinessOperationContext operation,
            LocalDateTime now
    ) {
        validateCustomerReadiness(customerId);
        LoanProduct product = products.findByProductCode(ProductCode.UNSECURED_CONSUMER_LOAN)
                .orElseThrow(() -> new EntityNotFoundException(
                        "PRODUCT_NOT_FOUND", "Unsecured Consumer Loan product was not found."));
        policy.validateProduct(product);
        policy.validateRequestedAmount(product, requestedAmount);
        policy.validateRequestedTerm(requestedTermMonths);

        applications.acquireCustomerProductLock(customerId, product.productCode());
        assertNoBlockingApplicationExists(customerId);
        assertNoOutstandingLoanAccountExists(customerId);

        LoanDocumentChecklistPort.SubmissionChecklistInitialState checklistInitialState =
                checklists.resolveSubmissionInitialState(product.productCode());
        LoanApplicationStatus initialStatus = checklistInitialState.uploadComplete()
                ? LoanApplicationStatus.SUBMITTED : LoanApplicationStatus.DOCUMENTS_PENDING;
        LoanApplicationTransitionResult submission = LoanApplication.submit(
                UUID.randomUUID(), customerId, product,
                formatApplicationNumber(applications.nextApplicationNumberSequence(), now),
                requestedAmount, requestedTermMonths, channel, now, initialStatus);

        LoanApplication savedApplication = applications.save(submission.loanApplication());
        LoanDocumentChecklistPort.SubmissionChecklistSnapshot checklist = checklists.createSubmissionChecklist(
                savedApplication.id(), savedApplication.productCode(), operation);
        UnsecuredConsumerLoanVerification verification = verifications.save(
                UnsecuredConsumerLoanVerification.pendingManualReview(
                        UUID.randomUUID(), savedApplication, now));
        transitions.record(operation, submission.facts(), null);
        audits.publish(BusinessAuditEvent.single(operation, BusinessAuditEntry.of(
                BusinessAuditAction.UNSECURED_CONSUMER_LOAN_APPLICATION_SUBMITTED,
                BusinessAuditEntityType.LOAN_APPLICATION,
                savedApplication.id())));
        return new Result(savedApplication, verification, checklist);
    }

    private void validateCustomerReadiness(UUID customerId) {
        CustomerReadinessSnapshot readiness = customers.findReadinessByCustomerId(customerId)
                .orElseThrow(() -> new EntityNotFoundException("CUSTOMER_NOT_FOUND", "Customer was not found."));
        if (!readiness.active()) {
            throw new BusinessStateConflictException(
                    "CUSTOMER_NOT_ACTIVE",
                    "Customer must be active before creating an Unsecured Consumer Loan application.");
        }
        if (!readiness.profileComplete()) {
            throw new BusinessRuleViolationException(
                    "PROFILE_INCOMPLETE",
                    "Customer profile must be complete before creating an Unsecured Consumer Loan application.");
        }
        if (!readiness.hasPrimaryActiveBankAccount()) {
            throw new BusinessRuleViolationException(
                    "PRIMARY_BANK_ACCOUNT_REQUIRED",
                    "Customer must have a primary active bank account before creating an Unsecured Consumer Loan application.");
        }
    }

    private void assertNoBlockingApplicationExists(UUID customerId) {
        if (applications.existsByCustomerIdAndProductCodeAndStatusIn(
                customerId, ProductCode.UNSECURED_CONSUMER_LOAN, LoanApplicationStatus.blockingStatuses())) {
            throw new BusinessStateConflictException(
                    "BLOCKING_APPLICATION_EXISTS",
                    "A blocking Unsecured Consumer Loan application already exists for this customer.");
        }
    }

    private void assertNoOutstandingLoanAccountExists(UUID customerId) {
        OutstandingLoanAccountQuery.GuardResult result = outstandingAccounts.inspect(
                customerId, ProductCode.UNSECURED_CONSUMER_LOAN);
        if (result == OutstandingLoanAccountQuery.GuardResult.INCONSISTENT) {
            throw new BusinessStateConflictException(
                    "SYSTEM_STATE_CONFLICT", "Unsecured Consumer Loan LoanAccount evidence is inconsistent.");
        }
        if (result == OutstandingLoanAccountQuery.GuardResult.OUTSTANDING_EXISTS) {
            throw new BusinessStateConflictException(
                    "OUTSTANDING_LOAN_ACCOUNT_EXISTS",
                    "A prior Unsecured Consumer Loan must be fully repaid before another application.");
        }
    }

    private static String formatApplicationNumber(long sequence, LocalDateTime submittedAt) {
        return "UCL-" + submittedAt.format(APPLICATION_NUMBER_DATE_FORMAT) + "-" + String.format("%06d", sequence);
    }

    record Result(
            LoanApplication application,
            UnsecuredConsumerLoanVerification verification,
            LoanDocumentChecklistPort.SubmissionChecklistSnapshot checklist
    ) {
    }
}
