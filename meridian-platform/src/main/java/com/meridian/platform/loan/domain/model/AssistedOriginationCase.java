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
        LocalDateTime terminalAt,
        UUID loanApplicationId
) {
    public AssistedOriginationCase(
            UUID id,
            ProductCode productCode,
            UUID customerId,
            AssistedOriginationCaseStatus status,
            UUID createdByStaffUserId,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            LocalDateTime terminalAt
    ) {
        this(id, productCode, customerId, status, createdByStaffUserId,
                createdAt, updatedAt, terminalAt, null);
    }

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
        if (status == AssistedOriginationCaseStatus.OPEN
                && (terminalAt != null || loanApplicationId != null)) {
            throw new IllegalArgumentException("Open assisted-origination cases cannot have terminal results.");
        }
        if (status == AssistedOriginationCaseStatus.ABANDONED
                && (terminalAt == null || loanApplicationId != null)) {
            throw new IllegalArgumentException("Abandoned assisted-origination cases require only terminalAt.");
        }
        if (status == AssistedOriginationCaseStatus.COMPLETED
                && (customerId == null || terminalAt == null || loanApplicationId == null)) {
            throw new IllegalArgumentException(
                    "Completed assisted-origination cases require Customer, Loan Application, and terminalAt."
            );
        }
    }

    public AssistedOriginationCase associateCustomer(UUID selectedCustomerId, LocalDateTime now) {
        requireOpen();
        return new AssistedOriginationCase(
                id, productCode, Objects.requireNonNull(selectedCustomerId), status,
                createdByStaffUserId, createdAt, Objects.requireNonNull(now), null, null
        );
    }

    public AssistedOriginationCase abandon(LocalDateTime now) {
        requireOpen();
        return new AssistedOriginationCase(
                id, productCode, customerId, AssistedOriginationCaseStatus.ABANDONED,
                createdByStaffUserId, createdAt, Objects.requireNonNull(now), now, null
        );
    }

    public AssistedOriginationCase complete(UUID resultingLoanApplicationId, LocalDateTime now) {
        requireOpen();
        if (customerId == null) {
            throw new BusinessStateConflictException(
                    "ASSISTED_ORIGINATION_CUSTOMER_REQUIRED",
                    "A selected Customer is required before assisted origination can be completed."
            );
        }
        LocalDateTime completedAt = Objects.requireNonNull(now, "now must not be null");
        return new AssistedOriginationCase(
                id, productCode, customerId, AssistedOriginationCaseStatus.COMPLETED,
                createdByStaffUserId, createdAt, completedAt, completedAt,
                Objects.requireNonNull(resultingLoanApplicationId, "loanApplicationId must not be null")
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
