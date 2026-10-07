package com.meridian.platform.identity.application.service;

import com.meridian.platform.identity.application.port.out.CustomerDigitalAccessVerificationPort;
import com.meridian.platform.identity.application.port.out.EmailVerificationTokenCodecPort;
import com.meridian.platform.identity.application.port.out.EmailVerificationTokenRepository;
import com.meridian.platform.identity.application.port.out.PasswordHashingPort;
import com.meridian.platform.identity.application.port.out.UserRepository;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomerDigitalAccessTransactionServiceTest {
    @Test
    void onlyStaffWithExactIntakePermissionMayReadStatus() {
        CurrentUserProvider currentUsers = mock(CurrentUserProvider.class);
        var service = new CustomerDigitalAccessTransactionService(mock(UserRepository.class),
                mock(CustomerDigitalAccessVerificationPort.class), mock(PasswordHashingPort.class),
                mock(EmailVerificationTokenCodecPort.class), mock(EmailVerificationTokenRepository.class),
                mock(com.meridian.platform.identity.application.port.out.PasswordResetTokenCodecPort.class),
                mock(com.meridian.platform.identity.application.port.out.PasswordResetTokenRepository.class),
                currentUsers, mock(BusinessAuditPublisher.class), Duration.ofHours(24), Clock.systemUTC());
        UUID id = UUID.randomUUID();
        for (AuthenticatedUser actor : new AuthenticatedUser[] {
                new AuthenticatedUser(id, "customer@example.com", "CUSTOMER", UUID.randomUUID(),
                        Set.of("CUSTOMER"), Set.of("customer:intake:manage")),
                new AuthenticatedUser(id, "staff@example.com", "STAFF", null,
                        Set.of("LOAN_OFFICER"), Set.of("customer:intake:manage:all")),
                new AuthenticatedUser(id, "staff@example.com", "STAFF", null,
                        Set.of("LOAN_OFFICER"), Set.of("customer:read"))
        }) {
            when(currentUsers.currentUser()).thenReturn(actor);
            assertEquals("STAFF_CUSTOMER_INTAKE_ACCESS_DENIED", assertThrows(AuthorizationException.class,
                    () -> service.status(UUID.randomUUID())).getErrorCode());
        }
    }
}
