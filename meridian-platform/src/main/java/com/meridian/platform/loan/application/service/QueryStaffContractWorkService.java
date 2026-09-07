package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.dto.ContractReadinessDto;
import com.meridian.platform.loan.application.dto.LoanContractDto;
import com.meridian.platform.loan.application.dto.StaffContractCaseDto;
import com.meridian.platform.loan.application.dto.StaffContractWorkPageDto;
import com.meridian.platform.loan.application.mapper.LoanContractMapper;
import com.meridian.platform.loan.application.port.in.QueryContractReadinessUseCase;
import com.meridian.platform.loan.application.port.in.QueryStaffContractWorkUseCase;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.LoanContractRepository;
import com.meridian.platform.loan.domain.model.ContractReadinessBlockerCode;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.LoanContract;
import com.meridian.platform.loan.domain.model.LoanContractStatus;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
public class QueryStaffContractWorkService implements QueryStaffContractWorkUseCase {

    public static final int MAX_PAGE_SIZE = 100;

    private final LoanApplicationRepository applications;
    private final LoanContractRepository contracts;
    private final QueryContractReadinessUseCase readiness;
    private final LoanContractMapper contractMapper;
    private final CurrentUserProvider currentUserProvider;

    public QueryStaffContractWorkService(
            LoanApplicationRepository applications,
            LoanContractRepository contracts,
            QueryContractReadinessUseCase readiness,
            LoanContractMapper contractMapper,
            CurrentUserProvider currentUserProvider
    ) {
        this.applications = applications;
        this.contracts = contracts;
        this.readiness = readiness;
        this.contractMapper = contractMapper;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public StaffContractWorkPageDto queryWork(ProductCode productCode, int page, int size) {
        requireAuthority(currentUserProvider.currentUser());
        requireValidPage(page, size);
        LoanApplicationRepository.StaffPage selected = applications.findStaffPage(
                productCode,
                LoanApplicationStatus.CONTRACT_PENDING,
                page,
                size
        );
        return new StaffContractWorkPageDto(
                selected.page(),
                selected.size(),
                selected.totalElements(),
                selected.totalPages(),
                selected.applications().stream().map(this::toItem).toList()
        );
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public StaffContractCaseDto queryCase(UUID loanApplicationId) {
        Objects.requireNonNull(loanApplicationId, "loanApplicationId must not be null");
        requireAuthority(currentUserProvider.currentUser());
        LoanApplication application = applications.findById(loanApplicationId)
                .orElseThrow(QueryStaffContractWorkService::notFound);
        if (application.status() != LoanApplicationStatus.CONTRACT_PENDING
                && application.status() != LoanApplicationStatus.DISBURSEMENT_PENDING) {
            throw new BusinessStateConflictException(
                    "INVALID_APPLICATION_STATE",
                    "Loan Application is not in a contract workspace state."
            );
        }
        Projection projection = project(application);
        return new StaffContractCaseDto(
                application.id(),
                application.applicationNumber(),
                application.productCode().name(),
                application.productType().name(),
                application.requestedAmount(),
                application.requestedTermMonths(),
                application.status().name(),
                application.submittedAt(),
                projection.contract(),
                projection.readiness(),
                projection.stage().name()
        );
    }

    private StaffContractWorkPageDto.ItemDto toItem(LoanApplication application) {
        if (application.status() != LoanApplicationStatus.CONTRACT_PENDING) {
            throw systemConflict();
        }
        Projection projection = project(application);
        return new StaffContractWorkPageDto.ItemDto(
                application.id(),
                application.applicationNumber(),
                application.productCode().name(),
                application.productType().name(),
                application.requestedAmount(),
                application.requestedTermMonths(),
                application.status().name(),
                application.submittedAt(),
                projection.contract(),
                projection.readiness(),
                projection.stage().name()
        );
    }

    private Projection project(LoanApplication application) {
        LoanContract current = contracts.findCurrentByApplicationId(application.id()).orElse(null);
        Integer expectedVersion = current == null ? null : current.contractVersion();
        QueryContractReadinessUseCase.Snapshot readinessSnapshot = readiness.query(
                application.id(),
                expectedVersion
        );
        validateReadinessIdentity(application, current, readinessSnapshot);
        WorkStage stage = classify(application, current, readinessSnapshot);
        return new Projection(
                current == null ? null : contractMapper.toDto(current),
                contractMapper.toDto(readinessSnapshot),
                stage
        );
    }

    private static WorkStage classify(
            LoanApplication application,
            LoanContract current,
            QueryContractReadinessUseCase.Snapshot readinessSnapshot
    ) {
        if (application.status() == LoanApplicationStatus.DISBURSEMENT_PENDING) {
            if (current == null
                    || current.status() != LoanContractStatus.READY_FOR_DISBURSEMENT
                    || !readinessSnapshot.blockers().contains(
                            ContractReadinessBlockerCode.READINESS_ALREADY_CONFIRMED
                    )) {
                throw systemConflict();
            }
            return WorkStage.READINESS_CONFIRMED;
        }
        if (application.status() != LoanApplicationStatus.CONTRACT_PENDING) {
            throw systemConflict();
        }
        if (current == null) {
            if (readinessSnapshot.ready()
                    || !readinessSnapshot.blockers().contains(
                            ContractReadinessBlockerCode.CURRENT_CONTRACT_MISSING
                    )) {
                throw systemConflict();
            }
            return WorkStage.NEEDS_PREPARATION;
        }
        return switch (current.status()) {
            case PREPARED -> {
                if (readinessSnapshot.ready()
                        || !readinessSnapshot.blockers().contains(
                                ContractReadinessBlockerCode.ACKNOWLEDGMENT_MISSING
                        )) {
                    throw systemConflict();
                }
                yield WorkStage.CUSTOMER_ACKNOWLEDGMENT_REQUIRED;
            }
            case ACKNOWLEDGED -> {
                if (readinessSnapshot.ready() != readinessSnapshot.blockers().isEmpty()) {
                    throw systemConflict();
                }
                yield readinessSnapshot.ready()
                        ? WorkStage.READY_TO_CONFIRM
                        : WorkStage.READINESS_BLOCKED;
            }
            case READY_FOR_DISBURSEMENT, SUPERSEDED -> throw systemConflict();
        };
    }

    private static void validateReadinessIdentity(
            LoanApplication application,
            LoanContract current,
            QueryContractReadinessUseCase.Snapshot snapshot
    ) {
        if (!application.id().equals(snapshot.loanApplicationId())) {
            throw systemConflict();
        }
        if (current == null) {
            if (snapshot.contractId() != null || snapshot.contractVersion() != null) {
                throw systemConflict();
            }
            return;
        }
        if (!current.id().equals(snapshot.contractId())
                || !Objects.equals(current.contractVersion(), snapshot.contractVersion())) {
            throw systemConflict();
        }
    }

    private static void requireAuthority(AuthenticatedUser actor) {
        if (!"STAFF".equals(actor.userType())
                || actor.optionalCustomerId().isPresent()
                || !actor.hasPermission("loan:contract:read")) {
            throw new AuthorizationException(
                    "LOAN_CONTRACT_WORK_ACCESS_DENIED",
                    "Staff contract work access is denied."
            );
        }
        if (!actor.roles().contains("ACCOUNTING_OFFICER")) {
            throw new AuthorizationException(
                    "ACCOUNTING_ROLE_REQUIRED",
                    "Accounting authority is required for contract work."
            );
        }
    }

    private static void requireValidPage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Contract work page arguments are invalid.");
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
                "Contract work evidence is inconsistent."
        );
    }

    private enum WorkStage {
        NEEDS_PREPARATION,
        CUSTOMER_ACKNOWLEDGMENT_REQUIRED,
        READINESS_BLOCKED,
        READY_TO_CONFIRM,
        READINESS_CONFIRMED
    }

    private record Projection(
            LoanContractDto contract,
            ContractReadinessDto readiness,
            WorkStage stage
    ) {
    }
}
