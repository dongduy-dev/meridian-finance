package com.meridian.platform.audit.infrastructure.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import com.meridian.platform.audit.domain.model.AuditEventPayloadEntry;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AuditPayloadJsonSerializer {

    private final ObjectMapper objectMapper;

    public AuditPayloadJsonSerializer(@Qualifier("auditObjectMapper") ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String serialize(List<AuditEventPayloadEntry> payload) {
        Map<String, String> objectPayload = new LinkedHashMap<>();
        for (AuditEventPayloadEntry entry : payload) {
            if (objectPayload.put(entry.key(), entry.value()) != null) {
                throw new IllegalArgumentException("payload contains duplicate key " + entry.key());
            }
        }

        try {
            return objectMapper.writeValueAsString(objectPayload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize audit payload", exception);
        }
    }
}
