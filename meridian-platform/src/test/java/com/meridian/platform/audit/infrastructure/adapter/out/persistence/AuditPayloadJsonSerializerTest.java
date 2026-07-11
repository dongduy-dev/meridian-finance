package com.meridian.platform.audit.infrastructure.adapter.out.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridian.platform.audit.domain.model.AuditEventPayloadEntry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuditPayloadJsonSerializerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AuditPayloadJsonSerializer serializer = new AuditPayloadJsonSerializer(objectMapper);

    @Test
    void serializesPayloadAsJsonObject() throws Exception {
        String json = serializer.serialize(List.of(
                new AuditEventPayloadEntry("PRODUCT_CODE", "SALARY_ADVANCE"),
                new AuditEventPayloadEntry("MOVEMENT_TYPE", "RESERVED")
        ));

        assertEquals(Map.of("PRODUCT_CODE", "SALARY_ADVANCE", "MOVEMENT_TYPE", "RESERVED"),
                objectMapper.readValue(json, Map.class));
    }

    @Test
    void escapesQuotesAndBackslashesThroughObjectMapper() throws Exception {
        String json = serializer.serialize(List.of(
                new AuditEventPayloadEntry("PRODUCT_CODE", "VALUE\"WITH\\BACKSLASH")
        ));

        assertEquals("VALUE\"WITH\\BACKSLASH", objectMapper.readValue(json, Map.class).get("PRODUCT_CODE"));
    }

    @Test
    void rejectsDuplicateKeysDefensively() {
        assertThrows(IllegalArgumentException.class, () -> serializer.serialize(List.of(
                new AuditEventPayloadEntry("PRODUCT_CODE", "SALARY_ADVANCE"),
                new AuditEventPayloadEntry("PRODUCT_CODE", "SALARY_ADVANCE")
        )));
    }
}
