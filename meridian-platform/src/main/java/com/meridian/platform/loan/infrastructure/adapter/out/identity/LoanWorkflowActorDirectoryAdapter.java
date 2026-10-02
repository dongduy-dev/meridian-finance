package com.meridian.platform.loan.infrastructure.adapter.out.identity;

import com.meridian.platform.identity.application.port.in.QueryWorkflowActorSummariesUseCase;
import com.meridian.platform.loan.application.port.out.StaffActorSummary;
import com.meridian.platform.loan.application.port.out.WorkflowActorDirectoryPort;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class LoanWorkflowActorDirectoryAdapter implements WorkflowActorDirectoryPort {
    private final QueryWorkflowActorSummariesUseCase actors;

    public LoanWorkflowActorDirectoryAdapter(QueryWorkflowActorSummariesUseCase actors) {
        this.actors = actors;
    }

    @Override
    public Map<UUID, ActorSummary> findByUserIds(Set<UUID> userIds) {
        Map<UUID, ActorSummary> result = new LinkedHashMap<>();
        actors.findByUserIds(userIds).forEach((id, actor) -> result.put(id, new ActorSummary(
                actor.userId(), actor.userType(), actor.customerId(), actor.staff() == null ? null
                : new StaffActorSummary(actor.staff().userId(), actor.staff().displayName(), actor.staff().email()))));
        return result;
    }
}
