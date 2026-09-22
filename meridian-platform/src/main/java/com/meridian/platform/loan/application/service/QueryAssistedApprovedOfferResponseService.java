package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.dto.*;
import com.meridian.platform.loan.application.mapper.ApprovedOfferMapper;
import com.meridian.platform.loan.application.port.in.QueryAssistedApprovedOfferResponseUseCase;
import com.meridian.platform.loan.application.port.out.*;
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
public class QueryAssistedApprovedOfferResponseService implements QueryAssistedApprovedOfferResponseUseCase {

    private final LoanApplicationRepository applications;
    private final ApprovedOfferRepository offers;
    private final LoanAssistedActionEvidencePort evidence;
    private final CurrentUserProvider currentUsers;
    private final ApprovedOfferMapper mapper;
    private final Clock clock;

    public QueryAssistedApprovedOfferResponseService(
            LoanApplicationRepository applications,
            ApprovedOfferRepository offers,
            LoanAssistedActionEvidencePort evidence,
            CurrentUserProvider currentUsers,
            ApprovedOfferMapper mapper,
            Clock clock
    ) {
        this.applications = applications;
        this.offers = offers;
        this.evidence = evidence;
        this.currentUsers = currentUsers;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public AssistedOfferResponseCaseDto query(UUID loanApplicationId) {
        requireActor(currentUsers.currentUser());
        LoanApplication application = applications.findById(loanApplicationId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "LOAN_APPLICATION_NOT_FOUND", "Loan application was not found."));
        if (application.originationChannel() != OriginationChannel.STAFF_ASSISTED
                || (application.productCode() != ProductCode.UNSECURED_CONSUMER_LOAN
                && application.productCode() != ProductCode.COLLATERAL_LOAN)) {
            throw new BusinessStateConflictException("ASSISTED_ACTION_NOT_ALLOWED",
                    "This application does not permit a Staff-assisted offer response.");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        ApprovedOffer offer = offers.findByLoanApplicationId(application.id())
                .orElseThrow(() -> new EntityNotFoundException(
                        "APPROVED_OFFER_NOT_FOUND", "Approved offer was not found."));
        AssistedActionEvidenceMetadataDto metadata = evidence.findOfferEvidence(application.id(), offer.id())
                .map(QueryAssistedApprovedOfferResponseService::toDto).orElse(null);
        String workState = application.status() == LoanApplicationStatus.CUSTOMER_ACCEPTANCE_PENDING
                && offer.status() == ApprovedOfferStatus.PENDING && !offer.isExpiredAt(now)
                ? "ACTION_AVAILABLE"
                : offer.effectiveStatusAt(now).name();
        return new AssistedOfferResponseCaseDto(
                application.id(), application.applicationNumber(), application.productCode().name(),
                application.productType().name(), application.originationChannel().name(),
                application.status().name(), application.submittedAt(), mapper.toDto(offer, now), metadata, workState);
    }

    private static void requireActor(AuthenticatedUser actor) {
        if (!"STAFF".equals(actor.userType()) || actor.optionalCustomerId().isPresent()
                || !actor.hasPermission("loan:offer:respond:staff")) {
            throw new AuthorizationException("ASSISTED_ACTION_ACCESS_DENIED",
                    "Staff-assisted offer response access is denied.");
        }
        if (!actor.roles().contains("LOAN_OFFICER")) {
            throw new AuthorizationException("ASSISTED_ACTION_ROLE_REQUIRED",
                    "Loan Officer authority is required for assisted offer response work.");
        }
    }

    public static AssistedActionEvidenceMetadataDto toDto(LoanAssistedActionEvidencePort.EvidenceSnapshot value) {
        UUID targetId = value.approvedOfferId() != null ? value.approvedOfferId() : value.loanContractId();
        return new AssistedActionEvidenceMetadataDto(
                value.documentId(), value.documentVersionId(), value.evidenceType(),
                value.declaredOfferDecision(), targetId, value.contractVersion(), value.versionNumber(),
                value.detectedMimeType(), value.byteSize(), value.uploadedAt());
    }
}
