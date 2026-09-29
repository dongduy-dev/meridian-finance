package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.dto.AccountingCaseContextDto;
import com.meridian.platform.loan.application.port.out.AccountingCustomerIdentityPort;
import com.meridian.platform.loan.application.port.out.ApprovedOfferApprovalProvenancePort;
import com.meridian.platform.loan.application.port.out.ApprovedOfferRepository;
import com.meridian.platform.loan.application.port.out.StaffActorDirectoryPort;
import com.meridian.platform.loan.application.port.out.StaffActorSummary;
import com.meridian.platform.loan.application.port.out.StaffAssistedContractAcknowledgmentRepository;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.ApprovedOfferStatus;
import com.meridian.platform.loan.domain.model.LoanContract;
import com.meridian.platform.loan.domain.model.ManualDisbursement;
import com.meridian.platform.loan.domain.model.OriginationChannel;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.StaffAssistedContractAcknowledgment;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Component
public class AccountingCaseContextComposer {
    private final AccountingCustomerIdentityPort customers;
    private final ApprovedOfferRepository offers;
    private final ApprovedOfferApprovalProvenancePort approvals;
    private final StaffAssistedContractAcknowledgmentRepository acknowledgments;
    private final StaffActorDirectoryPort staffActors;

    public AccountingCaseContextComposer(
            AccountingCustomerIdentityPort customers,
            ApprovedOfferRepository offers,
            ApprovedOfferApprovalProvenancePort approvals,
            StaffAssistedContractAcknowledgmentRepository acknowledgments,
            StaffActorDirectoryPort staffActors
    ) {
        this.customers = customers;
        this.offers = offers;
        this.approvals = approvals;
        this.acknowledgments = acknowledgments;
        this.staffActors = staffActors;
    }

    public AccountingCaseContextDto compose(
            LoanApplication application, LoanContract contract, ManualDisbursement disbursement
    ) {
        var customer = customers.findByCustomerId(application.customerId()).orElseThrow(AccountingCaseContextComposer::conflict);
        if (customer.customerNumber() == null || customer.customerNumber().isBlank()
                || customer.fullName() == null || customer.fullName().isBlank()) {
            throw conflict();
        }
        var offer = offers.findByLoanApplicationId(application.id()).orElseThrow(AccountingCaseContextComposer::conflict);
        if (!offer.loanApplicationId().equals(application.id())
                || offer.status() != ApprovedOfferStatus.ACCEPTED
                || contract != null && (!contract.loanApplicationId().equals(application.id())
                        || !contract.approvedOfferId().equals(offer.id()))) {
            throw conflict();
        }
        var approval = approvals.requireExactApproval(application.id(), offer.generatedAt());
        if (approval == null || approval.approverUserId() == null
                || !offer.generatedAt().equals(approval.approvedAt())) {
            throw conflict();
        }

        StaffAssistedContractAcknowledgment assisted = null;
        if (contract != null) {
            assisted = acknowledgments.findByLoanContractIdAndContractVersion(
                    contract.id(), contract.contractVersion()).orElse(null);
            boolean acknowledged = contract.acknowledgedAt() != null;
            if (application.originationChannel() == OriginationChannel.STAFF_ASSISTED) {
                if (application.productCode() != ProductCode.UNSECURED_CONSUMER_LOAN
                        && application.productCode() != ProductCode.COLLATERAL_LOAN) {
                    throw conflict();
                }
                if (acknowledged && (assisted == null
                        || !application.id().equals(assisted.loanApplicationId())
                        || !application.customerId().equals(assisted.customerId())
                        || !contract.id().equals(assisted.loanContractId())
                        || contract.contractVersion() != assisted.contractVersion()
                        || !Objects.equals(contract.acknowledgmentRequestId(), assisted.acknowledgmentRequestId())
                        || !Objects.equals(contract.acknowledgedByUserId(), assisted.recordedByStaffUserId())
                        || !Objects.equals(contract.acknowledgedAt(), assisted.recordedAt()))) {
                    throw conflict();
                }
            }
            if (assisted != null && (!acknowledged
                    || application.originationChannel() != OriginationChannel.STAFF_ASSISTED)) {
                throw conflict();
            }
        }
        if (disbursement != null && (contract == null
                || !application.id().equals(disbursement.loanApplicationId())
                || !contract.id().equals(disbursement.loanContractId())
                || disbursement.expectedContractVersion() != contract.contractVersion())) {
            throw conflict();
        }

        Set<UUID> actorIds = new LinkedHashSet<>();
        actorIds.add(approval.approverUserId());
        if (contract != null) {
            actorIds.add(contract.preparedByUserId());
            if (assisted != null) actorIds.add(assisted.recordedByStaffUserId());
            if (contract.confirmedByUserId() != null) actorIds.add(contract.confirmedByUserId());
        }
        if (disbursement != null) actorIds.add(disbursement.confirmedByUserId());
        Map<UUID, StaffActorSummary> actors = staffActors.findByUserIds(actorIds);
        if (actors == null) throw conflict();
        for (UUID id : actorIds) requireActor(actors, id);

        AccountingCaseContextDto.AcknowledgmentDto acknowledgment = null;
        if (contract != null && contract.acknowledgedAt() != null) {
            acknowledgment = assisted == null
                    ? new AccountingCaseContextDto.AcknowledgmentDto(
                            "CUSTOMER_SELF_SERVICE", null, contract.acknowledgedAt(), null)
                    : new AccountingCaseContextDto.AcknowledgmentDto(
                            "STAFF_RECORDED_CUSTOMER_EVIDENCE",
                            requireActor(actors, assisted.recordedByStaffUserId()),
                            assisted.recordedAt(), assisted.evidenceDocumentVersionId());
        }
        return new AccountingCaseContextDto(
                new AccountingCaseContextDto.CustomerDto(customer.customerNumber(), customer.fullName()),
                new AccountingCaseContextDto.HandoffDto(
                        event(actors, approval.approverUserId(), approval.approvedAt()),
                        contract == null ? null : event(actors, contract.preparedByUserId(), contract.preparedAt()),
                        acknowledgment,
                        contract == null || contract.confirmedAt() == null ? null
                                : event(actors, contract.confirmedByUserId(), contract.confirmedAt()),
                        disbursement == null ? null
                                : event(actors, disbursement.confirmedByUserId(), disbursement.confirmedAt())
                )
        );
    }

    private static AccountingCaseContextDto.ActorEventDto event(
            Map<UUID, StaffActorSummary> actors, UUID id, LocalDateTime at
    ) {
        if (at == null) throw conflict();
        return new AccountingCaseContextDto.ActorEventDto(requireActor(actors, id), at);
    }

    private static AccountingCaseContextDto.StaffActorDto requireActor(
            Map<UUID, StaffActorSummary> actors, UUID id
    ) {
        StaffActorSummary actor = id == null ? null : actors.get(id);
        if (actor == null || !id.equals(actor.userId()) || actor.displayName() == null
                || actor.displayName().isBlank() || actor.email() == null || actor.email().isBlank()) {
            throw conflict();
        }
        return new AccountingCaseContextDto.StaffActorDto(actor.userId(), actor.displayName(), actor.email());
    }

    private static BusinessStateConflictException conflict() {
        return new BusinessStateConflictException(
                "SYSTEM_STATE_CONFLICT", "Accounting handoff evidence is inconsistent.");
    }
}
