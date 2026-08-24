package com.meridian.platform.loan.application.service.salaryadvance;

import com.meridian.platform.loan.application.dto.SalaryAdvanceReadinessDto;
import com.meridian.platform.loan.application.port.out.CustomerReadinessPort;
import com.meridian.platform.loan.application.port.out.CustomerReadinessSnapshot;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.LoanProductRepository;
import com.meridian.platform.loan.application.port.out.OutstandingLoanAccountQuery;
import com.meridian.platform.loan.application.port.out.PartnerEligibilityAssessment;
import com.meridian.platform.loan.application.port.out.PartnerEligibilityPort;
import com.meridian.platform.loan.application.port.out.SalaryAdvanceLimitRepository;
import com.meridian.platform.loan.domain.model.LoanProduct;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductType;
import com.meridian.platform.loan.domain.model.salaryadvance.SalaryAdvanceEmployeeVerificationOutcome;
import com.meridian.platform.loan.domain.model.salaryadvance.SalaryAdvanceLimit;
import com.meridian.platform.loan.domain.model.salaryadvance.SalaryAdvanceLimitStatus;
import com.meridian.platform.loan.domain.model.salaryadvance.VerifiedPartnerEmployeeLinkSnapshot;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class QuerySalaryAdvanceReadinessServiceTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final UUID LINK_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final LocalDateTime REFRESHED_AT = LocalDateTime.of(2026, 8, 10, 8, 0);

    @Mock CustomerReadinessPort customers;
    @Mock LoanProductRepository products;
    @Mock PartnerEligibilityPort partners;
    @Mock SalaryAdvanceLimitRepository limits;
    @Mock LoanApplicationRepository applications;
    @Mock OutstandingLoanAccountQuery outstanding;
    @Mock CurrentUserProvider currentUserProvider;

    private QuerySalaryAdvanceReadinessService service;

    @BeforeEach
    void setUp() {
        service = new QuerySalaryAdvanceReadinessService(
                customers,
                products,
                partners,
                limits,
                applications,
                outstanding,
                currentUserProvider
        );
        lenient().when(currentUserProvider.currentUser()).thenReturn(customer(Set.of("loan:submit")));
        lenient().when(customers.findReadinessByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(
                new CustomerReadinessSnapshot(CUSTOMER_ID, true, true, true, "VERIFIED")
        ));
        lenient().when(products.findByProductCode(ProductCode.SALARY_ADVANCE)).thenReturn(Optional.of(product()));
        lenient().when(partners.inspectCurrentEmployeeLink(CUSTOMER_ID)).thenReturn(
                PartnerEligibilityAssessment.eligible(partnerSnapshot())
        );
        lenient().when(applications.existsByCustomerIdAndProductCodeAndStatusIn(
                any(), any(), any()
        )).thenReturn(false);
        lenient().when(outstanding.inspect(CUSTOMER_ID, ProductCode.SALARY_ADVANCE))
                .thenReturn(OutstandingLoanAccountQuery.GuardResult.CLEAR);
    }

    @Test
    void returnsAccurateSafeCurrentReadinessWithoutMutation() {
        SalaryAdvanceLimit stored = new SalaryAdvanceLimit(
                UUID.randomUUID(), CUSTOMER_ID, LINK_ID,
                amount(6_000_000), amount(1_000_000), amount(2_000_000), amount(3_000_000),
                SalaryAdvanceLimitStatus.ACTIVE, REFRESHED_AT
        );
        when(limits.findByCustomerIdAndCustomerPartnerEmployeeLinkId(CUSTOMER_ID, LINK_ID))
                .thenReturn(Optional.of(stored));

        SalaryAdvanceReadinessDto result = service.queryReadiness();

        assertTrue(result.applicationAllowed());
        assertTrue(result.blockerCodes().isEmpty());
        assertEquals(LINK_ID, result.customerPartnerEmployeeLinkId());
        assertEquals("VERIFIED", result.employeeVerificationStatus());
        assertEquals("ELIGIBLE", result.partnerEligibilityStatus());
        assertEquals("ACTIVE", result.limitStatus());
        assertEquals(amount(6_000_000), result.totalAmount());
        assertEquals(amount(1_000_000), result.usedAmount());
        assertEquals(amount(2_000_000), result.reservedAmount());
        assertEquals(amount(3_000_000), result.availableAmount());
        assertEquals(REFRESHED_AT, result.lastRefreshAt());
        verify(limits, never()).save(any());
        verify(limits, never()).acquireCustomerLinkLock(any(), any());
        verify(applications, never()).save(any());
        verify(applications, never()).acquireCustomerProductLock(any(), any());
    }

    @Test
    void stalePartnerEvidenceReturnsSafeBlockedReadiness() {
        when(partners.inspectCurrentEmployeeLink(CUSTOMER_ID)).thenReturn(
                PartnerEligibilityAssessment.ineligible(
                        PartnerEligibilityAssessment.Status.EVIDENCE_STALE
                )
        );

        SalaryAdvanceReadinessDto result = service.queryReadiness();

        assertFalse(result.applicationAllowed());
        assertEquals("EVIDENCE_STALE", result.partnerEligibilityStatus());
        assertTrue(result.blockerCodes().contains("SALARY_ADVANCE_ELIGIBILITY_DATA_STALE"));
        assertNull(result.customerPartnerEmployeeLinkId());
    }

    @Test
    void positiveOutstandingAccountIsRepresentedWithoutChangingExposure() {
        when(outstanding.inspect(CUSTOMER_ID, ProductCode.SALARY_ADVANCE))
                .thenReturn(OutstandingLoanAccountQuery.GuardResult.OUTSTANDING_EXISTS);

        SalaryAdvanceReadinessDto result = service.queryReadiness();

        assertFalse(result.applicationAllowed());
        assertTrue(result.blockerCodes().contains("OUTSTANDING_LOAN_ACCOUNT_EXISTS"));
        verify(limits, never()).save(any());
    }

    @Test
    void rejectsStaffAndCustomersWithoutLoanSubmitPermission() {
        when(currentUserProvider.currentUser()).thenReturn(new AuthenticatedUser(
                UUID.randomUUID(), "staff@meridian.test", "STAFF", null,
                Set.of("LOAN_OFFICER"), Set.of("loan:read")
        ));
        assertEquals(
                "SALARY_ADVANCE_READINESS_ACCESS_DENIED",
                assertThrows(AuthorizationException.class, service::queryReadiness).getErrorCode()
        );

        when(currentUserProvider.currentUser()).thenReturn(customer(Set.of("loan:read:own")));
        assertEquals(
                "SALARY_ADVANCE_READINESS_ACCESS_DENIED",
                assertThrows(AuthorizationException.class, service::queryReadiness).getErrorCode()
        );
    }

    private static AuthenticatedUser customer(Set<String> permissions) {
        return new AuthenticatedUser(
                UUID.randomUUID(), "customer@meridian.test", "CUSTOMER", CUSTOMER_ID,
                Set.of("CUSTOMER"), permissions
        );
    }

    private static LoanProduct product() {
        return new LoanProduct(
                UUID.randomUUID(), ProductCode.SALARY_ADVANCE, ProductType.SALARY_BASED,
                "Salary Advance", null, true, amount(500_000), amount(20_000_000)
        );
    }

    private static VerifiedPartnerEmployeeLinkSnapshot partnerSnapshot() {
        return new VerifiedPartnerEmployeeLinkSnapshot(
                CUSTOMER_ID,
                LINK_ID,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                SalaryAdvanceEmployeeVerificationOutcome.MATCHED_ACTIVE,
                amount(10_000_000),
                amount(18_000_000),
                amount(6_000_000),
                REFRESHED_AT,
                REFRESHED_AT
        );
    }

    private static BigDecimal amount(long value) {
        return BigDecimal.valueOf(value).setScale(2);
    }
}
