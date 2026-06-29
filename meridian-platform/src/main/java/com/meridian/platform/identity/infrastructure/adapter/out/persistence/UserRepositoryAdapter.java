package com.meridian.platform.identity.infrastructure.adapter.out.persistence;

import com.meridian.platform.identity.application.port.out.UserRepository;
import com.meridian.platform.identity.domain.model.User;
import com.meridian.platform.identity.domain.model.UserStatus;
import com.meridian.platform.identity.domain.model.UserType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
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
        List<UserRow> rows = jdbcTemplate.query(
                """
                        SELECT id, email, password_hash, user_type, status, display_name, customer_id
                        FROM users
                        WHERE normalized_email = ?
                        """,
                (resultSet, rowNum) -> mapUserRow(resultSet),
                normalizedEmail
        );

        if (rows.isEmpty()) {
            return Optional.empty();
        }

        UserRow row = rows.get(0);
        return Optional.of(new User(
                row.id(),
                row.email(),
                row.passwordHash(),
                UserType.valueOf(row.userType()),
                UserStatus.valueOf(row.status()),
                row.displayName(),
                row.customerId(),
                findRoles(row.id()),
                findPermissions(row.id())
        ));
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
                resultSet.getObject("customer_id", UUID.class)
        );
    }

    private record UserRow(
            UUID id,
            String email,
            String passwordHash,
            String userType,
            String status,
            String displayName,
            UUID customerId
    ) {
    }
}
