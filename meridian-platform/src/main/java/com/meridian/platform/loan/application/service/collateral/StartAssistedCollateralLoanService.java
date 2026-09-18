package com.meridian.platform.loan.application.service.collateral;

import com.meridian.platform.loan.application.dto.AssistedOriginationCaseDto;
import com.meridian.platform.loan.application.dto.CollateralDetailsRequest;
import com.meridian.platform.loan.application.dto.CollateralLoanApplicationRequest;
import com.meridian.platform.loan.application.port.in.StartAssistedCollateralLoanUseCase;
import com.meridian.platform.loan.application.port.out.AssistedOriginationCaseRepository;
import com.meridian.platform.loan.application.port.out.CollateralLoanVerificationRepository;
import com.meridian.platform.loan.application.port.out.CollateralRepository;
import com.meridian.platform.loan.application.port.out.CustomerReadinessPort;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.LoanDocumentChecklistPort;
import com.meridian.platform.loan.application.port.out.LoanIntakeEvidencePort;
import com.meridian.platform.loan.application.port.out.LoanProductRepository;
import com.meridian.platform.loan.application.service.LoanApplicationStatusTransitionRecorder;
import com.meridian.platform.loan.domain.model.AssistedOriginationCase;
import com.meridian.platform.loan.domain.model.OriginationChannel;
import com.meridian.platform.loan.domain.model.ProductCode;
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
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Service
public class StartAssistedCollateralLoanService implements StartAssistedCollateralLoanUseCase {

    private static final String PERMISSION = "loan:originate:staff";

    private final AssistedOriginationCaseRepository cases;
    private final LoanIntakeEvidencePort intakeEvidence;
    private final CurrentUserProvider currentUsers;
    private final BusinessAuditPublisher audits;
    private final Clock clock;
    private final CollateralLoanOrigination origination;

    public StartAssistedCollateralLoanService(
            AssistedOriginationCaseRepository cases,
            LoanIntakeEvidencePort intakeEvidence,
            LoanProductRepository products,
            LoanApplicationRepository applications,
            CollateralRepository collaterals,
            LoanDocumentChecklistPort checklists,
            CollateralLoanVerificationRepository verifications,
            CustomerReadinessPort customers,
            LoanApplicationStatusTransitionRecorder transitions,
            CurrentUserProvider currentUsers,
            BusinessAuditPublisher audits,
            Clock clock
    ) {
        this.cases = cases;
        this.intakeEvidence = intakeEvidence;
        this.currentUsers = currentUsers;
        this.audits = audits;
        this.clock = clock;
        this.origination = new CollateralLoanOrigination(
                products, applications, collaterals, checklists, verifications,
                customers, transitions, audits);
    }

    @Override
    @Transactional
    public AssistedOriginationCaseDto submit(
            UUID assistedOriginationCaseId,
            CollateralLoanApplicationRequest request
    ) {
        AuthenticatedUser actor = requireStaff();
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(request.requestedAmount(), "requestedAmount must not be null");
        Objects.requireNonNull(request.requestedTermMonths(), "requestedTermMonths must not be null");
        CollateralDetailsRequest details = Objects.requireNonNull(
                request.collateral(), "collateral must not be null");

        AssistedOriginationCase assistedCase = cases.findByIdForUpdate(assistedOriginationCaseId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "ASSISTED_ORIGINATION_CASE_NOT_FOUND",
                        "Assisted origination case was not found."));
        assistedCase.requireOpen();
        if (assistedCase.productCode() != ProductCode.COLLATERAL_LOAN) {
            throw new BusinessStateConflictException(
                    "ASSISTED_ORIGINATION_PRODUCT_MISMATCH",
                    "Assisted origination case is not eligible for Collateral Loan conversion.");
        }
        if (assistedCase.customerId() == null) {
            throw new BusinessStateConflictException(
                    "ASSISTED_ORIGINATION_CUSTOMER_REQUIRED",
                    "A selected Customer is required before assisted origination can be completed.");
        }

        intakeEvidence.requireCurrentCollateralPaperApplication(assistedCase.id());
        LocalDateTime now = LocalDateTime.now(clock);
        BusinessOperationContext operation = BusinessOperationContext.user(
                UUID.randomUUID(), actor.userId(), now);
        CollateralLoanOrigination.Result result = origination.create(
                assistedCase.customerId(), request.requestedAmount(), request.requestedTermMonths(),
                details, OriginationChannel.STAFF_ASSISTED, operation, now);

        AssistedOriginationCase completed = cases.save(assistedCase.complete(
                result.application().id(), now));
        audits.publish(BusinessAuditEvent.single(operation, new BusinessAuditEntry(
                BusinessAuditAction.ASSISTED_ORIGINATION_CASE_COMPLETED,
                BusinessAuditEntityType.ASSISTED_ORIGINATION_CASE,
                completed.id(),
                BusinessAuditPayload.builder()
                        .put(BusinessAuditPayloadKey.ASSISTED_ORIGINATION_CASE_ID, completed.id())
                        .put(BusinessAuditPayloadKey.LOAN_APPLICATION_ID, result.application().id())
                        .put(BusinessAuditPayloadKey.CUSTOMER_ID, completed.customerId())
                        .put(BusinessAuditPayloadKey.PRODUCT_CODE, completed.productCode())
                        .put(BusinessAuditPayloadKey.ASSISTED_ORIGINATION_STATUS, completed.status())
                        .build())));
        return toDto(completed);
    }

    private AuthenticatedUser requireStaff() {
        AuthenticatedUser actor = currentUsers.currentUser();
        if (!"STAFF".equals(actor.userType()) || actor.optionalCustomerId().isPresent()
                || !actor.hasPermission(PERMISSION)) {
            throw new AuthorizationException(
                    "ASSISTED_ORIGINATION_ACCESS_DENIED",
                    "Staff-assisted origination access is denied.");
        }
        return actor;
    }

    private static AssistedOriginationCaseDto toDto(AssistedOriginationCase value) {
        return new AssistedOriginationCaseDto(
                value.id(), value.productCode().name(), value.customerId(), value.status().name(),
                value.loanApplicationId(), value.createdByStaffUserId(), value.createdAt(),
                value.updatedAt(), value.terminalAt());
    }
}
