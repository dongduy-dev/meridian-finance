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
        List<UserRow> rows = jdbcTemplate.query(
                """
                        SELECT id, email, password_hash, user_type, status, display_name, customer_id,
                               failed_login_attempts, locked_until
                        FROM users
                        WHERE id = ?
                        """,
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

    private Optional<User> findByNormalizedEmail(String normalizedEmail, boolean forUpdate) {
        String lockClause = forUpdate ? " FOR UPDATE" : "";
        List<UserRow> rows = jdbcTemplate.query(
                """
                        SELECT id, email, password_hash, user_type, status, display_name, customer_id,
                               failed_login_attempts, locked_until
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
                row.lockedUntil()
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
                toInstant(resultSet.getObject("locked_until", LocalDateTime.class))
        );
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
            Instant lockedUntil
    ) {
    }
}
