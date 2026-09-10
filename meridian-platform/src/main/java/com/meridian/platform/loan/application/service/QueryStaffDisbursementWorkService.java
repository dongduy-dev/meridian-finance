package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.dto.LoanContractDto;
import com.meridian.platform.loan.application.dto.StaffDisbursementActivationDto;
import com.meridian.platform.loan.application.dto.StaffDisbursementCaseDto;
import com.meridian.platform.loan.application.dto.StaffDisbursementContractDto;
import com.meridian.platform.loan.application.dto.StaffDisbursementWorkPageDto;
import com.meridian.platform.loan.application.mapper.LoanContractMapper;
import com.meridian.platform.loan.application.port.in.QueryStaffDisbursementWorkUseCase;
import com.meridian.platform.loan.application.port.out.LoanAccountRepository;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.LoanContractRepository;
import com.meridian.platform.loan.application.port.out.ManualDisbursementRepository;
import com.meridian.platform.loan.application.port.out.RepaymentScheduleRepository;
import com.meridian.platform.loan.domain.model.ApprovedOfferFinancialTerms;
import com.meridian.platform.loan.domain.model.LoanAccount;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.LoanContract;
import com.meridian.platform.loan.domain.model.LoanContractStatus;
import com.meridian.platform.loan.domain.model.ManualDisbursement;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProtectedDisbursementBankAccount;
import com.meridian.platform.loan.domain.model.RepaymentSchedule;
import com.meridian.platform.loan.domain.model.RepaymentScheduleItem;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

@Service
public class QueryStaffDisbursementWorkService implements QueryStaffDisbursementWorkUseCase {

    public static final int MAX_PAGE_SIZE = 100;

    private final LoanApplicationRepository applications;
    private final LoanContractRepository contracts;
    private final LoanAccountRepository loanAccounts;
    private final ManualDisbursementRepository manualDisbursements;
    private final RepaymentScheduleRepository repaymentSchedules;
    private final LoanContractMapper contractMapper;
    private final CurrentUserProvider currentUserProvider;

    public QueryStaffDisbursementWorkService(
            LoanApplicationRepository applications,
            LoanContractRepository contracts,
            LoanAccountRepository loanAccounts,
            ManualDisbursementRepository manualDisbursements,
            RepaymentScheduleRepository repaymentSchedules,
            LoanContractMapper contractMapper,
            CurrentUserProvider currentUserProvider
    ) {
        this.applications = applications;
        this.contracts = contracts;
        this.loanAccounts = loanAccounts;
        this.manualDisbursements = manualDisbursements;
        this.repaymentSchedules = repaymentSchedules;
        this.contractMapper = contractMapper;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public StaffDisbursementWorkPageDto queryWork(ProductCode productCode, int page, int size) {
        requireAuthority(currentUserProvider.currentUser());
        requireValidPage(page, size);
        LoanApplicationRepository.StaffPage selected = applications.findStaffPage(
                productCode,
                LoanApplicationStatus.DISBURSEMENT_PENDING,
                page,
                size
        );
        return new StaffDisbursementWorkPageDto(
                selected.page(),
                selected.size(),
                selected.totalElements(),
                selected.totalPages(),
                selected.applications().stream().map(this::toItem).toList()
        );
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public StaffDisbursementCaseDto queryCase(UUID loanApplicationId) {
        Objects.requireNonNull(loanApplicationId, "loanApplicationId must not be null");
        requireAuthority(currentUserProvider.currentUser());
        LoanApplication application = applications.findById(loanApplicationId)
                .orElseThrow(QueryStaffDisbursementWorkService::notFound);
        if (application.status() != LoanApplicationStatus.DISBURSEMENT_PENDING
                && application.status() != LoanApplicationStatus.DISBURSED) {
            throw new BusinessStateConflictException(
                    "INVALID_APPLICATION_STATE",
                    "Loan Application is not in a disbursement workspace state."
            );
        }

        LoanContract contract = requireReadyContract(application);
        StaffDisbursementActivationDto activation;
        WorkStage stage;
        if (application.status() == LoanApplicationStatus.DISBURSEMENT_PENDING) {
            requireNoActivationEvidence(application.id());
            activation = null;
            stage = WorkStage.READY_TO_DISBURSE;
        } else {
            activation = requireCompletedActivation(application, contract);
            stage = WorkStage.DISBURSED;
        }

        return new StaffDisbursementCaseDto(
                application.id(),
                application.applicationNumber(),
                application.productCode().name(),
                application.productType().name(),
                application.requestedAmount(),
                application.requestedTermMonths(),
                application.status().name(),
                application.submittedAt(),
                contractMapper.toDto(contract),
                activation,
                stage.name()
        );
    }

    private StaffDisbursementWorkPageDto.ItemDto toItem(LoanApplication application) {
        if (application.status() != LoanApplicationStatus.DISBURSEMENT_PENDING) {
            throw systemConflict();
        }
        LoanContract contract = requireReadyContract(application);
        requireNoActivationEvidence(application.id());
        return new StaffDisbursementWorkPageDto.ItemDto(
                application.id(),
                application.applicationNumber(),
                application.productCode().name(),
                application.productType().name(),
                application.requestedAmount(),
                application.requestedTermMonths(),
                application.status().name(),
                application.submittedAt(),
                toContractSummary(contract),
                WorkStage.READY_TO_DISBURSE.name()
        );
    }

    private LoanContract requireReadyContract(LoanApplication application) {
        LoanContract contract = contracts.findCurrentByApplicationId(application.id())
                .orElseThrow(QueryStaffDisbursementWorkService::systemConflict);
        ProtectedDisbursementBankAccount destination = contract.disbursementBankAccount();
        if (!contract.loanApplicationId().equals(application.id())
                || !destination.customerId().equals(application.customerId())
                || contract.status() != LoanContractStatus.READY_FOR_DISBURSEMENT
                || contract.confirmedAt() == null
                || contract.supersededAt() != null) {
            throw systemConflict();
        }
        return contract;
    }

    private void requireNoActivationEvidence(UUID applicationId) {
        if (loanAccounts.findByLoanApplicationId(applicationId).isPresent()
                || manualDisbursements.findByLoanApplicationId(applicationId).isPresent()
                || repaymentSchedules.findByLoanApplicationId(applicationId).isPresent()) {
            throw systemConflict();
        }
    }

    private StaffDisbursementActivationDto requireCompletedActivation(
            LoanApplication application,
            LoanContract contract
    ) {
        LoanAccount account = loanAccounts.findByLoanApplicationId(application.id())
                .orElseThrow(QueryStaffDisbursementWorkService::systemConflict);
        ManualDisbursement disbursement = manualDisbursements
                .findByLoanApplicationId(application.id())
                .orElseThrow(QueryStaffDisbursementWorkService::systemConflict);
        RepaymentSchedule schedule = repaymentSchedules.findByLoanApplicationId(application.id())
                .orElseThrow(QueryStaffDisbursementWorkService::systemConflict);
        validateCompletedIdentity(application, contract, account, disbursement, schedule);
        return new StaffDisbursementActivationDto(
                account.id(),
                account.accountNumber(),
                account.status().name(),
                account.activatedAt(),
                disbursement.disbursedAmount(),
                disbursement.valueDate(),
                disbursement.firstRepaymentDate(),
                schedule.id(),
                schedule.scheduleType().name(),
                schedule.version(),
                schedule.items().stream()
                        .map(QueryStaffDisbursementWorkService::toScheduleItem)
                        .toList()
        );
    }

    private static void validateCompletedIdentity(
            LoanApplication application,
            LoanContract contract,
            LoanAccount account,
            ManualDisbursement disbursement,
            RepaymentSchedule schedule
    ) {
        ApprovedOfferFinancialTerms terms = contract.financialTerms();
        if (!account.loanApplicationId().equals(application.id())
                || !account.loanContractId().equals(contract.id())
                || !account.customerId().equals(application.customerId())
                || !disbursement.loanApplicationId().equals(application.id())
                || !disbursement.loanContractId().equals(contract.id())
                || !disbursement.loanAccountId().equals(account.id())
                || disbursement.expectedContractVersion() != contract.contractVersion()
                || !schedule.loanApplicationId().equals(application.id())
                || !schedule.loanContractId().equals(contract.id())
                || !schedule.loanAccountId().equals(account.id())
                || differs(disbursement.disbursedAmount(), terms.approvedPrincipal())
                || differs(account.approvedPrincipal(), terms.approvedPrincipal())
                || account.approvedTermMonths() != terms.approvedTermMonths()
                || differs(account.totalInterest(), terms.totalInterest())
                || differs(account.feeAmount(), terms.feeAmount())
                || differs(account.totalRepaymentAmount(), terms.totalRepaymentAmount())
                || differs(schedule.approvedPrincipal(), terms.approvedPrincipal())
                || schedule.approvedTermMonths() != terms.approvedTermMonths()
                || differs(schedule.totalInterest(), terms.totalInterest())
                || differs(schedule.feeAmount(), terms.feeAmount())
                || differs(schedule.totalRepaymentAmount(), terms.totalRepaymentAmount())
                || !schedule.firstDueDate().equals(disbursement.firstRepaymentDate())
                || !account.activatedAt().equals(disbursement.confirmedAt())
                || !account.activatedAt().equals(schedule.generatedAt())) {
            throw systemConflict();
        }
    }

    private static boolean differs(BigDecimal left, BigDecimal right) {
        return left.compareTo(right) != 0;
    }

    private static StaffDisbursementContractDto toContractSummary(LoanContract contract) {
        ProtectedDisbursementBankAccount destination = contract.disbursementBankAccount();
        return new StaffDisbursementContractDto(
                contract.id(),
                contract.contractReference(),
                contract.contractVersion(),
                contract.status().name(),
                contract.financialTerms().approvedPrincipal(),
                contract.financialTerms().approvedTermMonths(),
                contract.financialTerms().repaymentMethod().name(),
                contract.confirmedAt(),
                new StaffDisbursementContractDto.DestinationDto(
                        destination.bankCode(),
                        destination.bankNameSnapshot(),
                        destination.accountHolderName(),
                        "****" + destination.lastFour()
                )
        );
    }

    private static StaffDisbursementActivationDto.ScheduleItemDto toScheduleItem(
            RepaymentScheduleItem item
    ) {
        return new StaffDisbursementActivationDto.ScheduleItemDto(
                item.installmentNumber(),
                item.dueDate(),
                item.principalDue(),
                item.interestDue(),
                item.feeDue(),
                item.totalDue()
        );
    }

    private static void requireAuthority(AuthenticatedUser actor) {
        if (!"STAFF".equals(actor.userType())
                || actor.optionalCustomerId().isPresent()
                || !actor.hasPermission("loan:disburse")) {
            throw new AuthorizationException(
                    "LOAN_DISBURSEMENT_WORK_ACCESS_DENIED",
                    "Staff disbursement work access is denied."
            );
        }
        if (!actor.roles().contains("ACCOUNTING_OFFICER")) {
            throw new AuthorizationException(
                    "ACCOUNTING_ROLE_REQUIRED",
                    "Accounting authority is required for disbursement work."
            );
        }
    }

    private static void requireValidPage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Disbursement work page arguments are invalid.");
        }
    }

    private static EntityNotFoundException notFound() {
        return new EntityNotFoundException(
                "LOAN_APPLICATION_NOT_FOUND",
                "Loan Application was not found."
        );
    }

    private static BusinessStateConflictException systemConflict() {
        return new BusinessStateConflictException(
                "SYSTEM_STATE_CONFLICT",
                "Disbursement work evidence is inconsistent."
        );
    }

    private enum WorkStage {
        READY_TO_DISBURSE,
        DISBURSED
    }
}
