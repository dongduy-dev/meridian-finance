package com.meridian.platform.identity.application.service;

import com.meridian.platform.identity.application.port.out.PasswordHashingPort;
import com.meridian.platform.identity.application.port.out.PasswordResetTokenCodecPort;
import com.meridian.platform.identity.application.port.out.PasswordResetTokenRepository;
import com.meridian.platform.identity.application.port.out.RefreshTokenSessionRepository;
import com.meridian.platform.identity.application.port.out.UserRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class PasswordResetTransactionServiceTest {

    @Test
    void rejectsZeroAndNegativeTokenLifetimeAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> service(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> service(Duration.ofSeconds(-1)));
    }

    private PasswordResetTransactionService service(Duration lifetime) {
        return new PasswordResetTransactionService(
                mock(UserRepository.class),
                mock(PasswordResetTokenRepository.class),
                mock(PasswordResetTokenCodecPort.class),
                mock(PasswordHashingPort.class),
                mock(RefreshTokenSessionRepository.class),
                lifetime,
                Clock.systemUTC()
        );
    }
}
