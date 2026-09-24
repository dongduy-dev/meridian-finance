package com.meridian.platform.partner.application.service;

import com.meridian.platform.partner.application.port.out.PartnerEligibilityReviewRepository;
import com.meridian.platform.partner.domain.model.EmployeeVerificationOutcome;
import com.meridian.platform.partner.domain.model.PartnerEligibilityReview;
import com.meridian.platform.partner.domain.model.PartnerEligibilityReviewReason;
import com.meridian.platform.partner.domain.model.PartnerEligibilityReviewStatus;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueryOwnPartnerEmployeeVerificationServiceTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final UUID COMPANY_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID REVIEW_ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 9, 24, 8, 0);

    @Mock PartnerEligibilityReviewRepository reviews;
    @Mock CurrentUserProvider currentUserProvider;

    private QueryOwnPartnerEmployeeVerificationService service;

    @BeforeEach
    void setUp() {
        service = new QueryOwnPartnerEmployeeVerificationService(reviews, currentUserProvider);
    }

    @Test
    void authenticatedCustomerReadsOnlyOwnPendingStateForTheSelectedCompany() {
        when(currentUserProvider.currentUser()).thenReturn(customer(CUSTOMER_ID));
        when(reviews.findLatestByCustomerIdAndPartnerCompanyId(CUSTOMER_ID, COMPANY_ID))
                .thenReturn(Optional.of(review(PartnerEligibilityReviewStatus.PENDING, null)));

        var result = service.getLatestOwnVerification(COMPANY_ID);

        assertEquals(COMPANY_ID, result.partnerCompanyId());
        assertEquals("PENDING_MANUAL_REVIEW", result.outcome());
        assertTrue(result.manualReviewRequired());
        verify(reviews).findLatestByCustomerIdAndPartnerCompanyId(CUSTOMER_ID, COMPANY_ID);
    }

    @Test
    void approvedManualReviewReturnsCustomerSafeTerminalOutcome() {
        when(currentUserProvider.currentUser()).thenReturn(customer(CUSTOMER_ID));
        when(reviews.findLatestByCustomerIdAndPartnerCompanyId(CUSTOMER_ID, COMPANY_ID))
                .thenReturn(Optional.of(review(
                        PartnerEligibilityReviewStatus.APPROVED,
                        EmployeeVerificationOutcome.MANUAL_REVIEW_APPROVED
                )));

        var result = service.getLatestOwnVerification(COMPANY_ID);

        assertEquals("MANUAL_REVIEW_APPROVED", result.outcome());
        assertFalse(result.manualReviewRequired());
    }

    @Test
    void rejectedManualReviewReturnsCustomerSafeTerminalOutcome() {
        when(currentUserProvider.currentUser()).thenReturn(customer(CUSTOMER_ID));
        when(reviews.findLatestByCustomerIdAndPartnerCompanyId(CUSTOMER_ID, COMPANY_ID))
                .thenReturn(Optional.of(review(
                        PartnerEligibilityReviewStatus.REJECTED,
                        EmployeeVerificationOutcome.MANUAL_REVIEW_REJECTED
                )));

        var result = service.getLatestOwnVerification(COMPANY_ID);

        assertEquals("MANUAL_REVIEW_REJECTED", result.outcome());
        assertFalse(result.manualReviewRequired());
    }

    @Test
    void nonCustomerCannotSupplyOrSubstituteAnotherCustomerIdentity() {
        when(currentUserProvider.currentUser()).thenReturn(new AuthenticatedUser(
                UUID.randomUUID(), "staff@meridian.test", "STAFF", null,
                Set.of("BACK_OFFICE_ADMIN"), Set.of("partner:employee:verify:own")
        ));

        assertThrows(AuthorizationException.class, () -> service.getLatestOwnVerification(COMPANY_ID));
        verifyNoInteractions(reviews);
    }

    private static PartnerEligibilityReview review(
            PartnerEligibilityReviewStatus status,
            EmployeeVerificationOutcome decisionOutcome
    ) {
        return new PartnerEligibilityReview(
                REVIEW_ID, CUSTOMER_ID, COMPANY_ID, "2026-09", null,
                EmployeeVerificationOutcome.NOT_FOUND, "GHOST-999", status, decisionOutcome,
                status == PartnerEligibilityReviewStatus.APPROVED
                        ? PartnerEligibilityReviewReason.CURRENT_EMPLOYEE_CONFIRMED
                        : status == PartnerEligibilityReviewStatus.REJECTED
                                ? PartnerEligibilityReviewReason.NO_ELIGIBLE_CURRENT_EMPLOYEE
                                : null,
                null, null, null, status == PartnerEligibilityReviewStatus.PENDING ? null : CREATED_AT.plusHours(1),
                CREATED_AT, status == PartnerEligibilityReviewStatus.PENDING ? CREATED_AT : CREATED_AT.plusHours(1)
        );
    }

    private static AuthenticatedUser customer(UUID customerId) {
        return new AuthenticatedUser(
                UUID.randomUUID(), "customer@meridian.test", "CUSTOMER", customerId,
                Set.of("CUSTOMER"), Set.of("partner:employee:verify:own")
        );
    }
}
