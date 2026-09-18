package com.meridian.platform.loan.application.service.collateral;

import com.meridian.platform.loan.application.dto.CollateralDetailsRequest;
import com.meridian.platform.loan.application.port.out.CollateralLoanVerificationRepository;
import com.meridian.platform.loan.application.port.out.CollateralRepository;
import com.meridian.platform.loan.application.port.out.CustomerReadinessPort;
import com.meridian.platform.loan.application.port.out.CustomerReadinessSnapshot;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.LoanDocumentChecklistPort;
import com.meridian.platform.loan.application.port.out.LoanProductRepository;
import com.meridian.platform.loan.application.service.LoanApplicationStatusTransitionRecorder;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.LoanApplicationTransitionResult;
import com.meridian.platform.loan.domain.model.LoanProduct;
import com.meridian.platform.loan.domain.model.OriginationChannel;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.collateral.Collateral;
import com.meridian.platform.loan.domain.model.collateral.CollateralLoanVerification;
import com.meridian.platform.loan.domain.service.collateral.CollateralLoanApplicationPolicy;
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

final class CollateralLoanOrigination {

    private static final DateTimeFormatter APPLICATION_NUMBER_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    private final LoanProductRepository products;
    private final LoanApplicationRepository applications;
    private final CollateralRepository collaterals;
    private final LoanDocumentChecklistPort checklists;
    private final CollateralLoanVerificationRepository verifications;
    private final CustomerReadinessPort customers;
    private final LoanApplicationStatusTransitionRecorder transitions;
    private final BusinessAuditPublisher audits;
    private final CollateralLoanApplicationPolicy policy = new CollateralLoanApplicationPolicy();

    CollateralLoanOrigination(
            LoanProductRepository products,
            LoanApplicationRepository applications,
            CollateralRepository collaterals,
            LoanDocumentChecklistPort checklists,
            CollateralLoanVerificationRepository verifications,
            CustomerReadinessPort customers,
            LoanApplicationStatusTransitionRecorder transitions,
            BusinessAuditPublisher audits
    ) {
        this.products = products;
        this.applications = applications;
        this.collaterals = collaterals;
        this.checklists = checklists;
        this.verifications = verifications;
        this.customers = customers;
        this.transitions = transitions;
        this.audits = audits;
    }

    Result create(
            UUID customerId,
            BigDecimal requestedAmount,
            int requestedTermMonths,
            CollateralDetailsRequest details,
            OriginationChannel channel,
            BusinessOperationContext operation,
            LocalDateTime now
    ) {
        validateCustomerReadiness(customerId);
        LoanProduct product = products.findByProductCode(ProductCode.COLLATERAL_LOAN)
                .orElseThrow(() -> new EntityNotFoundException(
                        "PRODUCT_NOT_FOUND", "Collateral Loan product was not found."));
        policy.validateProduct(product);
        policy.validateRequestedAmount(product, requestedAmount);
        policy.validateRequestedTerm(requestedTermMonths);
        policy.validateCollateralDetails(
                details.type(), details.description(), details.estimatedValue(),
                details.ownershipStatus(), details.conditionNote());

        applications.acquireCustomerProductLock(customerId, product.productCode());
        assertNoBlockingApplicationExists(customerId);

        LoanDocumentChecklistPort.SubmissionChecklistInitialState checklistInitialState =
                checklists.resolveSubmissionInitialState(product.productCode());
        LoanApplicationStatus initialStatus = checklistInitialState.uploadComplete()
                ? LoanApplicationStatus.SUBMITTED : LoanApplicationStatus.DOCUMENTS_PENDING;
        LoanApplicationTransitionResult submission = LoanApplication.submit(
                UUID.randomUUID(), customerId, product,
                formatApplicationNumber(applications.nextApplicationNumberSequence(), now),
                requestedAmount, requestedTermMonths, channel, now, initialStatus);

        LoanApplication savedApplication = applications.save(submission.loanApplication());
        Collateral savedCollateral = collaterals.save(policy.createCollateral(
                UUID.randomUUID(), savedApplication, details.type(), details.description(),
                details.estimatedValue(), details.ownershipStatus(), details.conditionNote(), now));
        LoanDocumentChecklistPort.SubmissionChecklistSnapshot checklist = checklists.createSubmissionChecklist(
                savedApplication.id(), savedApplication.productCode(), operation);
        CollateralLoanVerification verification = verifications.save(
                CollateralLoanVerification.pendingManualReview(UUID.randomUUID(), savedApplication, now));
        transitions.record(operation, submission.facts(), null);
        audits.publish(BusinessAuditEvent.single(operation, BusinessAuditEntry.of(
                BusinessAuditAction.COLLATERAL_LOAN_APPLICATION_SUBMITTED,
                BusinessAuditEntityType.LOAN_APPLICATION,
                savedApplication.id())));
        return new Result(savedApplication, savedCollateral, verification, checklist);
    }

    private void validateCustomerReadiness(UUID customerId) {
        CustomerReadinessSnapshot readiness = customers.findReadinessByCustomerId(customerId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "CUSTOMER_NOT_FOUND", "Customer was not found."));
        if (!readiness.active()) {
            throw new BusinessStateConflictException(
                    "CUSTOMER_NOT_ACTIVE",
                    "Customer must be active before creating a Collateral Loan application.");
        }
        if (!readiness.profileComplete()) {
            throw new BusinessRuleViolationException(
                    "PROFILE_INCOMPLETE",
                    "Customer profile must be complete before creating a Collateral Loan application.");
        }
        if (!readiness.hasPrimaryActiveBankAccount()) {
            throw new BusinessRuleViolationException(
                    "PRIMARY_BANK_ACCOUNT_REQUIRED",
                    "Customer must have a primary active bank account before creating a Collateral Loan application.");
        }
    }

    private void assertNoBlockingApplicationExists(UUID customerId) {
        if (applications.existsByCustomerIdAndProductCodeAndStatusIn(
                customerId, ProductCode.COLLATERAL_LOAN, LoanApplicationStatus.blockingStatuses())) {
            throw new BusinessStateConflictException(
                    "BLOCKING_APPLICATION_EXISTS",
                    "A blocking Collateral Loan application already exists for this customer.");
        }
    }

    private static String formatApplicationNumber(long sequence, LocalDateTime submittedAt) {
        return "CL-" + submittedAt.format(APPLICATION_NUMBER_DATE_FORMAT) + "-" + String.format("%06d", sequence);
    }

    record Result(
            LoanApplication application,
            Collateral collateral,
            CollateralLoanVerification verification,
            LoanDocumentChecklistPort.SubmissionChecklistSnapshot checklist
    ) {
    }
}
