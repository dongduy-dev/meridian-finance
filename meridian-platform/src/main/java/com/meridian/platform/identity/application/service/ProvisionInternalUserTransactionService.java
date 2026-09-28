package com.meridian.platform.identity.application.service;

import com.meridian.platform.identity.application.dto.CreateInternalUserRequest;
import com.meridian.platform.identity.application.mapper.InternalUserMapper;
import com.meridian.platform.identity.application.port.out.GeneratedPasswordResetToken;
import com.meridian.platform.identity.application.port.out.InternalUserAdministrationRepository;
import com.meridian.platform.identity.application.port.out.PasswordHashingPort;
import com.meridian.platform.identity.application.port.out.PasswordResetTokenCodecPort;
import com.meridian.platform.identity.application.port.out.PasswordResetTokenRepository;
import com.meridian.platform.identity.application.port.out.UserRepository;
import com.meridian.platform.identity.domain.model.PasswordResetToken;
import com.meridian.platform.identity.domain.model.User;
import com.meridian.platform.identity.domain.model.UserStatus;
import com.meridian.platform.identity.domain.model.UserType;
import com.meridian.platform.shared.application.audit.BusinessAuditEntry;
import com.meridian.platform.shared.application.audit.BusinessAuditEvent;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.operation.BusinessOperationContext;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.audit.BusinessAuditAction;
import com.meridian.platform.shared.domain.audit.BusinessAuditEntityType;
import com.meridian.platform.shared.domain.audit.BusinessAuditPayload;
import com.meridian.platform.shared.domain.audit.BusinessAuditPayloadKey;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

@Service
public class ProvisionInternalUserTransactionService {
    private final UserRepository users;
    private final InternalUserAdministrationRepository administration;
    private final PasswordHashingPort passwordHashing;
    private final PasswordResetTokenCodecPort tokenCodec;
    private final PasswordResetTokenRepository tokens;
    private final InternalUserMapper mapper;
    private final CurrentUserProvider currentUserProvider;
    private final BusinessAuditPublisher auditPublisher;
    private final Duration tokenLifetime;
    private final Clock clock;

    public ProvisionInternalUserTransactionService(
            UserRepository users,
            InternalUserAdministrationRepository administration,
            PasswordHashingPort passwordHashing,
            PasswordResetTokenCodecPort tokenCodec,
            PasswordResetTokenRepository tokens,
            InternalUserMapper mapper,
            CurrentUserProvider currentUserProvider,
            BusinessAuditPublisher auditPublisher,
            @Value("${meridian.identity.password-reset.lifetime:30m}") Duration tokenLifetime,
            Clock clock
    ) {
        this.users = users;
        this.administration = administration;
        this.passwordHashing = passwordHashing;
        this.tokenCodec = tokenCodec;
        this.tokens = tokens;
        this.mapper = mapper;
        this.currentUserProvider = currentUserProvider;
        this.auditPublisher = auditPublisher;
        if (tokenLifetime == null || tokenLifetime.isZero() || tokenLifetime.isNegative()) {
            throw new IllegalArgumentException("password-setup lifetime must be positive");
        }
        this.tokenLifetime = tokenLifetime;
        this.clock = clock;
    }

    @Transactional
    public PendingStaffSetupDelivery create(CreateInternalUserRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        String email = Objects.requireNonNull(request.email(), "email must not be null").trim().toLowerCase(Locale.ROOT);
        String displayName = Objects.requireNonNull(request.displayName(), "displayName must not be null").trim();
        if (email.isBlank() || displayName.isBlank()) {
            throw new BusinessRuleViolationException("VALIDATION_ERROR", "Email and display name are required.");
        }
        Set<String> roles = validateRoles(request.roleCodes());
        if (users.findByNormalizedEmail(email).isPresent()) {
            throw new BusinessStateConflictException("EMAIL_ALREADY_REGISTERED", "An account with this email already exists.");
        }

        Instant now = Instant.now(clock);
        UUID userId = UUID.randomUUID();
        User user = new User(userId, email, passwordHashing.hash(tokenCodec.generate().tokenValue()),
                UserType.STAFF, UserStatus.ACTIVE, displayName, null, roles, Set.of(), 0, 0, null, now);
        users.createStaffUser(user);
        for (String role : roles) {
            administration.assignRole(userId, role);
        }
        GeneratedPasswordResetToken generated = createToken(userId, now);
        publishCreation(userId, roles, now);
        return new PendingStaffSetupDelivery(mapper.toDto(user, user.status(), roles), email, generated.tokenValue());
    }

    @Transactional
    public PendingStaffSetupDelivery issueForStaffUser(UUID userId) {
        User user = users.findByIdForUpdate(userId).orElseThrow(ProvisionInternalUserTransactionService::userNotFound);
        if (user.userType() != UserType.STAFF || user.customerId() != null) {
            throw userNotFound();
        }
        if (!user.isActive() || !user.isEmailVerified()) {
            throw new BusinessStateConflictException("INTERNAL_USER_NOT_ACTIVE", "Reactivate and verify the Internal User before sending a setup link.");
        }
        Instant now = Instant.now(clock);
        tokens.revokeActiveForUser(user.id(), now);
        GeneratedPasswordResetToken generated = createToken(user.id(), now);
        return new PendingStaffSetupDelivery(mapper.toDto(user, user.status(), user.roles()), user.email(), generated.tokenValue());
    }

    private Set<String> validateRoles(List<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            throw new BusinessRuleViolationException("VALIDATION_ERROR", "At least one Staff role is required.");
        }
        Set<String> roles = new TreeSet<>();
        for (String raw : roleCodes) {
            String role = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
            if (role.isBlank() || administration.findAssignableRole(role).isEmpty()) {
                throw new EntityNotFoundException("INTERNAL_ROLE_NOT_FOUND", "Internal role was not found.");
            }
            roles.add(role);
        }
        return roles;
    }

    private GeneratedPasswordResetToken createToken(UUID userId, Instant now) {
        GeneratedPasswordResetToken generated = tokenCodec.generate();
        tokens.create(new PasswordResetToken(UUID.randomUUID(), userId, generated.tokenDigest(),
                now, now.plus(tokenLifetime), null, null));
        return generated;
    }

    private void publishCreation(UUID userId, Set<String> roles, Instant now) {
        List<BusinessAuditEntry> entries = new ArrayList<>();
        entries.add(BusinessAuditEntry.of(BusinessAuditAction.IDENTITY_USER_CREATED,
                BusinessAuditEntityType.IDENTITY_USER, userId));
        for (String role : roles) {
            entries.add(new BusinessAuditEntry(BusinessAuditAction.IDENTITY_USER_ROLE_ASSIGNED,
                    BusinessAuditEntityType.IDENTITY_USER, userId,
                    BusinessAuditPayload.builder().put(BusinessAuditPayloadKey.ROLE_CODE, role).build()));
        }
        auditPublisher.publish(new BusinessAuditEvent(
                BusinessOperationContext.user(UUID.randomUUID(), currentUserProvider.currentUser().userId(),
                        LocalDateTime.ofInstant(now, ZoneOffset.UTC)), entries));
    }

    private static EntityNotFoundException userNotFound() {
        return new EntityNotFoundException("INTERNAL_USER_NOT_FOUND", "Internal User was not found.");
    }
}
