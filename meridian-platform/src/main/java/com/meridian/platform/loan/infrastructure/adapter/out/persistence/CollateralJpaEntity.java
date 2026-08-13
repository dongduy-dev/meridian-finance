package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.domain.model.Collateral;
import com.meridian.platform.loan.domain.model.CollateralType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "collaterals")
public class CollateralJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "loan_application_id", nullable = false)
    private UUID loanApplicationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "collateral_type", nullable = false, length = 50)
    private CollateralType collateralType;

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @Column(name = "estimated_value", nullable = false, precision = 19, scale = 2)
    private BigDecimal estimatedValue;

    @Column(name = "ownership_status", nullable = false, length = 200)
    private String ownershipStatus;

    @Column(name = "condition_note", nullable = false, length = 500)
    private String conditionNote;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected CollateralJpaEntity() {
    }

    public CollateralJpaEntity(Collateral collateral) {
        this.id = collateral.id();
        this.loanApplicationId = collateral.loanApplicationId();
        this.collateralType = collateral.collateralType();
        this.description = collateral.description();
        this.estimatedValue = collateral.estimatedValue();
        this.ownershipStatus = collateral.ownershipStatus();
        this.conditionNote = collateral.conditionNote();
        this.createdAt = collateral.createdAt();
    }

    public Collateral toDomain() {
        return new Collateral(
                id,
                loanApplicationId,
                collateralType,
                description,
                estimatedValue,
                ownershipStatus,
                conditionNote,
                createdAt
        );
    }
}
