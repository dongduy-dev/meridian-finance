package com.meridian.platform.customer.application.service;

import com.meridian.platform.customer.application.dto.CustomerIdentityContent;
import com.meridian.platform.customer.application.port.out.CustomerIdentityEvidencePort;
import com.meridian.platform.customer.application.port.out.CustomerIdentityVerificationRepository;
import com.meridian.platform.customer.application.port.out.CustomerRepository;
import com.meridian.platform.customer.application.port.out.CustomerSensitiveValueProtector;
import com.meridian.platform.customer.domain.model.CustomerIdentityVerification;
import com.meridian.platform.customer.domain.model.CustomerIdentityVerification.Source;
import com.meridian.platform.customer.domain.model.CustomerIdentityVerification.Status;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.InputStream;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerIdentityVerificationServiceTest {
    private final UUID customerId = UUID.randomUUID();
    private final UUID verificationId = UUID.randomUUID();
    private final UUID versionId = UUID.randomUUID();
    private final CustomerRepository customers = mock(CustomerRepository.class);
    private final CustomerIdentityVerificationRepository verifications = mock(CustomerIdentityVerificationRepository.class);
    private final CustomerIdentityEvidencePort evidence = mock(CustomerIdentityEvidencePort.class);
    private final CurrentUserProvider users = mock(CurrentUserProvider.class);
    private final CustomerIdentityVerificationService service = new CustomerIdentityVerificationService(
            customers, verifications, evidence, mock(CustomerSensitiveValueProtector.class), users,
            mock(BusinessAuditPublisher.class), Clock.systemUTC());

    @ParameterizedTest
    @EnumSource(Source.class)
    void bothReadersMapExactEvidenceAndLeaveTheStreamUntouched(Source source) {
        var verification = verification(source);
        when(verifications.findById(verificationId)).thenReturn(Optional.of(verification));
        var stream = mock(InputStream.class);
        when(evidence.read(customerId, verification.caseId(), versionId))
                .thenReturn(new CustomerIdentityEvidencePort.Content("hồ sơ.pdf", "application/pdf", 1234L, stream));

        for (boolean staff : List.of(false, true)) {
            authorize(staff);
            CustomerIdentityContent result = read(staff);
            assertEquals("hồ sơ.pdf", result.filename());
            assertEquals("application/pdf", result.mimeType());
            assertEquals(1234L, result.byteSize());
            assertSame(stream, result.content());
            verifyNoInteractions(stream);
        }
        var order = inOrder(users, verifications, evidence);
        for (int i = 0; i < 2; i++) {
            order.verify(users).currentUser();
            order.verify(verifications).findById(verificationId);
            order.verify(evidence).read(customerId, verification.caseId(), versionId);
        }
        verifyNoMoreInteractions(evidence);
        verifyNoInteractions(customers);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void evidenceAndLookupFailuresPropagateUnchanged(boolean staff) {
        authorize(staff);
        var verification = verification(Source.CUSTOMER_DIGITAL);
        when(verifications.findById(verificationId)).thenReturn(Optional.of(verification));
        var evidenceFailure = new IllegalStateException("Evidence unavailable");
        when(evidence.read(customerId, null, versionId)).thenThrow(evidenceFailure);
        assertSame(evidenceFailure, assertThrows(IllegalStateException.class, () -> read(staff)));

        clearInvocations(evidence);
        var lookupFailure = new IllegalStateException("Verification unavailable");
        when(verifications.findById(verificationId)).thenThrow(lookupFailure);
        assertSame(lookupFailure, assertThrows(IllegalStateException.class, () -> read(staff)));
        verifyNoInteractions(evidence);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void missingVerificationIsConcealedBeforeReadingEvidence(boolean staff) {
        authorize(staff);
        when(verifications.findById(verificationId)).thenReturn(Optional.empty());
        assertEquals("IDENTITY_VERIFICATION_NOT_FOUND",
                assertThrows(EntityNotFoundException.class, () -> read(staff)).getErrorCode());
        verifyNoInteractions(evidence);
    }

    @Test
    void foreignCustomerGetsTheSameMissingErrorWithoutReadingEvidence() {
        authorize(false);
        when(users.currentUser()).thenReturn(actor("CUSTOMER", UUID.randomUUID(), "customer:identity:read:own"));
        when(verifications.findById(verificationId)).thenReturn(Optional.of(verification(Source.CUSTOMER_DIGITAL)));
        assertEquals("IDENTITY_VERIFICATION_NOT_FOUND",
                assertThrows(EntityNotFoundException.class, () -> service.readOwn(verificationId)).getErrorCode());
        verifyNoInteractions(evidence);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void wrongActorShapeOrPermissionIsDeniedBeforeLookupOrEvidence(boolean staff) {
        String permission = staff ? "customer:identity:verify" : "customer:identity:read:own";
        var invalidActors = staff
                ? List.of(actor("STAFF", null, "customer:read"), actor("STAFF", customerId, permission),
                        actor("CUSTOMER", customerId, permission))
                : List.of(actor("CUSTOMER", customerId, "customer:read:own"), actor("CUSTOMER", null, permission),
                        actor("STAFF", null, permission), actor("STAFF", customerId, permission));
        for (var actor : invalidActors) {
            when(users.currentUser()).thenReturn(actor);
            assertEquals("IDENTITY_VERIFICATION_ACCESS_DENIED",
                    assertThrows(AuthorizationException.class, () -> read(staff)).getErrorCode());
        }
        verifyNoInteractions(verifications, evidence, customers);
    }

    private CustomerIdentityContent read(boolean staff) {
        return staff ? service.readStaff(verificationId) : service.readOwn(verificationId);
    }

    private void authorize(boolean staff) {
        when(users.currentUser()).thenReturn(staff
                ? actor("STAFF", null, "customer:identity:verify")
                : actor("CUSTOMER", customerId, "customer:identity:read:own"));
    }

    private AuthenticatedUser actor(String type, UUID owner, String permission) {
        return new AuthenticatedUser(UUID.randomUUID(), "fictional@meridian.test", type, owner, Set.of(), Set.of(permission));
    }

    private CustomerIdentityVerification verification(Source source) {
        return new CustomerIdentityVerification(verificationId, customerId, 1, source,
                source == Source.STAFF_ASSISTED_INTAKE ? UUID.randomUUID() : null, versionId, "Fictional Customer",
                Status.PENDING_REVIEW, null, UUID.randomUUID(), LocalDateTime.of(2026, 10, 27, 0, 0), null, null, null);
    }
}
