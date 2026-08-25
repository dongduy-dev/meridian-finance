package com.meridian.platform.identity.application.service;

import com.meridian.platform.identity.application.dto.CustomerRegistrationRequest;
import com.meridian.platform.identity.application.port.out.CustomerRegistrationPort;
import com.meridian.platform.identity.application.port.out.EmailVerificationTokenCodecPort;
import com.meridian.platform.identity.application.port.out.EmailVerificationTokenRepository;
import com.meridian.platform.identity.application.port.out.GeneratedEmailVerificationToken;
import com.meridian.platform.identity.application.port.out.PasswordHashingPort;
import com.meridian.platform.identity.application.port.out.UserRepository;
import com.meridian.platform.identity.domain.model.EmailVerificationToken;
import com.meridian.platform.identity.domain.model.User;
import com.meridian.platform.identity.domain.model.UserStatus;
import com.meridian.platform.identity.domain.model.UserType;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class CustomerRegistrationTransactionService {

    private final UserRepository userRepository;
    private final CustomerRegistrationPort customerRegistrationPort;
    private final PasswordHashingPort passwordHashingPort;
    private final EmailVerificationTokenCodecPort tokenCodec;
    private final EmailVerificationTokenRepository tokenRepository;
    private final Duration tokenLifetime;
    private final Clock clock;

    public CustomerRegistrationTransactionService(
            UserRepository userRepository,
            CustomerRegistrationPort customerRegistrationPort,
            PasswordHashingPort passwordHashingPort,
            EmailVerificationTokenCodecPort tokenCodec,
            EmailVerificationTokenRepository tokenRepository,
            @Value("${meridian.identity.email-verification.lifetime:24h}") Duration tokenLifetime,
            Clock clock
    ) {
        this.userRepository = Objects.requireNonNull(userRepository);
        this.customerRegistrationPort = Objects.requireNonNull(customerRegistrationPort);
        this.passwordHashingPort = Objects.requireNonNull(passwordHashingPort);
        this.tokenCodec = Objects.requireNonNull(tokenCodec);
        this.tokenRepository = Objects.requireNonNull(tokenRepository);
        this.tokenLifetime = requirePositive(tokenLifetime);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional
    public PendingEmailVerificationDelivery register(CustomerRegistrationRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        String normalizedEmail = normalizeEmail(request.email());
        if (userRepository.findByNormalizedEmail(normalizedEmail).isPresent()) {
            throw emailAlreadyRegistered();
        }

        UUID customerId = customerRegistrationPort.registerCustomer();
        UUID userId = UUID.randomUUID();
        User user = new User(
                userId,
                normalizedEmail,
                passwordHashingPort.hash(request.password()),
                UserType.CUSTOMER,
                UserStatus.ACTIVE,
                request.displayName().trim(),
                customerId,
                Set.of("CUSTOMER"),
                Set.of(),
                0,
                null,
                null
        );
        userRepository.createCustomerUser(user);

        Instant now = Instant.now(clock);
        GeneratedEmailVerificationToken generatedToken = tokenCodec.generate();
        tokenRepository.create(new EmailVerificationToken(
                UUID.randomUUID(),
                userId,
                generatedToken.tokenDigest(),
                now,
                now.plus(tokenLifetime),
                null,
                null
        ));
        return new PendingEmailVerificationDelivery(normalizedEmail, generatedToken.tokenValue());
    }

    private static String normalizeEmail(String email) {
        return Objects.requireNonNull(email, "email must not be null")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private static Duration requirePositive(Duration duration) {
        Objects.requireNonNull(duration, "email-verification lifetime must not be null");
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("email-verification lifetime must be positive");
        }
        return duration;
    }

    private static BusinessStateConflictException emailAlreadyRegistered() {
        return new BusinessStateConflictException(
                "EMAIL_ALREADY_REGISTERED",
                "An account with this email already exists."
        );
    }
}
