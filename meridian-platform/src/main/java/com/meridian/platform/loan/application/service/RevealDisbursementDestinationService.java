package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.port.in.RevealDisbursementDestinationUseCase;
import com.meridian.platform.loan.application.port.out.DisbursementBankAccountProtectionContext;
import com.meridian.platform.loan.application.port.out.DisbursementBankAccountProtector;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.LoanContractRepository;
import com.meridian.platform.loan.application.port.out.ProtectedBankAccountEnvelope;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.LoanContract;
import com.meridian.platform.loan.domain.model.LoanContractStatus;
import com.meridian.platform.loan.domain.model.ProtectedDisbursementBankAccount;
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

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

@Service
public class RevealDisbursementDestinationService
        implements RevealDisbursementDestinationUseCase {

    private final LoanApplicationRepository applications;
    private final LoanContractRepository contracts;
    private final DisbursementBankAccountProtector accountProtector;
    private final BusinessAuditPublisher auditPublisher;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    public RevealDisbursementDestinationService(
            LoanApplicationRepository applications,
            LoanContractRepository contracts,
            DisbursementBankAccountProtector accountProtector,
            BusinessAuditPublisher auditPublisher,
            CurrentUserProvider currentUserProvider,
            Clock clock
    ) {
        this.applications = applications;
        this.contracts = contracts;
        this.accountProtector = accountProtector;
        this.auditPublisher = auditPublisher;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Result reveal(Command command) {
        Objects.requireNonNull(command, "command must not be null");
        AuthenticatedUser actor = currentUserProvider.currentUser();
        requireDisbursementAuthority(actor);

        applications.acquireWorkflowLock(command.loanApplicationId());
        LoanApplication application = applications.findByIdForUpdate(command.loanApplicationId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "LOAN_APPLICATION_NOT_FOUND",
                        "Loan application was not found."
                ));
        LoanContract contract = contracts.findCurrentByApplicationIdForUpdate(application.id())
                .orElseThrow(() -> new EntityNotFoundException(
                        "CURRENT_CONTRACT_MISSING",
                        "Current Loan contract was not found."
                ));
        validateRevealState(application, contract, command.expectedContractVersion());

        ProtectedDisbursementBankAccount destination = contract.disbursementBankAccount();
        byte[] plaintext = null;
        String accountNumber;
        try {
            plaintext = accountProtector.revealToBytes(
                    new ProtectedBankAccountEnvelope(
                            destination.protectionScheme(),
                            destination.keyId(),
                            destination.nonce(),
                            destination.ciphertext(),
                            destination.aadVersion()
                    ),
                    new DisbursementBankAccountProtectionContext(
                            contract.id(),
                            application.id(),
                            application.customerId(),
                            destination.sourceBankAccountId()
                    )
            );
            accountNumber = new String(plaintext, StandardCharsets.UTF_8);
            requireValidRevealedValue(accountNumber, destination.lastFour());
        } catch (RuntimeException exception) {
            throw destinationUnavailable();
        } finally {
            if (plaintext != null) {
                Arrays.fill(plaintext, (byte) 0);
            }
        }

        LocalDateTime now = LocalDateTime.now(clock);
        BusinessAuditPayload payload = BusinessAuditPayload.builder()
                .put(BusinessAuditPayloadKey.LOAN_APPLICATION_ID, application.id())
                .put(BusinessAuditPayloadKey.LOAN_CONTRACT_ID, contract.id())
                .build();
        auditPublisher.publish(BusinessAuditEvent.single(
                BusinessOperationContext.user(UUID.randomUUID(), actor.userId(), now),
                new BusinessAuditEntry(
                        BusinessAuditAction.LOAN_CONTRACT_DISBURSEMENT_DESTINATION_REVEALED,
                        BusinessAuditEntityType.LOAN_CONTRACT,
                        contract.id(),
                        payload
                )
        ));

        return new Result(
                application.id(),
                contract.id(),
                contract.contractVersion(),
                destination.bankCode(),
                destination.bankNameSnapshot(),
                destination.accountHolderName(),
                accountNumber
        );
    }

    private static void validateRevealState(
            LoanApplication application,
            LoanContract contract,
            int expectedContractVersion
    ) {
        if (!contract.loanApplicationId().equals(application.id())
                || !contract.disbursementBankAccount().customerId().equals(application.customerId())) {
            throw systemStateConflict();
        }
        if (contract.contractVersion() != expectedContractVersion) {
            throw new BusinessStateConflictException(
                    "CONTRACT_VERSION_STALE",
                    "Expected contract version is stale."
            );
        }
        if (application.status() != LoanApplicationStatus.DISBURSEMENT_PENDING
                || contract.status() != LoanContractStatus.READY_FOR_DISBURSEMENT
                || contract.supersededAt() != null
                || contract.confirmedAt() == null) {
            throw new BusinessStateConflictException(
                    "DISBURSEMENT_DESTINATION_REVEAL_NOT_ALLOWED",
                    "The contractual disbursement destination cannot be revealed in the current state."
            );
        }
    }

    private static void requireDisbursementAuthority(AuthenticatedUser actor) {
        if (actor.optionalCustomerId().isPresent() || !actor.hasPermission("loan:disburse")) {
            throw new AuthorizationException(
                    "DISBURSEMENT_DESTINATION_ACCESS_DENIED",
                    "Disbursement destination access is denied."
            );
        }
    }

    private static void requireValidRevealedValue(String accountNumber, String lastFour) {
        if (accountNumber.isBlank() || accountNumber.length() > 100
                || !accountNumber.endsWith(lastFour)
                || accountNumber.chars().anyMatch(Character::isISOControl)) {
            throw destinationUnavailable();
        }
    }

    private static BusinessStateConflictException destinationUnavailable() {
        return new BusinessStateConflictException(
                "DISBURSEMENT_DESTINATION_UNAVAILABLE",
                "The contractual disbursement destination is unavailable."
        );
    }

    private static BusinessStateConflictException systemStateConflict() {
        return new BusinessStateConflictException(
                "SYSTEM_STATE_CONFLICT",
                "Loan contract evidence conflicts with existing state."
        );
    }
}
