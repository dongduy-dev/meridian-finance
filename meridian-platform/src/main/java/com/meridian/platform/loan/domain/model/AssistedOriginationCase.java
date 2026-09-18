package com.meridian.platform.loan.domain.model;

import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public record AssistedOriginationCase(
        UUID id,
        ProductCode productCode,
        UUID customerId,
        AssistedOriginationCaseStatus status,
        UUID createdByStaffUserId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime terminalAt
) {
    public AssistedOriginationCase {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(productCode, "productCode must not be null");
        if (productCode == ProductCode.SALARY_ADVANCE) {
            throw new IllegalArgumentException("Salary Advance does not support Staff-assisted origination.");
        }
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdByStaffUserId, "createdByStaffUserId must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if ((status == AssistedOriginationCaseStatus.OPEN) != (terminalAt == null)) {
            throw new IllegalArgumentException("Only terminal assisted-origination cases require terminalAt.");
        }
    }

    public AssistedOriginationCase associateCustomer(UUID selectedCustomerId, LocalDateTime now) {
        requireOpen();
        return new AssistedOriginationCase(
                id, productCode, Objects.requireNonNull(selectedCustomerId), status,
                createdByStaffUserId, createdAt, Objects.requireNonNull(now), null
        );
    }

    public AssistedOriginationCase abandon(LocalDateTime now) {
        requireOpen();
        return new AssistedOriginationCase(
                id, productCode, customerId, AssistedOriginationCaseStatus.ABANDONED,
                createdByStaffUserId, createdAt, Objects.requireNonNull(now), now
        );
    }

    public void requireOpen() {
        if (status != AssistedOriginationCaseStatus.OPEN) {
            throw new BusinessStateConflictException(
                    "ASSISTED_ORIGINATION_CASE_NOT_OPEN",
                    "Assisted origination case is no longer open."
            );
        }
    }
}
