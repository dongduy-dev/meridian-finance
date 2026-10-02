package com.meridian.platform.loan.application.port.out;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface WorkflowActorDirectoryPort {
    Map<UUID, ActorSummary> findByUserIds(Set<UUID> userIds);

    record ActorSummary(UUID userId, String userType, UUID customerId, StaffActorSummary staff) {
    }
}
