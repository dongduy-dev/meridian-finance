package com.meridian.platform.document.infrastructure.adapter.out.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.health.contributor.Status;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DocumentStorageHealthIndicatorTest {
    @TempDir Path root;

    @Test
    void stagesMovesReadsAndCleansWithoutTouchingExistingEvidence() throws Exception {
        Files.createDirectory(root.resolve("staging")); Files.createDirectory(root.resolve("objects"));
        Path evidence = root.resolve("objects/preserved"); Files.writeString(evidence, "fictional evidence");
        var indicator = new DocumentStorageHealthIndicator(root.toString());
        assertEquals(Status.UP, indicator.health().getStatus());
        assertEquals("fictional evidence", Files.readString(evidence));
        try (var staged = Files.list(root.resolve("staging")); var objects = Files.list(root.resolve("objects"))) {
            assertEquals(0, staged.count()); assertEquals(1, objects.count());
        }
    }

    @Test
    void failsSafelyWhenStorageIsNotFunctional() throws Exception {
        Files.createDirectory(root.resolve("staging")); Files.writeString(root.resolve("objects"), "not a directory");
        var health = new DocumentStorageHealthIndicator(root.toString()).health();
        assertEquals(Status.DOWN, health.getStatus()); assertTrue(health.getDetails().isEmpty());
    }
}
