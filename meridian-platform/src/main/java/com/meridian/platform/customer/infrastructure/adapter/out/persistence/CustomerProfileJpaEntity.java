package com.meridian.platform.customer.infrastructure.adapter.out.persistence;

import com.meridian.platform.customer.domain.model.CustomerProfile;
import com.meridian.platform.customer.domain.model.ProtectedSensitiveValue;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "customer_profiles")
public class CustomerProfileJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "identity_reference_ciphertext", nullable = false)
    private String identityReferenceCiphertext;

    @Column(name = "identity_reference_fingerprint", nullable = false)
    private String identityReferenceFingerprint;

    @Column(name = "identity_reference_last_four", nullable = false)
    private String identityReferenceLastFour;

    @Column(name = "phone_number", nullable = false)
    private String phoneNumber;

    @Column(name = "residential_address", nullable = false)
    private String residentialAddress;

    @Column(name = "employment_status", nullable = false)
    private String employmentStatus;

    @Column(name = "employer_name")
    private String employerName;

    @Column(name = "terms_consent_accepted", nullable = false)
    private boolean termsConsentAccepted;

    @Column(name = "data_processing_consent_accepted", nullable = false)
    private boolean dataProcessingConsentAccepted;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected CustomerProfileJpaEntity() {
    }

    public CustomerProfileJpaEntity(CustomerProfile profile) {
        this.id = profile.id() == null ? UUID.randomUUID() : profile.id();
        apply(profile);
        this.createdAt = profile.createdAt() == null ? LocalDateTime.now() : profile.createdAt();
        this.updatedAt = profile.updatedAt() == null ? LocalDateTime.now() : profile.updatedAt();
    }

    public void updateFrom(CustomerProfile profile) {
        apply(profile);
        this.updatedAt = profile.updatedAt() == null ? LocalDateTime.now() : profile.updatedAt();
    }

    private void apply(CustomerProfile profile) {
        this.customerId = profile.customerId();
        this.fullName = profile.fullName();
        this.identityReferenceCiphertext = profile.identityReference().ciphertext();
        this.identityReferenceFingerprint = profile.identityReference().fingerprint();
        this.identityReferenceLastFour = profile.identityReference().lastFour();
        this.phoneNumber = profile.phoneNumber();
        this.residentialAddress = profile.residentialAddress();
        this.employmentStatus = profile.employmentStatus();
        this.employerName = profile.employerName();
        this.termsConsentAccepted = profile.termsConsentAccepted();
        this.dataProcessingConsentAccepted = profile.dataProcessingConsentAccepted();
    }

    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public String getFullName() {
        return fullName;
    }

    public String getIdentityReferenceCiphertext() {
        return identityReferenceCiphertext;
    }

    public String getIdentityReferenceFingerprint() {
        return identityReferenceFingerprint;
    }

    public String getIdentityReferenceLastFour() {
        return identityReferenceLastFour;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getResidentialAddress() {
        return residentialAddress;
    }

    public String getEmploymentStatus() {
        return employmentStatus;
    }

    public String getEmployerName() {
        return employerName;
    }

    public boolean isTermsConsentAccepted() {
        return termsConsentAccepted;
    }

    public boolean isDataProcessingConsentAccepted() {
        return dataProcessingConsentAccepted;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public CustomerProfile toDomain() {
        return new CustomerProfile(
                id,
                customerId,
                fullName,
                new ProtectedSensitiveValue(
                        identityReferenceCiphertext,
                        identityReferenceFingerprint,
                        identityReferenceLastFour
                ),
                phoneNumber,
                residentialAddress,
                employmentStatus,
                employerName,
                termsConsentAccepted,
                dataProcessingConsentAccepted,
                createdAt,
                updatedAt
        );
    }
}