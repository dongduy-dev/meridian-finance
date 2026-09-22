package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.port.out.*;
import com.meridian.platform.loan.domain.model.*;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssistedActionEvidenceAuthorizationServiceTest {

    @Mock LoanApplicationRepository applications;
    @Mock ApprovedOfferRepository offers;
    @Mock LoanContractRepository contracts;
    @Mock StaffAssistedOfferResponseRepository offerResponses;
    @Mock StaffAssistedContractAcknowledgmentRepository acknowledgments;
    @Mock CurrentUserProvider users;
    private AssistedActionEvidenceAuthorizationService service;
    private final UUID applicationId = UUID.randomUUID();
    private final UUID offerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AssistedActionEvidenceAuthorizationService(
                applications, offers, contracts, offerResponses, acknowledgments, users,
                Clock.fixed(Instant.parse("2026-09-22T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void authorizesExactPendingAssistedUclOfferForLoanOfficer() {
        LoanApplication application = application(OriginationChannel.STAFF_ASSISTED,
                LoanApplicationStatus.CUSTOMER_ACCEPTANCE_PENDING);
        ApprovedOffer offer = mock(ApprovedOffer.class);
        when(users.currentUser()).thenReturn(staff("LOAN_OFFICER", "loan:offer:respond:staff"));
        when(applications.findByIdForUpdate(applicationId)).thenReturn(Optional.of(application));
        when(offers.findByLoanApplicationIdForUpdate(applicationId)).thenReturn(Optional.of(offer));
        when(offer.id()).thenReturn(offerId);
        when(offer.status()).thenReturn(ApprovedOfferStatus.PENDING);
        when(offer.isExpiredAt(any())).thenReturn(false);
        when(offerResponses.findByApprovedOfferId(offerId)).thenReturn(Optional.empty());

        service.authorizeOfferEvidence(applicationId, offerId, "ACCEPT");

        verify(applications).acquireWorkflowLock(applicationId);
    }

    @Test
    void rejectsDigitalApplicationBeforeTargetRead() {
        when(users.currentUser()).thenReturn(staff("LOAN_OFFICER", "loan:offer:respond:staff"));
        when(applications.findByIdForUpdate(applicationId)).thenReturn(Optional.of(
                application(OriginationChannel.CUSTOMER_DIGITAL,
                        LoanApplicationStatus.CUSTOMER_ACCEPTANCE_PENDING)));

        BusinessStateConflictException error = assertThrows(BusinessStateConflictException.class,
                () -> service.authorizeOfferEvidence(applicationId, offerId, "ACCEPT"));

        assertEquals("ASSISTED_ACTION_NOT_ALLOWED", error.getErrorCode());
        verifyNoInteractions(offers);
    }

    @Test
    void rejectsPermissionWithoutRequiredBusinessRole() {
        when(users.currentUser()).thenReturn(staff("APPROVER", "loan:offer:respond:staff"));

        AuthorizationException error = assertThrows(AuthorizationException.class,
                () -> service.authorizeOfferEvidence(applicationId, offerId, "DECLINE"));

        assertEquals("ASSISTED_ACTION_ROLE_REQUIRED", error.getErrorCode());
        verifyNoInteractions(applications, offers);
    }

    private LoanApplication application(OriginationChannel channel, LoanApplicationStatus status) {
        return new LoanApplication(applicationId, UUID.randomUUID(), UUID.randomUUID(), "UCL-1",
                ProductCode.UNSECURED_CONSUMER_LOAN, ProductType.UNSECURED, channel, status,
                BigDecimal.valueOf(5_000_000), 6, LocalDateTime.of(2026, 9, 20, 0, 0));
    }

    private static AuthenticatedUser staff(String role, String permission) {
        return new AuthenticatedUser(UUID.randomUUID(), "staff@meridian.test", "STAFF", null,
                Set.of(role), Set.of(permission));
    }
}
