package com.meridian.platform.identity.infrastructure.adapter.out.persistence;

import com.meridian.platform.identity.application.port.out.AuthorizationVersionRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;

@Repository
public class AuthorizationVersionRepositoryAdapter implements AuthorizationVersionRepository {

    private final JdbcTemplate jdbcTemplate;

    public AuthorizationVersionRepositoryAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public OptionalLong findAuthorizationVersion(UUID userId) {
        List<Long> versions = jdbcTemplate.queryForList(
                "SELECT authorization_version FROM users WHERE id = ?",
                Long.class,
                userId
        );
        return versions.isEmpty() ? OptionalLong.empty() : OptionalLong.of(versions.getFirst());
    }
}
