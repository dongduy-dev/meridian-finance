package com.meridian.platform.identity.infrastructure.adapter.out.persistence;

import com.meridian.platform.identity.application.port.in.StaffActorSummary;
import com.meridian.platform.identity.application.port.out.StaffActorSummaryRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Repository
public class JdbcStaffActorSummaryRepository implements StaffActorSummaryRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcStaffActorSummaryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Map<UUID, StaffActorSummary> findByUserIds(Set<UUID> userIds) {
        Map<UUID, StaffActorSummary> summaries = new LinkedHashMap<>();
        jdbcTemplate.query(
                """
                        SELECT id, display_name, email
                        FROM users
                        WHERE user_type = 'STAFF'
                          AND id IN (:userIds)
                        ORDER BY id
                        """,
                new MapSqlParameterSource("userIds", userIds),
                resultSet -> {
                    UUID userId = resultSet.getObject("id", UUID.class);
                    summaries.put(userId, new StaffActorSummary(
                            userId,
                            resultSet.getString("display_name"),
                            resultSet.getString("email")
                    ));
                }
        );
        return summaries;
    }
}
