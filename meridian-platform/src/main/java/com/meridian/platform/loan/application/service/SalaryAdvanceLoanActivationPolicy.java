package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.port.out.SalaryAdvanceLimitMovementRepository;
import com.meridian.platform.loan.application.port.out.SalaryAdvanceLimitRepository;
import com.meridian.platform.loan.application.port.out.SalaryAdvanceVerificationRepository;
import com.meridian.platform.loan.domain.model.LoanAccount;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanContract;
import com.meridian.platform.loan.domain.model.LoanContractStatus;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductVerificationResult;
import com.meridian.platform.loan.domain.model.SalaryAdvanceLimit;
import com.meridian.platform.loan.domain.model.SalaryAdvanceLimitMovement;
import com.meridian.platform.loan.domain.model.SalaryAdvanceLimitMovementType;
import com.meridian.platform.loan.domain.model.SalaryAdvanceVerification;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class SalaryAdvanceLoanActivationPolicy implements LoanProductActivationPolicy {

    private final SalaryAdvanceVerificationRepository verifications;
    private final SalaryAdvanceLimitRepository limits;
    private final SalaryAdvanceLimitMovementRepository movements;

    public SalaryAdvanceLoanActivationPolicy(
            SalaryAdvanceVerificationRepository verifications,
            SalaryAdvanceLimitRepository limits,
            SalaryAdvanceLimitMovementRepository movements
    ) {
        this.verifications = verifications;
        this.limits = limits;
        this.movements = movements;
    }

    @Override
    public ProductCode supportedProduct() {
        return ProductCode.SALARY_ADVANCE;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public ProductActivationResult activate(ProductActivationCommand command) {
        LoanApplication application = command.loanApplication();
        LoanContract contract = command.loanContract();
        LoanAccount account = command.loanAccount();
        requireMatchingActivation(application, contract, account);

        SalaryAdvanceVerification verification = verifications
                .findByLoanApplicationIdForUpdate(application.id())
                .orElseThrow(SalaryAdvanceLoanActivationPolicy::invalidReservation);
        requireMatchingVerification(application, verification);

        limits.acquireCustomerLinkLock(
                verification.customerId(),
                verification.customerPartnerEmployeeLinkId()
        );
        SalaryAdvanceLimit limit = limits.findByIdForUpdate(verification.salaryAdvanceLimitId())
                .orElseThrow(SalaryAdvanceLoanActivationPolicy::invalidReservation);
        requireMatchingLimit(application, verification, limit);

        List<SalaryAdvanceLimitMovement> reservations =
                movements.findByLoanApplicationIdAndMovementTypeForUpdate(
                        application.id(),
                        SalaryAdvanceLimitMovementType.RESERVED
                );
        List<SalaryAdvanceLimitMovement> releases =
                movements.findByLoanApplicationIdAndMovementTypeForUpdate(
                        application.id(),
                        SalaryAdvanceLimitMovementType.RESERVATION_RELEASED
                );
        List<SalaryAdvanceLimitMovement> conversions =
                movements.findByLoanApplicationIdAndMovementTypeForUpdate(
                        application.id(),
                        SalaryAdvanceLimitMovementType.DISBURSED_TO_USED
                );

        if (!releases.isEmpty()) {
            throw new BusinessStateConflictException(
                    "SALARY_ADVANCE_RESERVATION_RELEASED",
                    "Salary Advance reservation was already released."
            );
        }
        if (!conversions.isEmpty()) {
            throw new BusinessStateConflictException(
                    "SYSTEM_STATE_CONFLICT",
                    "Salary Advance exposure was already converted."
            );
        }

        BigDecimal principal = contract.financialTerms().approvedPrincipal();
        requireIntactReservation(application, limit, reservations, principal);

        BigDecimal outstandingReserved = movements.calculateOutstandingReservedAmount(limit.id());
        BigDecimal aggregateUsed = movements.calculateUsedAmount(limit.id());
        if (outstandingReserved == null
                || aggregateUsed == null
                || limit.reservedAmount().compareTo(outstandingReserved) != 0
                || limit.usedAmount().compareTo(aggregateUsed) != 0
                || outstandingReserved.compareTo(principal) < 0) {
            throw invalidReservation();
        }

        SalaryAdvanceLimit converted = limits.save(limit.convertReservedToUsed(principal));
        SalaryAdvanceLimitMovement movement = movements.save(
                SalaryAdvanceLimitMovement.disbursedToUsed(
                        command.movementId(),
                        converted.id(),
                        application.id(),
                        account.id(),
                        principal,
                        command.occurredAt()
                )
        );

        return new ProductActivationResult(
                ProductCode.SALARY_ADVANCE,
                converted.id(),
                movement.id(),
                principal,
                converted.usedAmount(),
                converted.reservedAmount(),
                converted.availableAmount()
        );
    }

    private static void requireMatchingActivation(
            LoanApplication application,
            LoanContract contract,
            LoanAccount account
    ) {
        if (application.productCode() != ProductCode.SALARY_ADVANCE) {
            throw stateConflict("Activation policy does not support the Loan product.");
        }
        if (contract.status() != LoanContractStatus.READY_FOR_DISBURSEMENT) {
            throw stateConflict("Loan contract is not ready for activation.");
        }
        if (!application.id().equals(contract.loanApplicationId())
                || !application.id().equals(account.loanApplicationId())
                || !contract.id().equals(account.loanContractId())
                || !application.customerId().equals(account.customerId())
                || !application.customerId().equals(
                        contract.disbursementBankAccount().customerId()
                )
                || account.approvedPrincipal().compareTo(
                        contract.financialTerms().approvedPrincipal()
                ) != 0
                || account.approvedTermMonths() != contract.financialTerms().approvedTermMonths()
                || account.totalInterest().compareTo(
                        contract.financialTerms().totalInterest()
                ) != 0
                || account.feeAmount().compareTo(contract.financialTerms().feeAmount()) != 0
                || account.totalRepaymentAmount().compareTo(
                        contract.financialTerms().totalRepaymentAmount()
                ) != 0) {
            throw stateConflict("Activation source references do not match.");
        }
    }

    private static void requireMatchingVerification(
            LoanApplication application,
            SalaryAdvanceVerification verification
    ) {
        if (!application.id().equals(verification.loanApplicationId())
                || !application.customerId().equals(verification.customerId())
                || verification.productVerificationResult() != ProductVerificationResult.VERIFIED) {
            throw invalidReservation();
        }
    }

    private static void requireMatchingLimit(
            LoanApplication application,
            SalaryAdvanceVerification verification,
            SalaryAdvanceLimit limit
    ) {
        if (!verification.salaryAdvanceLimitId().equals(limit.id())
                || !application.customerId().equals(limit.customerId())
                || !verification.customerPartnerEmployeeLinkId().equals(
                        limit.customerPartnerEmployeeLinkId()
                )) {
            throw invalidReservation();
        }
    }

    private static void requireIntactReservation(
            LoanApplication application,
            SalaryAdvanceLimit limit,
            List<SalaryAdvanceLimitMovement> reservations,
            BigDecimal principal
    ) {
        if (reservations.size() != 1) {
            throw invalidReservation();
        }
        SalaryAdvanceLimitMovement reservation = reservations.getFirst();
        if (!application.id().equals(reservation.loanApplicationId())
                || !limit.id().equals(reservation.salaryAdvanceLimitId())
                || reservation.amount().compareTo(principal) != 0) {
            throw invalidReservation();
        }
    }

    private static BusinessStateConflictException invalidReservation() {
        return new BusinessStateConflictException(
                "SALARY_ADVANCE_RESERVATION_INVALID",
                "Salary Advance reservation evidence is not valid for activation."
        );
    }

    private static BusinessStateConflictException stateConflict(String message) {
        return new BusinessStateConflictException("SYSTEM_STATE_CONFLICT", message);
    }
}
