package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.port.in.QueryLoanAccountUseCase;
import com.meridian.platform.loan.application.port.out.LoanAccountRepository;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.LoanContractRepository;
import com.meridian.platform.loan.application.port.out.RepaymentScheduleRepository;
import com.meridian.platform.loan.domain.model.LoanAccount;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.LoanContract;
import com.meridian.platform.loan.domain.model.LoanContractStatus;
import com.meridian.platform.loan.domain.model.RepaymentSchedule;
import com.meridian.platform.loan.domain.model.RepaymentScheduleType;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
public class QueryLoanAccountService implements QueryLoanAccountUseCase {

    private static final String FULLY_MASKED_DESTINATION = "********";

    private final LoanApplicationRepository applications;
    private final LoanContractRepository contracts;
    private final LoanAccountRepository loanAccounts;
    private final RepaymentScheduleRepository repaymentSchedules;
    private final CurrentUserProvider currentUserProvider;

    public QueryLoanAccountService(
            LoanApplicationRepository applications,
            LoanContractRepository contracts,
            LoanAccountRepository loanAccounts,
            RepaymentScheduleRepository repaymentSchedules,
            CurrentUserProvider currentUserProvider
    ) {
        this.applications = applications;
        this.contracts = contracts;
        this.loanAccounts = loanAccounts;
        this.repaymentSchedules = repaymentSchedules;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional(readOnly = true)
    public Result query(UUID loanApplicationId) {
        Objects.requireNonNull(loanApplicationId, "loanApplicationId must not be null");
        AuthenticatedUser actor = currentUserProvider.currentUser();
        requireReadAuthority(actor);
        LoanApplication application = applications.findById(loanApplicationId)
                .orElseThrow(() -> applicationNotFound(actor));
        authorize(actor, application);

        LoanAccount account = loanAccounts.findByLoanApplicationId(application.id())
                .orElseThrow(QueryLoanAccountService::loanAccountNotFound);
        LoanContract contract = contracts.findCurrentByApplicationId(application.id())
                .orElseThrow(QueryLoanAccountService::systemStateConflict);
        RepaymentSchedule schedule = repaymentSchedules.findByLoanAccountId(account.id())
                .orElseThrow(QueryLoanAccountService::systemStateConflict);
        validateEvidence(application, contract, account, schedule);

        var destination = contract.disbursementBankAccount();
        return new Result(
                application.id(),
                account.id(),
                account.accountNumber(),
                account.status(),
                account.activatedAt(),
                account.approvedPrincipal(),
                account.approvedTermMonths(),
                account.totalInterest(),
                account.feeAmount(),
                account.totalRepaymentAmount(),
                new DestinationSummary(
                        destination.bankCode(),
                        destination.bankNameSnapshot(),
                        destination.accountHolderName(),
                        FULLY_MASKED_DESTINATION
                ),
                schedule.id(),
                schedule.scheduleType(),
                schedule.version(),
                schedule.firstDueDate(),
                schedule.lastDueDate(),
                schedule.items().stream()
                        .map(item -> new ScheduleItem(
                                item.installmentNumber(),
                                item.dueDate(),
                                item.principalDue(),
                                item.interestDue(),
                                item.feeDue(),
                                item.totalDue()
                        ))
                        .toList()
        );
    }

    private static void authorize(AuthenticatedUser actor, LoanApplication application) {
        if (actor.optionalCustomerId().isPresent()
                && !application.customerId().equals(actor.requireCustomerId())) {
            throw loanAccountNotFound();
        }
    }

    private static void requireReadAuthority(AuthenticatedUser actor) {
        if (actor.optionalCustomerId().isPresent()) {
            if (!actor.hasPermission("loan:read:own")) {
                throw accessDenied();
            }
        } else if (!actor.hasPermission("loan:read")) {
            throw accessDenied();
        }
    }

    private static EntityNotFoundException applicationNotFound(AuthenticatedUser actor) {
        if (actor.optionalCustomerId().isPresent()) {
            return loanAccountNotFound();
        }
        return new EntityNotFoundException(
                "LOAN_APPLICATION_NOT_FOUND",
                "Loan application was not found."
        );
    }

    private static EntityNotFoundException loanAccountNotFound() {
        return new EntityNotFoundException(
                "LOAN_ACCOUNT_NOT_FOUND",
                "Loan Account was not found."
        );
    }

    private static void validateEvidence(
            LoanApplication application,
            LoanContract contract,
            LoanAccount account,
            RepaymentSchedule schedule
    ) {
        if (application.status() != LoanApplicationStatus.DISBURSED
                || contract.status() != LoanContractStatus.READY_FOR_DISBURSEMENT
                || contract.supersededAt() != null
                || !contract.loanApplicationId().equals(application.id())
                || !contract.disbursementBankAccount().customerId().equals(application.customerId())
                || !account.loanApplicationId().equals(application.id())
                || !account.loanContractId().equals(contract.id())
                || !account.customerId().equals(application.customerId())
                || !schedule.loanApplicationId().equals(application.id())
                || !schedule.loanContractId().equals(contract.id())
                || !schedule.loanAccountId().equals(account.id())
                || schedule.scheduleType() != RepaymentScheduleType.FINAL
                || schedule.version() != RepaymentSchedule.INITIAL_FINAL_VERSION
                || account.approvedTermMonths() != schedule.approvedTermMonths()
                || account.approvedPrincipal().compareTo(schedule.approvedPrincipal()) != 0
                || account.totalInterest().compareTo(schedule.totalInterest()) != 0
                || account.feeAmount().compareTo(schedule.feeAmount()) != 0
                || account.totalRepaymentAmount().compareTo(schedule.totalRepaymentAmount()) != 0
                || account.approvedPrincipal().compareTo(
                        contract.financialTerms().approvedPrincipal()) != 0
                || account.approvedTermMonths()
                        != contract.financialTerms().approvedTermMonths()
                || account.totalInterest().compareTo(
                        contract.financialTerms().totalInterest()) != 0
                || account.feeAmount().compareTo(contract.financialTerms().feeAmount()) != 0
                || account.totalRepaymentAmount().compareTo(
                        contract.financialTerms().totalRepaymentAmount()) != 0) {
            throw systemStateConflict();
        }
    }

    private static AuthorizationException accessDenied() {
        return new AuthorizationException(
                "LOAN_APPLICATION_ACCESS_DENIED",
                "Loan application access is denied."
        );
    }

    private static BusinessStateConflictException systemStateConflict() {
        return new BusinessStateConflictException(
                "SYSTEM_STATE_CONFLICT",
                "Loan Account evidence conflicts with existing state."
        );
    }
}
