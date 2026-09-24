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

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
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
    private static final UUID OTHER_COMPANY_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 9, 24, 8, 0);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-24T08:00:00Z"), ZoneOffset.UTC);

    @Mock PartnerEligibilityReviewRepository reviews;
    @Mock CurrentUserProvider currentUserProvider;

    private QueryOwnPartnerEmployeeVerificationService service;

    @BeforeEach
    void setUp() {
        service = new QueryOwnPartnerEmployeeVerificationService(reviews, currentUserProvider, CLOCK);
    }

    @Test
    void authenticatedCustomerDiscoversOwnCurrentStatesSeparatedByPartnerCompany() {
        when(currentUserProvider.currentUser()).thenReturn(customer(CUSTOMER_ID));
        when(reviews.findCurrentLatestByCustomerIdAndEffectiveMonth(CUSTOMER_ID, "2026-09"))
                .thenReturn(List.of(
                        review(COMPANY_ID, PartnerEligibilityReviewStatus.PENDING, null),
                        review(
                                OTHER_COMPANY_ID,
                                PartnerEligibilityReviewStatus.REJECTED,
                                EmployeeVerificationOutcome.MANUAL_REVIEW_REJECTED
                        )
                ));

        var result = service.getCurrentOwnVerifications();

        assertEquals(2, result.size());
        assertEquals(COMPANY_ID, result.get(0).partnerCompanyId());
        assertEquals("PENDING_MANUAL_REVIEW", result.get(0).outcome());
        assertTrue(result.get(0).manualReviewRequired());
        assertEquals(OTHER_COMPANY_ID, result.get(1).partnerCompanyId());
        assertEquals("MANUAL_REVIEW_REJECTED", result.get(1).outcome());
        assertFalse(result.get(1).manualReviewRequired());
        verify(reviews).findCurrentLatestByCustomerIdAndEffectiveMonth(CUSTOMER_ID, "2026-09");
    }

    @Test
    void approvedManualReviewReturnsCustomerSafeTerminalOutcome() {
        when(currentUserProvider.currentUser()).thenReturn(customer(CUSTOMER_ID));
        when(reviews.findCurrentLatestByCustomerIdAndEffectiveMonth(CUSTOMER_ID, "2026-09"))
                .thenReturn(List.of(review(
                        COMPANY_ID,
                        PartnerEligibilityReviewStatus.APPROVED,
                        EmployeeVerificationOutcome.MANUAL_REVIEW_APPROVED
                )));

        var result = service.getCurrentOwnVerifications();

        assertEquals(1, result.size());
        assertEquals("MANUAL_REVIEW_APPROVED", result.getFirst().outcome());
        assertFalse(result.getFirst().manualReviewRequired());
    }

    @Test
    void supersededOrInvalidReviewIsNotSurfacedDefensively() {
        when(currentUserProvider.currentUser()).thenReturn(customer(CUSTOMER_ID));
        when(reviews.findCurrentLatestByCustomerIdAndEffectiveMonth(CUSTOMER_ID, "2026-09"))
                .thenReturn(List.of(
                        review(COMPANY_ID, PartnerEligibilityReviewStatus.SUPERSEDED, null),
                        review(OTHER_COMPANY_ID, PartnerEligibilityReviewStatus.REJECTED, null)
                ));

        assertTrue(service.getCurrentOwnVerifications().isEmpty());
    }

    @Test
    void nonCustomerCannotSupplyOrSubstituteAnotherCustomerIdentity() {
        when(currentUserProvider.currentUser()).thenReturn(new AuthenticatedUser(
                UUID.randomUUID(), "staff@meridian.test", "STAFF", CUSTOMER_ID,
                Set.of("BACK_OFFICE_ADMIN"), Set.of("partner:employee:verify:own")
        ));

        assertThrows(AuthorizationException.class, service::getCurrentOwnVerifications);
        verifyNoInteractions(reviews);
    }

    private static PartnerEligibilityReview review(
            UUID partnerCompanyId,
            PartnerEligibilityReviewStatus status,
            EmployeeVerificationOutcome decisionOutcome
    ) {
        return new PartnerEligibilityReview(
                UUID.nameUUIDFromBytes(partnerCompanyId.toString().getBytes(StandardCharsets.UTF_8)),
                CUSTOMER_ID,
                partnerCompanyId,
                "2026-09",
                null,
                EmployeeVerificationOutcome.NOT_FOUND,
                "GHOST-999",
                status,
                decisionOutcome,
                status == PartnerEligibilityReviewStatus.APPROVED
                        ? PartnerEligibilityReviewReason.CURRENT_EMPLOYEE_CONFIRMED
                        : status == PartnerEligibilityReviewStatus.REJECTED && decisionOutcome != null
                                ? PartnerEligibilityReviewReason.NO_ELIGIBLE_CURRENT_EMPLOYEE
                                : null,
                null,
                null,
                null,
                status == PartnerEligibilityReviewStatus.PENDING
                        || status == PartnerEligibilityReviewStatus.SUPERSEDED
                        ? null
                        : CREATED_AT.plusHours(1),
                CREATED_AT,
                status == PartnerEligibilityReviewStatus.PENDING ? CREATED_AT : CREATED_AT.plusHours(1)
        );
    }

    private static AuthenticatedUser customer(UUID customerId) {
        return new AuthenticatedUser(
                UUID.randomUUID(), "customer@meridian.test", "CUSTOMER", customerId,
                Set.of("CUSTOMER"), Set.of("partner:employee:verify:own")
        );
    }
}
