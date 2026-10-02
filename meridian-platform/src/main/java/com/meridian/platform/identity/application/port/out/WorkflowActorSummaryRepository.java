package com.meridian.platform.identity.application.port.out;

import com.meridian.platform.identity.application.port.in.WorkflowActorSummary;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface WorkflowActorSummaryRepository {
    Map<UUID, WorkflowActorSummary> findByUserIds(Set<UUID> userIds);
}
