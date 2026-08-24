package com.meridian.platform.loan.application.service.salaryadvance;

import com.meridian.platform.loan.application.dto.SalaryAdvanceReadinessDto;
import com.meridian.platform.loan.application.port.in.QuerySalaryAdvanceReadinessUseCase;
import com.meridian.platform.loan.application.port.out.CustomerReadinessPort;
import com.meridian.platform.loan.application.port.out.CustomerReadinessSnapshot;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.LoanProductRepository;
import com.meridian.platform.loan.application.port.out.OutstandingLoanAccountQuery;
import com.meridian.platform.loan.application.port.out.PartnerEligibilityAssessment;
import com.meridian.platform.loan.application.port.out.PartnerEligibilityPort;
import com.meridian.platform.loan.application.port.out.SalaryAdvanceLimitRepository;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.LoanProduct;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.salaryadvance.SalaryAdvanceLimit;
import com.meridian.platform.loan.domain.model.salaryadvance.SalaryAdvanceLimitStatus;
import com.meridian.platform.loan.domain.model.salaryadvance.VerifiedPartnerEmployeeLinkSnapshot;
import com.meridian.platform.loan.domain.service.salaryadvance.SalaryAdvanceApplicationPolicy;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class QuerySalaryAdvanceReadinessService implements QuerySalaryAdvanceReadinessUseCase {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final CustomerReadinessPort customerReadiness;
    private final LoanProductRepository products;
    private final PartnerEligibilityPort partnerEligibility;
    private final SalaryAdvanceLimitRepository limits;
    private final LoanApplicationRepository applications;
    private final OutstandingLoanAccountQuery outstandingLoanAccounts;
    private final CurrentUserProvider currentUserProvider;
    private final SalaryAdvanceApplicationPolicy applicationPolicy = new SalaryAdvanceApplicationPolicy();

    public QuerySalaryAdvanceReadinessService(
            CustomerReadinessPort customerReadiness,
            LoanProductRepository products,
            PartnerEligibilityPort partnerEligibility,
            SalaryAdvanceLimitRepository limits,
            LoanApplicationRepository applications,
            OutstandingLoanAccountQuery outstandingLoanAccounts,
            CurrentUserProvider currentUserProvider
    ) {
        this.customerReadiness = customerReadiness;
        this.products = products;
        this.partnerEligibility = partnerEligibility;
        this.limits = limits;
        this.applications = applications;
        this.outstandingLoanAccounts = outstandingLoanAccounts;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional(readOnly = true)
    public SalaryAdvanceReadinessDto queryReadiness() {
        AuthenticatedUser actor = currentUserProvider.currentUser();
        if (actor.optionalCustomerId().isEmpty() || !actor.hasPermission("loan:submit")) {
            throw new AuthorizationException(
                    "SALARY_ADVANCE_READINESS_ACCESS_DENIED",
                    "Salary Advance readiness is available only to an authorized Customer."
            );
        }
        UUID customerId = actor.requireCustomerId();
        List<String> blockers = new ArrayList<>();

        CustomerReadinessSnapshot customer = customerReadiness.findReadinessByCustomerId(customerId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "CUSTOMER_NOT_FOUND",
                        "Customer was not found."
                ));
        if (!customer.active()) {
            blockers.add("CUSTOMER_NOT_ACTIVE");
        }
        if (!customer.profileComplete()) {
            blockers.add("PROFILE_INCOMPLETE");
        }
        if (!customer.hasPrimaryActiveBankAccount()) {
            blockers.add("PRIMARY_BANK_ACCOUNT_REQUIRED");
        }

        Optional<LoanProduct> productResult = products.findByProductCode(ProductCode.SALARY_ADVANCE);
        if (productResult.isEmpty() || !productResult.orElseThrow().active()) {
            blockers.add("PRODUCT_NOT_AVAILABLE");
        }

        PartnerEligibilityAssessment partner = partnerEligibility.inspectCurrentEmployeeLink(customerId);
        addPartnerBlocker(partner.status(), blockers);
        Optional<VerifiedPartnerEmployeeLinkSnapshot> partnerSnapshot = partner.optionalSnapshot();

        Optional<SalaryAdvanceLimit> storedLimit = partnerSnapshot
                .flatMap(snapshot -> limits.findByCustomerIdAndCustomerPartnerEmployeeLinkId(
                        customerId,
                        snapshot.customerPartnerEmployeeLinkId()
                ));
        if (partnerSnapshot.isEmpty() && storedLimit.isEmpty()) {
            storedLimit = limits.findLatestByCustomerId(customerId);
        }
        LimitView limitView = limitView(productResult, partnerSnapshot, storedLimit, blockers);

        if (applications.existsByCustomerIdAndProductCodeAndStatusIn(
                customerId,
                ProductCode.SALARY_ADVANCE,
                LoanApplicationStatus.blockingStatuses()
        )) {
            blockers.add("BLOCKING_APPLICATION_EXISTS");
        }

        switch (outstandingLoanAccounts.inspect(customerId, ProductCode.SALARY_ADVANCE)) {
            case OUTSTANDING_EXISTS -> blockers.add("OUTSTANDING_LOAN_ACCOUNT_EXISTS");
            case INCONSISTENT -> blockers.add("SYSTEM_STATE_CONFLICT");
            case CLEAR -> {
            }
        }

        List<String> distinctBlockers = blockers.stream().distinct().toList();
        return new SalaryAdvanceReadinessDto(
                ProductCode.SALARY_ADVANCE.name(),
                partnerSnapshot.map(VerifiedPartnerEmployeeLinkSnapshot::customerPartnerEmployeeLinkId)
                        .orElse(null),
                partner.status() == PartnerEligibilityAssessment.Status.NOT_VERIFIED
                        ? "NOT_VERIFIED" : "VERIFIED",
                partner.status().name(),
                limitView.status(),
                limitView.total(),
                limitView.used(),
                limitView.reserved(),
                limitView.available(),
                limitView.lastRefreshedAt(),
                distinctBlockers.isEmpty(),
                distinctBlockers
        );
    }

    private LimitView limitView(
            Optional<LoanProduct> product,
            Optional<VerifiedPartnerEmployeeLinkSnapshot> partnerSnapshot,
            Optional<SalaryAdvanceLimit> storedLimit,
            List<String> blockers
    ) {
        if (partnerSnapshot.isEmpty() || product.isEmpty() || !product.orElseThrow().active()) {
            if (storedLimit.isEmpty()) {
                blockers.add("SALARY_ADVANCE_LIMIT_UNAVAILABLE");
                return LimitView.unavailable();
            }
            SalaryAdvanceLimit existing = storedLimit.orElseThrow();
            blockers.add("SALARY_ADVANCE_LIMIT_UNAVAILABLE");
            return LimitView.from(existing);
        }

        VerifiedPartnerEmployeeLinkSnapshot eligibility = partnerSnapshot.orElseThrow();
        BigDecimal effectiveTotal = applicationPolicy.calculateEffectiveTotalLimit(
                product.orElseThrow(),
                eligibility
        );
        if (storedLimit.isEmpty()) {
            if (effectiveTotal.compareTo(product.orElseThrow().minAmount()) < 0) {
                blockers.add("INSUFFICIENT_AVAILABLE_LIMIT");
            }
            return new LimitView(
                    "NOT_INITIALIZED",
                    effectiveTotal,
                    ZERO,
                    ZERO,
                    effectiveTotal,
                    eligibility.lastRefreshedAt()
            );
        }

        SalaryAdvanceLimit existing = storedLimit.orElseThrow();
        if (!existing.customerPartnerEmployeeLinkId().equals(
                eligibility.customerPartnerEmployeeLinkId()
        )) {
            blockers.add("SALARY_ADVANCE_LIMIT_UNAVAILABLE");
            return LimitView.from(existing);
        }
        if (existing.status() != SalaryAdvanceLimitStatus.ACTIVE) {
            blockers.add("SALARY_ADVANCE_LIMIT_UNAVAILABLE");
        }
        try {
            SalaryAdvanceLimit projected = existing.refreshTotalLimit(
                    effectiveTotal,
                    eligibility.lastRefreshedAt()
            );
            if (projected.availableAmount().compareTo(product.orElseThrow().minAmount()) < 0) {
                blockers.add("INSUFFICIENT_AVAILABLE_LIMIT");
            }
            return LimitView.from(projected);
        } catch (BusinessRuleViolationException exception) {
            blockers.add(exception.getErrorCode());
            return LimitView.from(existing);
        }
    }

    private static void addPartnerBlocker(
            PartnerEligibilityAssessment.Status status,
            List<String> blockers
    ) {
        switch (status) {
            case ELIGIBLE -> {
            }
            case EVIDENCE_STALE -> blockers.add("SALARY_ADVANCE_ELIGIBILITY_DATA_STALE");
            case NOT_VERIFIED, PARTNER_INACTIVE, EMPLOYEE_INACTIVE -> blockers.add("EMPLOYEE_NOT_VERIFIED");
        }
    }

    private record LimitView(
            String status,
            BigDecimal total,
            BigDecimal used,
            BigDecimal reserved,
            BigDecimal available,
            LocalDateTime lastRefreshedAt
    ) {
        private static LimitView unavailable() {
            return new LimitView("UNAVAILABLE", ZERO, ZERO, ZERO, ZERO, null);
        }

        private static LimitView from(SalaryAdvanceLimit limit) {
            return new LimitView(
                    limit.status().name(),
                    limit.totalLimit(),
                    limit.usedAmount(),
                    limit.reservedAmount(),
                    limit.availableAmount(),
                    limit.lastRefreshedAt()
            );
        }
    }
}
