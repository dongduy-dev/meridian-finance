package com.meridian.platform.document.application.spi.identity;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface DocumentStaffActorDirectorySpi {
    Map<UUID, StaffActorSummary> findByUserIds(Set<UUID> userIds);

    record StaffActorSummary(UUID userId, String displayName, String email) {
    }
}
