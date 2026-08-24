package com.meridian.platform.audit.infrastructure.adapter.in.event;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.meridian.platform.shared.application.audit.BusinessAuditEntry;
import com.meridian.platform.shared.application.audit.BusinessAuditEvent;
import com.meridian.platform.shared.application.operation.BusinessOperationContext;
import com.meridian.platform.shared.domain.audit.BusinessAuditAction;
import com.meridian.platform.shared.domain.audit.BusinessAuditEntityType;
import com.meridian.platform.shared.domain.audit.BusinessAuditPayload;
import com.meridian.platform.shared.domain.audit.BusinessAuditPayloadKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BusinessAuditEventListenerTest {

    private static final UUID OPERATION_ID = UUID.fromString("1333e099-9fb9-4ea0-a409-88362bdd28c7");
    private static final UUID ENTITY_ID = UUID.fromString("fc9d6f26-418d-4c7e-9e60-e888df73e05b");
    private static final UUID PAYLOAD_VALUE = UUID.fromString("528df2af-b18d-4543-9895-59312048c927");

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void correlatesAuditRecordingWithBusinessOperationAndRetainsRequestCorrelation() {
        String requestCorrelationId = "c73e775f-b9f9-4539-bb58-57b9b0c670f6";
        MDC.put("requestCorrelationId", requestCorrelationId);
        BusinessAuditEvent event = event();

        BusinessAuditEventListener listener = new BusinessAuditEventListener(recordedEvent -> {
            assertSame(event, recordedEvent);
            assertEquals(
                    OPERATION_ID.toString(),
                    MDC.get(BusinessAuditEventListener.BUSINESS_OPERATION_MDC_KEY)
            );
            assertEquals(requestCorrelationId, MDC.get("requestCorrelationId"));
        });

        listener.onBusinessAuditEvent(event);

        assertNull(MDC.get(BusinessAuditEventListener.BUSINESS_OPERATION_MDC_KEY));
        assertEquals(requestCorrelationId, MDC.get("requestCorrelationId"));
    }

    @Test
    void restoresPriorBusinessCorrelationWhenAuditRecordingFails() {
        MDC.put(BusinessAuditEventListener.BUSINESS_OPERATION_MDC_KEY, "outer-operation");
        IllegalStateException failure = new IllegalStateException("audit persistence failed");
        BusinessAuditEventListener listener = new BusinessAuditEventListener(event -> {
            assertEquals(
                    OPERATION_ID.toString(),
                    MDC.get(BusinessAuditEventListener.BUSINESS_OPERATION_MDC_KEY)
            );
            throw failure;
        });

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> listener.onBusinessAuditEvent(event())
        );

        assertSame(failure, thrown);
        assertEquals(
                "outer-operation",
                MDC.get(BusinessAuditEventListener.BUSINESS_OPERATION_MDC_KEY)
        );
    }

    @Test
    void successfulBusinessLogContainsOnlyBoundedOperationalFields() {
        Logger logger = (Logger) LoggerFactory.getLogger(BusinessAuditEventListener.class);
        Level previousLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.setLevel(Level.INFO);
        logger.addAppender(appender);
        try {
            new BusinessAuditEventListener(event -> { }).onBusinessAuditEvent(event());
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
            appender.stop();
        }

        assertEquals(1, appender.list.size());
        ILoggingEvent loggingEvent = appender.list.getFirst();
        assertEquals("Business audit recorded", loggingEvent.getFormattedMessage());
        assertEquals(
                OPERATION_ID.toString(),
                loggingEvent.getMDCPropertyMap().get(BusinessAuditEventListener.BUSINESS_OPERATION_MDC_KEY)
        );
        assertEquals("USER", keyValue(loggingEvent, "actorType"));
        assertEquals("1", keyValue(loggingEvent, "entryCount"));
        assertFalse(loggingEvent.toString().contains(PAYLOAD_VALUE.toString()));
        assertFalse(loggingEvent.toString().contains(BusinessAuditPayloadKey.LOAN_APPLICATION_ID.jsonName()));
    }

    private static String keyValue(ILoggingEvent event, String key) {
        return event.getKeyValuePairs().stream()
                .filter(pair -> pair.key.equals(key))
                .map(pair -> String.valueOf(pair.value))
                .findFirst()
                .orElse(null);
    }

    private static BusinessAuditEvent event() {
        BusinessOperationContext operationContext = BusinessOperationContext.user(
                OPERATION_ID,
                UUID.fromString("780f4901-6a91-4d0d-af68-264451679cb7"),
                LocalDateTime.of(2026, 8, 24, 9, 30)
        );
        BusinessAuditPayload payload = BusinessAuditPayload.builder()
                .put(BusinessAuditPayloadKey.LOAN_APPLICATION_ID, PAYLOAD_VALUE)
                .build();
        return BusinessAuditEvent.single(
                operationContext,
                new BusinessAuditEntry(
                        BusinessAuditAction.LOAN_REVIEW_STARTED,
                        BusinessAuditEntityType.LOAN_APPLICATION,
                        ENTITY_ID,
                        payload
                )
        );
    }
}
