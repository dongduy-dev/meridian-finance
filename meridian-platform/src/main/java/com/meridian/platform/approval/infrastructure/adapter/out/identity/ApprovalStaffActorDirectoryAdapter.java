package com.meridian.platform.approval.infrastructure.adapter.out.identity;

import com.meridian.platform.approval.application.port.out.StaffActorDirectoryPort;
import com.meridian.platform.approval.application.port.out.StaffActorSummary;
import com.meridian.platform.identity.application.port.in.QueryStaffActorSummariesUseCase;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class ApprovalStaffActorDirectoryAdapter implements StaffActorDirectoryPort {
    private final QueryStaffActorSummariesUseCase queryStaffActors;

    public ApprovalStaffActorDirectoryAdapter(QueryStaffActorSummariesUseCase queryStaffActors) {
        this.queryStaffActors = queryStaffActors;
    }

    @Override
    public Map<UUID, StaffActorSummary> findByUserIds(Set<UUID> userIds) {
        Map<UUID, StaffActorSummary> summaries = new LinkedHashMap<>();
        queryStaffActors.findByUserIds(userIds).forEach((userId, summary) -> summaries.put(
                userId,
                new StaffActorSummary(summary.userId(), summary.displayName(), summary.email())
        ));
        return summaries;
    }
}
