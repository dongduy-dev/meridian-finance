package com.meridian.platform.shared.infrastructure.health;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/** Small query probe; startup/Flyway completion is covered by readinessState. */
@Component("databaseHealthIndicator")
public class DatabaseReadinessHealthIndicator implements HealthIndicator {
    private final DataSource dataSource;

    public DatabaseReadinessHealthIndicator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Health health() {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.setQueryTimeout(2);
            try (var result = statement.executeQuery("SELECT 1")) {
                return result.next() && result.getInt(1) == 1 ? Health.up().build() : Health.down().build();
            }
        } catch (Exception ignored) {
            return Health.down().build();
        }
    }
}
