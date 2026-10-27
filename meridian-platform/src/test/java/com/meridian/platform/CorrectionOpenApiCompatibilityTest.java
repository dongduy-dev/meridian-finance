package com.meridian.platform;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "meridian.loan.offer-expiry.enabled=false")
@AutoConfigureMockMvc
class CorrectionOpenApiCompatibilityTest {
    private static final String TEST_SCHEMA = "meridian_s3_api_" + UUID.randomUUID().toString().replace("-", "");
    private static final JsonMapper JSON = JsonMapper.builder().build();
    @Autowired private MockMvc mvc;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.schemas", () -> TEST_SCHEMA);
        registry.add("spring.flyway.default-schema", () -> TEST_SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> TEST_SCHEMA);
    }

    @Test
    void affectedEndpointsAndTransitiveSchemasMatchS2Baseline() throws Exception {
        JsonNode api = JSON.readTree(mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        ObjectNode contract = JSON.createObjectNode();
        ObjectNode paths = contract.putObject("paths");
        api.path("paths").properties().forEach(entry -> {
            String path = entry.getKey();
            if (path.endsWith("/approval-decisions") || path.endsWith("/review-recommendations")
                    || path.contains("verification/complete") || path.endsWith("/reviews")) {
                paths.set(path, entry.getValue());
            }
        });
        assertEquals(5, paths.size(), "all five S3 endpoint families must be compared");
        ObjectNode schemas = contract.putObject("schemas");
        var pending = new ArrayDeque<String>();
        collectReferences(paths, pending);
        Set<String> visited = new HashSet<>();
        while (!pending.isEmpty()) {
            String name = pending.removeFirst();
            if (visited.add(name)) {
                JsonNode schema = api.path("components").path("schemas").path(name);
                schemas.set(name, schema);
                collectReferences(schema, pending);
            }
        }
        Path baseline = Path.of("src/test/resources/contracts/s3-correction-openapi-s2.json");
        assertEquals(JSON.readTree(Files.readString(baseline)), contract);
    }

    private static void collectReferences(JsonNode node, ArrayDeque<String> pending) {
        if (node.isObject()) {
            JsonNode ref = node.get("$ref");
            if (ref != null && ref.asString().startsWith("#/components/schemas/")) {
                pending.add(ref.asString().substring("#/components/schemas/".length()));
            }
            node.properties().forEach(entry -> collectReferences(entry.getValue(), pending));
        } else if (node.isArray()) {
            node.forEach(child -> collectReferences(child, pending));
        }
    }
}
