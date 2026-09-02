package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.dto.StaffLoanApplicationCaseDto;
import com.meridian.platform.loan.application.dto.StaffLoanApplicationPageDto;
import com.meridian.platform.loan.application.port.in.QueryStaffLoanApplicationsUseCase;
import com.meridian.platform.loan.application.port.out.CustomerReadinessPort;
import com.meridian.platform.loan.application.port.out.CustomerReadinessSnapshot;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.LoanApplicationStatusTransitionRepository;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.LoanApplicationStatusTransition;
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
public class QueryStaffLoanApplicationsService implements QueryStaffLoanApplicationsUseCase {

    public static final int MAX_PAGE_SIZE = 100;

    private final LoanApplicationRepository applications;
    private final LoanApplicationStatusTransitionRepository transitions;
    private final CustomerReadinessPort customerReadiness;
    private final CurrentUserProvider currentUserProvider;

    public QueryStaffLoanApplicationsService(
            LoanApplicationRepository applications,
            LoanApplicationStatusTransitionRepository transitions,
            CustomerReadinessPort customerReadiness,
            CurrentUserProvider currentUserProvider
    ) {
        this.applications = applications;
        this.transitions = transitions;
        this.customerReadiness = customerReadiness;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public StaffLoanApplicationPageDto queryApplications(
            ProductCode productCode,
            LoanApplicationStatus status,
            int page,
            int size
    ) {
        requireStaffReadAuthority(currentUserProvider.currentUser());
        requireValidPage(page, size);

        LoanApplicationRepository.StaffPage selected = applications.findStaffPage(
                productCode,
                status,
                page,
                size
        );
        return new StaffLoanApplicationPageDto(
                selected.page(),
                selected.size(),
                selected.totalElements(),
                selected.totalPages(),
                selected.applications().stream().map(this::toItem).toList()
        );
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public StaffLoanApplicationCaseDto queryCase(UUID loanApplicationId) {
        Objects.requireNonNull(loanApplicationId, "loanApplicationId must not be null");
        requireStaffReadAuthority(currentUserProvider.currentUser());

        LoanApplication application = applications.findById(loanApplicationId)
                .orElseThrow(QueryStaffLoanApplicationsService::notFound);
        CustomerReadinessSnapshot readiness = customerReadiness
                .findReadinessByCustomerId(application.customerId())
                .orElseThrow(QueryStaffLoanApplicationsService::readinessUnavailable);

        return new StaffLoanApplicationCaseDto(
                application.id(),
                application.applicationNumber(),
                application.productCode().name(),
                application.productType().name(),
                application.requestedAmount(),
                application.requestedTermMonths(),
                application.status().name(),
                application.submittedAt(),
                new StaffLoanApplicationCaseDto.CustomerReadinessDto(
                        readiness.active(),
                        readiness.profileComplete(),
                        readiness.hasPrimaryActiveBankAccount(),
                        readiness.verificationStatus()
                ),
                transitions.findByLoanApplicationIdOrderBySequenceNumberAsc(application.id())
                        .stream()
                        .map(QueryStaffLoanApplicationsService::toLifecycleItem)
                        .toList()
        );
    }

    private StaffLoanApplicationPageDto.ItemDto toItem(LoanApplication application) {
        return new StaffLoanApplicationPageDto.ItemDto(
                application.id(),
                application.applicationNumber(),
                application.productCode().name(),
                application.productType().name(),
                application.requestedAmount(),
                application.requestedTermMonths(),
                application.status().name(),
                application.submittedAt()
        );
    }

    private static StaffLoanApplicationCaseDto.LifecycleItemDto toLifecycleItem(
            LoanApplicationStatusTransition transition
    ) {
        return new StaffLoanApplicationCaseDto.LifecycleItemDto(
                transition.fromStatus() == null ? null : transition.fromStatus().name(),
                transition.toStatus().name(),
                transition.action().name(),
                transition.actorType().name(),
                transition.occurredAt()
        );
    }

    private static void requireStaffReadAuthority(AuthenticatedUser actor) {
        if (!"STAFF".equals(actor.userType())
                || actor.optionalCustomerId().isPresent()
                || !actor.hasPermission("loan:read")) {
            throw new AuthorizationException(
                    "LOAN_APPLICATION_ACCESS_DENIED",
                    "Staff Loan Application access is denied."
            );
        }
    }

    private static void requireValidPage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Staff application page arguments are invalid.");
        }
    }

    private static EntityNotFoundException notFound() {
        return new EntityNotFoundException(
                "LOAN_APPLICATION_NOT_FOUND",
                "Loan Application was not found."
        );
    }

    private static BusinessStateConflictException readinessUnavailable() {
        return new BusinessStateConflictException(
                "SYSTEM_STATE_CONFLICT",
                "Customer readiness evidence is unavailable."
        );
    }
}
