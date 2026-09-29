package com.meridian.platform.identity.application.service;

import com.meridian.platform.identity.application.dto.CustomerDigitalAccessDto;
import com.meridian.platform.identity.application.dto.EnableCustomerDigitalAccessRequest;
import com.meridian.platform.identity.application.port.out.CustomerDigitalAccessVerificationPort;
import com.meridian.platform.identity.application.port.out.EmailVerificationTokenCodecPort;
import com.meridian.platform.identity.application.port.out.EmailVerificationTokenRepository;
import com.meridian.platform.identity.application.port.out.GeneratedEmailVerificationToken;
import com.meridian.platform.identity.application.port.out.PasswordHashingPort;
import com.meridian.platform.identity.application.port.out.UserRepository;
import com.meridian.platform.identity.domain.model.EmailVerificationToken;
import com.meridian.platform.identity.domain.model.User;
import com.meridian.platform.identity.domain.model.UserStatus;
import com.meridian.platform.identity.domain.model.UserType;
import com.meridian.platform.shared.application.audit.BusinessAuditEntry;
import com.meridian.platform.shared.application.audit.BusinessAuditEvent;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.operation.BusinessOperationContext;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.audit.BusinessAuditAction;
import com.meridian.platform.shared.domain.audit.BusinessAuditEntityType;
import com.meridian.platform.shared.domain.audit.BusinessAuditPayload;
import com.meridian.platform.shared.domain.audit.BusinessAuditPayloadKey;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class CustomerDigitalAccessTransactionService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final UserRepository users;
    private final CustomerDigitalAccessVerificationPort customers;
    private final PasswordHashingPort passwords;
    private final EmailVerificationTokenCodecPort tokenCodec;
    private final EmailVerificationTokenRepository tokens;
    private final CurrentUserProvider currentUsers;
    private final BusinessAuditPublisher audit;
    private final Duration tokenLifetime;
    private final Clock clock;

    public CustomerDigitalAccessTransactionService(
            UserRepository users, CustomerDigitalAccessVerificationPort customers, PasswordHashingPort passwords,
            EmailVerificationTokenCodecPort tokenCodec, EmailVerificationTokenRepository tokens,
            CurrentUserProvider currentUsers, BusinessAuditPublisher audit,
            @Value("${meridian.identity.email-verification.lifetime:24h}") Duration tokenLifetime, Clock clock
    ) {
        this.users = Objects.requireNonNull(users);
        this.customers = Objects.requireNonNull(customers);
        this.passwords = Objects.requireNonNull(passwords);
        this.tokenCodec = Objects.requireNonNull(tokenCodec);
        this.tokens = Objects.requireNonNull(tokens);
        this.currentUsers = Objects.requireNonNull(currentUsers);
        this.audit = Objects.requireNonNull(audit);
        if (tokenLifetime == null || tokenLifetime.isZero() || tokenLifetime.isNegative()) {
            throw new IllegalArgumentException("email-verification lifetime must be positive");
        }
        this.tokenLifetime = tokenLifetime;
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional(readOnly = true)
    public CustomerDigitalAccessDto status(UUID customerId) {
        requireStaff();
        customers.requireExistingCustomer(customerId);
        return users.findByCustomerId(customerId).map(CustomerDigitalAccessTransactionService::toStatus)
                .orElseGet(() -> CustomerDigitalAccessDto.absent(customerId));
    }

    @Transactional
    public PendingCustomerDigitalAccessInvitation enable(UUID customerId, EnableCustomerDigitalAccessRequest request) {
        AuthenticatedUser actor = requireStaff();
        Objects.requireNonNull(request, "request must not be null");
        var candidate = customers.verifyForActivation(customerId, request.identityReference());
        String normalizedEmail = Objects.requireNonNull(request.email()).trim().toLowerCase(Locale.ROOT);
        if (users.findByCustomerId(customerId).isPresent()) throw alreadyEnabled();
        if (users.findByNormalizedEmail(normalizedEmail).isPresent()) throw emailRegistered();

        Instant now = Instant.now(clock);
        UUID userId = UUID.randomUUID();
        User user = new User(userId, normalizedEmail, passwords.hash(randomSecret()), UserType.CUSTOMER,
                UserStatus.ACTIVE, candidate.displayName(), candidate.customerId(), Set.of("CUSTOMER"), Set.of(),
                0, 0, null, null);
        if (!users.createLinkedCustomerUser(user)) {
            if (users.findByCustomerId(customerId).isPresent()) throw alreadyEnabled();
            if (users.findByNormalizedEmail(normalizedEmail).isPresent()) throw emailRegistered();
            throw new IllegalStateException("Customer User insert conflict could not be classified.");
        }
        GeneratedEmailVerificationToken token = tokenCodec.generate();
        tokens.create(new EmailVerificationToken(UUID.randomUUID(), userId, token.tokenDigest(), now,
                now.plus(tokenLifetime), null, null));
        audit.publish(BusinessAuditEvent.single(
                BusinessOperationContext.user(UUID.randomUUID(), actor.userId(),
                        LocalDateTime.ofInstant(now, ZoneOffset.UTC)),
                new BusinessAuditEntry(BusinessAuditAction.IDENTITY_CUSTOMER_DIGITAL_ACCESS_ENABLED,
                        BusinessAuditEntityType.IDENTITY_USER, userId,
                        BusinessAuditPayload.builder().put(BusinessAuditPayloadKey.CUSTOMER_ID, customerId).build())));
        return new PendingCustomerDigitalAccessInvitation(toStatus(user), normalizedEmail, token.tokenValue());
    }

    private AuthenticatedUser requireStaff() {
        AuthenticatedUser actor = currentUsers.currentUser();
        if (!"STAFF".equals(actor.userType()) || actor.optionalCustomerId().isPresent()
                || !actor.hasPermission("customer:intake:manage")) {
            throw new AuthorizationException("STAFF_CUSTOMER_INTAKE_ACCESS_DENIED",
                    "Staff Customer intake access is denied.");
        }
        return actor;
    }

    private static String randomSecret() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static CustomerDigitalAccessDto toStatus(User user) {
        return new CustomerDigitalAccessDto(user.customerId(), true, user.email(), user.isEmailVerified());
    }

    private static BusinessStateConflictException alreadyEnabled() {
        return new BusinessStateConflictException("CUSTOMER_DIGITAL_ACCESS_ALREADY_ENABLED",
                "Digital access is already enabled for this Customer.");
    }

    private static BusinessStateConflictException emailRegistered() {
        return new BusinessStateConflictException("EMAIL_ALREADY_REGISTERED", "An account with this email already exists.");
    }
}
