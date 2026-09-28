package com.meridian.platform.identity.application.port.out;

import com.meridian.platform.identity.application.port.in.StaffActorSummary;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface StaffActorSummaryRepository {
    Map<UUID, StaffActorSummary> findByUserIds(Set<UUID> userIds);
}
