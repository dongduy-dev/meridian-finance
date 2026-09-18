package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.domain.model.AssistedOriginationCase;
import com.meridian.platform.loan.domain.model.AssistedOriginationCaseStatus;
import com.meridian.platform.loan.domain.model.ProductCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "assisted_origination_cases")
public class AssistedOriginationCaseJpaEntity {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_code", nullable = false)
    private ProductCode productCode;

    @Column(name = "customer_id")
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AssistedOriginationCaseStatus status;

    @Column(name = "created_by_staff_user_id", nullable = false)
    private UUID createdByStaffUserId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "terminal_at")
    private LocalDateTime terminalAt;

    @Column(name = "loan_application_id")
    private UUID loanApplicationId;

    protected AssistedOriginationCaseJpaEntity() {
    }

    AssistedOriginationCaseJpaEntity(AssistedOriginationCase value) {
        update(value);
    }

    void update(AssistedOriginationCase value) {
        id = value.id();
        productCode = value.productCode();
        customerId = value.customerId();
        status = value.status();
        createdByStaffUserId = value.createdByStaffUserId();
        createdAt = value.createdAt();
        updatedAt = value.updatedAt();
        terminalAt = value.terminalAt();
        loanApplicationId = value.loanApplicationId();
    }

    AssistedOriginationCase toDomain() {
        return new AssistedOriginationCase(
                id, productCode, customerId, status, createdByStaffUserId,
                createdAt, updatedAt, terminalAt, loanApplicationId
        );
    }
}
