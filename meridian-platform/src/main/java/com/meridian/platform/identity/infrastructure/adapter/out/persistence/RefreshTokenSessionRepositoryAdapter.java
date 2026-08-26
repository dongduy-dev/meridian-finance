package com.meridian.platform.identity.infrastructure.adapter.out.persistence;

import com.meridian.platform.identity.application.port.out.RefreshTokenSessionRepository;
import com.meridian.platform.identity.domain.model.RefreshTokenSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class RefreshTokenSessionRepositoryAdapter implements RefreshTokenSessionRepository {

    private final JdbcTemplate jdbcTemplate;

    public RefreshTokenSessionRepositoryAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void create(RefreshTokenSession session) {
        jdbcTemplate.update(
                """
                        INSERT INTO refresh_token_sessions (
                            id, user_id, family_id, token_digest,
                            issued_at, expires_at, consumed_at, revoked_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                session.id(),
                session.userId(),
                session.familyId(),
                session.tokenDigest(),
                Timestamp.from(session.issuedAt()),
                Timestamp.from(session.expiresAt()),
                timestampOrNull(session.consumedAt()),
                timestampOrNull(session.revokedAt())
        );
    }

    @Override
    public Optional<RefreshTokenSession> findByDigestForUpdate(String tokenDigest) {
        List<RefreshTokenSession> sessions = jdbcTemplate.query(
                """
                        SELECT id, user_id, family_id, token_digest,
                               issued_at, expires_at, consumed_at, revoked_at
                        FROM refresh_token_sessions
                        WHERE token_digest = ?
                        FOR UPDATE
                        """,
                (resultSet, rowNum) -> mapSession(resultSet),
                tokenDigest
        );
        return sessions.stream().findFirst();
    }

    @Override
    public void markConsumed(UUID sessionId, Instant consumedAt) {
        int updated = jdbcTemplate.update(
                """
                        UPDATE refresh_token_sessions
                        SET consumed_at = ?
                        WHERE id = ?
                          AND consumed_at IS NULL
                          AND revoked_at IS NULL
                        """,
                Timestamp.from(consumedAt),
                sessionId
        );
        if (updated != 1) {
            throw new IllegalStateException("Refresh-token session could not be consumed.");
        }
    }

    @Override
    public void revokeFamily(UUID familyId, Instant revokedAt) {
        jdbcTemplate.update(
                """
                        UPDATE refresh_token_sessions
                        SET revoked_at = ?
                        WHERE family_id = ?
                          AND revoked_at IS NULL
                        """,
                Timestamp.from(revokedAt),
                familyId
        );
    }

    @Override
    public void revokeAllForUser(UUID userId, Instant revokedAt) {
        jdbcTemplate.update(
                """
                        UPDATE refresh_token_sessions
                        SET revoked_at = ?
                        WHERE user_id = ?
                          AND revoked_at IS NULL
                        """,
                Timestamp.from(revokedAt),
                userId
        );
    }

    private RefreshTokenSession mapSession(ResultSet resultSet) throws SQLException {
        return new RefreshTokenSession(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("user_id", UUID.class),
                resultSet.getObject("family_id", UUID.class),
                resultSet.getString("token_digest"),
                resultSet.getTimestamp("issued_at").toInstant(),
                resultSet.getTimestamp("expires_at").toInstant(),
                instantOrNull(resultSet.getTimestamp("consumed_at")),
                instantOrNull(resultSet.getTimestamp("revoked_at"))
        );
    }

    private static Timestamp timestampOrNull(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static Instant instantOrNull(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
