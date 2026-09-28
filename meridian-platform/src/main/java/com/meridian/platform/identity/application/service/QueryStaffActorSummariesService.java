package com.meridian.platform.identity.application.service;

import com.meridian.platform.identity.application.port.in.QueryStaffActorSummariesUseCase;
import com.meridian.platform.identity.application.port.in.StaffActorSummary;
import com.meridian.platform.identity.application.port.out.StaffActorSummaryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class QueryStaffActorSummariesService implements QueryStaffActorSummariesUseCase {
    private final StaffActorSummaryRepository repository;

    public QueryStaffActorSummariesService(StaffActorSummaryRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, StaffActorSummary> findByUserIds(Set<UUID> userIds) {
        Objects.requireNonNull(userIds, "userIds must not be null");
        if (userIds.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("userIds must not contain null");
        }
        return userIds.isEmpty() ? Map.of() : Map.copyOf(repository.findByUserIds(Set.copyOf(userIds)));
    }
}
