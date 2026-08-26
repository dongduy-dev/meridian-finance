package com.meridian.platform.identity.infrastructure.adapter.out.persistence;

import com.meridian.platform.identity.application.port.out.UserRepository;
import com.meridian.platform.identity.domain.model.User;
import com.meridian.platform.identity.domain.model.UserStatus;
import com.meridian.platform.identity.domain.model.UserType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public class UserRepositoryAdapter implements UserRepository {

    private final JdbcTemplate jdbcTemplate;

    public UserRepositoryAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<User> findByNormalizedEmail(String normalizedEmail) {
        return findByNormalizedEmail(normalizedEmail, false);
    }

    @Override
    public Optional<User> findByNormalizedEmailForUpdate(String normalizedEmail) {
        return findByNormalizedEmail(normalizedEmail, true);
    }

    @Override
    public Optional<User> findById(UUID userId) {
        return findById(userId, false);
    }

    @Override
    public Optional<User> findByIdForUpdate(UUID userId) {
        return findById(userId, true);
    }

    @Override
    public void createCustomerUser(User user) {
        int inserted = jdbcTemplate.update(
                """
                        INSERT INTO users (
                            id, email, normalized_email, password_hash, user_type, status,
                            display_name, customer_id, failed_login_attempts, locked_until,
                            email_verified_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT ON CONSTRAINT uq_users_normalized_email DO NOTHING
                        """,
                user.id(),
                user.email(),
                user.email(),
                user.passwordHash(),
                user.userType().name(),
                user.status().name(),
                user.displayName(),
                user.customerId(),
                user.failedLoginAttempts(),
                toLocalDateTime(user.lockedUntil()),
                toLocalDateTime(user.emailVerifiedAt())
        );
        if (inserted != 1) {
            throw new com.meridian.platform.shared.domain.exception.BusinessStateConflictException(
                    "EMAIL_ALREADY_REGISTERED",
                    "An account with this email already exists."
            );
        }

        int assigned = jdbcTemplate.update(
                """
                        INSERT INTO role_assignments (id, user_id, role_id)
                        SELECT ?, ?, id
                        FROM roles
                        WHERE code = 'CUSTOMER'
                        """,
                UUID.randomUUID(),
                user.id()
        );
        if (assigned != 1) {
            throw new IllegalStateException("CUSTOMER role is not configured.");
        }
    }

    @Override
    public void markEmailVerified(UUID userId, Instant verifiedAt) {
        int updated = jdbcTemplate.update(
                """
                        UPDATE users
                        SET email_verified_at = ?, updated_at = CURRENT_TIMESTAMP
                        WHERE id = ? AND email_verified_at IS NULL
                        """,
                toLocalDateTime(verifiedAt),
                userId
        );
        if (updated != 1) {
            throw new IllegalStateException("User email could not be marked verified.");
        }
    }

    private Optional<User> findById(UUID userId, boolean forUpdate) {
        String lockClause = forUpdate ? " FOR UPDATE" : "";
        List<UserRow> rows = jdbcTemplate.query(
                """
                        SELECT id, email, password_hash, user_type, status, display_name, customer_id,
                               failed_login_attempts, locked_until, email_verified_at
                        FROM users
                        WHERE id = ?
                        """ + lockClause,
                (resultSet, rowNum) -> mapUserRow(resultSet),
                userId
        );
        return rows.stream().findFirst().map(this::toDomain);
    }

    @Override
    public void updateLoginProtection(UUID userId, int failedLoginAttempts, Instant lockedUntil) {
        jdbcTemplate.update(
                """
                        UPDATE users
                        SET failed_login_attempts = ?,
                            locked_until = ?,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE id = ?
                        """,
                failedLoginAttempts,
                lockedUntil == null ? null : LocalDateTime.ofInstant(lockedUntil, ZoneOffset.UTC),
                userId
        );
    }

    @Override
    public void replacePasswordAndClearLoginProtection(UUID userId, String passwordHash) {
        int updated = jdbcTemplate.update(
                """
                        UPDATE users
                        SET password_hash = ?,
                            failed_login_attempts = 0,
                            locked_until = NULL,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE id = ?
                        """,
                passwordHash,
                userId
        );
        if (updated != 1) {
            throw new IllegalStateException("User password could not be replaced.");
        }
    }

    private Optional<User> findByNormalizedEmail(String normalizedEmail, boolean forUpdate) {
        String lockClause = forUpdate ? " FOR UPDATE" : "";
        List<UserRow> rows = jdbcTemplate.query(
                """
                        SELECT id, email, password_hash, user_type, status, display_name, customer_id,
                               failed_login_attempts, locked_until, email_verified_at
                        FROM users
                        WHERE normalized_email = ?
                        """ + lockClause,
                (resultSet, rowNum) -> mapUserRow(resultSet),
                normalizedEmail
        );

        return rows.stream().findFirst().map(this::toDomain);
    }

    private User toDomain(UserRow row) {
        return new User(
                row.id(),
                row.email(),
                row.passwordHash(),
                UserType.valueOf(row.userType()),
                UserStatus.valueOf(row.status()),
                row.displayName(),
                row.customerId(),
                findRoles(row.id()),
                findPermissions(row.id()),
                row.failedLoginAttempts(),
                row.lockedUntil(),
                row.emailVerifiedAt()
        );
    }

    private Set<String> findRoles(UUID userId) {
        return new LinkedHashSet<>(jdbcTemplate.queryForList(
                """
                        SELECT r.code
                        FROM role_assignments ra
                        JOIN roles r ON r.id = ra.role_id
                        WHERE ra.user_id = ?
                        ORDER BY r.code
                        """,
                String.class,
                userId
        ));
    }

    private Set<String> findPermissions(UUID userId) {
        return new LinkedHashSet<>(jdbcTemplate.queryForList(
                """
                        SELECT DISTINCT p.code
                        FROM role_assignments ra
                        JOIN role_permissions rp ON rp.role_id = ra.role_id
                        JOIN permissions p ON p.id = rp.permission_id
                        WHERE ra.user_id = ?
                        ORDER BY p.code
                        """,
                String.class,
                userId
        ));
    }

    private UserRow mapUserRow(ResultSet resultSet) throws SQLException {
        return new UserRow(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("email"),
                resultSet.getString("password_hash"),
                resultSet.getString("user_type"),
                resultSet.getString("status"),
                resultSet.getString("display_name"),
                resultSet.getObject("customer_id", UUID.class),
                resultSet.getInt("failed_login_attempts"),
                toInstant(resultSet.getObject("locked_until", LocalDateTime.class)),
                toInstant(resultSet.getObject("email_verified_at", LocalDateTime.class))
        );
    }

    private LocalDateTime toLocalDateTime(Instant value) {
        return value == null ? null : LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private record UserRow(
            UUID id,
            String email,
            String passwordHash,
            String userType,
            String status,
            String displayName,
            UUID customerId,
            int failedLoginAttempts,
            Instant lockedUntil,
            Instant emailVerifiedAt
    ) {
    }
}
