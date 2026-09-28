package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.dto.StaffLoanApplicationCaseDto;
import com.meridian.platform.loan.application.dto.StaffLoanApplicationPageDto;
import com.meridian.platform.loan.application.dto.CollateralAssessmentSnapshotDto;
import com.meridian.platform.loan.application.port.in.QueryStaffLoanApplicationsUseCase;
import com.meridian.platform.loan.application.port.out.CustomerReadinessPort;
import com.meridian.platform.loan.application.port.out.CustomerReadinessSnapshot;
import com.meridian.platform.loan.application.port.out.CustomerLoanCaseContactPort;
import com.meridian.platform.loan.application.port.out.CustomerLoanCaseContactSnapshot;
import com.meridian.platform.loan.application.port.out.CollateralRepository;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.LoanApplicationStatusTransitionRepository;
import com.meridian.platform.loan.application.port.out.LoanReviewCycleRepository;
import com.meridian.platform.loan.application.port.out.StaffActorDirectoryPort;
import com.meridian.platform.loan.application.port.out.StaffActorSummary;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.LoanApplicationStatusTransition;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.collateral.Collateral;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class QueryStaffLoanApplicationsService implements QueryStaffLoanApplicationsUseCase {

    public static final int MAX_PAGE_SIZE = 100;

    private final LoanApplicationRepository applications;
    private final LoanApplicationStatusTransitionRepository transitions;
    private final CustomerReadinessPort customerReadiness;
    private final CustomerLoanCaseContactPort customerContacts;
    private final CollateralRepository collaterals;
    private final LoanReviewCycleRepository reviewCycles;
    private final StaffActorDirectoryPort staffActors;
    private final CurrentUserProvider currentUserProvider;

    public QueryStaffLoanApplicationsService(
            LoanApplicationRepository applications,
            LoanApplicationStatusTransitionRepository transitions,
            CustomerReadinessPort customerReadiness,
            CustomerLoanCaseContactPort customerContacts,
            CollateralRepository collaterals,
            LoanReviewCycleRepository reviewCycles,
            StaffActorDirectoryPort staffActors,
            CurrentUserProvider currentUserProvider
    ) {
        this.applications = applications;
        this.transitions = transitions;
        this.customerReadiness = customerReadiness;
        this.customerContacts = customerContacts;
        this.collaterals = collaterals;
        this.reviewCycles = reviewCycles;
        this.staffActors = staffActors;
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
        AuthenticatedUser actor = currentUserProvider.currentUser();
        requireStaffReadAuthority(actor);

        LoanApplication application = applications.findById(loanApplicationId)
                .orElseThrow(QueryStaffLoanApplicationsService::notFound);
        CustomerReadinessSnapshot readiness = customerReadiness
                .findReadinessByCustomerId(application.customerId())
                .orElseThrow(QueryStaffLoanApplicationsService::readinessUnavailable);
        StaffLoanApplicationCaseDto.CustomerContextDto customerContext = null;
        if (actor.hasPermission("customer:read")) {
            CustomerLoanCaseContactSnapshot contact = customerContacts.findByCustomerId(application.customerId())
                    .orElseThrow(QueryStaffLoanApplicationsService::contactUnavailable);
            customerContext = new StaffLoanApplicationCaseDto.CustomerContextDto(
                    contact.customerNumber(), contact.fullName(), contact.phoneNumber()
            );
        }
        CollateralAssessmentSnapshotDto collateralContext = collateralContext(application);
        List<LoanApplicationStatusTransition> history = transitions
                .findByLoanApplicationIdOrderBySequenceNumberAsc(application.id());
        var latestReviewCycle = reviewCycles.findLatestByLoanApplicationId(application.id()).orElse(null);
        Set<UUID> actorIds = new LinkedHashSet<>();
        history.stream().map(LoanApplicationStatusTransition::actorUserId)
                .filter(Objects::nonNull).forEach(actorIds::add);
        if (latestReviewCycle != null && latestReviewCycle.assignedLoanOfficerUserId() != null) {
            actorIds.add(latestReviewCycle.assignedLoanOfficerUserId());
        }
        Map<UUID, StaffActorSummary> actorSummaries = staffActors.findByUserIds(actorIds);

        return new StaffLoanApplicationCaseDto(
                application.id(),
                application.applicationNumber(),
                application.productCode().name(),
                application.productType().name(),
                application.originationChannel().name(),
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
                customerContext,
                collateralContext,
                latestReviewCycle != null,
                latestReviewCycle == null ? null : toActor(
                        actorSummaries.get(latestReviewCycle.assignedLoanOfficerUserId())
                ),
                history.stream()
                        .map(transition -> toLifecycleItem(transition, actorSummaries))
                        .toList()
        );
    }

    private CollateralAssessmentSnapshotDto collateralContext(LoanApplication application) {
        if (application.productCode() != ProductCode.COLLATERAL_LOAN) {
            return null;
        }
        List<Collateral> facts = collaterals.findByLoanApplicationId(application.id());
        if (facts.size() != 1 || !application.id().equals(facts.getFirst().loanApplicationId())) {
            throw collateralUnavailable();
        }
        Collateral collateral = facts.getFirst();
        return new CollateralAssessmentSnapshotDto(
                collateral.collateralType().name(), collateral.description(), collateral.estimatedValue(),
                collateral.ownershipStatus(), collateral.conditionNote()
        );
    }

    private StaffLoanApplicationPageDto.ItemDto toItem(LoanApplication application) {
        return new StaffLoanApplicationPageDto.ItemDto(
                application.id(),
                application.applicationNumber(),
                application.productCode().name(),
                application.productType().name(),
                application.originationChannel().name(),
                application.requestedAmount(),
                application.requestedTermMonths(),
                application.status().name(),
                application.submittedAt()
        );
    }

    private static StaffLoanApplicationCaseDto.LifecycleItemDto toLifecycleItem(
            LoanApplicationStatusTransition transition,
            Map<UUID, StaffActorSummary> actorSummaries
    ) {
        return new StaffLoanApplicationCaseDto.LifecycleItemDto(
                transition.fromStatus() == null ? null : transition.fromStatus().name(),
                transition.toStatus().name(),
                transition.action().name(),
                transition.actorType().name(),
                transition.actorUserId() == null ? null : toActor(actorSummaries.get(transition.actorUserId())),
                transition.occurredAt()
        );
    }

    private static StaffLoanApplicationCaseDto.StaffActorDto toActor(StaffActorSummary actor) {
        return actor == null ? null : new StaffLoanApplicationCaseDto.StaffActorDto(
                actor.userId(), actor.displayName(), actor.email()
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

    private static BusinessStateConflictException contactUnavailable() {
        return new BusinessStateConflictException(
                "SYSTEM_STATE_CONFLICT", "Customer contact context is unavailable."
        );
    }

    private static BusinessStateConflictException collateralUnavailable() {
        return new BusinessStateConflictException(
                "SYSTEM_STATE_CONFLICT", "Authoritative collateral facts are inconsistent."
        );
    }
}
