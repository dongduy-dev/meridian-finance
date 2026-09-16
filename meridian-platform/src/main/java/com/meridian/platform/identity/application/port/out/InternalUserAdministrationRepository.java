package com.meridian.platform.identity.application.port.out;

import com.meridian.platform.identity.domain.model.UserStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InternalUserAdministrationRepository {

    List<InternalUserRecord> findAllInternalUsers();

    List<AssignableInternalRole> findAssignableRoles();

    Optional<AssignableInternalRole> findAssignableRole(String roleCode);

    void updateStatusAndAuthorizationVersion(UUID userId, UserStatus status, Instant updatedAt);

    void assignRole(UUID userId, String roleCode);

    void removeRole(UUID userId, String roleCode);

    void incrementAuthorizationVersion(UUID userId, Instant updatedAt);
}
