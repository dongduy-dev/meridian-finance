package com.meridian.platform.document.application.port.out;

import java.util.Objects;

public record StoredObject(String storageKey) {
    public StoredObject {
        Objects.requireNonNull(storageKey, "storageKey must not be null");
    }
}
