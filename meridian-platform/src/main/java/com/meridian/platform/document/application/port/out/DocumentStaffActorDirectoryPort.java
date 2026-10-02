package com.meridian.platform.document.application.port.out;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface DocumentStaffActorDirectoryPort {
    Map<UUID, StaffActorSummary> findByUserIds(Set<UUID> userIds);

    record StaffActorSummary(UUID userId, String displayName, String email) {
    }
}
