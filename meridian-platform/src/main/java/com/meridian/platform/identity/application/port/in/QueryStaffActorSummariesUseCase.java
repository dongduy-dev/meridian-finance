package com.meridian.platform.identity.application.port.in;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface QueryStaffActorSummariesUseCase {
    Map<UUID, StaffActorSummary> findByUserIds(Set<UUID> userIds);
}
