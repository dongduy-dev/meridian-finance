package com.meridian.platform.customer.infrastructure.adapter.out.persistence;

import com.meridian.platform.customer.domain.model.Customer;
import com.meridian.platform.customer.domain.model.CustomerStatus;
import com.meridian.platform.customer.domain.model.ProfileCompletionStatus;
import com.meridian.platform.customer.domain.model.VerificationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "customers")
public class CustomerJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "customer_number", nullable = false)
    private String customerNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CustomerStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false)
    private VerificationStatus verificationStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "profile_completion_status", nullable = false)
    private ProfileCompletionStatus profileCompletionStatus;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected CustomerJpaEntity() {
    }

    public CustomerJpaEntity(Customer customer) {
        LocalDateTime now = LocalDateTime.now();
        this.id = customer.id();
        this.createdAt = customer.createdAt() == null ? now : customer.createdAt();
        apply(customer, customer.updatedAt() == null ? now : customer.updatedAt());
    }

    public void updateFrom(Customer customer) {
        apply(customer, customer.updatedAt() == null ? LocalDateTime.now() : customer.updatedAt());
    }

    private void apply(Customer customer, LocalDateTime updatedAt) {
        Objects.requireNonNull(customer, "customer must not be null");
        this.customerNumber = customer.customerNumber();
        this.status = customer.status();
        this.verificationStatus = customer.verificationStatus();
        this.profileCompletionStatus = customer.profileCompletionStatus();
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getCustomerNumber() {
        return customerNumber;
    }

    public CustomerStatus getStatus() {
        return status;
    }

    public VerificationStatus getVerificationStatus() {
        return verificationStatus;
    }

    public ProfileCompletionStatus getProfileCompletionStatus() {
        return profileCompletionStatus;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}