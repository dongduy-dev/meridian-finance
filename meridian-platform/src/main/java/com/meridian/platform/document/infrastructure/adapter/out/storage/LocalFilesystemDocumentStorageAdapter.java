package com.meridian.platform.document.infrastructure.adapter.out.storage;

import com.meridian.platform.document.application.port.out.DocumentStoragePort;
import com.meridian.platform.document.application.port.out.StagedDocument;
import com.meridian.platform.document.application.port.out.StoredObject;
import com.meridian.platform.document.application.port.out.StoredObjectCandidate;
import com.meridian.platform.document.domain.model.DocumentVersion;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.ServiceUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
public class LocalFilesystemDocumentStorageAdapter implements DocumentStoragePort {

    private static final int BUFFER_SIZE = 8192;
    private static final int SIGNATURE_SIZE = 8;
    private static final Set<String> ALLOWED_MIME_TYPES = DocumentVersion.ALLOWED_MIME_TYPES;

    private final Path root;
    private final Path stagingRoot;
    private final Path objectsRoot;

    public LocalFilesystemDocumentStorageAdapter(
            @Value("${meridian.document.storage-root:${java.io.tmpdir}/meridian-documents}") String configuredRoot
    ) {
        this.root = Path.of(configuredRoot).toAbsolutePath().normalize();
        this.stagingRoot = root.resolve("staging").normalize();
        this.objectsRoot = root.resolve("objects").normalize();
        initializeRoots();
    }

    @Override
    public StagedDocument stage(InputStream content, String declaredMimeType, String originalFilename) {
        validateInput(content, declaredMimeType, originalFilename);
        UUID stagingId = UUID.randomUUID();
        Path stagingPath = contained(stagingRoot.resolve(stagingId + ".stage"), stagingRoot);
        MessageDigest digest = sha256();
        byte[] signature = new byte[SIGNATURE_SIZE];
        int signatureLength = 0;
        long size = 0;

        try (InputStream input = content;
             OutputStream output = Files.newOutputStream(
                     stagingPath,
                     StandardOpenOption.CREATE_NEW,
                     StandardOpenOption.WRITE
             )) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (read == 0) {
                    continue;
                }
                size += read;
                if (size > DocumentVersion.MAX_BYTE_SIZE) {
                    throw invalid("Document exceeds the 10 MiB size limit.");
                }
                int copyLength = Math.min(read, SIGNATURE_SIZE - signatureLength);
                if (copyLength > 0) {
                    System.arraycopy(buffer, 0, signature, signatureLength, copyLength);
                    signatureLength += copyLength;
                }
                digest.update(buffer, 0, read);
                output.write(buffer, 0, read);
            }
        } catch (RuntimeException exception) {
            deleteQuietly(stagingPath);
            throw exception;
        } catch (IOException exception) {
            deleteQuietly(stagingPath);
            throw unavailable();
        }

        if (size == 0) {
            deleteQuietly(stagingPath);
            throw invalid("Document must not be empty.");
        }
        String detectedMimeType = detectMimeType(signature, signatureLength);
        if (!declaredMimeType.equals(detectedMimeType)) {
            deleteQuietly(stagingPath);
            throw invalid("Declared document MIME type does not match the detected file signature.");
        }

        return new StagedDocument(
                stagingId,
                originalFilename,
                declaredMimeType,
                detectedMimeType,
                size,
                HexFormat.of().formatHex(digest.digest())
        );
    }

    @Override
    public StoredObject commit(StagedDocument stagedDocument) {
        Path stagedPath = stagingPath(stagedDocument);
        String objectId = UUID.randomUUID().toString().replace("-", "");
        String storageKey = objectId.substring(0, 2) + "/" + objectId;
        Path finalPath = contained(objectsRoot.resolve(storageKey), objectsRoot);
        try {
            ensureRootSafety();
            Files.createDirectories(finalPath.getParent());
            if (Files.isSymbolicLink(finalPath.getParent())) {
                throw unavailable();
            }
            Files.move(stagedPath, finalPath, StandardCopyOption.ATOMIC_MOVE);
            return new StoredObject(storageKey);
        } catch (AtomicMoveNotSupportedException exception) {
            throw unavailable();
        } catch (IOException exception) {
            throw unavailable();
        }
    }

    @Override
    public InputStream open(String storageKey) {
        Path path = storagePath(storageKey);
        try {
            if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                throw unavailable();
            }
            return Files.newInputStream(path, StandardOpenOption.READ);
        } catch (IOException exception) {
            throw unavailable();
        }
    }

    @Override
    public void discardStaged(StagedDocument stagedDocument) {
        deleteQuietly(stagingPath(stagedDocument));
    }

    @Override
    public void deleteFinal(String storageKey) {
        Path path = storagePath(storageKey);
        if (Files.isSymbolicLink(path)) {
            return;
        }
        deleteQuietly(path);
    }

    @Override
    public int deleteStagingFilesOlderThan(Instant cutoff) {
        int deleted = 0;
        try (var paths = Files.list(stagingRoot)) {
            for (Path path : paths.toList()) {
                if (!Files.isSymbolicLink(path)
                        && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                        && Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toInstant().isBefore(cutoff)) {
                    Files.deleteIfExists(path);
                    deleted++;
                }
            }
        } catch (IOException exception) {
            throw unavailable();
        }
        return deleted;
    }

    @Override
    public List<StoredObjectCandidate> findFinalObjectsOlderThan(Instant cutoff) {
        List<StoredObjectCandidate> candidates = new ArrayList<>();
        try (var paths = Files.walk(objectsRoot)) {
            for (Path path : paths.toList()) {
                if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                Instant createdAt = Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toInstant();
                if (createdAt.isBefore(cutoff)) {
                    String key = objectsRoot.relativize(path).toString().replace('\\', '/');
                    candidates.add(new StoredObjectCandidate(key, createdAt));
                }
            }
        } catch (IOException exception) {
            throw unavailable();
        }
        return List.copyOf(candidates);
    }

    private void initializeRoots() {
        try {
            Files.createDirectories(stagingRoot);
            Files.createDirectories(objectsRoot);
            ensureRootSafety();
        } catch (IOException exception) {
            throw unavailable();
        }
    }

    private void ensureRootSafety() {
        if (!stagingRoot.startsWith(root) || !objectsRoot.startsWith(root)
                || Files.isSymbolicLink(root)
                || Files.isSymbolicLink(stagingRoot)
                || Files.isSymbolicLink(objectsRoot)) {
            throw unavailable();
        }
    }

    private Path stagingPath(StagedDocument stagedDocument) {
        return contained(stagingRoot.resolve(stagedDocument.stagingId() + ".stage"), stagingRoot);
    }

    private Path storagePath(String storageKey) {
        if (storageKey == null || storageKey.isBlank() || storageKey.contains("\\")
                || storageKey.startsWith("/") || storageKey.contains("..")) {
            throw unavailable();
        }
        return contained(objectsRoot.resolve(storageKey), objectsRoot);
    }

    private Path contained(Path candidate, Path expectedRoot) {
        Path normalized = candidate.toAbsolutePath().normalize();
        if (!normalized.startsWith(expectedRoot)) {
            throw unavailable();
        }
        return normalized;
    }

    private void validateInput(InputStream content, String declaredMimeType, String originalFilename) {
        if (content == null || !ALLOWED_MIME_TYPES.contains(declaredMimeType)) {
            throw invalid("Document MIME type is not allowed.");
        }
        if (originalFilename == null || originalFilename.isBlank() || originalFilename.length() > 255
                || originalFilename.contains("/") || originalFilename.contains("\\")
                || originalFilename.contains("..")
                || originalFilename.chars().anyMatch(Character::isISOControl)) {
            throw invalid("Original filename is invalid.");
        }
    }

    private String detectMimeType(byte[] signature, int length) {
        if (length >= 5
                && signature[0] == '%'
                && signature[1] == 'P'
                && signature[2] == 'D'
                && signature[3] == 'F'
                && signature[4] == '-') {
            return "application/pdf";
        }
        if (length >= 3
                && (signature[0] & 0xff) == 0xff
                && (signature[1] & 0xff) == 0xd8
                && (signature[2] & 0xff) == 0xff) {
            return "image/jpeg";
        }
        byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        if (length >= png.length) {
            boolean matches = true;
            for (int index = 0; index < png.length; index++) {
                matches &= signature[index] == png[index];
            }
            if (matches) {
                return "image/png";
            }
        }
        throw invalid("Document file signature is not allowed.");
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available.", exception);
        }
    }

    private void deleteQuietly(Path path) {
        try {
            if (path != null && !Files.isSymbolicLink(path)) {
                Files.deleteIfExists(path);
            }
        } catch (IOException ignored) {
            // A later conservative orphan reconciliation retries cleanup.
        }
    }

    private BusinessRuleViolationException invalid(String message) {
        return new BusinessRuleViolationException("INVALID_DOCUMENT_UPLOAD", message);
    }

    private ServiceUnavailableException unavailable() {
        return new ServiceUnavailableException(
                "DOCUMENT_STORAGE_UNAVAILABLE",
                "Document storage is temporarily unavailable."
        );
    }
}
