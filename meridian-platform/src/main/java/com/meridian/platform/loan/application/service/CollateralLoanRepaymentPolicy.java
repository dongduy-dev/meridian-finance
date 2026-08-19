package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.domain.model.LoanAccount;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.RepaymentAllocation;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class CollateralLoanRepaymentPolicy
        implements LoanProductRepaymentPolicy {

    @Override
    public ProductCode supportedProduct() {
        return ProductCode.COLLATERAL_LOAN;
    }

    @Override
    public BigDecimal releasePrincipal(PrincipalReleaseCommand command) {
        validateIdentity(command.application(), command.account());
        validateAllocationIdentity(command.repaymentTransactionId(), command.allocations());
        return BigDecimal.ZERO;
    }

    @Override
    public void validateReleaseSemantics(ReleaseValidationCommand command) {
        validateIdentity(command.application(), command.account());
        validateAllocationIdentity(command.repaymentTransactionId(), command.allocations());
        if (command.principalReleased().signum() != 0) {
            throw conflict();
        }
    }

    @Override
    public void validateCompletedRelease(CompletedReleaseCommand command) {
        validateReleaseSemantics(new ReleaseValidationCommand(
                command.application(), command.account(), command.repaymentTransactionId(),
                command.allocations(), command.expectedPrincipalReleased()
        ));
    }

    @Override
    public void validateCompletedPayoff(CompletedPayoffCommand command) {
        validateIdentity(command.application(), command.account());
        Set<UUID> transactionIds = new HashSet<>();
        BigDecimal principalAllocated = BigDecimal.ZERO;
        for (ReleaseEvidence evidence : command.releases()) {
            if (!transactionIds.add(evidence.repaymentTransactionId())
                    || evidence.principalAllocated().signum() < 0
                    || evidence.principalReleased().signum() != 0) {
                throw conflict();
            }
            principalAllocated = principalAllocated.add(evidence.principalAllocated());
        }
        if (principalAllocated.compareTo(command.account().approvedPrincipal()) != 0) {
            throw conflict();
        }
    }

    private static void validateIdentity(
            LoanApplication application,
            LoanAccount account
    ) {
        if (application.productCode() != ProductCode.COLLATERAL_LOAN
                || !application.id().equals(account.loanApplicationId())
                || !application.customerId().equals(account.customerId())) {
            throw conflict();
        }
    }

    private static void validateAllocationIdentity(
            UUID transactionId,
            List<RepaymentAllocation> allocations
    ) {
        if (allocations.stream().anyMatch(allocation ->
                !transactionId.equals(allocation.repaymentTransactionId()))) {
            throw conflict();
        }
    }

    private static BusinessStateConflictException conflict() {
        return new BusinessStateConflictException(
                "SYSTEM_STATE_CONFLICT",
                "Collateral Loan repayment exposure evidence is inconsistent."
        );
    }
}
