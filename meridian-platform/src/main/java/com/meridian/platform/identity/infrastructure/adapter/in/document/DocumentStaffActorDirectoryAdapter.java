package com.meridian.platform.identity.infrastructure.adapter.in.document;

import com.meridian.platform.document.application.spi.identity.DocumentStaffActorDirectorySpi;
import com.meridian.platform.identity.application.port.in.QueryStaffActorSummariesUseCase;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class DocumentStaffActorDirectoryAdapter implements DocumentStaffActorDirectorySpi {
    private final QueryStaffActorSummariesUseCase actors;

    public DocumentStaffActorDirectoryAdapter(QueryStaffActorSummariesUseCase actors) {
        this.actors = actors;
    }

    @Override
    public Map<UUID, StaffActorSummary> findByUserIds(Set<UUID> userIds) {
        return Map.copyOf(actors.findByUserIds(userIds).entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey, entry -> new StaffActorSummary(
                        entry.getValue().userId(), entry.getValue().displayName(), entry.getValue().email()))));
    }
}
