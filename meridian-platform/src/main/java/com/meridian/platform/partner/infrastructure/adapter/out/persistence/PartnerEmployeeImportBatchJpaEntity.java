package com.meridian.platform.partner.infrastructure.adapter.out.persistence;

import com.meridian.platform.partner.domain.model.PartnerEmployeeImportBatchStatus;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "partner_employee_import_batches")
public class PartnerEmployeeImportBatchJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "partner_company_id", nullable = false)
    private UUID partnerCompanyId;

    @Column(name = "effective_month", nullable = false)
    private String effectiveMonth;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PartnerEmployeeImportBatchStatus status;

    @Column(name = "valid_row_count", nullable = false)
    private int validRowCount;

    @Column(name = "invalid_row_count", nullable = false)
    private int invalidRowCount;

    protected PartnerEmployeeImportBatchJpaEntity() {
    }

    public UUID getId() {
        return id;
    }

    public UUID getPartnerCompanyId() {
        return partnerCompanyId;
    }

    public String getEffectiveMonth() {
        return effectiveMonth;
    }

    public PartnerEmployeeImportBatchStatus getStatus() {
        return status;
    }

    public int getValidRowCount() {
        return validRowCount;
    }

    public int getInvalidRowCount() {
        return invalidRowCount;
    }
}