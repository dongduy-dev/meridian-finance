package com.meridian.platform.loan.application.service.collateral;

import com.meridian.platform.loan.application.service.LoanProductActivationPolicy;

import com.meridian.platform.loan.application.port.out.CollateralLoanVerificationRepository;
import com.meridian.platform.loan.domain.model.collateral.CollateralLoanVerification;
import com.meridian.platform.loan.domain.model.LoanAccount;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanContract;
import com.meridian.platform.loan.domain.model.LoanContractStatus;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductVerificationResult;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CollateralLoanActivationPolicy implements LoanProductActivationPolicy {

    private final CollateralLoanVerificationRepository verifications;

    public CollateralLoanActivationPolicy(
            CollateralLoanVerificationRepository verifications
    ) {
        this.verifications = verifications;
    }

    @Override
    public ProductCode supportedProduct() {
        return ProductCode.COLLATERAL_LOAN;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public ProductActivationResult activate(ProductActivationCommand command) {
        requireMatchingActivation(
                command.loanApplication(),
                command.loanContract(),
                command.loanAccount()
        );
        requireVerified(command.loanApplication(), false);
        return ProductActivationResult.withoutExposureEffect(ProductCode.COLLATERAL_LOAN);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public void validateCompletedActivation(CompletedActivationValidationCommand command) {
        requireMatchingActivation(
                command.loanApplication(),
                command.loanContract(),
                command.loanAccount()
        );
        requireVerified(command.loanApplication(), true);
    }

    private void requireVerified(LoanApplication application, boolean completed) {
        CollateralLoanVerification verification = verifications
                .findLatestByLoanApplicationId(application.id())
                .orElseThrow(() -> completed ? completedStateConflict() : invalidVerification());
        if (!verification.loanApplicationId().equals(application.id())
                || verification.productVerificationResult() != ProductVerificationResult.VERIFIED) {
            if (completed) {
                throw completedStateConflict();
            }
            throw invalidVerification();
        }
    }

    private static void requireMatchingActivation(
            LoanApplication application,
            LoanContract contract,
            LoanAccount account
    ) {
        if (application.productCode() != ProductCode.COLLATERAL_LOAN) {
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
                || account.approvedTermMonths()
                        != contract.financialTerms().approvedTermMonths()
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

    private static BusinessStateConflictException invalidVerification() {
        return new BusinessStateConflictException(
                "COLLATERAL_VERIFICATION_INVALID",
                "Collateral Loan verification is not valid for activation."
        );
    }

    private static BusinessStateConflictException stateConflict(String message) {
        return new BusinessStateConflictException("SYSTEM_STATE_CONFLICT", message);
    }

    private static BusinessStateConflictException completedStateConflict() {
        return stateConflict("Completed Collateral Loan activation evidence is inconsistent.");
    }
}
