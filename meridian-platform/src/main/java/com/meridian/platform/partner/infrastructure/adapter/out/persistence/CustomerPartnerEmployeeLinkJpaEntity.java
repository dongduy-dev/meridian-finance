package com.meridian.platform.partner.infrastructure.adapter.out.persistence;

import com.meridian.platform.partner.domain.model.CustomerPartnerEmployeeLink;
import com.meridian.platform.partner.domain.model.CustomerPartnerEmployeeLinkStatus;
import com.meridian.platform.partner.domain.model.EmployeeVerificationOutcome;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "customer_partner_employee_links")
public class CustomerPartnerEmployeeLinkJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "partner_company_id", nullable = false)
    private UUID partnerCompanyId;

    @Column(name = "partner_employee_id", nullable = false)
    private UUID partnerEmployeeId;

    @Column(name = "source_import_batch_id", nullable = false)
    private UUID sourceImportBatchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_outcome", nullable = false)
    private EmployeeVerificationOutcome verificationOutcome;

    @Enumerated(EnumType.STRING)
    @Column(name = "link_status", nullable = false)
    private CustomerPartnerEmployeeLinkStatus linkStatus;

    @Column(name = "verified_identity_ref", nullable = false)
    private String verifiedIdentityRef;

    @Column(name = "verified_employee_code", nullable = false)
    private String verifiedEmployeeCode;

    @Column(name = "last_verified_at", nullable = false)
    private LocalDateTime lastVerifiedAt;

    @Column(name = "last_refreshed_at", nullable = false)
    private LocalDateTime lastRefreshedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected CustomerPartnerEmployeeLinkJpaEntity() {
    }

    public CustomerPartnerEmployeeLinkJpaEntity(CustomerPartnerEmployeeLink link) {
        LocalDateTime now = LocalDateTime.now();
        this.id = link.id();
        this.createdAt = now;
        apply(link, now);
    }

    public void updateFrom(CustomerPartnerEmployeeLink link) {
        apply(link, LocalDateTime.now());
    }

    private void apply(CustomerPartnerEmployeeLink link, LocalDateTime updatedAt) {
        this.customerId = link.customerId();
        this.partnerCompanyId = link.partnerCompanyId();
        this.partnerEmployeeId = link.partnerEmployeeId();
        this.sourceImportBatchId = link.sourceImportBatchId();
        this.verificationOutcome = link.verificationOutcome();
        this.linkStatus = link.linkStatus();
        this.verifiedIdentityRef = link.verifiedIdentityRef();
        this.verifiedEmployeeCode = link.verifiedEmployeeCode();
        this.lastVerifiedAt = link.lastVerifiedAt();
        this.lastRefreshedAt = link.lastRefreshedAt();
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public UUID getPartnerCompanyId() {
        return partnerCompanyId;
    }

    public UUID getPartnerEmployeeId() {
        return partnerEmployeeId;
    }

    public UUID getSourceImportBatchId() {
        return sourceImportBatchId;
    }

    public EmployeeVerificationOutcome getVerificationOutcome() {
        return verificationOutcome;
    }

    public CustomerPartnerEmployeeLinkStatus getLinkStatus() {
        return linkStatus;
    }

    public String getVerifiedIdentityRef() {
        return verifiedIdentityRef;
    }

    public String getVerifiedEmployeeCode() {
        return verifiedEmployeeCode;
    }

    public LocalDateTime getLastVerifiedAt() {
        return lastVerifiedAt;
    }

    public LocalDateTime getLastRefreshedAt() {
        return lastRefreshedAt;
    }
}
