package com.meridian.platform;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

class EventPublicationRecoveryPostgreSqlIntegrationTest {

    private static final String TEST_SCHEMA = "meridian_event_recovery_"
            + UUID.randomUUID().toString().replace("-", "");
    private static final String LISTENER_ID = "event-publication-recovery-test-listener";
    private static final String PUBLICATION_TABLE = TEST_SCHEMA + ".event_publication";
    private static final String SIDE_EFFECT_TABLE = TEST_SCHEMA + ".event_publication_recovery_effects";
    private static final Duration ASYNC_WAIT_TIMEOUT = Duration.ofSeconds(10);
    private static final long ASYNC_POLL_INTERVAL_MILLIS = 25L;

    @Test
    void republishesTheExactFailedPublicationOnceAcrossApplicationRestarts() {
        UUID eventId = UUID.randomUUID();
        UUID publicationId;
        Instant recoveredCompletionDate;

        try (ConfigurableApplicationContext firstContext = startContext(true)) {
            JdbcTemplate jdbcTemplate = firstContext.getBean(JdbcTemplate.class);
            createSideEffectTable(jdbcTemplate);

            publishTransactionally(firstContext, new RecoveryTestEvent(eventId));

            PublicationSnapshot failedPublication = awaitPublication(
                    () -> findPublicationByListener(jdbcTemplate),
                    publication -> "FAILED".equals(publication.status())
            );
            publicationId = failedPublication.id();

            assertAll(
                    () -> assertEquals("FAILED", failedPublication.status()),
                    () -> assertNull(failedPublication.completionDate()),
                    () -> assertEquals(1, failedPublication.completionAttempts()),
                    () -> assertEquals(0, countSideEffects(jdbcTemplate, eventId)),
                    () -> assertEquals(1, countPublicationsForListener(jdbcTemplate))
            );
        }

        try (ConfigurableApplicationContext secondContext = startContext(false)) {
            JdbcTemplate jdbcTemplate = secondContext.getBean(JdbcTemplate.class);
            PublicationSnapshot recoveredPublication = awaitPublication(
                    () -> requirePublicationById(jdbcTemplate, publicationId),
                    publication -> "COMPLETED".equals(publication.status())
            );
            recoveredCompletionDate = recoveredPublication.completionDate();

            assertAll(
                    () -> assertEquals(publicationId, recoveredPublication.id()),
                    () -> assertEquals("COMPLETED", recoveredPublication.status()),
                    () -> assertNotNull(recoveredPublication.completionDate()),
                    () -> assertEquals(2, recoveredPublication.completionAttempts()),
                    () -> assertEquals(1, countSideEffects(jdbcTemplate, eventId)),
                    () -> assertEquals(1, countPublicationsForListener(jdbcTemplate))
            );
        }

        try (ConfigurableApplicationContext thirdContext = startContext(false)) {
            JdbcTemplate jdbcTemplate = thirdContext.getBean(JdbcTemplate.class);
            PublicationSnapshot completedPublication = requirePublicationById(jdbcTemplate, publicationId);

            assertAll(
                    () -> assertEquals(publicationId, completedPublication.id()),
                    () -> assertEquals("COMPLETED", completedPublication.status()),
                    () -> assertEquals(recoveredCompletionDate, completedPublication.completionDate()),
                    () -> assertEquals(2, completedPublication.completionAttempts()),
                    () -> assertEquals(1, countSideEffects(jdbcTemplate, eventId)),
                    () -> assertEquals(1, countPublicationsForListener(jdbcTemplate))
            );
        }
    }

    private ConfigurableApplicationContext startContext(boolean listenerFails) {
        return new SpringApplicationBuilder(MeridianPlatformApplication.class, RecoveryTestConfiguration.class)
                .web(WebApplicationType.SERVLET)
                .run(
                        "--server.port=0",
                        "--spring.flyway.schemas=" + TEST_SCHEMA,
                        "--spring.flyway.default-schema=" + TEST_SCHEMA,
                        "--spring.jpa.properties.hibernate.default_schema=" + TEST_SCHEMA,
                        "--spring.modulith.events.republish-outstanding-events-on-restart=true",
                        "--meridian.loan.offer-expiry.enabled=false",
                        "--meridian.loan.overdue-evaluation.enabled=false",
                        "--meridian.document.orphan-reconciliation.enabled=false",
                        "--meridian.test.event-publication-recovery.listener-fails=" + listenerFails
                );
    }

    private void createSideEffectTable(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                create table %s (
                    delivery_id bigserial primary key,
                    event_id uuid not null,
                    delivered_at timestamp with time zone not null default current_timestamp
                )
                """.formatted(SIDE_EFFECT_TABLE));
    }

    private void publishTransactionally(
            ConfigurableApplicationContext context,
            RecoveryTestEvent event
    ) {
        PlatformTransactionManager transactionManager = context.getBean(PlatformTransactionManager.class);
        new TransactionTemplate(transactionManager).executeWithoutResult(
                ignored -> context.publishEvent(event)
        );
    }

    private PublicationSnapshot awaitPublication(
            Supplier<PublicationSnapshot> lookup,
            Predicate<PublicationSnapshot> condition
    ) {
        Instant deadline = Instant.now().plus(ASYNC_WAIT_TIMEOUT);
        PublicationSnapshot lastObserved = null;

        while (Instant.now().isBefore(deadline)) {
            lastObserved = lookup.get();
            if (lastObserved != null && condition.test(lastObserved)) {
                return lastObserved;
            }
            pauseForAsyncLifecycle();
        }

        return fail("Timed out waiting for the event publication lifecycle. Last observed: " + lastObserved);
    }

    private void pauseForAsyncLifecycle() {
        try {
            Thread.sleep(ASYNC_POLL_INTERVAL_MILLIS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            fail("Interrupted while waiting for asynchronous event delivery.", exception);
        }
    }

    private PublicationSnapshot findPublicationByListener(JdbcTemplate jdbcTemplate) {
        List<PublicationSnapshot> publications = jdbcTemplate.query(
                """
                        select id, status, completion_attempts, completion_date
                        from %s
                        where listener_id = ?
                        """.formatted(PUBLICATION_TABLE),
                (resultSet, rowNumber) -> toSnapshot(resultSet),
                LISTENER_ID
        );
        if (publications.size() > 1) {
            fail("Expected one publication for the test listener but found " + publications.size() + ".");
        }
        return publications.isEmpty() ? null : publications.getFirst();
    }

    private PublicationSnapshot requirePublicationById(JdbcTemplate jdbcTemplate, UUID publicationId) {
        List<PublicationSnapshot> publications = jdbcTemplate.query(
                """
                        select id, status, completion_attempts, completion_date
                        from %s
                        where id = ?
                        """.formatted(PUBLICATION_TABLE),
                (resultSet, rowNumber) -> toSnapshot(resultSet),
                publicationId
        );
        if (publications.size() != 1) {
            return fail("Expected publication " + publicationId + " but found " + publications.size() + ".");
        }
        return publications.getFirst();
    }

    private PublicationSnapshot toSnapshot(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        Timestamp completionDate = resultSet.getTimestamp("completion_date");
        return new PublicationSnapshot(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("status"),
                resultSet.getObject("completion_attempts", Integer.class),
                completionDate == null ? null : completionDate.toInstant()
        );
    }

    private int countSideEffects(JdbcTemplate jdbcTemplate, UUID eventId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from " + SIDE_EFFECT_TABLE + " where event_id = ?",
                Integer.class,
                eventId
        );
        return count == null ? 0 : count;
    }

    private int countPublicationsForListener(JdbcTemplate jdbcTemplate) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from " + PUBLICATION_TABLE + " where listener_id = ?",
                Integer.class,
                LISTENER_ID
        );
        return count == null ? 0 : count;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RecoveryTestConfiguration {

        @Bean
        RecoveryTestListener recoveryTestListener(
                JdbcTemplate jdbcTemplate,
                @Value("${meridian.test.event-publication-recovery.listener-fails}") boolean listenerFails
        ) {
            return new RecoveryTestListener(jdbcTemplate, listenerFails);
        }
    }

    static class RecoveryTestListener {

        private final JdbcTemplate jdbcTemplate;
        private final boolean listenerFails;

        RecoveryTestListener(JdbcTemplate jdbcTemplate, boolean listenerFails) {
            this.jdbcTemplate = jdbcTemplate;
            this.listenerFails = listenerFails;
        }

        @ApplicationModuleListener(id = LISTENER_ID)
        public void on(RecoveryTestEvent event) {
            if (listenerFails) {
                throw new IllegalStateException("Deliberate event publication recovery test failure.");
            }
            jdbcTemplate.update(
                    "insert into " + SIDE_EFFECT_TABLE + " (event_id) values (?)",
                    event.eventId()
            );
        }
    }

    public record RecoveryTestEvent(UUID eventId) {
    }

    private record PublicationSnapshot(
            UUID id,
            String status,
            Integer completionAttempts,
            Instant completionDate
    ) {
    }
}
