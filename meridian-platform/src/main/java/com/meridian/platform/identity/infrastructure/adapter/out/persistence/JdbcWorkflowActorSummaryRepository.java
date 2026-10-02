package com.meridian.platform.identity.infrastructure.adapter.out.persistence;

import com.meridian.platform.identity.application.port.in.StaffActorSummary;
import com.meridian.platform.identity.application.port.in.WorkflowActorSummary;
import com.meridian.platform.identity.application.port.out.WorkflowActorSummaryRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Repository
public class JdbcWorkflowActorSummaryRepository implements WorkflowActorSummaryRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public JdbcWorkflowActorSummaryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<UUID, WorkflowActorSummary> findByUserIds(Set<UUID> userIds) {
        if (userIds.isEmpty()) return Map.of();
        Map<UUID, WorkflowActorSummary> result = new LinkedHashMap<>();
        jdbc.query("""
                SELECT id, user_type, customer_id,
                       CASE WHEN user_type = 'STAFF' THEN display_name END AS staff_name,
                       CASE WHEN user_type = 'STAFF' THEN email END AS staff_email
                FROM users WHERE id IN (:userIds) ORDER BY id
                """, new MapSqlParameterSource("userIds", userIds), row -> {
            UUID id = row.getObject("id", UUID.class);
            String type = row.getString("user_type");
            result.put(id, new WorkflowActorSummary(id, type, row.getObject("customer_id", UUID.class),
                    "STAFF".equals(type) ? new StaffActorSummary(
                            id, row.getString("staff_name"), row.getString("staff_email")) : null));
        });
        return result;
    }
}
