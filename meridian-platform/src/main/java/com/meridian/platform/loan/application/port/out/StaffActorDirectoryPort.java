package com.meridian.platform.loan.application.port.out;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface StaffActorDirectoryPort {
    Map<UUID, StaffActorSummary> findByUserIds(Set<UUID> userIds);
}
