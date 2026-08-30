package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.dto.CustomerLoanApplicationSummaryDto;
import com.meridian.platform.loan.application.dto.LoanApplicationStatusDto;
import com.meridian.platform.loan.application.port.in.QueryLoanApplicationUseCase;
import com.meridian.platform.loan.application.port.out.ApprovedOfferRepository;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.LoanContractRepository;
import com.meridian.platform.loan.application.port.out.LoanCorrectionRepository;
import com.meridian.platform.loan.domain.model.ApprovedOfferStatus;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanContractStatus;
import com.meridian.platform.loan.domain.model.LoanCorrectionRequestStatus;
import com.meridian.platform.loan.domain.model.LoanCorrectionTaskStatus;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class QueryLoanApplicationService implements QueryLoanApplicationUseCase {

    private final LoanApplicationRepository applications;
    private final CurrentUserProvider currentUserProvider;
    private final LoanCorrectionRepository corrections;
    private final ApprovedOfferRepository offers;
    private final LoanContractRepository contracts;
    private final Clock clock;

    public QueryLoanApplicationService(
            LoanApplicationRepository applications,
            CurrentUserProvider currentUserProvider,
            LoanCorrectionRepository corrections,
            ApprovedOfferRepository offers,
            LoanContractRepository contracts,
            Clock clock
    ) {
        this.applications = applications;
        this.currentUserProvider = currentUserProvider;
        this.corrections = corrections;
        this.offers = offers;
        this.contracts = contracts;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerLoanApplicationSummaryDto> queryOwnApplications() {
        AuthenticatedUser actor = currentUserProvider.currentUser();
        if (actor.optionalCustomerId().isEmpty() || !actor.hasPermission("loan:read:own")) {
            throw new AuthorizationException(
                    "LOAN_APPLICATION_ACCESS_DENIED",
                    "Customer Loan Application access is denied."
            );
        }
        UUID customerId = actor.requireCustomerId();
        LocalDateTime now = LocalDateTime.now(clock);
        return applications.findByCustomerIdOrderBySubmittedAtDesc(customerId).stream()
                .map(application -> toSummary(application, customerId, now))
                .toList();
    }

    private CustomerLoanApplicationSummaryDto toSummary(
            LoanApplication application,
            UUID customerId,
            LocalDateTime now
    ) {
        return new CustomerLoanApplicationSummaryDto(
                application.id(),
                application.applicationNumber(),
                application.productCode().name(),
                application.productType().name(),
                application.requestedAmount(),
                application.requestedTermMonths(),
                application.status().name(),
                application.submittedAt(),
                application.status().isLifecycleActive(),
                requiredAction(application, customerId, now)
        );
    }

    private CustomerLoanApplicationSummaryDto.CustomerApplicationAction requiredAction(
            LoanApplication application,
            UUID customerId,
            LocalDateTime now
    ) {
        return switch (application.status()) {
            case DOCUMENTS_PENDING ->
                    CustomerLoanApplicationSummaryDto.CustomerApplicationAction.UPLOAD_DOCUMENTS;
            case RETURNED_FOR_REVISION -> correctionAction(application.id(), customerId);
            case CUSTOMER_ACCEPTANCE_PENDING -> offers.findByLoanApplicationId(application.id())
                    .filter(offer -> offer.effectiveStatusAt(now) == ApprovedOfferStatus.PENDING)
                    .map(ignored -> CustomerLoanApplicationSummaryDto.CustomerApplicationAction
                            .REVIEW_APPROVED_OFFER)
                    .orElse(CustomerLoanApplicationSummaryDto.CustomerApplicationAction.NONE);
            case CONTRACT_PENDING -> contracts.findCurrentByApplicationId(application.id())
                    .filter(contract -> contract.status() == LoanContractStatus.PREPARED)
                    .map(ignored -> CustomerLoanApplicationSummaryDto.CustomerApplicationAction
                            .ACKNOWLEDGE_CONTRACT)
                    .orElse(CustomerLoanApplicationSummaryDto.CustomerApplicationAction.NONE);
            default -> CustomerLoanApplicationSummaryDto.CustomerApplicationAction.NONE;
        };
    }

    private CustomerLoanApplicationSummaryDto.CustomerApplicationAction correctionAction(
            UUID applicationId,
            UUID customerId
    ) {
        var latestRequest = corrections.findLatestRequestByApplicationId(applicationId);
        if (latestRequest.isEmpty() || !latestRequest.orElseThrow().isActive()) {
            return CustomerLoanApplicationSummaryDto.CustomerApplicationAction.NONE;
        }
        if (latestRequest.orElseThrow().status() == LoanCorrectionRequestStatus.READY_FOR_RESUBMISSION
                || corrections.findCustomerTasks(applicationId, customerId).stream()
                .anyMatch(task -> task.status() == LoanCorrectionTaskStatus.OPEN)) {
            return CustomerLoanApplicationSummaryDto.CustomerApplicationAction.COMPLETE_CORRECTIONS;
        }
        return CustomerLoanApplicationSummaryDto.CustomerApplicationAction.NONE;
    }

    @Override
    @Transactional(readOnly = true)
    public LoanApplicationStatusDto query(UUID loanApplicationId) {
        Objects.requireNonNull(loanApplicationId, "loanApplicationId must not be null");
        AuthenticatedUser actor = currentUserProvider.currentUser();
        requireReadAuthority(actor);
        LoanApplication application = applications.findById(loanApplicationId)
                .orElseThrow(QueryLoanApplicationService::notFound);
        if (actor.optionalCustomerId().isPresent()
                && !application.customerId().equals(actor.requireCustomerId())) {
            throw notFound();
        }
        return new LoanApplicationStatusDto(
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

    private static void requireReadAuthority(AuthenticatedUser actor) {
        boolean allowed = actor.optionalCustomerId().isPresent()
                ? actor.hasPermission("loan:read:own")
                : actor.hasPermission("loan:read");
        if (!allowed) {
            throw new AuthorizationException(
                    "LOAN_APPLICATION_ACCESS_DENIED",
                    "Loan Application access is denied."
            );
        }
    }

    private static EntityNotFoundException notFound() {
        return new EntityNotFoundException(
                "LOAN_APPLICATION_NOT_FOUND",
                "Loan Application was not found."
        );
    }
}
