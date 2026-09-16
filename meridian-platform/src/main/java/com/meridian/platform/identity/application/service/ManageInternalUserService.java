package com.meridian.platform.identity.application.service;

import com.meridian.platform.identity.application.dto.ChangeInternalUserRoleRequest;
import com.meridian.platform.identity.application.dto.ChangeInternalUserStatusRequest;
import com.meridian.platform.identity.application.dto.InternalUserDto;
import com.meridian.platform.identity.application.mapper.InternalUserMapper;
import com.meridian.platform.identity.application.port.in.ManageInternalUserUseCase;
import com.meridian.platform.identity.application.port.out.InternalUserAdministrationRepository;
import com.meridian.platform.identity.application.port.out.RefreshTokenSessionRepository;
import com.meridian.platform.identity.application.port.out.UserRepository;
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
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

@Service
public class ManageInternalUserService implements ManageInternalUserUseCase {

    private final UserRepository users;
    private final InternalUserAdministrationRepository administration;
    private final RefreshTokenSessionRepository refreshTokens;
    private final InternalUserMapper mapper;
    private final CurrentUserProvider currentUserProvider;
    private final BusinessAuditPublisher auditPublisher;
    private final Clock clock;

    public ManageInternalUserService(
            UserRepository users,
            InternalUserAdministrationRepository administration,
            RefreshTokenSessionRepository refreshTokens,
            InternalUserMapper mapper,
            CurrentUserProvider currentUserProvider,
            BusinessAuditPublisher auditPublisher,
            Clock clock
    ) {
        this.users = users;
        this.administration = administration;
        this.refreshTokens = refreshTokens;
        this.mapper = mapper;
        this.currentUserProvider = currentUserProvider;
        this.auditPublisher = auditPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public InternalUserDto changeStatus(UUID userId, ChangeInternalUserStatusRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        User user = internalUserForUpdate(userId);
        UserStatus target = Objects.requireNonNull(request.status(), "status must not be null");
        if (user.status() == target) {
            return mapper.toDto(user, user.status(), user.roles());
        }

        Instant now = Instant.now(clock);
        administration.updateStatusAndAuthorizationVersion(user.id(), target, now);
        if (target == UserStatus.SUSPENDED || target == UserStatus.DISABLED) {
            refreshTokens.revokeAllForUser(user.id(), now);
        }
        publishStatus(user, target, now);
        return mapper.toDto(user, target, user.roles());
    }

    @Override
    @Transactional
    public InternalUserDto changeRoleAssignment(
            UUID userId,
            String roleCode,
            ChangeInternalUserRoleRequest request
    ) {
        Objects.requireNonNull(request, "request must not be null");
        User user = internalUserForUpdate(userId);
        String normalizedRoleCode = normalizeRoleCode(roleCode);
        administration.findAssignableRole(normalizedRoleCode).orElseThrow(ManageInternalUserService::roleNotFound);
        boolean assigned = Objects.requireNonNull(request.assigned(), "assigned must not be null");
        boolean alreadyAssigned = user.roles().contains(normalizedRoleCode);
        if (alreadyAssigned == assigned) {
            return mapper.toDto(user, user.status(), user.roles());
        }

        if (assigned) {
            administration.assignRole(user.id(), normalizedRoleCode);
        } else {
            administration.removeRole(user.id(), normalizedRoleCode);
        }
        Instant now = Instant.now(clock);
        administration.incrementAuthorizationVersion(user.id(), now);
        publishRole(user.id(), normalizedRoleCode, assigned, now);

        Set<String> roles = new TreeSet<>(user.roles());
        if (assigned) {
            roles.add(normalizedRoleCode);
        } else {
            roles.remove(normalizedRoleCode);
        }
        return mapper.toDto(user, user.status(), roles);
    }

    private User internalUserForUpdate(UUID userId) {
        User user = users.findByIdForUpdate(userId).orElseThrow(ManageInternalUserService::userNotFound);
        if (user.userType() != UserType.STAFF || user.customerId() != null) {
            throw userNotFound();
        }
        return user;
    }

    private String normalizeRoleCode(String roleCode) {
        try {
            String normalized = roleCode.trim().toUpperCase(Locale.ROOT);
            if (normalized.isBlank()) {
                throw roleNotFound();
            }
            return normalized;
        } catch (NullPointerException exception) {
            throw roleNotFound();
        }
    }

    private void publishStatus(User user, UserStatus target, Instant occurredAt) {
        BusinessAuditPayload payload = BusinessAuditPayload.builder()
                .put(BusinessAuditPayloadKey.PREVIOUS_USER_STATUS, user.status())
                .put(BusinessAuditPayloadKey.FINAL_USER_STATUS, target)
                .build();
        publish(user.id(), BusinessAuditAction.IDENTITY_USER_STATUS_CHANGED, payload, occurredAt);
    }

    private void publishRole(UUID userId, String roleCode, boolean assigned, Instant occurredAt) {
        BusinessAuditPayload payload = BusinessAuditPayload.builder()
                .put(BusinessAuditPayloadKey.ROLE_CODE, roleCode)
                .build();
        publish(
                userId,
                assigned ? BusinessAuditAction.IDENTITY_USER_ROLE_ASSIGNED : BusinessAuditAction.IDENTITY_USER_ROLE_REMOVED,
                payload,
                occurredAt
        );
    }

    private void publish(UUID userId, BusinessAuditAction action, BusinessAuditPayload payload, Instant occurredAt) {
        AuthenticatedUser actor = currentUserProvider.currentUser();
        auditPublisher.publish(BusinessAuditEvent.single(
                BusinessOperationContext.user(
                        UUID.randomUUID(), actor.userId(), LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC)
                ),
                new BusinessAuditEntry(action, BusinessAuditEntityType.IDENTITY_USER, userId, payload)
        ));
    }

    private static EntityNotFoundException userNotFound() {
        return new EntityNotFoundException("INTERNAL_USER_NOT_FOUND", "Internal User was not found.");
    }

    private static EntityNotFoundException roleNotFound() {
        return new EntityNotFoundException("INTERNAL_ROLE_NOT_FOUND", "Internal role was not found.");
    }
}
