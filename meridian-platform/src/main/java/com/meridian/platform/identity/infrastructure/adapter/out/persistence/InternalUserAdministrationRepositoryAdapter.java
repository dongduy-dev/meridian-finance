package com.meridian.platform.identity.infrastructure.adapter.out.persistence;

import com.meridian.platform.identity.application.port.out.AssignableInternalRole;
import com.meridian.platform.identity.application.port.out.InternalUserAdministrationRepository;
import com.meridian.platform.identity.application.port.out.InternalUserRecord;
import com.meridian.platform.identity.domain.model.UserStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class InternalUserAdministrationRepositoryAdapter implements InternalUserAdministrationRepository {

    private final JdbcTemplate jdbcTemplate;

    public InternalUserAdministrationRepositoryAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<InternalUserRecord> findAllInternalUsers() {
        return jdbcTemplate.query(
                """
                        SELECT id, email, display_name, status
                        FROM users
                        WHERE user_type = 'STAFF' AND customer_id IS NULL
                        ORDER BY normalized_email, id
                        """,
                (resultSet, rowNumber) -> new InternalUserRecord(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getString("email"),
                        resultSet.getString("display_name"),
                        UserStatus.valueOf(resultSet.getString("status")),
                        findRoleCodes(resultSet.getObject("id", UUID.class))
                )
        );
    }

    @Override
    public List<AssignableInternalRole> findAssignableRoles() {
        return jdbcTemplate.query(
                "SELECT code, name FROM roles WHERE code <> 'CUSTOMER' ORDER BY code",
                (resultSet, rowNumber) -> new AssignableInternalRole(
                        resultSet.getString("code"), resultSet.getString("name")
                )
        );
    }

    @Override
    public Optional<AssignableInternalRole> findAssignableRole(String roleCode) {
        return jdbcTemplate.query(
                "SELECT code, name FROM roles WHERE code = ? AND code <> 'CUSTOMER'",
                (resultSet, rowNumber) -> new AssignableInternalRole(
                        resultSet.getString("code"), resultSet.getString("name")
                ),
                roleCode
        ).stream().findFirst();
    }

    @Override
    public void updateStatusAndAuthorizationVersion(UUID userId, UserStatus status, Instant updatedAt) {
        requireOne(jdbcTemplate.update(
                """
                        UPDATE users
                        SET status = ?, authorization_version = authorization_version + 1, updated_at = ?
                        WHERE id = ? AND user_type = 'STAFF' AND customer_id IS NULL
                        """,
                status.name(), Timestamp.from(updatedAt), userId
        ));
    }

    @Override
    public void assignRole(UUID userId, String roleCode) {
        requireOne(jdbcTemplate.update(
                """
                        INSERT INTO role_assignments (id, user_id, role_id)
                        SELECT ?, ?, id FROM roles WHERE code = ? AND code <> 'CUSTOMER'
                        """,
                UUID.randomUUID(), userId, roleCode
        ));
    }

    @Override
    public void removeRole(UUID userId, String roleCode) {
        requireOne(jdbcTemplate.update(
                """
                        DELETE FROM role_assignments
                        WHERE user_id = ? AND role_id = (SELECT id FROM roles WHERE code = ? AND code <> 'CUSTOMER')
                        """,
                userId, roleCode
        ));
    }

    @Override
    public void incrementAuthorizationVersion(UUID userId, Instant updatedAt) {
        requireOne(jdbcTemplate.update(
                """
                        UPDATE users
                        SET authorization_version = authorization_version + 1, updated_at = ?
                        WHERE id = ? AND user_type = 'STAFF' AND customer_id IS NULL
                        """,
                Timestamp.from(updatedAt), userId
        ));
    }

    private LinkedHashSet<String> findRoleCodes(UUID userId) {
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

    private static void requireOne(int changed) {
        if (changed != 1) {
            throw new IllegalStateException("Internal User authority change did not affect exactly one row.");
        }
    }
}
