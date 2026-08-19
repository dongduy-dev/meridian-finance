package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.port.in.EvaluateLoanAccountOverdueUseCase;
import com.meridian.platform.loan.application.port.out.LoanAccountRepository;
import com.meridian.platform.loan.application.port.out.LoanAccountStatusTransitionRepository;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.RepaymentInstallmentProgressRepository;
import com.meridian.platform.loan.application.port.out.RepaymentInstallmentStatusTransitionRepository;
import com.meridian.platform.loan.application.port.out.RepaymentScheduleRepository;
import com.meridian.platform.loan.domain.model.LoanAccount;
import com.meridian.platform.loan.domain.model.LoanAccountServicingAction;
import com.meridian.platform.loan.domain.model.LoanAccountStatus;
import com.meridian.platform.loan.domain.model.LoanAccountStatusTransition;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.RepaymentInstallmentProgress;
import com.meridian.platform.loan.domain.model.RepaymentInstallmentServicingAction;
import com.meridian.platform.loan.domain.model.RepaymentInstallmentStatusTransition;
import com.meridian.platform.loan.domain.model.RepaymentSchedule;
import com.meridian.platform.loan.domain.model.RepaymentScheduleType;
import com.meridian.platform.loan.domain.service.OverdueServicingCalculator;
import com.meridian.platform.shared.application.audit.BusinessAuditEntry;
import com.meridian.platform.shared.application.audit.BusinessAuditEvent;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.operation.BusinessOperationContext;
import com.meridian.platform.shared.domain.audit.BusinessAuditAction;
import com.meridian.platform.shared.domain.audit.BusinessAuditEntityType;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.model.ActorType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class EvaluateLoanAccountOverdueService
        implements EvaluateLoanAccountOverdueUseCase {

    private final LoanApplicationRepository applications;
    private final LoanAccountRepository accounts;
    private final RepaymentScheduleRepository schedules;
    private final RepaymentInstallmentProgressRepository progressRepository;
    private final RepaymentInstallmentStatusTransitionRepository installmentHistory;
    private final LoanAccountStatusTransitionRepository accountHistory;
    private final LoanProductRepaymentPolicyResolver repaymentPolicies;
    private final BusinessAuditPublisher auditPublisher;
    private final OverdueServicingCalculator calculator = new OverdueServicingCalculator();

    public EvaluateLoanAccountOverdueService(
            LoanApplicationRepository applications,
            LoanAccountRepository accounts,
            RepaymentScheduleRepository schedules,
            RepaymentInstallmentProgressRepository progressRepository,
            RepaymentInstallmentStatusTransitionRepository installmentHistory,
            LoanAccountStatusTransitionRepository accountHistory,
            LoanProductRepaymentPolicyResolver repaymentPolicies,
            BusinessAuditPublisher auditPublisher
    ) {
        this.applications = applications;
        this.accounts = accounts;
        this.schedules = schedules;
        this.progressRepository = progressRepository;
        this.installmentHistory = installmentHistory;
        this.accountHistory = accountHistory;
        this.repaymentPolicies = repaymentPolicies;
        this.auditPublisher = auditPublisher;
    }

    @Override
    @Transactional
    public Result evaluate(Command command) {
        applications.acquireWorkflowLock(command.loanApplicationId());
        LoanApplication application = applications
                .findByIdForUpdate(command.loanApplicationId())
                .orElseThrow(EvaluateLoanAccountOverdueService::conflict);
        if (application.status() != LoanApplicationStatus.DISBURSED) {
            throw conflict();
        }
        repaymentPolicies.resolve(application.productCode());
        LoanAccount account = accounts
                .findByLoanApplicationIdForUpdate(application.id())
                .orElseThrow(EvaluateLoanAccountOverdueService::conflict);
        validateOwnership(command, application, account);

        if (account.status() == LoanAccountStatus.SETTLED
                && account.repaymentBalance().totalOutstanding().signum() == 0) {
            return noOp(command, account);
        }
        if ((account.status() != LoanAccountStatus.ACTIVE
                && account.status() != LoanAccountStatus.OVERDUE)
                || account.repaymentBalance().totalOutstanding().signum() <= 0
                || command.evaluationDate().isBefore(account.servicingEvaluationDate())) {
            throw conflict();
        }
        if (command.evaluationDate().equals(account.servicingEvaluationDate())) {
            return noOp(command, account);
        }

        RepaymentSchedule schedule = schedules.findByLoanAccountIdForUpdate(account.id())
                .orElseThrow(EvaluateLoanAccountOverdueService::conflict);
        validateSchedule(application, account, schedule);
        List<RepaymentInstallmentProgress> current =
                progressRepository.findByLoanAccountIdForUpdate(account.id());
        OverdueServicingCalculator.Result calculated = calculator.evaluate(
                account, schedule, current, command.evaluationDate(), command.evaluatedAt()
        );

        UUID operationId = UUID.randomUUID();
        progressRepository.saveAll(calculated.progress());
        int installmentTransitions = appendInstallmentHistory(
                current, calculated, operationId, command
        );
        LoanAccount updated = account.withServicingState(
                calculated.balance(), calculated.accountStatus(), command.evaluatedAt()
        );
        accounts.updateServicingState(updated);
        boolean accountChanged = account.status() != updated.status();
        if (accountChanged) {
            accountHistory.save(new LoanAccountStatusTransition(
                    UUID.randomUUID(), account.id(),
                    accountHistory.nextSequenceNumber(account.id()), operationId,
                    account.status(), updated.status(),
                    LoanAccountServicingAction.OVERDUE_EVALUATED,
                    ActorType.SYSTEM, null, command.evaluationDate(), command.evaluatedAt()
            ));
            auditPublisher.publish(BusinessAuditEvent.single(
                    BusinessOperationContext.system(operationId, command.evaluatedAt()),
                    BusinessAuditEntry.of(
                            BusinessAuditAction.LOAN_ACCOUNT_STATUS_CHANGED,
                            BusinessAuditEntityType.LOAN_ACCOUNT,
                            account.id()
                    )
            ));
        }
        return new Result(
                application.id(), account.id(), command.evaluationDate(),
                account.status(), updated.status(), installmentTransitions,
                accountChanged, false
        );
    }

    private int appendInstallmentHistory(
            List<RepaymentInstallmentProgress> current,
            OverdueServicingCalculator.Result calculated,
            UUID operationId,
            Command command
    ) {
        Map<UUID, RepaymentInstallmentProgress> before = new LinkedHashMap<>();
        current.forEach(item -> before.put(item.repaymentScheduleItemId(), item));
        int count = 0;
        for (RepaymentInstallmentProgress after : calculated.progress()) {
            RepaymentInstallmentProgress prior = before.get(
                    after.repaymentScheduleItemId()
            );
            if (prior == null) {
                throw conflict();
            }
            if (prior.status() != after.status()) {
                installmentHistory.save(new RepaymentInstallmentStatusTransition(
                        UUID.randomUUID(), after.repaymentScheduleItemId(),
                        installmentHistory.nextSequenceNumber(
                                after.repaymentScheduleItemId()
                        ), operationId, prior.status(), after.status(),
                        RepaymentInstallmentServicingAction.OVERDUE_EVALUATED,
                        ActorType.SYSTEM, null, command.evaluationDate(),
                        command.evaluatedAt()
                ));
                count++;
            }
        }
        return count;
    }

    private static void validateOwnership(
            Command command,
            LoanApplication application,
            LoanAccount account
    ) {
        if (!command.loanAccountId().equals(account.id())
                || !application.id().equals(account.loanApplicationId())
                || !application.customerId().equals(account.customerId())) {
            throw conflict();
        }
    }

    private static void validateSchedule(
            LoanApplication application,
            LoanAccount account,
            RepaymentSchedule schedule
    ) {
        if (schedule.scheduleType() != RepaymentScheduleType.FINAL
                || schedule.version() != RepaymentSchedule.INITIAL_FINAL_VERSION
                || !application.id().equals(schedule.loanApplicationId())
                || !account.id().equals(schedule.loanAccountId())
                || !account.loanContractId().equals(schedule.loanContractId())) {
            throw conflict();
        }
    }

    private static Result noOp(Command command, LoanAccount account) {
        return new Result(
                command.loanApplicationId(), account.id(), command.evaluationDate(),
                account.status(), account.status(), 0, false, true
        );
    }

    private static BusinessStateConflictException conflict() {
        return new BusinessStateConflictException(
                "SYSTEM_STATE_CONFLICT",
                "Loan Account overdue servicing evidence is inconsistent."
        );
    }
}
