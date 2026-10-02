package com.meridian.platform.document.infrastructure.adapter.out.identity;

import com.meridian.platform.document.application.port.out.DocumentStaffActorDirectoryPort;
import com.meridian.platform.identity.application.port.in.QueryStaffActorSummariesUseCase;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class DocumentStaffActorDirectoryAdapter implements DocumentStaffActorDirectoryPort {
    private final QueryStaffActorSummariesUseCase actors;

    public DocumentStaffActorDirectoryAdapter(QueryStaffActorSummariesUseCase actors) {
        this.actors = actors;
    }

    @Override
    public Map<UUID, StaffActorSummary> findByUserIds(Set<UUID> userIds) {
        return actors.findByUserIds(userIds).entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey, entry -> new StaffActorSummary(
                        entry.getValue().userId(), entry.getValue().displayName(), entry.getValue().email())));
    }
}
