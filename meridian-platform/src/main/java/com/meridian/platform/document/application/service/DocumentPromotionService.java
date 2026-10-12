package com.meridian.platform.document.application.service;

import com.meridian.platform.document.application.port.out.DocumentMalwareScanPort;
import com.meridian.platform.document.application.port.out.DocumentStoragePort;
import com.meridian.platform.document.application.port.out.StagedDocument;
import com.meridian.platform.document.application.port.out.StoredObject;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.ServiceUnavailableException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class DocumentPromotionService {

    private final DocumentStoragePort storage;
    private final DocumentMalwareScanPort scanner;

    public DocumentPromotionService(DocumentStoragePort storage, DocumentMalwareScanPort scanner) {
        this.storage = storage;
        this.scanner = scanner;
    }

    public StoredObject promote(StagedDocument staged) {
        // Callers resolve exact replay first and retain their existing staging/rollback cleanup.
        MessageDigest digest = sha256();
        try (InputStream content = new DigestInputStream(storage.openStaged(staged), digest)) {
            DocumentMalwareScanPort.ScanResult result = scanner.scan(content, staged.byteSize());
            if (result == DocumentMalwareScanPort.ScanResult.UNSAFE) {
                throw new BusinessRuleViolationException(
                        "UNSAFE_DOCUMENT_UPLOAD", "Document failed the required safety check.");
            }
            if (result != DocumentMalwareScanPort.ScanResult.CLEAN) {
                throw scanUnavailable();
            }
            if (!HexFormat.of().formatHex(digest.digest()).equals(staged.sha256Hex())) {
                throw scanUnavailable();
            }
        } catch (IOException exception) {
            throw scanUnavailable();
        }
        return storage.commit(staged);
    }

    private static ServiceUnavailableException scanUnavailable() {
        return new ServiceUnavailableException(
                "DOCUMENT_SCAN_UNAVAILABLE", "Document safety checking is temporarily unavailable.");
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available.", exception);
        }
    }
}
