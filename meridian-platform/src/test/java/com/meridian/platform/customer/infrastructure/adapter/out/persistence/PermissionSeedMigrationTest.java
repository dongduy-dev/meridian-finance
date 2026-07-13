package com.meridian.platform.customer.infrastructure.adapter.out.persistence;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionSeedMigrationTest {

    private static final Pattern PERMISSION_INSERT = Pattern.compile(
            "(?is)INSERT\\s+INTO\\s+permissions\\s*\\([^)]*\\)\\s*VALUES\\s*(.*?)(?:ON\\s+CONFLICT|;)"
    );
    private static final Pattern UUID_LITERAL = Pattern.compile(
            "'([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})'"
    );

    @Test
    void permissionSeedIdsAreUniqueAcrossMigrations() throws IOException {
        Map<String, String> firstMigrationById = new HashMap<>();
        List<String> duplicates = new ArrayList<>();

        try (Stream<Path> migrations = Files.list(Path.of("src/main/resources/db/migration"))) {
            for (Path migration : migrations
                    .filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .sorted()
                    .toList()) {
                String sql = Files.readString(migration).replace("\r\n", "\n");
                Matcher insertMatcher = PERMISSION_INSERT.matcher(sql);
                while (insertMatcher.find()) {
                    Matcher uuidMatcher = UUID_LITERAL.matcher(insertMatcher.group(1));
                    while (uuidMatcher.find()) {
                        String permissionId = uuidMatcher.group(1).toLowerCase();
                        String previousMigration = firstMigrationById.putIfAbsent(
                                permissionId,
                                migration.getFileName().toString()
                        );
                        if (previousMigration != null) {
                            duplicates.add(permissionId + " in " + previousMigration
                                    + " and " + migration.getFileName());
                        }
                    }
                }
            }
        }

        assertTrue(duplicates.isEmpty(), "Duplicate permission seed IDs: " + duplicates);
    }
}
