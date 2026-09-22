package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.port.in.AuthorizeAssistedActionEvidenceUseCase;
import com.meridian.platform.loan.application.port.out.ApprovedOfferRepository;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.LoanApplicationCancellationRepository;
import com.meridian.platform.loan.application.port.out.LoanContractRepository;
import com.meridian.platform.loan.application.port.out.LoanCorrectionRepository;
import com.meridian.platform.loan.application.port.out.StaffAssistedContractAcknowledgmentRepository;
import com.meridian.platform.loan.application.port.out.StaffAssistedOfferResponseRepository;
import com.meridian.platform.loan.domain.model.*;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AssistedActionEvidenceAuthorizationService implements AuthorizeAssistedActionEvidenceUseCase {

    private final LoanApplicationRepository applications;
    private final ApprovedOfferRepository offers;
    private final LoanContractRepository contracts;
    private final LoanCorrectionRepository corrections;
    private final LoanApplicationCancellationRepository cancellations;
    private final StaffAssistedOfferResponseRepository offerResponses;
    private final StaffAssistedContractAcknowledgmentRepository acknowledgments;
    private final CurrentUserProvider currentUsers;
    private final Clock clock;

    public AssistedActionEvidenceAuthorizationService(
            LoanApplicationRepository applications,
            ApprovedOfferRepository offers,
            LoanContractRepository contracts,
            LoanCorrectionRepository corrections,
            LoanApplicationCancellationRepository cancellations,
            StaffAssistedOfferResponseRepository offerResponses,
            StaffAssistedContractAcknowledgmentRepository acknowledgments,
            CurrentUserProvider currentUsers,
            Clock clock
    ) {
        this.applications = applications;
        this.offers = offers;
        this.contracts = contracts;
        this.corrections = corrections;
        this.cancellations = cancellations;
        this.offerResponses = offerResponses;
        this.acknowledgments = acknowledgments;
        this.currentUsers = currentUsers;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void authorizeOfferEvidence(UUID loanApplicationId, UUID approvedOfferId, String declaredDecision) {
        CustomerOfferDecision.valueOf(declaredDecision);
        AuthenticatedUser actor = requireStaff("loan:offer:respond:staff", "LOAN_OFFICER");
        applications.acquireWorkflowLock(loanApplicationId);
        LoanApplication application = lockApplication(loanApplicationId);
        requireAssistedApplication(application);
        if (application.status() != LoanApplicationStatus.CUSTOMER_ACCEPTANCE_PENDING) {
            throw notAllowed("Offer evidence is not mutable in the current application state.");
        }
        ApprovedOffer offer = offers.findByLoanApplicationIdForUpdate(loanApplicationId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "APPROVED_OFFER_NOT_FOUND", "Approved offer was not found."));
        if (!offer.id().equals(approvedOfferId) || offer.status() != ApprovedOfferStatus.PENDING
                || offer.isExpiredAt(LocalDateTime.now(clock))) {
            throw notAllowed("Offer evidence target is not eligible for response.");
        }
        if (offerResponses.findByApprovedOfferId(offer.id()).isPresent()) {
            throw notAllowed("Recorded offer-response evidence cannot be replaced.");
        }
        requireRole(actor, "LOAN_OFFICER");
    }

    @Override
    @Transactional
    public void authorizeContractEvidence(UUID loanApplicationId, UUID loanContractId, int contractVersion) {
        AuthenticatedUser actor = requireStaff("loan:contract:acknowledge:staff", "ACCOUNTING_OFFICER");
        if (contractVersion <= 0) throw new IllegalArgumentException("contractVersion must be positive");
        applications.acquireWorkflowLock(loanApplicationId);
        LoanApplication application = lockApplication(loanApplicationId);
        requireAssistedApplication(application);
        if (application.status() != LoanApplicationStatus.CONTRACT_PENDING) {
            throw notAllowed("Contract evidence is not mutable in the current application state.");
        }
        LoanContract contract = contracts.findCurrentByApplicationIdForUpdate(loanApplicationId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "CURRENT_CONTRACT_MISSING", "Current loan contract was not found."));
        if (!contract.id().equals(loanContractId) || contract.contractVersion() != contractVersion) {
            throw new BusinessStateConflictException(
                    "CONTRACT_VERSION_STALE", "The assisted evidence target is not the current contract version.");
        }
        if (contract.status() != LoanContractStatus.PREPARED) {
            throw notAllowed("Contract acknowledgment evidence is not mutable after acknowledgment.");
        }
        if (acknowledgments.findByLoanContractIdAndContractVersion(contract.id(), contractVersion).isPresent()) {
            throw notAllowed("Recorded contract-acknowledgment evidence cannot be replaced.");
        }
        requireRole(actor, "ACCOUNTING_OFFICER");
    }

    @Override
    @Transactional
    public void authorizeCancellationEvidence(UUID loanApplicationId, UUID correctionRequestId) {
        AuthenticatedUser actor = requireStaff("loan:cancel:staff", "LOAN_OFFICER");
        applications.acquireWorkflowLock(loanApplicationId);
        LoanApplication application = lockApplication(loanApplicationId);
        if (application.originationChannel() != OriginationChannel.STAFF_ASSISTED
                || application.productCode() != ProductCode.UNSECURED_CONSUMER_LOAN
                || application.status() != LoanApplicationStatus.RETURNED_FOR_REVISION) {
            throw notAllowed("Cancellation evidence is available only for an eligible Staff-assisted UCL correction.");
        }
        LoanCorrectionRequest correction = corrections
                .findActiveRequestByApplicationIdForUpdate(loanApplicationId)
                .orElseThrow(() -> notAllowed("An active correction request is required."));
        if (!correction.id().equals(correctionRequestId)) {
            throw new BusinessStateConflictException(
                    "CORRECTION_REQUEST_CONFLICT",
                    "The cancellation evidence target is not the active correction request."
            );
        }
        if (cancellations.findByLoanApplicationId(loanApplicationId).isPresent()) {
            throw notAllowed("Recorded cancellation evidence cannot be replaced.");
        }
        requireRole(actor, "LOAN_OFFICER");
    }

    private LoanApplication lockApplication(UUID loanApplicationId) {
        return applications.findByIdForUpdate(loanApplicationId).orElseThrow(() -> new EntityNotFoundException(
                "LOAN_APPLICATION_NOT_FOUND", "Loan application was not found."));
    }

    private static void requireAssistedApplication(LoanApplication application) {
        if (application.originationChannel() != OriginationChannel.STAFF_ASSISTED
                || (application.productCode() != ProductCode.UNSECURED_CONSUMER_LOAN
                && application.productCode() != ProductCode.COLLATERAL_LOAN)) {
            throw notAllowed("Assisted-action evidence is available only for Staff-assisted UCL or Collateral Loan.");
        }
    }

    private AuthenticatedUser requireStaff(String permission, String role) {
        AuthenticatedUser actor = currentUsers.currentUser();
        if (!"STAFF".equals(actor.userType()) || actor.optionalCustomerId().isPresent()
                || !actor.hasPermission(permission)) {
            throw new AuthorizationException(
                    "ASSISTED_ACTION_ACCESS_DENIED", "Staff-assisted Customer action access is denied.");
        }
        requireRole(actor, role);
        return actor;
    }

    private static void requireRole(AuthenticatedUser actor, String role) {
        if (!actor.roles().contains(role)) {
            throw new AuthorizationException(
                    "ASSISTED_ACTION_ROLE_REQUIRED", "The required Staff business role is missing.");
        }
    }

    private static BusinessStateConflictException notAllowed(String message) {
        return new BusinessStateConflictException("ASSISTED_ACTION_NOT_ALLOWED", message);
    }
}
