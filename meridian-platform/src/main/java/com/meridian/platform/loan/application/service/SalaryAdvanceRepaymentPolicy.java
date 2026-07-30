package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.port.out.SalaryAdvanceLimitMovementRepository;
import com.meridian.platform.loan.application.port.out.SalaryAdvanceLimitRepository;
import com.meridian.platform.loan.application.port.out.SalaryAdvanceVerificationRepository;
import com.meridian.platform.loan.domain.model.LoanAccount;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductVerificationResult;
import com.meridian.platform.loan.domain.model.RepaymentAllocation;
import com.meridian.platform.loan.domain.model.RepaymentAllocationComponent;
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
import java.util.UUID;

@Service
public class SalaryAdvanceRepaymentPolicy implements LoanProductRepaymentPolicy {
    private final SalaryAdvanceVerificationRepository verifications;
    private final SalaryAdvanceLimitRepository limits;
    private final SalaryAdvanceLimitMovementRepository movements;

    public SalaryAdvanceRepaymentPolicy(
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
    public BigDecimal releasePrincipal(PrincipalReleaseCommand command) {
        Context context = lockAndValidate(command.application(), command.account());
        BigDecimal principal = principal(command.allocations());
        List<SalaryAdvanceLimitMovement> releases = releases(command.application().id());
        if (releases.stream().anyMatch(movement -> command.repaymentTransactionId()
                .equals(movement.repaymentTransactionId()))) {
            throw conflict();
        }
        BigDecimal converted = conversionAmount(
                command.application(), command.account(), context.limit()
        );
        validateExposure(context, command.account(), releases);
        BigDecimal alreadyReleased = releases.stream()
                .map(SalaryAdvanceLimitMovement::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (alreadyReleased.compareTo(converted) > 0) {
            throw conflict();
        }
        if (principal.signum() == 0) {
            return principal;
        }
        if (alreadyReleased.add(principal).compareTo(converted) > 0) {
            throw conflict();
        }
        SalaryAdvanceLimit updated = limits.save(context.limit().releaseUsed(principal));
        movements.save(SalaryAdvanceLimitMovement.repaidReleased(
                UUID.randomUUID(), updated.id(), command.application().id(),
                command.account().id(), command.repaymentTransactionId(), principal,
                command.occurredAt()
        ));
        return principal;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public void validateCompletedRelease(CompletedReleaseCommand command) {
        Context context = lockAndValidate(command.application(), command.account());
        List<SalaryAdvanceLimitMovement> releases = releases(command.application().id());
        BigDecimal converted = conversionAmount(
                command.application(), command.account(), context.limit()
        );
        validateExposure(context, command.account(), releases);
        BigDecimal alreadyReleased = releases.stream()
                .map(SalaryAdvanceLimitMovement::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (alreadyReleased.compareTo(converted) > 0) {
            throw conflict();
        }
        List<SalaryAdvanceLimitMovement> matching = releases.stream()
                .filter(item -> command.repaymentTransactionId()
                        .equals(item.repaymentTransactionId()))
                .toList();
        if (command.expectedPrincipalReleased().signum() == 0) {
            if (!matching.isEmpty()) {
                throw conflict();
            }
        } else if (matching.size() != 1
                || matching.getFirst().amount()
                .compareTo(command.expectedPrincipalReleased()) != 0) {
            throw conflict();
        }
    }

    private Context lockAndValidate(LoanApplication application, LoanAccount account) {
        if (application.productCode() != ProductCode.SALARY_ADVANCE
                || !application.id().equals(account.loanApplicationId())
                || !application.customerId().equals(account.customerId())) {
            throw conflict();
        }
        SalaryAdvanceVerification verification = verifications
                .findByLoanApplicationIdForUpdate(application.id())
                .orElseThrow(SalaryAdvanceRepaymentPolicy::conflict);
        if (!application.customerId().equals(verification.customerId())
                || verification.productVerificationResult()
                != ProductVerificationResult.VERIFIED) {
            throw conflict();
        }
        limits.acquireCustomerLinkLock(
                verification.customerId(),
                verification.customerPartnerEmployeeLinkId()
        );
        SalaryAdvanceLimit limit = limits
                .findByIdForUpdate(verification.salaryAdvanceLimitId())
                .orElseThrow(SalaryAdvanceRepaymentPolicy::conflict);
        if (!application.customerId().equals(limit.customerId())
                || !verification.customerPartnerEmployeeLinkId().equals(
                limit.customerPartnerEmployeeLinkId())) {
            throw conflict();
        }
        return new Context(limit);
    }

    private List<SalaryAdvanceLimitMovement> releases(UUID applicationId) {
        return movements.findByLoanApplicationIdAndMovementTypeForUpdate(
                applicationId,
                SalaryAdvanceLimitMovementType.REPAID_RELEASED
        );
    }

    private void validateExposure(
            Context context,
            LoanAccount account,
            List<SalaryAdvanceLimitMovement> releases
    ) {
        for (SalaryAdvanceLimitMovement release : releases) {
            if (!context.limit().id().equals(release.salaryAdvanceLimitId())
                    || !account.id().equals(release.loanAccountId())) {
                throw conflict();
            }
        }
        BigDecimal aggregateUsed = movements.calculateUsedAmount(context.limit().id());
        if (aggregateUsed == null
                || aggregateUsed.compareTo(context.limit().usedAmount()) != 0) {
            throw conflict();
        }
    }

    private BigDecimal conversionAmount(
            LoanApplication application,
            LoanAccount account,
            SalaryAdvanceLimit limit
    ) {
        List<SalaryAdvanceLimitMovement> conversions =
                movements.findByLoanApplicationIdAndMovementTypeForUpdate(
                        application.id(),
                        SalaryAdvanceLimitMovementType.DISBURSED_TO_USED
                );
        if (conversions.size() != 1) {
            throw conflict();
        }
        SalaryAdvanceLimitMovement conversion = conversions.getFirst();
        if (!limit.id().equals(conversion.salaryAdvanceLimitId())
                || !account.id().equals(conversion.loanAccountId())
                || conversion.amount().compareTo(account.approvedPrincipal()) != 0) {
            throw conflict();
        }
        return conversion.amount();
    }

    private static BigDecimal principal(List<RepaymentAllocation> allocations) {
        return allocations.stream()
                .filter(item -> item.component() == RepaymentAllocationComponent.PRINCIPAL)
                .map(RepaymentAllocation::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BusinessStateConflictException conflict() {
        return new BusinessStateConflictException(
                "SYSTEM_STATE_CONFLICT",
                "Salary Advance repayment exposure evidence is inconsistent."
        );
    }

    private record Context(SalaryAdvanceLimit limit) {
    }
}
