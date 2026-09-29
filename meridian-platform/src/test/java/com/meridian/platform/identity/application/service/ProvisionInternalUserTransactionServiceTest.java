package com.meridian.platform.identity.application.service;

import com.meridian.platform.identity.application.dto.CreateInternalUserRequest;
import com.meridian.platform.identity.application.mapper.InternalUserMapper;
import com.meridian.platform.identity.application.port.out.AssignableInternalRole;
import com.meridian.platform.identity.application.port.out.GeneratedPasswordResetToken;
import com.meridian.platform.identity.application.port.out.InternalUserAdministrationRepository;
import com.meridian.platform.identity.application.port.out.PasswordHashingPort;
import com.meridian.platform.identity.application.port.out.PasswordResetTokenCodecPort;
import com.meridian.platform.identity.application.port.out.PasswordResetTokenRepository;
import com.meridian.platform.identity.application.port.out.UserRepository;
import com.meridian.platform.identity.domain.model.User;
import com.meridian.platform.shared.application.audit.BusinessAuditEvent;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.audit.BusinessAuditAction;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProvisionInternalUserTransactionServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final InternalUserAdministrationRepository administration = mock(InternalUserAdministrationRepository.class);
    private final PasswordHashingPort hashing = mock(PasswordHashingPort.class);
    private final PasswordResetTokenCodecPort codec = mock(PasswordResetTokenCodecPort.class);
    private final PasswordResetTokenRepository tokens = mock(PasswordResetTokenRepository.class);
    private final CurrentUserProvider actor = mock(CurrentUserProvider.class);
    private final BusinessAuditPublisher audit = mock(BusinessAuditPublisher.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-29T00:00:00Z"), ZoneOffset.UTC);
    private ProvisionInternalUserTransactionService service;

    @BeforeEach
    void setUp() {
        service = new ProvisionInternalUserTransactionService(users, administration, hashing, codec,
                tokens, new InternalUserMapper(), actor, audit, Duration.ofMinutes(30), clock);
        when(administration.findAssignableRole("APPROVER"))
                .thenReturn(Optional.of(new AssignableInternalRole("APPROVER", "Approver")));
        when(administration.findAssignableRole("LOAN_OFFICER"))
                .thenReturn(Optional.of(new AssignableInternalRole("LOAN_OFFICER", "Loan Officer")));
        when(hashing.hash(anyString())).thenReturn("stored-placeholder-hash");
        when(codec.generate()).thenReturn(
                new GeneratedPasswordResetToken("placeholder-secret", "placeholder-digest"),
                new GeneratedPasswordResetToken("setup-secret", "setup-digest"));
        when(actor.currentUser()).thenReturn(new AuthenticatedUser(UUID.randomUUID(), "admin@meridian.local",
                "STAFF", null, Set.of("BACK_OFFICE_ADMIN"), Set.of("identity:user:manage")));
    }

    @Test
    void createsOnlyStaffWithNormalizedEmailAndSeparateSetupToken() {
        var delivery = service.create(new CreateInternalUserRequest(
                "  NEW@MERIDIAN.LOCAL  ", "  New Staff  ", List.of("loan_officer", "approver")));
        var user = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(users).createStaffUser(user.capture());
        assertEquals("new@meridian.local", user.getValue().email());
        assertEquals("New Staff", user.getValue().displayName());
        assertEquals(null, user.getValue().customerId());
        assertEquals(0, user.getValue().authorizationVersion());
        assertEquals(Instant.parse("2026-09-29T00:00:00Z"), user.getValue().emailVerifiedAt());
        assertEquals("stored-placeholder-hash", user.getValue().passwordHash());
        assertNotEquals("placeholder-secret", delivery.rawToken());
        assertEquals("setup-secret", delivery.rawToken());
        assertEquals(List.of("APPROVER", "LOAN_OFFICER"), delivery.user().assignedRoleCodes());
        verify(administration).assignRole(user.getValue().id(), "APPROVER");
        verify(administration).assignRole(user.getValue().id(), "LOAN_OFFICER");
        var event = org.mockito.ArgumentCaptor.forClass(BusinessAuditEvent.class);
        verify(audit).publish(event.capture());
        assertEquals(List.of(BusinessAuditAction.IDENTITY_USER_CREATED,
                        BusinessAuditAction.IDENTITY_USER_ROLE_ASSIGNED,
                        BusinessAuditAction.IDENTITY_USER_ROLE_ASSIGNED),
                event.getValue().entries().stream().map(entry -> entry.action()).toList());
        assertTrue(event.getValue().entries().getFirst().payload().values().isEmpty());
        assertTrue(event.getValue().entries().stream().allMatch(entry ->
                entry.entityId().equals(user.getValue().id())));
    }

    @Test
    void rejectsUnknownOrCustomerRoleBeforeCreatingAUser() {
        assertThrows(EntityNotFoundException.class, () -> service.create(new CreateInternalUserRequest(
                "new@meridian.local", "New Staff", List.of("CUSTOMER"))));
        verify(users, never()).createStaffUser(any());
        verify(tokens, never()).create(any());
    }

    @Test
    void deliveryFailureAfterTransactionDoesNotTurnCreationIntoFailure() {
        var transaction = mock(ProvisionInternalUserTransactionService.class);
        var notification = mock(com.meridian.platform.identity.application.port.out.StaffPasswordSetupNotificationPort.class);
        var dto = new com.meridian.platform.identity.application.dto.InternalUserDto(
                UUID.randomUUID(), "staff@meridian.local", "Staff", "ACTIVE", List.of("APPROVER"));
        var request = new CreateInternalUserRequest("staff@meridian.local", "Staff", List.of("APPROVER"));
        when(transaction.create(request)).thenReturn(new PendingStaffSetupDelivery(dto, dto.email(), "secret"));
        org.mockito.Mockito.doThrow(new IllegalStateException("mail unavailable"))
                .when(notification).sendSetupEmail(dto.email(), "secret");
        assertEquals(dto, new ProvisionInternalUserService(transaction, notification).create(request));
    }
}
