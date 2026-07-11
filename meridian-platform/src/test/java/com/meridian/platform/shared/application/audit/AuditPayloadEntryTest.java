package com.meridian.platform.shared.application.audit;

import com.meridian.platform.shared.domain.model.ActionActor;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuditPayloadEntryTest {

    @Test
    void acceptsCurrentUuidAndCodePayloadValues() {
        assertDoesNotThrow(() -> List.of(
                new AuditPayloadEntry(AuditPayloadKey.PRODUCT_CODE, "SALARY_ADVANCE"),
                new AuditPayloadEntry(AuditPayloadKey.MOVEMENT_TYPE, "RESERVED"),
                new AuditPayloadEntry(AuditPayloadKey.RECOMMENDATION_ACTION, "RECOMMEND_APPROVAL"),
                new AuditPayloadEntry(AuditPayloadKey.APPROVAL_DECISION_ACTION, "APPROVE"),
                new AuditPayloadEntry(AuditPayloadKey.RECOMMENDATION_ID, UUID.randomUUID().toString()),
                new AuditPayloadEntry(AuditPayloadKey.SOURCE_POLICY_ID, UUID.randomUUID().toString())
        ));
    }

    @Test
    void rejectsDuplicateKeysAtEventBoundary() {
        UUID operationId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new AuditRecordRequestedEvent(
                operationId,
                (short) 1,
                ActionActor.user(UUID.randomUUID()),
                AuditEntityType.LOAN_APPLICATION,
                entityId,
                AuditAction.APPLICATION_SUBMITTED,
                List.of(
                        new AuditPayloadEntry(AuditPayloadKey.PRODUCT_CODE, "SALARY_ADVANCE"),
                        new AuditPayloadEntry(AuditPayloadKey.PRODUCT_CODE, "SALARY_ADVANCE")
                ),
                LocalDateTime.of(2026, 7, 6, 12, 0)
        ));
    }

    @Test
    void rejectsInvalidUuidValues() {
        assertThrows(IllegalArgumentException.class,
                () -> new AuditPayloadEntry(AuditPayloadKey.RECOMMENDATION_ID, "not-a-uuid"));
    }

    @Test
    void rejectsLowercaseOrInvalidCodes() {
        assertThrows(IllegalArgumentException.class,
                () -> new AuditPayloadEntry(AuditPayloadKey.PRODUCT_CODE, "salary_advance"));
        assertThrows(IllegalArgumentException.class,
                () -> new AuditPayloadEntry(AuditPayloadKey.PRODUCT_CODE, "SALARY ADVANCE"));
    }

    @Test
    void rejectsControlCharactersOversizedValuesAndBlanks() {
        assertThrows(IllegalArgumentException.class,
                () -> new AuditPayloadEntry(AuditPayloadKey.PRODUCT_CODE, "SALARY\nADVANCE"));
        assertThrows(IllegalArgumentException.class,
                () -> new AuditPayloadEntry(AuditPayloadKey.PRODUCT_CODE, " "));
        assertThrows(IllegalArgumentException.class,
                () -> new AuditPayloadEntry(AuditPayloadKey.PRODUCT_CODE, "A".repeat(129)));
    }

    @Test
    void allowedKeysExcludeForbiddenSensitiveConcepts() {
        List<String> keyNames = Arrays.stream(AuditPayloadKey.values()).map(Enum::name).toList();

        assertFalse(keyNames.stream().anyMatch(name -> name.contains("REASON")));
        assertFalse(keyNames.stream().anyMatch(name -> name.contains("INTERNAL")));
        assertFalse(keyNames.stream().anyMatch(name -> name.contains("SALARY_AMOUNT")));
        assertFalse(keyNames.stream().anyMatch(name -> name.contains("GROSS_SALARY")));
        assertFalse(keyNames.stream().anyMatch(name -> name.contains("NET_SALARY")));
        assertFalse(keyNames.stream().anyMatch(name -> name.contains("EMPLOYEE_CODE")));
        assertFalse(keyNames.stream().anyMatch(name -> name.contains("TOKEN")));
        assertFalse(keyNames.stream().anyMatch(name -> name.contains("SECRET")));
    }
}
