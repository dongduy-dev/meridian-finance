package com.meridian.platform.audit.infrastructure.adapter.out.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuditJacksonConfig {

    @Bean("auditObjectMapper")
    public ObjectMapper auditObjectMapper() {
        return new ObjectMapper();
    }
}