package com.meridian.platform.document.application.port.out;

import java.time.Instant;
import java.util.Objects;

public record StoredObjectCandidate(String storageKey, Instant createdAt) {
    public StoredObjectCandidate {
        Objects.requireNonNull(storageKey, "storageKey must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }
}
