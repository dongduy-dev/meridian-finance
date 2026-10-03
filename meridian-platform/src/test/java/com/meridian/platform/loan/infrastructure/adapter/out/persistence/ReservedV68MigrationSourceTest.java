package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReservedV68MigrationSourceTest {
    @Test
    void reservesLatestVersionWithoutBusinessSchemaOrDataMutation() throws Exception {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V68__reserve_schema_version.sql"))
                .replace("\r\n", "\n");
        String snapshot = Files.readString(Path.of("../docs/database/MER-DB-CURRENT-SCHEMA.sql"));

        assertEquals("-- Intentionally reserved migration version.\n"
                + "-- V68 introduces no Meridian business-schema or data change.\n\nSELECT 1;\n", migration);
        assertTrue(snapshot.contains("Snapshot source: migrations V1 through V70"));
    }
}
