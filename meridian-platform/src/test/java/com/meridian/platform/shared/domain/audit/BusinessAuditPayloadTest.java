package com.meridian.platform.shared.domain.audit;

import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BusinessAuditPayloadTest {

    private static final UUID LOAN_APPLICATION_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID LIMIT_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID RECOMMENDATION_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    @Test
    void acceptsCurrentValidPayloads() {
        BusinessAuditPayload submission = BusinessAuditPayload.builder()
                .put(BusinessAuditPayloadKey.LOAN_APPLICATION_ID, LOAN_APPLICATION_ID)
                .build();
        BusinessAuditPayload movement = BusinessAuditPayload.builder()
                .put(BusinessAuditPayloadKey.SALARY_ADVANCE_LIMIT_ID, LIMIT_ID)
                .put(BusinessAuditPayloadKey.MOVEMENT_TYPE, TestAuditCode.RESERVED)
                .put(BusinessAuditPayloadKey.LOAN_APPLICATION_ID, LOAN_APPLICATION_ID)
                .build();
        BusinessAuditPayload approval = BusinessAuditPayload.builder()
                .put(BusinessAuditPayloadKey.LOAN_APPLICATION_ID, LOAN_APPLICATION_ID)
                .put(BusinessAuditPayloadKey.REVIEW_RECOMMENDATION_ID, RECOMMENDATION_ID)
                .put(BusinessAuditPayloadKey.APPROVAL_DECISION_ACTION, TestAuditCode.APPROVE)
                .build();
        BusinessAuditPayload offer = BusinessAuditPayload.builder()
                .put(BusinessAuditPayloadKey.LOAN_APPLICATION_ID, LOAN_APPLICATION_ID)
                .put(BusinessAuditPayloadKey.OFFER_STATUS, TestAuditCode.PENDING)
                .put(BusinessAuditPayloadKey.EXPIRY_DISCOVERY_TRIGGER, ExpiryDiscoveryTrigger.CUSTOMER_ACTION)
                .put(BusinessAuditPayloadKey.RESERVATION_RELEASE_TRIGGER, TestAuditCode.OFFER_EXPIRY)
                .build();
        BusinessAuditPayload customer = BusinessAuditPayload.builder()
                .put(BusinessAuditPayloadKey.CUSTOMER_ID, LOAN_APPLICATION_ID)
                .put(BusinessAuditPayloadKey.PROFILE_COMPLETION_STATUS, TestAuditCode.COMPLETE)
                .build();

        assertEquals(1, submission.values().size());
        assertEquals(3, movement.values().size());
        assertEquals(3, approval.values().size());
        assertEquals(4, offer.values().size());
        assertEquals(2, customer.values().size());
    }

    @Test
    void rejectsUnknownKey() {
        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class,
                () -> BusinessAuditPayload.fromStored(Map.of("unknownKey", "APPROVE"))
        );

        assertEquals("INVALID_AUDIT_PAYLOAD", exception.getErrorCode());
    }

    @Test
    void rejectsMalformedUuid() {
        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class,
                () -> BusinessAuditPayload.fromStored(Map.of(
                        BusinessAuditPayloadKey.LOAN_APPLICATION_ID.jsonName(),
                        "not-a-uuid"
                ))
        );

        assertEquals("INVALID_AUDIT_PAYLOAD", exception.getErrorCode());
    }

    @Test
    void rejectsMalformedCode() {
        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class,
                () -> BusinessAuditPayload.fromStored(Map.of(
                        BusinessAuditPayloadKey.OFFER_STATUS.jsonName(),
                        "pending"
                ))
        );

        assertEquals("INVALID_AUDIT_PAYLOAD", exception.getErrorCode());
    }

    @Test
    void rejectsDuplicateKey() {
        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class,
                () -> BusinessAuditPayload.builder()
                        .put(BusinessAuditPayloadKey.LOAN_APPLICATION_ID, LOAN_APPLICATION_ID)
                        .put(BusinessAuditPayloadKey.LOAN_APPLICATION_ID, LOAN_APPLICATION_ID)
        );

        assertEquals("DUPLICATE_AUDIT_PAYLOAD_KEY", exception.getErrorCode());
    }

    @Test
    void rejectsControlCharacters() {
        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class,
                () -> BusinessAuditPayload.fromStored(Map.of(
                        BusinessAuditPayloadKey.OFFER_STATUS.jsonName(),
                        "PENDING\n"
                ))
        );

        assertEquals("INVALID_AUDIT_PAYLOAD", exception.getErrorCode());
    }

    @Test
    void rejectsOversizedValue() {
        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class,
                () -> BusinessAuditPayload.fromStored(Map.of(
                        BusinessAuditPayloadKey.OFFER_STATUS.jsonName(),
                        "A".repeat(121)
                ))
        );

        assertEquals("INVALID_AUDIT_PAYLOAD", exception.getErrorCode());
    }

    @Test
    void rejectsForbiddenKeyAttempts() {
        for (String forbiddenKey : new String[] {
                "salary",
                "requestedAmount",
                "approvedAmount",
                "identityReference",
                "employeeCode",
                "bankAccountNumber",
                "documentContent",
                "ocrText",
                "password",
                "token",
                "secret",
                "internalNotes",
                "reason"
        }) {
            BusinessRuleViolationException exception = assertThrows(
                    BusinessRuleViolationException.class,
                    () -> BusinessAuditPayload.fromStored(Map.of(forbiddenKey, "SAFE_CODE"))
            );
            assertEquals("INVALID_AUDIT_PAYLOAD", exception.getErrorCode());
        }
    }

    private enum TestAuditCode {
        APPROVE,
        COMPLETE,
        OFFER_EXPIRY,
        PENDING,
        RESERVED
    }
}
