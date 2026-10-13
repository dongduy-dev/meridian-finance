package com.meridian.platform.document.infrastructure.adapter.out.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

/** Reversible infrastructure probe; it creates no business document or version. */
@Component("documentStorageHealthIndicator")
public class DocumentStorageHealthIndicator implements HealthIndicator {
    private final Path root;
    private static final byte[] PROBE = new byte[]{77, 69, 82};

    public DocumentStorageHealthIndicator(
            @Value("${meridian.document.storage-root:${java.io.tmpdir}/meridian-documents}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    @Override
    public synchronized Health health() {
        Path staged = null;
        Path directory = null;
        Path finalized = null;
        boolean healthy = false;
        try {
            for (Path path : new Path[]{root, root.resolve("staging"), root.resolve("objects")}) {
                if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
                    return Health.down().build();
                }
            }
            staged = Files.createTempFile(root.resolve("staging"), ".readiness-", ".probe");
            Files.write(staged, PROBE);
            directory = Files.createTempDirectory(root.resolve("objects"), ".readiness-");
            finalized = directory.resolve("probe");
            Files.move(staged, finalized, StandardCopyOption.ATOMIC_MOVE);
            healthy = Arrays.equals(PROBE, Files.readAllBytes(finalized));
        } catch (Exception ignored) {
            // Dependency details and filesystem paths must not enter health output/logs.
        } finally {
            for (Path path : new Path[]{finalized, staged, directory}) {
                try {
                    if (path != null) Files.deleteIfExists(path);
                } catch (Exception ignored) {
                    healthy = false;
                }
            }
        }
        return healthy ? Health.up().build() : Health.down().build();
    }
}
