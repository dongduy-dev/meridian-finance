package com.meridian.platform.identity.infrastructure.adapter.out.persistence;

import com.meridian.platform.identity.application.port.out.PasswordResetTokenRepository;
import com.meridian.platform.identity.domain.model.PasswordResetToken;
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
public class PasswordResetTokenRepositoryAdapter implements PasswordResetTokenRepository {

    private final JdbcTemplate jdbcTemplate;

    public PasswordResetTokenRepositoryAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void create(PasswordResetToken token) {
        jdbcTemplate.update(
                """
                        INSERT INTO password_reset_tokens (
                            id, user_id, token_digest, issued_at, expires_at, consumed_at, revoked_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                token.id(),
                token.userId(),
                token.tokenDigest(),
                Timestamp.from(token.issuedAt()),
                Timestamp.from(token.expiresAt()),
                timestampOrNull(token.consumedAt()),
                timestampOrNull(token.revokedAt())
        );
    }

    @Override
    public Optional<PasswordResetToken> findByDigestForUpdate(String tokenDigest) {
        List<PasswordResetToken> tokens = jdbcTemplate.query(
                """
                        SELECT id, user_id, token_digest, issued_at, expires_at, consumed_at, revoked_at
                        FROM password_reset_tokens
                        WHERE token_digest = ?
                        FOR UPDATE
                        """,
                (resultSet, rowNumber) -> map(resultSet),
                tokenDigest
        );
        return tokens.stream().findFirst();
    }

    @Override
    public void revokeActiveForUser(UUID userId, Instant revokedAt) {
        jdbcTemplate.update(
                """
                        UPDATE password_reset_tokens
                        SET revoked_at = ?
                        WHERE user_id = ?
                          AND consumed_at IS NULL
                          AND revoked_at IS NULL
                        """,
                Timestamp.from(revokedAt),
                userId
        );
    }

    @Override
    public void markConsumed(UUID tokenId, Instant consumedAt) {
        int updated = jdbcTemplate.update(
                """
                        UPDATE password_reset_tokens
                        SET consumed_at = ?
                        WHERE id = ?
                          AND consumed_at IS NULL
                          AND revoked_at IS NULL
                        """,
                Timestamp.from(consumedAt),
                tokenId
        );
        if (updated != 1) {
            throw new IllegalStateException("Password-reset token could not be consumed.");
        }
    }

    private PasswordResetToken map(ResultSet resultSet) throws SQLException {
        return new PasswordResetToken(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("user_id", UUID.class),
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
