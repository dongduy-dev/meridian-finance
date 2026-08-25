package com.meridian.platform.identity.infrastructure.adapter.out.persistence;

import com.meridian.platform.identity.application.port.out.AccessTokenRevocationRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Repository
public class AccessTokenRevocationRepositoryAdapter implements AccessTokenRevocationRepository {

    private final JdbcTemplate jdbcTemplate;

    public AccessTokenRevocationRepositoryAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void revoke(UUID tokenId, Instant revokedAt, Instant expiresAt) {
        jdbcTemplate.update(
                """
                        INSERT INTO access_token_revocations (token_id, revoked_at, expires_at)
                        VALUES (?, ?, ?)
                        ON CONFLICT (token_id) DO NOTHING
                        """,
                tokenId,
                Timestamp.from(revokedAt),
                Timestamp.from(expiresAt)
        );
    }

    @Override
    public boolean isRevoked(UUID tokenId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                """
                        SELECT EXISTS (
                            SELECT 1
                            FROM access_token_revocations
                            WHERE token_id = ?
                              AND expires_at > CURRENT_TIMESTAMP
                        )
                        """,
                Boolean.class,
                tokenId
        ));
    }
}
